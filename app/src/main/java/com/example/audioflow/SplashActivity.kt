package com.example.audioflow

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BuildCompat

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the device is running Android 12 or higher
        if (BuildCompat.isAtLeastS()) {
            // For Android 12 and above, skip the custom splash screen
            startMainActivity()
        } else {
            // For Android 11 and below, show the custom splash screen
            setContentView(R.layout.splash_screen)

            // Initialize views
            val appIcon = findViewById<ImageView>(R.id.app_icon)
            val appName = findViewById<TextView>(R.id.app_name)

            // Set app icon and name
            appIcon.setImageResource(R.mipmap.ic_launcher)
            appName.text = getString(R.string.app_name)

            // Add any animations or additional setup here

            // Delay for a few seconds before starting the main activity
            Handler(Looper.getMainLooper()).postDelayed({
                startMainActivity()
            }, 2000) // 2 seconds delay, adjust as needed
        }
    }

    private fun startMainActivity() {
        // Start your main activity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}