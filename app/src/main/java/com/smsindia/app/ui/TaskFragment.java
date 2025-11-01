package com.smsindia.app.ui;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.smsindia.app.R;

import java.util.ArrayList;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;
    private Button fetchNextBtn, sendSingleBtn, viewLogsBtn;
    private TextView tvFetchNumber, tvFetchMessage, statusMessage, failHint;
    private ProgressBar sendingProgress;
    private CardView statusCard;

    private String curPhone, curMessage, curDocId;
    private int failCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task, container, false);

        statusCard = v.findViewById(R.id.status_card);
        statusMessage = v.findViewById(R.id.status_message);
        failHint = v.findViewById(R.id.fail_hint);
        sendingProgress = v.findViewById(R.id.sending_progress);

        tvFetchNumber = v.findViewById(R.id.tv_fetch_number);
        tvFetchMessage = v.findViewById(R.id.tv_fetch_message);
        fetchNextBtn = v.findViewById(R.id.btn_fetch_next);
        sendSingleBtn = v.findViewById(R.id.btn_send_single);
        viewLogsBtn = v.findViewById(R.id.btn_view_logs);

        checkAndRequestSmsPermissions();

        fetchNextBtn.setOnClickListener(view -> fetchNextTask());
        sendSingleBtn.setOnClickListener(view -> sendCurrentTask());
        viewLogsBtn.setOnClickListener(v1 -> startActivity(new Intent(requireContext(), DeliveryLogActivity.class)));

        showReadyUI();
        return v;
    }

    private void fetchNextTask() {
        showReadyUI();
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
                } else {
                    tvFetchNumber.setText("Number: ");
                    tvFetchMessage.setText("Content: ");
                    curPhone = curMessage = curDocId = null;
                    statusMessage.setText("No SMS tasks available.");
                    statusCard.setCardBackgroundColor(Color.parseColor("#FFECB3")); // light yellow
                }
            })
            .addOnFailureListener(e -> {
                statusMessage.setText("Error fetching task: " + e.getMessage());
                statusCard.setCardBackgroundColor(Color.parseColor("#FFCDD2")); // light red
            });
    }

    private void sendCurrentTask() {
        if (curPhone == null || curMessage == null) {
            statusMessage.setText("No task loaded. Please fetch next.");
            statusCard.setCardBackgroundColor(Color.parseColor("#FFECB3"));
            return;
        }
        if (!hasSmsPermissions()) {
            statusMessage.setText("SMS permission missing. Please grant and retry.");
            statusCard.setCardBackgroundColor(Color.parseColor("#FFCDD2"));
            return;
        }

        showSendingUI();
        try {
            SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
            String userId = prefs.getString("mobile", "");

            Intent sent = new Intent("com.smsindia.SMS_SENT");
            sent.setClass(requireContext(), com.smsindia.app.receivers.SmsDeliveryReceiver.class);
            sent.putExtra("userId", userId);
            sent.putExtra("docId", curDocId);
            sent.putExtra("phone", curPhone);

            PendingIntent sentPI = PendingIntent.getBroadcast(
                requireContext(),
                curDocId != null ? curDocId.hashCode() : 0,
                sent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(curMessage);
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) sentIntents.add(sentPI);

            sms.sendMultipartTextMessage(curPhone, null, parts, sentIntents, null);

            statusMessage.setText("Sending SMS...");
            sendingProgress.setVisibility(View.VISIBLE);
            sendSingleBtn.setEnabled(false);
        } catch (Exception e) {
            showFailUI("Send failed: " + e.getMessage());
        }
    }

    // UI Helpers
    private void showReadyUI() {
        statusMessage.setText("Ready to send SMS");
        statusCard.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
        sendingProgress.setVisibility(View.GONE);
        sendSingleBtn.setEnabled(true);
        failHint.setVisibility(View.GONE);
        failCount = 0;
    }

    private void showSendingUI() {
        statusMessage.setText("Sending SMS...");
        statusCard.setCardBackgroundColor(Color.parseColor("#FFFDE7")); // Yellow
        sendingProgress.setVisibility(View.VISIBLE);
        sendSingleBtn.setEnabled(false);
        failHint.setVisibility(View.GONE);
    }

    // STATIC for use in BroadcastReceiver
    public static void showSuccessUI(View root, String phone) {
        TextView statusMessage = root.findViewById(R.id.status_message);
        CardView statusCard = root.findViewById(R.id.status_card);
        ProgressBar sendingProgress = root.findViewById(R.id.sending_progress);
        Button sendSingleBtn = root.findViewById(R.id.btn_send_single);
        TextView failHint = root.findViewById(R.id.fail_hint);

        statusMessage.setText("SMS sent to " + phone + " ✓
₹0.16 credited!");
        statusCard.setCardBackgroundColor(Color.parseColor("#C8E6C9")); // Green
        sendingProgress.setVisibility(View.GONE);
        sendSingleBtn.setEnabled(true);
        failHint.setVisibility(View.GONE);
    }

    public static void showFailUI(View root, String phone, boolean repeated) {
        TextView statusMessage = root.findViewById(R.id.status_message);
        CardView statusCard = root.findViewById(R.id.status_card);
        ProgressBar sendingProgress = root.findViewById(R.id.sending_progress);
        Button sendSingleBtn = root.findViewById(R.id.btn_send_single);
        TextView failHint = root.findViewById(R.id.fail_hint);

        statusMessage.setText("SMS failed to " + phone + ". Try again.");
        statusCard.setCardBackgroundColor(Color.parseColor("#FFCDD2")); // Red
        sendingProgress.setVisibility(View.GONE);
        sendSingleBtn.setEnabled(true);
        if (repeated) {
            failHint.setText("Please check your SIM plan, active data, and network.");
            failHint.setVisibility(View.VISIBLE);
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
