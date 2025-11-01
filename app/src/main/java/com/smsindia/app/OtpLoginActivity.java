package com.smsindia.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class OtpLoginActivity extends AppCompatActivity {

    private EditText phoneInput, otpInput;
    private Button sendOtpBtn, verifyBtn;
    private FirebaseAuth auth;
    private String verificationId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        phoneInput = findViewById(R.id.phoneInput);
        otpInput = findViewById(R.id.otpInput);
        sendOtpBtn = findViewById(R.id.sendOtpBtn);
        verifyBtn = findViewById(R.id.verifyBtn);

        // Auto-skip if already logged in
        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        sendOtpBtn.setOnClickListener(v -> sendOTP());
        verifyBtn.setOnClickListener(v -> verifyOTP());
    }

    private void sendOTP() {
        String phone = phoneInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
            Toast.makeText(this, "Enter valid phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber("+91" + phone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(callbacks)
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    signInWithCredential(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    Toast.makeText(OtpLoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCodeSent(@NonNull String id, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    verificationId = id;
                    Toast.makeText(OtpLoginActivity.this, "OTP Sent Successfully!", Toast.LENGTH_SHORT).show();
                }
            };

    private void verifyOTP() {
        String code = otpInput.getText().toString().trim();
        if (TextUtils.isEmpty(code) || verificationId == null) {
            Toast.makeText(this, "Enter received OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithCredential(credential);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    saveUserToFirestore(user);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                }
            } else {
                Toast.makeText(this, "Invalid OTP!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserToFirestore(FirebaseUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("phone", user.getPhoneNumber());
        data.put("balance", 0);
        data.put("uniqueId", generateUniqueId());

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                db.collection("users").document(user.getUid()).set(data);
            }
        });
    }

    private String generateUniqueId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}