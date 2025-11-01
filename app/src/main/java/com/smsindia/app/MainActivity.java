package com.smsindia.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.smsindia.app.ui.ProfileFragment;
import com.smsindia.app.ui.SMSFragment;
import com.smsindia.app.ui.TaskFragment;
import com.smsindia.app.ui.HomeFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView navView;

    // Permission launchers (kept for possible future use)
    private final ActivityResultLauncher<String> smsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<String> phonePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            Toast.makeText(this, "Phone permission denied", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        navView = findViewById(R.id.bottomNavigationView);

        // -----------------------------------------------------------------
        // 1. Check user registration
        // -----------------------------------------------------------------
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        String mobile = prefs.getString("mobile", null);
        String deviceId = prefs.getString("deviceId", null);

        if (mobile == null || deviceId == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // -----------------------------------------------------------------
        // 2. Load the default fragment
        // -----------------------------------------------------------------
        loadFragment(new HomeFragment());

        // -----------------------------------------------------------------
        // 3. Bottom-navigation handling – **NO SWITCH**, use if-else
        // -----------------------------------------------------------------
        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
            } else if (id == R.id.nav_tasks) {
                loadFragment(new TaskFragment());
            } else if (id == R.id.nav_sms) {
                // Permissions are checked **inside SMSFragment** when the user
                // actually tries to send an SMS. Opening the tab does NOT block.
                loadFragment(new SMSFragment());
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
            }

            return true;
        });
    }

    // -----------------------------------------------------------------
    // Helper: permission request (kept in case you want to reuse it)
    // -----------------------------------------------------------------
    @SuppressWarnings("unused")
    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
        }
    }

    @SuppressWarnings("unused")
    private void requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        }
    }

    // -----------------------------------------------------------------
    // Helper: replace fragment
    // -----------------------------------------------------------------
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();
    }
}