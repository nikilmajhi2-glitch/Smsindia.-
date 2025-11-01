package com.smsindia.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
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
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.smsindia.app.R;
import com.smsindia.app.workers.SmsWorker;

import java.util.UUID;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;
    private static final String WORK_NAME = "sms_task";
    private static final String TAG = "TaskFragment";

    private Button startBtn, viewLogsBtn;
    private TextView tvStatus, tvSentCount, tvDebug; // BUG DETECTOR
    private ProgressBar progressBar;

    private boolean isRunning = false;
    private UUID currentWorkId = null;

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
        tvDebug = v.findViewById(R.id.tv_debug); // BUG DETECTOR

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
        tvStatus.setText("Assigning tasks…");
        tvStatus.setTextColor(getResources().getColor(R.color.orange_700));
        tvSentCount.setText("Sent: 0");
        tvDebug.setText("Debug: Initializing...");

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SmsWorker.class)
                .setInputData(new androidx.work.Data.Builder()
                        .putString("userId", phone)
                        .build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();

        currentWorkId = work.getId();

        WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work);

        // BUG DETECTOR: Observe EVERYTHING
        WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(currentWorkId)
                .observe(getViewLifecycleOwner(), workInfo -> {
                    if (workInfo == null) {
                        tvDebug.setText("Debug: workInfo null");
                        return;
                    }

                    Log.d(TAG, "Work state: " + workInfo.getState());

                    // PROGRESS
                    androidx.work.Data progress = workInfo.getProgress();
                    int sent = progress.getInt("sent", 0);
                    int total = progress.getInt("total", 0);

                    if (total > 0) {
                        tvSentCount.setText("Sent: " + sent + "/" + total);
                        tvStatus.setText("Sending messages…");
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(total);
                        progressBar.setProgress(sent);
                        tvDebug.setText("Debug: Sending " + sent + "/" + total);
                    } else {
                        tvDebug.setText("Debug: Waiting for tasks...");
                    }

                    // FINAL STATE
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        if (total == 0) {
                            tvStatus.setText("No tasks available");
                            tvStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                            tvDebug.setText("Debug: No tasks in global pool");
                        } else {
                            tvStatus.setText("Task completed");
                            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            tvDebug.setText("Debug: Success! Sent " + total);
                        }
                        resetUI();
                    } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                        String error = workInfo.getOutputData().getString("error");
                        tvStatus.setText("Task failed");
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        tvDebug.setText("Debug: FAILED → " + (error != null ? error : "Unknown"));
                        resetUI();
                    } else if (workInfo.getState() == WorkInfo.State.CANCELLED) {
                        tvStatus.setText("Task cancelled");
                        tvDebug.setText("Debug: Cancelled by user");
                        resetUI();
                    }
                });

        Toast.makeText(requireContext(), "SMS Task Started!", Toast.LENGTH_LONG).show();
    }

    private void stopTask() {
        if (currentWorkId != null) {
            WorkManager.getInstance(requireContext()).cancelWorkById(currentWorkId);
        }
        resetUI();
        tvStatus.setText("Task stopped");
        tvStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tvDebug.setText("Debug: Stopped");
    }

    private void resetUI() {
        isRunning = false;
        progressBar.setIndeterminate(false);
        startBtn.setText("Start SMS Task");
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