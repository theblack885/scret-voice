package com.example.hidecamera;

// AndroidX ko hata kar standard Core Android Activity use ki hai
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PasscodeActivity extends Activity {
    private static final String MASTER_CODE = "1615";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_MS = 24 * 60 * 60 * 1000; // 24 Ghante

    private EditText etPasscode;
    private TextView tvStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passcode);

        etPasscode = findViewById(R.id.etPasscode);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnUnlock = findViewById(R.id.btnUnlock);
        
        prefs = getSharedPreferences("AppSecurityPrefs", MODE_PRIVATE);

        if (checkLockout()) {
            disableUiForLockout();
        }

        btnUnlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handlePasscodeVerification();
            }
        });
    }

    private boolean checkLockout() {
        long lockoutEndTime = prefs.getLong("lockout_end_time", 0);
        return System.currentTimeMillis() < lockoutEndTime;
    }

    private void disableUiForLockout() {
        long remainingTime = prefs.getLong("lockout_end_time", 0) - System.currentTimeMillis();
        long hours = remainingTime / (60 * 60 * 1000);
        etPasscode.setEnabled(false);
        tvStatus.setText("Too many failed attempts. Try again after " + hours + " hours.");
        tvStatus.setTextColor(android.graphics.Color.RED);
    }

    private void handlePasscodeVerification() {
        if (checkLockout()) {
            disableUiForLockout();
            return;
        }

        String input = etPasscode.getText().toString();
        if (input.equals(MASTER_CODE)) {
            prefs.edit().putInt("failed_attempts", 0).putLong("lockout_end_time", 0).apply();
            
            // Sahi code par video gallery activity open hogi
            Intent intent = new Intent(PasscodeActivity.this, VideoGalleryActivity.class);
            startActivity(intent);
            finish();
        } else {
            int failedAttempts = prefs.getInt("failed_attempts", 0) + 1;
            prefs.edit().putInt("failed_attempts", failedAttempts).apply();

            if (failedAttempts >= MAX_ATTEMPTS) {
                long lockoutEnd = System.currentTimeMillis() + LOCK_TIME_MS;
                prefs.edit().putLong("lockout_end_time", lockoutEnd).apply();
                disableUiForLockout();
                Toast.makeText(this, "Security Lock Activated! App locked for 24 hours.", Toast.LENGTH_LONG).show();
            } else {
                int attemptsLeft = MAX_ATTEMPTS - failedAttempts;
                Toast.makeText(this, "Wrong Code! Remaining Attempts: " + attemptsLeft, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
