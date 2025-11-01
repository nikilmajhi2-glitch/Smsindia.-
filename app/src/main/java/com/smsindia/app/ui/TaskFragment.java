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

    private Handler handler;
    private Runnable smsRunnable;
    private int sentCount = 0;

    private final Set<String> sentMessages = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task, container, false);

        startBtn = v.findViewById(R.id.btn_start_task);
        viewLogsBtn = v.findViewById(R.id.btn_view_logs);
        tvStatus = v.findViewById(R.id.tv_status);
        tvSentCount = v.findViewById(R.id.tv_sent_count);
        progressBar = v.findViewById(R.id.progress_bar);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        handler = new Handler(Looper.getMainLooper());

        checkAndRequestSmsPermissions();

        startBtn.setOnClickListener(view -> toggleTask());
        viewLogsBtn.setOnClickListener(v1 ->
                startActivity(new Intent(requireContext(), DeliveryLogActivity.class)));

        return v;
    }

    private void toggleTask() {
        if (!hasSmsPermissions()) {
            checkAndRequestSmsPermissions();
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
        progressBar.setIndeterminate(true);
        startBtn.setText("⏹ Stop Task");
        tvStatus.setText("🚀 Task running... Sending messages...");
        tvStatus.setTextColor(getResources().getColor(R.color.orange_700));

        smsRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                // Fetch a message from your SMS queue (dummy example)
                String msg = "This is test message #" + (sentCount + 1);

                if (!sentMessages.contains(msg)) {
                    sentMessages.add(msg);
                    sentCount++;
                    tvSentCount.setText("Total Sent: " + sentCount);
                    // Call your service to send the SMS
                    requireContext().startForegroundService(new Intent(requireContext(), SmsForegroundService.class));
                }

                // Repeat every 1 second
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(smsRunnable);
    }

    private void stopTask() {
        isRunning = false;
        handler.removeCallbacks(smsRunnable);
        progressBar.setIndeterminate(false);
        startBtn.setText("▶ Start Task");
        tvStatus.setText("⏸ Task paused");
        tvStatus.setTextColor(getResources().getColor(R.color.gray));
    }

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
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            boolean granted = true;
            for (int res : grantResults)
                if (res != PackageManager.PERMISSION_GRANTED) granted = false;
            Toast.makeText(getContext(),
                    granted ? "✅ SMS permissions granted" : "❌ Please allow all permissions",
                    Toast.LENGTH_LONG).show();
        }
    }
}