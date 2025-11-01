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
    private ActivityResultLauncher<String> smsPermissionLauncher;
    private ActivityResultLauncher<String> phonePermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        navView = findViewById(R.id.bottomNavigationView);

        // 🔹 Permission launchers
        smsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted)
                        Toast.makeText(this, "❌ SMS permission denied", Toast.LENGTH_SHORT).show();
                });

        phonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted)
                        Toast.makeText(this, "❌ Phone permission denied", Toast.LENGTH_SHORT).show();
                });

        // 🔹 Check if user is registered on this device
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        String mobile = prefs.getString("mobile", null);
        String deviceId = prefs.getString("deviceId", null);

        if (mobile == null || deviceId == null) {
            Toast.makeText(this, "⚠️ Please sign in first", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // ✅ Load HomeFragment initially
        loadFragment(new HomeFragment());

        // 🔹 Handle Bottom Navigation
        navView.setOnItemSelectedListener(item -> {
            Fragment selected = null;

            switch (item.getItemId()) {
                case R.id.nav_home:
                    selected = new HomeFragment();
                    break;
                case R.id.nav_tasks:
                    selected = new TaskFragment();
                    break;
                case R.id.nav_sms:
                    checkPermissionsBeforeSMS();
                    selected = new SMSFragment();
                    break;
                case R.id.nav_profile:
                    selected = new ProfileFragment();
                    break;
            }

            if (selected != null) {
                loadFragment(selected);
            }
            return true;
        });
    }

    private void checkPermissionsBeforeSMS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();
    }
}