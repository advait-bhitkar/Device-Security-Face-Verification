package com.silverstudio.devicesecurity.fragment

import android.Manifest
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.android.volley.AuthFailureError
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.silverstudio.devicesecurity.R
import com.silverstudio.devicesecurity.aws.AWSUtils
import com.silverstudio.devicesecurity.aws.AwsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VerifyFaceFragment : Fragment(), AWSUtils.OnAwsImageUploadListener2,
    AWSUtils.OnAwsImageUploadListener {


    private lateinit var imageUrl1:String
    private lateinit var imageUrl2: String
    private lateinit var previewView: PreviewView
    private lateinit var volleyRequestQueue: RequestQueue
    private lateinit var dialog: ProgressDialog

    private lateinit var uploadImageButton: MaterialButton

    private lateinit var imageCapture: ImageCapture

    lateinit var file: String

    private lateinit var sharedPref: SharedPreferences

    private lateinit var auth: FirebaseAuth
    private var url = "https://images.pexels.com/photos/220453/pexels-photo-220453.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2"

    private lateinit var faceId1:String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_verify_face, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.preview_view)
        uploadImageButton = view.findViewById(R.id.upload_image)
        auth = FirebaseAuth.getInstance()
        sharedPref = requireActivity().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)



            startCamera()



        uploadImageButton.setOnClickListener {

            takePhoto()

        }

    }






    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageCapture = ImageCapture.Builder().setTargetResolution(Size(480,640)).build()


            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    requireActivity(), cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireActivity()))
    }


    private fun takePhoto() {

        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);

        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val FILENAME_FORMAT = "dd/MM/yyyy"
        val name = getFileName()

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(requireActivity().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireActivity()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){


                    file = getRealPathFromURI(output.savedUri!!)

                    AWSUtils(requireActivity(), file, this@VerifyFaceFragment, AwsConstants.folderPath).beginUpload()


                }
            }
        )


        dialog.dismiss()
    }


    private fun detectFace(imageUrl: String) {


        volleyRequestQueue = Volley.newRequestQueue(requireActivity())
        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);
        val apiUrl = getString(R.string.azure_api_endpoint) + "face/v1.0/detect"

        val strReq: StringRequest = object : StringRequest(
            Method.POST,apiUrl,
            Response.Listener { response ->
                Log.e(TAG, "response: $response")
                dialog?.dismiss()

                try {
                    val responseObj = JSONArray(response)

                    if(responseObj.length() == 1)
                    {
                         faceId1 = responseObj.getJSONObject(0).get("faceId").toString()

                        val db = Firebase.firestore
                        val docRef = db.collection("userdata").document(auth.currentUser?.uid.toString())
                        docRef.get()
                            .addOnSuccessListener { document ->
                                if (document != null) {
                                    Log.d(TAG, "DocumentSnapshot data: ${document.data}")
                                     imageUrl2 = document.data!!["imageUrl"].toString()
                                    detectFace2(imageUrl2)
                                } else {
                                    Log.d(TAG, "No such document")
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.d(TAG, "get failed with ", exception)
                            }


                    }
                    else
                        Toast.makeText(requireActivity(), "Error!! Please scan face properly", Toast.LENGTH_SHORT).show()



                } catch (e: Exception) {
                    Log.e(TAG, "problem occurred")
                    e.printStackTrace()
                }
            },
            Response.ErrorListener { volleyError -> // error occurred
                Log.e(TAG, "problem occurred, volley error: $volleyError")
            }) {


            override fun getBodyContentType(): String {
                return "application/json"
            }

            @Throws(AuthFailureError::class)
            override fun getBody(): ByteArray {
                val param = HashMap<String, String>()
                param["url"] = imageUrl
                return JSONObject(param as Map<*, *>?).toString().toByteArray()
            }

            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {

                val headers: MutableMap<String, String> = HashMap()

                headers["Content-Type"] = "application/json"
                headers["Ocp-Apim-Subscription-Key"] = getString(R.string.azure_key)
                return headers
            }
        }
        volleyRequestQueue.add(strReq)
    }

    private fun detectFace2(imageUrl: String) {


        volleyRequestQueue = Volley.newRequestQueue(requireActivity())
        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);
        val apiUrl = getString(R.string.azure_api_endpoint) + "face/v1.0/detect"

        val strReq: StringRequest = object : StringRequest(
            Method.POST,apiUrl,
            Response.Listener { response ->
                Log.e(TAG, "response: $response")
                dialog?.dismiss()

                try {
                    val responseObj = JSONArray(response)

                    if(responseObj.length() == 1)
                    {
                        val faceId2 = responseObj.getJSONObject(0).get("faceId").toString()

                        verifyIdentity(faceId1, faceId2)


                    }
                    else
                        Toast.makeText(requireActivity(), "Error!! Please scan face properly", Toast.LENGTH_SHORT).show()



                } catch (e: Exception) {
                    Log.e(TAG, "problem occurred")
                    e.printStackTrace()
                }
            },
            Response.ErrorListener { volleyError -> // error occurred
                Log.e(TAG, "problem occurred, volley error: $volleyError")
            }) {


            override fun getBodyContentType(): String {
                return "application/json"
            }

            @Throws(AuthFailureError::class)
            override fun getBody(): ByteArray {
                val param = HashMap<String, String>()
                param["url"] = imageUrl
                return JSONObject(param as Map<*, *>?).toString().toByteArray()
            }

            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {

                val headers: MutableMap<String, String> = HashMap()

                headers["Content-Type"] = "application/json"
                headers["Ocp-Apim-Subscription-Key"] = getString(R.string.azure_key)
                return headers
            }
        }
        volleyRequestQueue.add(strReq)
    }

    private fun verifyIdentity(personId: String, faceId: String) {

        val uid = auth.currentUser?.uid
        volleyRequestQueue = Volley.newRequestQueue(requireActivity())
        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);
        val apiUrl = getString(R.string.azure_api_endpoint) + "face/v1.0/verify"

        val strReq: StringRequest = object : StringRequest(
            Method.POST,apiUrl,
            Response.Listener { response ->
                Log.e(TAG, "response: $response")
                dialog?.dismiss()

                try {
                    val responseObj = JSONObject(response)
                    val isIdentical = responseObj.get("isIdentical")


                    if (isIdentical.toString().toBoolean())
                    {
                        sharedPref.edit().putBoolean("isFaceVerified",true).apply()
                        findNavController().navigate(R.id.action_verifyFaceFragment_to_homeFragment)


                    }


                } catch (e: Exception) {
                    Log.e(TAG, "problem occurred")
                    e.printStackTrace()
                }
            },
            Response.ErrorListener { volleyError -> // error occurred
                Log.e(TAG, "problem occurred, volley error: $volleyError")
            }) {


            override fun getBodyContentType(): String {
                return "application/json"
            }

            @Throws(AuthFailureError::class)
            override fun getBody(): ByteArray {
                val param = HashMap<String, String>()
                param["faceId2"] = faceId
                param["faceId1"] = personId
//                param["personGroupId"] = getString(R.string.person_group_id)


                return JSONObject(param as Map<*, *>?).toString().toByteArray()
            }

            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {

                val headers: MutableMap<String, String> = HashMap()

                headers["Content-Type"] = "application/json"
                headers["Ocp-Apim-Subscription-Key"] = getString(R.string.azure_key)
                return headers
            }
        }
        volleyRequestQueue.add(strReq)
    }




    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun showProgressDialog() {
    }

    override fun hideProgressDialog() {
    }

    override fun onSuccess(imgUrl: String) {
        val fDelete = File(file)

        if (fDelete.exists()) {
            if (fDelete.delete()) {
                Log.d("smart_tag","file Deleted :")
            } else {
                Log.d("smart_tag","file not Deleted :")
            }
        }


        imageUrl1 = imgUrl
        detectFace(imgUrl)
    }

    override fun onError(errorMsg: String) {
        Toast.makeText(requireActivity(), "$errorMsg", Toast.LENGTH_SHORT).show()

    }

    private fun getRealPathFromURI  (contentURI: Uri): String {

        var result: String
        val cursor = requireActivity().contentResolver.query(contentURI, null, null, null,null)

        if (cursor==null)
        {
            result = contentURI.path!!
        }
        else
        {
            cursor.moveToFirst()
            var idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            result = cursor.getString(idx)
        }

        return result
    }


    private fun getFileName(): String {
        var timeStamp: String = "image" + SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Date())
        timeStamp = timeStamp.replace(':','_')
        timeStamp = timeStamp.replace('-','_')

        return "JPEG_" + timeStamp + "_.jpeg"
    }

}

