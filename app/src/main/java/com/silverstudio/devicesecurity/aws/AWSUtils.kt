package com.silverstudio.devicesecurity.aws

import android.content.ContentValues.TAG
import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import java.io.File

/**
 * Aws S3 image uploader class
 */

class AWSUtils(private val context: Context, private val filePath: String, val onAwsImageUploadListener: OnAwsImageUploadListener, val filePathKey: String) {


    /**
     * Declare variables
     */
    private var image: File? = null
    private var mTransferUtility: TransferUtility? = null
    private var sS3Client: AmazonS3Client? = null
    private var sCredProvider: CognitoCachingCredentialsProvider? = null

    /**
     * Get aws cognito credential ID
     */
    private fun getCredProvider(context: Context): CognitoCachingCredentialsProvider? {
        if (sCredProvider == null) {
            sCredProvider = CognitoCachingCredentialsProvider(context.applicationContext, AwsConstants.COGNITO_IDENTITY_ID, AwsConstants.COGNITO_REGION)
        }
        return sCredProvider
    }

    /**
     * Get an instance of AWS S3 Client
     */
    private fun getS3Client(context: Context?): AmazonS3Client? {
        if (sS3Client == null) {
            sS3Client = AmazonS3Client(getCredProvider(context!!))
            sS3Client!!.setRegion(Region.getRegion(Regions.AP_SOUTH_1))
        }
        return sS3Client
    }

    /**
     * Get an instance of Transfer utility
     * this is used to upload files to AWS
     */
    private fun getTransferUtility(context: Context): TransferUtility? {
        if (mTransferUtility == null) {
            mTransferUtility = TransferUtility(
                getS3Client(context.applicationContext),
                context.applicationContext
            )
        }
        return mTransferUtility
    }

    /**
     * Begin Image upload to S3 bucket
     * It requires locally stored image path and
     * AWS bucket url path
     */
    fun beginUpload() {

        if (TextUtils.isEmpty(filePath)) {
            onAwsImageUploadListener.onError("Could not find the filepath of the selected file")
            return
        }

        onAwsImageUploadListener.showProgressDialog()
        val file = File(filePath)
        image = file

        try {
            val observer = getTransferUtility(context)?.upload(
                AwsConstants.BUCKET_NAME, //Bucket name
                filePathKey + file.name, image //File name with folder path
            )
            observer?.setTransferListener(UploadListener())
        } catch (e: Exception) {
            e.printStackTrace()
            onAwsImageUploadListener.hideProgressDialog()
        }
    }

    /**
     * Implement upload Listener
     */

    private inner class UploadListener : TransferListener {

        override fun onError(id: Int, e: Exception) {
            onAwsImageUploadListener.hideProgressDialog()
        }

        override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
        }

        override fun onStateChanged(id: Int, newState: TransferState) {

            if (newState == TransferState.COMPLETED) {

                onAwsImageUploadListener.hideProgressDialog()
                val finalImageUrl = AwsConstants.S3_URL + filePathKey + image!!.name
                onAwsImageUploadListener.onSuccess(finalImageUrl)
                /**
                 * Delete Image from local storage after uploading
                 * to s3 storage
                 */
                deleteImage(image!!.absolutePath)


            } else if (newState == TransferState.CANCELED || newState == TransferState.FAILED) {
                onAwsImageUploadListener.hideProgressDialog()
                onAwsImageUploadListener.onError("Error in uploading file.")
            }
        }
    }


    /**
     * Delete local image
     * Takes file path as parameter
     */
    private fun deleteImage( filePath: String){

        val fDelete = File(filePath)
        if (fDelete.exists()) {
            if (fDelete.delete()) {
                Log.d(TAG,"file Deleted :")
            } else {
                Log.d(TAG,"file not Deleted :")
            }
        }

    }

    interface OnAwsImageUploadListener {
        fun showProgressDialog()
        fun hideProgressDialog()
        fun onSuccess(imgUrl: String)
        fun onError(errorMsg: String)
    }

    interface OnAwsImageUploadListener2 {
        fun showProgressDialog()
        fun hideProgressDialog()
        fun onSuccess(imgUrl: String)
        fun onError(errorMsg: String)
    }

}
