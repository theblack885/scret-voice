package com.yourpackage.name; // Apne app ka package name yahan dalein

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Apni XML layout file ka naam yahan rakhein

        Button btnCapture = findViewById(R.id.btnCapture); // Apne button ki ID yahan set karein
        btnCapture.setOnClickListener(v -> {
            if (checkPermissions()) {
                startHiddenService();
            } else {
                requestPermissions();
            }
        });
    }

    private boolean checkPermissions() {
        int cameraResult = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int foregroundResult = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE);
            return cameraResult == PackageManager.PERMISSION_GRANTED && foregroundResult == PackageManager.PERMISSION_GRANTED;
        }
        return cameraResult == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.FOREGROUND_SERVICE},
                    CAMERA_PERMISSION_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        }
    }

    private void startHiddenService() {
        Intent serviceIntent = new Intent(this, HiddenCameraService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Background capture started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startHiddenService();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
