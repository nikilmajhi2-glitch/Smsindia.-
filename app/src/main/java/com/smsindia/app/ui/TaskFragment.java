package com.smsindia.app.ui;

import android.Manifest;
import android.app.PendingIntent;
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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.smsindia.app.R;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;
    private Button fetchNextBtn, sendSingleBtn, viewLogsBtn;
    private TextView tvFetchNumber, tvFetchMessage, tvStatus, tvSentCount, tvDebug;
    private ProgressBar progressBar;

    private String curPhone, curMessage, curDocId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task, container, false);

        tvFetchNumber = v.findViewById(R.id.tv_fetch_number);
        tvFetchMessage = v.findViewById(R.id.tv_fetch_message);
        fetchNextBtn = v.findViewById(R.id.btn_fetch_next);
        sendSingleBtn = v.findViewById(R.id.btn_send_single);

        viewLogsBtn = v.findViewById(R.id.btn_view_logs);
        tvStatus = v.findViewById(R.id.tv_status);
        tvSentCount = v.findViewById(R.id.tv_sent_count);
        progressBar = v.findViewById(R.id.progress_bar);
        tvDebug = v.findViewById(R.id.tv_debug);

        checkAndRequestSmsPermissions();

        fetchNextBtn.setOnClickListener(view -> fetchNextTask());
        sendSingleBtn.setOnClickListener(view -> sendCurrentTask());
        viewLogsBtn.setOnClickListener(v1 -> startActivity(new Intent(requireContext(), DeliveryLogActivity.class)));

        return v;
    }

    private void fetchNextTask() {
        tvDebug.setText("Debug: Fetching...");
        FirebaseFirestore.getInstance()
            .collection("sms_tasks").limit(1).get()
            .addOnSuccessListener(snapshot -> {
                if (!snapshot.isEmpty()) {
                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    curPhone = doc.getString("phone");
                    curMessage = doc.getString("message");
                    curDocId = doc.getId();

                    tvFetchNumber.setText("Number: " + (curPhone != null ? curPhone : ""));
                    tvFetchMessage.setText("Content: " + (curMessage != null ? curMessage : ""));
                    tvDebug.setText("Debug: Loaded 1 task");
                } else {
                    tvFetchNumber.setText("Number: ");
                    tvFetchMessage.setText("Content: ");
                    curPhone = curMessage = curDocId = null;
                    tvDebug.setText("Debug: No tasks found");
                }
            })
            .addOnFailureListener(e -> {
                tvDebug.setText("Debug: Firestore error - " + e.getMessage());
            });
    }

    private void sendCurrentTask() {
        if (curPhone == null || curMessage == null) {
            Toast.makeText(requireContext(), "No task loaded!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasSmsPermissions()) {
            Toast.makeText(requireContext(), "SMS permission missing", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Get user id/mobile from SharedPreferences
            SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
            String userId = prefs.getString("mobile", "");

            // DELIVERY INTENT for SmsDeliveryReceiver
            Intent delivered = new Intent("com.smsindia.SMS_DELIVERED");
            delivered.setClass(requireContext(), com.smsindia.app.receivers.SmsDeliveryReceiver.class);
            delivered.putExtra("userId", userId); // mobile
            delivered.putExtra("docId", curDocId);
            delivered.putExtra("phone", curPhone);

            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                requireContext(),
                curDocId != null ? curDocId.hashCode() : 0,
                delivered,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(curPhone, null, curMessage, null, deliveredPI);

            Toast.makeText(requireContext(), "SMS send triggered!", Toast.LENGTH_SHORT).show();
            tvDebug.setText("Debug: SMS send triggered, waiting for delivery...");

            // Do NOT update balance or delete task here - receiver will handle!

            // Clear UI for next fetch
            tvFetchNumber.setText("Number: ");
            tvFetchMessage.setText("Content: ");
            curPhone = curMessage = curDocId = null;
        } catch (Exception e) {
            tvDebug.setText("Debug: SMS FAILED - " + e.getMessage());
            Toast.makeText(requireContext(), "Send failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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