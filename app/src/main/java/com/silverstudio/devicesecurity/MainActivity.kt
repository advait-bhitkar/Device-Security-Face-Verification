package com.silverstudio.devicesecurity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        /**
         * The app checks if face security feature is enabled,
         * if it is enabled we set 'isFaceVerified' to false
         * The user will go to VerifyFaceFragment to verify face
         * before going to HomeFragment
         */
        val sharedPref = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val isFaceSecurityAdded = sharedPref.getBoolean("isFaceSecurityAdded", false)
        if(isFaceSecurityAdded)
            sharedPref.edit().putBoolean("isFaceVerified",false).apply()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}