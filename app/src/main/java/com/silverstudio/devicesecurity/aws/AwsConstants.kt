package com.silverstudio.devicesecurity.aws

import android.content.res.Resources
import com.amazonaws.regions.Regions
import com.silverstudio.devicesecurity.R

object AwsConstants {

    val COGNITO_IDENTITY_ID: String = Resources.getSystem().getString(R.string.cognito_id)

    val COGNITO_REGION: Regions = Regions.AP_SOUTH_1 // Region
    val BUCKET_NAME: String = "bucket-customer-images"
    val S3_URL: String = "https://$BUCKET_NAME.s3.ap-south-1.amazonaws.com/"
    val folderPath = "my-images/"

}
