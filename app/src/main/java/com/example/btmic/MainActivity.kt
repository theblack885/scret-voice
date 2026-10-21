package com.example.btmic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val REQUEST_CODE_PERMISSIONS = 200
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var toggleHideCamera: ToggleButton? = null
    private var btnViewSecureVideos: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ZAROORI NOTE: Agar aapke layout file ka naam 'activity_main' nahi hai,
        // toh niche apne asli layout ka naam set karein (jaise R.layout.your_layout_name)
        setContentView(R.layout.activity_main)

        // Null-safe findViewById framework taaki compile time par koi error na aaye
        toggleHideCamera = findViewById(R.id.toggleHideCamera)
        btnViewSecureVideos = findViewById(R.id.btnViewSecureVideos)

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Hide Camera option switch logic
        toggleHideCamera?.setOnClickListener {
            val serviceIntent = Intent(this, BackgroundRecorderService::class.java)
            if (toggleHideCamera?.isChecked == true) {
                if (allPermissionsGranted()) {
                    startService(serviceIntent)
                    Toast.makeText(this, "Hidden Engine Started", Toast.LENGTH_SHORT).show()
                } else {
                    toggleHideCamera?.isChecked = false
                    Toast.makeText(this, "Permissions required!", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopService(serviceIntent)
                Toast.makeText(this, "Hidden Engine Stopped", Toast.LENGTH_SHORT).show()
            }
        }

        // Passcode entry verification activity trigger
        btnViewSecureVideos?.setOnClickListener {
            val intent = Intent(this, PasscodeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions are required for this feature.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
