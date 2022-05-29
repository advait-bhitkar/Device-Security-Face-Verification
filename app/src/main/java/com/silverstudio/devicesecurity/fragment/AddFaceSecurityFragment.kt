package com.silverstudio.devicesecurity.fragment

import android.Manifest
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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


class AddFaceSecurityFragment : Fragment(), AWSUtils.OnAwsImageUploadListener {

    private lateinit var previewView: PreviewView
    private lateinit var volleyRequestQueue: RequestQueue
    private lateinit var dialog: ProgressDialog

    private lateinit var uploadImageButton: MaterialButton

    private lateinit var imageCapture: ImageCapture

    lateinit var file: String

    private lateinit var sharedPref:SharedPreferences

    private lateinit var auth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_add_face_security, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.preview_view)
        uploadImageButton = view.findViewById(R.id.upload_image)
        auth = FirebaseAuth.getInstance()
        sharedPref = requireActivity().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)



        if (hasPermissions(activity as Context, REQUIRED_PERMISSIONS)) {
            Toast.makeText(requireActivity(), "All permission fulfill", Toast.LENGTH_SHORT).show()
            startCamera()

        } else {
            permReqLauncher.launch(
                REQUIRED_PERMISSIONS
            )
        }


        uploadImageButton.setOnClickListener {

            takePhoto()
        }

    }

    private val permReqLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all {
                it.value == true
            }
            if (granted) {

                Toast.makeText(requireActivity(), "All permissions Granted", Toast.LENGTH_SHORT).show()
                startCamera()
            }
        }



    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
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

                    dialog = ProgressDialog.show(requireActivity(), "", "Uploading Photo...", true);

                    file = getRealPathFromURI(output.savedUri!!)

                    AWSUtils(requireActivity(), file , this@AddFaceSecurityFragment, AwsConstants.folderPath).beginUpload()


                }
            }
        )
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


        Log.d("smart_tag","$imgUrl :")

        if (fDelete.exists()) {
            if (fDelete.delete()) {
                Log.d("smart_tag","file Deleted :")
            } else {
                Log.d("smart_tag","file not Deleted :")
            }
        }


        val db = Firebase.firestore
        val data = hashMapOf(
            "imageUrl" to imgUrl,
        )
        db.collection("userdata").document(auth.currentUser?.uid.toString()).set(data)

        sharedPref.edit().putBoolean("isFaceSecurityAdded",true).apply()

        dialog.dismiss()
        findNavController().navigate(R.id.action_addFaceSecurityFragment_to_homeFragment)

//        detectFace(imgUrl)
    }

    override fun onError(errorMsg: String) {
        Toast.makeText(requireActivity(), "$errorMsg", Toast.LENGTH_SHORT).show()

    }

    private fun getRealPathFromURI  (contentURI: Uri): String {

        val result: String
        val cursor = requireActivity().contentResolver.query(contentURI, null, null, null,null)

        if (cursor==null)
        {
            result = contentURI.path!!
        }
        else
        {
            cursor.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
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

