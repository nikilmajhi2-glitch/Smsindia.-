package com.smsindia.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginActivity extends AppCompatActivity {

    private TextView deviceIdText;
    private EditText phoneInput;
    private Button registerBtn;
    private FirebaseFirestore db;
    private SharedPreferences prefs;

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        deviceIdText = findViewById(R.id.deviceIdText);
        phoneInput = findViewById(R.id.phoneInput);
        registerBtn = findViewById(R.id.registerBtn);

        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("SMSIndiaPrefs", Context.MODE_PRIVATE);

        // Get unique Android ID
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceIdText.setText("Device ID: " + deviceId);

        // If already registered, skip to Main
        if (prefs.getBoolean("isRegistered", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        registerBtn.setOnClickListener(v -> registerDevice(deviceId));
    }

    private void registerDevice(String deviceId) {
        String phone = phoneInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "Please enter your mobile number", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if device already exists in Firestore
        db.collection("users").document(deviceId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                Toast.makeText(this, "Device already registered!", Toast.LENGTH_LONG).show();
                prefs.edit().putBoolean("isRegistered", true).apply();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                saveNewUser(deviceId, phone);
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Error checking device: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveNewUser(String deviceId, String phone) {
        String uniqueToken = UUID.randomUUID().toString();

        Map<String, Object> user = new HashMap<>();
        user.put("phone", phone);
        user.put("deviceId", deviceId);
        user.put("token", uniqueToken);
        user.put("balance", 0);
        user.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(deviceId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    prefs.edit()
                            .putBoolean("isRegistered", true)
                            .putString("deviceId", deviceId)
                            .putString("phone", phone)
                            .apply();

                    Toast.makeText(this, "Device registered successfully!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}