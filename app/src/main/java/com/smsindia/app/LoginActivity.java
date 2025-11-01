package com.smsindia.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText phoneInput, passwordInput;
    private Button loginBtn, signupBtn;
    private TextView deviceIdText;

    private FirebaseFirestore db;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = FirebaseFirestore.getInstance();

        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        signupBtn = findViewById(R.id.signupBtn);
        deviceIdText = findViewById(R.id.deviceIdText);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceIdText.setText("Device ID: " + deviceId);

        loginBtn.setOnClickListener(v -> loginUser());
        signupBtn.setOnClickListener(v -> registerUser());
    }

    private void loginUser() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(phone).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Toast.makeText(this, "User not found!", Toast.LENGTH_SHORT).show();
                return;
            }

            String storedPass = snapshot.getString("password");
            String storedDevice = snapshot.getString("deviceId");

            if (storedPass != null && storedPass.equals(password)) {
                if (storedDevice != null && !storedDevice.equals(deviceId)) {
                    Toast.makeText(this,
                            "This account is linked to another device. Login denied.",
                            Toast.LENGTH_LONG).show();
                } else {
                    // SAVE LOGIN STATE
                    SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
                    prefs.edit()
                        .putString("mobile", phone)
                        .putString("deviceId", deviceId)
                        .apply();

                    // SHOW LOADING + GO TO MAIN
                    showLoadingAndProceed("Logging you in...", () -> {
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    });
                }
            } else {
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void registerUser() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter phone and password", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        Toast.makeText(this,
                                "This device is already registered!\nTrying again may lead to a ban.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    db.collection("users").document(phone).get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists()) {
                                    Toast.makeText(this,
                                            "Phone already registered! Use Login.",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                Map<String, Object> user = new HashMap<>();
                                user.put("phone", phone);
                                user.put("password", password);
                                user.put("deviceId", deviceId);
                                user.put("createdAt", System.currentTimeMillis());
                                user.put("balance", 0);

                                db.collection("users").document(phone).set(user)
                                        .addOnSuccessListener(unused -> {
                                            // SAVE REGISTRATION STATE
                                            SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
                                            prefs.edit()
                                                .putString("mobile", phone)
                                                .putString("deviceId", deviceId)
                                                .apply();

                                            // SHOW LOADING + GO TO MAIN
                                            showLoadingAndProceed("Creating your account...", () -> {
                                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                                finish();
                                            });
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this,
                                                        "Error: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show());
                            });
                });
    }

    // LOADING DIALOG WITH ANIMATION
    private void showLoadingAndProceed(String message, Runnable onComplete) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        TextView tvMessage = dialogView.findViewById(R.id.tv_loading_message);
        tvMessage.setText(message);
        builder.setView(dialogView);
        builder.setCancelable(false);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            dialog.dismiss();
            onComplete.run();
        }, 1500); // 1.5 seconds
    }
}