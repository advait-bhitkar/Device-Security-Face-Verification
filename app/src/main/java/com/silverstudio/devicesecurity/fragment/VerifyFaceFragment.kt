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
import android.widget.ImageView
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

/**
 * This fragment is used to verify the user with face recognition
 * The user need to take the photo and upload it to database
 *
 * then the two images this one and the one saved in the database is send
 *  to azure face api for verification
 *
 *  The face api compares the two images and returns whether the images
 *  are of same person or not
 *
 *  It the images are matched the api returns true
 *  Thus we can verify user and give access to them
 */
class VerifyFaceFragment : Fragment(), AWSUtils.OnAwsImageUploadListener2,
    AWSUtils.OnAwsImageUploadListener {

    /**
     * Declare Variables and views
     */
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
    private lateinit var faceId1:String
    private lateinit var imageView: ImageView
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

        /**
         * Initialize views and variables
         */
        previewView = view.findViewById(R.id.preview_view)
        uploadImageButton = view.findViewById(R.id.upload_image)
        imageView = view.findViewById(R.id.image)
        auth = FirebaseAuth.getInstance()
        sharedPref = requireActivity().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)


        startCamera()

        uploadImageButton.setOnClickListener {
            takePhoto()
        }
    }

    /**
     * Function to launch camera
     * Uses camera 2 api
     */

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity())
        cameraProviderFuture.addListener({
            /**
             * Used to bind the lifecycle of cameras to the lifecycle owner
             */
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            /**
             * Preview Builder
             * set the surface provider as previewView
             */
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            /**
             *Select front camera as we want user to scan their face
             */
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            /**
             * Image capture builder
             * We set image resolution to 480 x 640
             * Because we want the size of image as low as possible
             * Else it will take quite a time to upload to server
             */
            imageCapture = ImageCapture.Builder().setTargetResolution(Size(480,640)).build()


            try {
                /**
                 * Unbind use cases before rebinding
                 */
                cameraProvider.unbindAll()

                /**
                 * Bind use cases to camera
                 * Here we pass in our preview builder and
                 * image capture builder to bind it with camera
                 */
                cameraProvider.bindToLifecycle(
                    requireActivity(), cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireActivity()))
    }



    /**
     * Function to capture photo
     */
    private fun takePhoto() {
        val imageCapture = imageCapture

        /**
         * Generate filename for the captured image
         *
         */
        val name = getFileName()

        /**
         * Set image parameters
         */
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        /**
         * Create output options object which contains file + metadata
         */
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(requireActivity().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        /**
         * Set up image capture listener, which is triggered
         * after photo haS been taken
         */
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireActivity()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    dialog = ProgressDialog.show(requireActivity(), "", "Uploading Photo...", true);
                    file = getRealPathFromURI(output.savedUri!!)
                    imageView.setImageURI(null);
                    imageView.setImageURI(output.savedUri);

                    /**
                     * Start upload of captured image to aws s3 bucket
                     */
                    AWSUtils(requireActivity(), file , this@VerifyFaceFragment, AwsConstants.folderPath).beginUpload()


                }
            }
        )
    }


    /**
     * Detect face and generate face Id
     * using microsoft face api
     */
    private fun detectFace(imageUrl: String) {
        /**
         * Used Volley library to make rest api calls
         */
        volleyRequestQueue = Volley.newRequestQueue(requireActivity())
        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);

        /**
         * Face detect api url
         */
        val apiUrl = getString(R.string.azure_api_endpoint) + "face/v1.0/detect"

        /**
         * Send request to azure face api
         */
        val strReq: StringRequest = object : StringRequest(
            Method.POST,apiUrl,
            Response.Listener { response ->
                Log.e(TAG, "response: $response")
                dialog.dismiss()

                try {
                    /**
                     * Returns the array of detected faces object
                     * we want only one face in image
                     * It returns parameter 'faceId' which is used to
                     * compare or verify two faces
                     */

                    val responseObj = JSONArray(response)
                    if(responseObj.length() == 1)
                    {
                        /**
                         * Store the face id of first image in variable 'faceId1'
                         */
                        faceId1 = responseObj.getJSONObject(0).get("faceId").toString()

                        /**
                         * Send the other image to azure face api
                         * saved in database to generate faceId
                         */
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
                return JSONObject((param as Map<*, *>?)!!).toString().toByteArray()
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

    /**
     * Detect face and generate face Id
     * using microsoft face api
     */
    private fun detectFace2(imageUrl: String) {
        /**
         * Used Volley library to make rest api calls
         */
        volleyRequestQueue = Volley.newRequestQueue(requireActivity())
        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);

        /**
         * Face detect api url
         */
        val apiUrl = getString(R.string.azure_api_endpoint) + "face/v1.0/detect"

        /**
         * Send request to azure face api
         */
        val strReq: StringRequest = object : StringRequest(
            Method.POST,apiUrl,
            Response.Listener { response ->
                Log.e(TAG, "response: $response")
                dialog.dismiss()

                try {
                    /**
                     * Returns the array of detected faces object
                     * we want only one face in image
                     * It returns parameter 'faceId' which is used to
                     * compare or verify two faces
                     */

                    val responseObj = JSONArray(response)
                    if(responseObj.length() == 1)
                    {
                        val faceId2 = responseObj.getJSONObject(0).get("faceId").toString()
                        /**
                         * Call verifyIdentity function to compare the two faces
                         * we need to pass the face ids of the two images
                         */
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
                return JSONObject((param as Map<*, *>?)!!).toString().toByteArray()
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

    /**
     * Function to verify face
     * It requires two parameters in body
     * 'faceId1' and 'faceId2'
     * The azure api compares the two images and return
     * 'isIdentical' value which is ba boolean
     * if 'isIdentical' is true we grant the user access to the app
     *
     */
    private fun verifyIdentity(personId: String, faceId: String) {
        volleyRequestQueue = Volley.newRequestQueue(requireActivity())
        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);
        val apiUrl = getString(R.string.azure_api_endpoint) + "face/v1.0/verify"

        val strReq: StringRequest = object : StringRequest(
            Method.POST,apiUrl,
            Response.Listener { response ->
                Log.e(TAG, "response: $response")
                dialog.dismiss()

                try {
                    val responseObj = JSONObject(response)
                    val isIdentical = responseObj.get("isIdentical")

                    if (isIdentical.toString().toBoolean())
                    {
                        /**
                         * set 'isFaceVerified to true and grant user
                         * access to the app
                         */
                        sharedPref.edit().putBoolean("isFaceVerified",true).apply()
                        Toast.makeText(requireActivity(), "Face verified Successfully!!", Toast.LENGTH_LONG).show()
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

                return JSONObject((param as Map<*, *>?)!!).toString().toByteArray()
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
    }

    override fun showProgressDialog() {
    }

    override fun hideProgressDialog() {
    }

    override fun onSuccess(imgUrl: String) {
        dialog.dismiss()
        deleteFile(imgUrl)
        imageUrl1 = imgUrl
        detectFace(imgUrl)
    }

    override fun onError(errorMsg: String) {
        Toast.makeText(requireActivity(), errorMsg, Toast.LENGTH_SHORT).show()
    }

    /**
     * Function to get the absolute path of image from the
     * URI returned by the camera
     */
    private fun getRealPathFromURI  (contentURI: Uri): String {
        val result: String
        val cursor = requireActivity().contentResolver.query(contentURI, null, null, null,null)
        result = if (cursor==null) {
            contentURI.path!!
        } else {
            cursor.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            cursor.getString(idx)
        }
        return result
    }


    /**
     * Function to generate a unique file name for the captured image
     * We use date to generate the filename
     */
    private fun getFileName(): String {
        var timeStamp: String = "image" + SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Date())
        timeStamp = timeStamp.replace(':','_')
        timeStamp = timeStamp.replace('-','_')
        return "JPEG_" + timeStamp + "_.jpeg"
    }


    /**
     * Function to delete the file
     * after uploading to AWS S3
     */
    private fun deleteFile(filePath: String){
        val fDelete = File(filePath)
        if (fDelete.exists()) {
            if (fDelete.delete()) {
                Log.d(TAG,"file Deleted :")
            } else {
                Log.d(TAG,"file not Deleted :")
            }
        }

    }

}

