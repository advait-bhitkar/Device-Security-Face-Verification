package com.silverstudio.devicesecurity.aws

import com.amazonaws.regions.Regions
/**
AWS constants file
Contains cognito identity id
Cognito Region
Bucket Name
S3 base Url
Image folder path
 */

object AwsConstants {

    val COGNITO_IDENTITY_ID: String = "ap-south-1:7e09adee-565c-41c6-a44c-92a67068ff28"
    val COGNITO_REGION: Regions = Regions.AP_SOUTH_1
    val BUCKET_NAME: String = "bucket-customer-images"
    val S3_URL: String = "https://$BUCKET_NAME.s3.ap-south-1.amazonaws.com/"
    val folderPath = "my-images/"

}
