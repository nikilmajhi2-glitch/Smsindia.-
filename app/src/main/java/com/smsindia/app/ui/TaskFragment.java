package com.smsindia.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.smsindia.app.R;
import com.smsindia.app.service.SmsForegroundService;

import java.util.HashSet;
import java.util.Set;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;

    private Button startBtn, viewLogsBtn;
    private TextView tvStatus, tvSentCount;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isRunning = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable smsRunnable;
    private int sentCount = 0;

    private final Set<String> sentMessages = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task, container, false);

        // Initialize views
        startBtn = view.findViewById(R.id.btn_start_task);
        viewLogsBtn = view.findViewById(R.id.btn_view_logs);
        tvStatus = view.findViewById(R.id.tv_status);
        tvSentCount = view.findViewById(R.id.tv_sent_count);
        progressBar = view.findViewById(R.id.progress_bar);

        // Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check permissions on start
        checkAndRequestSmsPermissions();

        // Set click listeners
        startBtn.setOnClickListener(v -> toggleTask());
        viewLogsBtn.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DeliveryLogActivity.class)));

        return view;
    }

    private void toggleTask() {
        if (!hasSmsPermissions()) {
            checkAndRequestSmsPermissions();
            Toast.makeText(requireContext(), "Please grant SMS permissions to continue", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRunning) {
            stopTask();
        } else {
            startTask();
        }
    }

    private void startTask() {
        isRunning = true;
        sentMessages.clear();
        sentCount = 0;
        updateSentCount();

        progressBar.setVisibility(View.VISIBLE);
        startBtn.setText("Stop Task");
        tvStatus.setText("Task running... Sending messages...");
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange_700));

        smsRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                // Simulate sending a message
                String message = "Test SMS #" + (sentCount + 1);

                if (sentMessages.add(message)) { // Only count unique
                    sentCount++;
                    updateSentCount();

                    // Trigger foreground service to send SMS
                    Intent serviceIntent = new Intent(requireContext(), SmsForegroundService.class);
                    serviceIntent.putExtra("message", message);
                    ContextCompat.startForegroundService(requireContext(), serviceIntent);
                }

                // Schedule next
                handler.postDelayed(this, 1500); // Every 1.5 sec
            }
        };

        handler.post(smsRunnable);
    }

    private void stopTask() {
        isRunning = false;
        if (smsRunnable != null) {
            handler.removeCallbacks(smsRunnable);
        }

        progressBar.setVisibility(View.GONE);
        startBtn.setText("Start Task");
        tvStatus.setText("Task stopped");
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
    }

    private void updateSentCount() {
        tvSentCount.setText("Total Sent: " + sentCount);
    }

    // Permissions
    private void checkAndRequestSmsPermissions() {
        if (!hasSmsPermissions()) {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    SMS_PERMISSION_CODE
            );
        }
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            Toast.makeText(
                    requireContext(),
                    allGranted ? "SMS permissions granted" : "Permissions denied. Cannot send SMS.",
                    Toast.LENGTH_LONG
            ).show();

            if (allGranted && !isRunning) {
                // Optionally auto-start if permissions were the blocker
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTask(); // Prevent leaks
    }
}