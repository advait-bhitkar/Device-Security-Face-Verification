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
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.volley.RequestQueue
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.silverstudio.devicesecurity.R
import com.silverstudio.devicesecurity.aws.AWSUtils
import com.silverstudio.devicesecurity.aws.AwsConstants
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment to upload face to save for face verification
 * The image is captured from camera and is uploaded to AWS S3 bucket
 * The URL of the uploaded image is saved to the Firestore database
 */

class AddFaceSecurityFragment : Fragment(), AWSUtils.OnAwsImageUploadListener {

    /**
     * Declare variables
     */
    private lateinit var previewView: PreviewView
    private lateinit var uploadImageButton: MaterialButton
    private lateinit var dialog: ProgressDialog
    private lateinit var imageCapture: ImageCapture
    lateinit var file: String
    private lateinit var sharedPref:SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var imageView: ImageView


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

        /**
         * Initialize views and other variables
         */
        previewView = view.findViewById(R.id.preview_view)
        uploadImageButton = view.findViewById(R.id.upload_image)
        imageView = view.findViewById(R.id.image)

        auth = FirebaseAuth.getInstance()
        sharedPref = requireActivity().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)

        /**
         * Check is the app has all required permissions
         * If not Request permission
         * If has all permissions start camera
         */
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

    /**
     * Permission request launcher
     */
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


    /**
     * Function to check if app has all the required permissions
     */
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
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
                    AWSUtils(requireActivity(), file , this@AddFaceSecurityFragment, AwsConstants.folderPath).beginUpload()


                }
            }
        )
    }


    companion object {
        private const val TAG = "AddFaceSecurityFragment"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun showProgressDialog() {
    }

    override fun hideProgressDialog() {
    }

    override fun onSuccess(imgUrl: String) {

        /**
         * Delete image from local storage after its
         * uploaded online to AWS S3
         */
        deleteFile(file)

        /**
         * Save the uploaded image url to Firestore database
         * it save the image url to a document
         * The document name is the uid of the login user
         * Thus the user can save the file in their corresponding uid document
         */
        val db = Firebase.firestore
        val data = hashMapOf(
            "imageUrl" to imgUrl,
        )
        db.collection("userdata").document(auth.currentUser?.uid.toString()).set(data)

        /**
         * set isFaceSecurityAdded variable to true
         * The user will need to verify face every time it opens the app
         */
        sharedPref.edit().putBoolean("isFaceSecurityAdded",true).apply()
        sharedPref.edit().putBoolean("isFaceVerified",true).apply()

        Toast.makeText(requireActivity(), "You will need to verify your face when you open the app", Toast.LENGTH_LONG).show()

        dialog.dismiss()
        findNavController().navigate(R.id.action_addFaceSecurityFragment_to_homeFragment)
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

