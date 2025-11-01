package com.smsindia.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.smsindia.app.R;
import com.smsindia.app.service.SmsForegroundService;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;

    private Button startBtn, viewLogsBtn;
    private TextView tvStatus, tvSentCount;
    private ProgressBar progressBar;

    private boolean isRunning = false;
    private Handler handler = new Handler(Looper.getMainLooper());

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
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        String phone = prefs.getString("mobile", "");
        if (phone.isEmpty()) {
            Toast.makeText(requireContext(), "Not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = true;
        progressBar.setIndeterminate(true);
        startBtn.setText("Stop Task");
        tvStatus.setText("Task running... Sending messages...");
        tvStatus.setTextColor(getResources().getColor(R.color.orange_700));
        tvSentCount.setText("Sent: 0");

        // START SERVICE ONCE — LET IT HANDLE LOOP
        Intent intent = new Intent(requireContext(), SmsForegroundService.class);
        requireContext().startForegroundService(intent);

        Toast.makeText(requireContext(), "SMS Task Started!", Toast.LENGTH_LONG).show();
    }

    private void stopTask() {
        isRunning = false;
        progressBar.setIndeterminate(false);
        startBtn.setText("Start SMS Task");
        tvStatus.setText("Task stopped");
        tvStatus.setTextColor(getResources().getColor(R.color.gray));

        // Optional: Stop service via intent
        Intent intent = new Intent(requireContext(), SmsForegroundService.class);
        requireContext().stopService(intent);
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
                    granted ? "SMS permissions granted" : "Please allow all permissions",
                    Toast.LENGTH_LONG).show();
        }
    }
}