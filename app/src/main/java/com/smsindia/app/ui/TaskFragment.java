package com.smsindia.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
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

import com.google.android.material.textfield.TextInputEditText;
import com.smsindia.app.R;
import com.smsindia.app.workers.SmsWorker;

import java.util.UUID;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;
    private static final String WORK_NAME = "sms_task";

    private Button startBtn, viewLogsBtn, sendTestBtn;
    private TextView tvStatus, tvSentCount, tvDebug;
    private ProgressBar progressBar;
    private TextInputEditText etPhone, etMessage;

    private boolean isRunning = false;
    private UUID currentWorkId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task, container, false);

        startBtn = v.findViewById(R.id.btn_start_task);
        viewLogsBtn = v.findViewById(R.id.btn_view_logs);
        sendTestBtn = v.findViewById(R.id.btn_send_test);
        tvStatus = v.findViewById(R.id.tv_status);
        tvSentCount = v.findViewById(R.id.tv_sent_count);
        progressBar = v.findViewById(R.id.progress_bar);
        tvDebug = v.findViewById(R.id.tv_debug);
        etPhone = v.findViewById(R.id.et_phone);
        etMessage = v.findViewById(R.id.et_message);

        checkAndRequestSmsPermissions();

        startBtn.setOnClickListener(view -> toggleTask());
        viewLogsBtn.setOnClickListener(v1 -> startActivity(new Intent(requireContext(), DeliveryLogActivity.class)));

        sendTestBtn.setOnClickListener(v -> sendManualSms());

        return v;
    }

    private void sendManualSms() {
        String phone = etPhone.getText().toString().trim();
        String msg = etMessage.getText().toString().trim();

        if (phone.isEmpty() || msg.isEmpty()) {
            Toast.makeText(requireContext(), "Fill both fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasSmsPermissions()) {
            Toast.makeText(requireContext(), "SMS permission missing", Toast.LENGTH_SHORT).show();
            return;
        }

        tvDebug.setText("Debug: Sending test SMS...");
        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(phone, null, msg, null, null);
            tvDebug.setText("Debug: Test SMS sent to " + phone);
            Toast.makeText(requireContext(), "Test SMS sent!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            tvDebug.setText("Debug: SMS FAILED -> " + e.getMessage());
            Toast.makeText(requireContext(), "SMS failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        tvStatus.setText("Loading tasks...");
        tvSentCount.setText("Sent: 0");
        tvDebug.setText("Debug: Loading global tasks...");

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SmsWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();

        currentWorkId = work.getId();
        WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work);

        WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(currentWorkId)
                .observe(getViewLifecycleOwner(), workInfo -> {
                    if (workInfo == null) return;

                    var progress = workInfo.getProgress();
                    int sent = progress.getInt("sent", 0);
                    int total = progress.getInt("total", 0);

                    if (total > 0) {
                        tvSentCount.setText("Sent: " + sent + "/" + total);
                        tvStatus.setText("Sending...");
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(total);
                        progressBar.setProgress(sent);
                        tvDebug.setText("Debug: Sending " + sent + "/" + total);
                    }

                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        if (total == 0) {
                            tvStatus.setText("No tasks");
                            tvDebug.setText("Debug: No tasks in Firestore");
                        } else {
                            tvStatus.setText("Completed!");
                            tvDebug.setText("Debug: Sent " + total);
                        }
                        resetUI();
                    } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                        String err = workInfo.getOutputData().getString("error");
                        tvStatus.setText("Failed");
                        tvDebug.setText("Debug: " + (err != null ? err : "Unknown"));
                        resetUI();
                    }
                });
    }

    private void stopTask() {
        if (currentWorkId != null) {
            WorkManager.getInstance(requireContext()).cancelWorkById(currentWorkId);
        }
        resetUI();
        tvStatus.setText("Stopped");
        tvDebug.setText("Debug: Stopped");
    }

    private void resetUI() {
        isRunning = false;
        progressBar.setIndeterminate(false);
        startBtn.setText("Start Auto SMS");
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
                    granted ? "Permissions OK" : "Allow all permissions",
                    Toast.LENGTH_LONG).show();
        }
    }
}