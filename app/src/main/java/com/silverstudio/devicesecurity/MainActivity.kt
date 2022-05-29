package com.silverstudio.devicesecurity

import android.content.Context
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {

        val sharedPref = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val isFaceSecurityAdded = sharedPref.getBoolean("isFaceSecurityAdded", false)
        if(isFaceSecurityAdded)
            sharedPref.edit().putBoolean("isFaceVerified",false).apply()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}