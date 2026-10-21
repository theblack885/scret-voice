package com.example.btmic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
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

    private lateinit var toggleHideCamera: ToggleButton
    private lateinit var btnViewSecureVideos: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Components ko link kar rahe hain
        toggleHideCamera = findViewById(R.id.toggleHideCamera)
        btnViewSecureVideos = findViewById(R.id.btnViewSecureVideos)

        // Permissions check kar rahe hain
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Hide Camera Option ko ON/OFF karne ka logic
        toggleHideCamera.setOnClickListener {
            val serviceIntent = Intent(this, BackgroundRecorderService::class.java)
            if (toggleHideCamera.isChecked) {
                if (allPermissionsGranted()) {
                    startService(serviceIntent)
                    Toast.makeText(this, "Hidden Engine Started", Toast.LENGTH_SHORT).show()
                } else {
                    toggleHideCamera.isChecked = false
                    Toast.makeText(this, "Permissions required!", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopService(serviceIntent)
                Toast.makeText(this, "Hidden Engine Stopped", Toast.LENGTH_SHORT).show()
            }
        }

        // Secure Videos dekhne ke liye Password Lock Screen kholne ka logic
        btnViewSecureVideos.setOnClickListener {
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
