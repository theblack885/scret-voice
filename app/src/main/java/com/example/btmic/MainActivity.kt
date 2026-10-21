package com.example.hidecamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// Standard Activity support
import android.app.Activity;

// Sabse zaroori line: Aapke main app ke resources (R) ko import kiya gaya hai
import com.example.btmic.R;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_PERMISSIONS = 200;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA, 
            Manifest.permission.RECORD_AUDIO
    };

    private ToggleButton toggleHideCamera;
    private Button btnViewSecureVideos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); 

        toggleHideCamera = findViewById(R.id.toggleHideCamera); 
        btnViewSecureVideos = findViewById(R.id.btnViewSecureVideos);

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        toggleHideCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(MainActivity.this, BackgroundRecorderService.class);
                if (toggleHideCamera.isChecked()) {
                    if (allPermissionsGranted()) {
                        startService(serviceIntent);
                        Toast.makeText(MainActivity.this, "Hidden Engine Started", Toast.LENGTH_SHORT).show();
                    } else {
                        toggleHideCamera.setChecked(false);
                        Toast.makeText(MainActivity.this, "Permissions required!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    stopService(serviceIntent);
                    Toast.makeText(MainActivity.this, "Hidden Engine Stopped", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnViewSecureVideos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PasscodeActivity.class);
                startActivity(intent);
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions are required for this feature.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
