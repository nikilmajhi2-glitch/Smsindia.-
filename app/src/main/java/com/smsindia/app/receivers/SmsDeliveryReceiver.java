package com.smsindia.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;
import com.smsindia.app.ui.TaskFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;

public class SmsDeliveryReceiver extends BroadcastReceiver {
    private static int failCount = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        int partIndex = intent.getIntExtra("partIndex", 0);
        if (partIndex != 0) return; // Credit/delete/log only once, on first part

        String userId = intent.getStringExtra("userId");
        String docId = intent.getStringExtra("docId");
        String phone = intent.getStringExtra("phone");
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String status = "failed";
        switch (getResultCode()) {
            case android.app.Activity.RESULT_OK:
                status = "sent";
                failCount = 0;
                Toast.makeText(context, "SMS Sent to " + phone + ". ₹0.16 credited!", Toast.LENGTH_SHORT).show();

                if (userId != null && !userId.isEmpty()) {
                    db.collection("users")
                      .document(userId)
                      .update("balance", FieldValue.increment(0.16));
                }

                if (docId != null) {
                    db.collection("sms_tasks").document(docId).delete();
                }

                if (context instanceof android.app.Activity) {
                    Handler handler = new Handler(context.getMainLooper());
                    handler.post(() -> {
                        TaskFragment.showSuccessUI(((android.app.Activity) context).findViewById(android.R.id.content), phone);
                    });
                }
                break;

            default:
                failCount++;
                Toast.makeText(context, "SMS Failed to " + phone, Toast.LENGTH_SHORT).show();
                if (docId != null) {
                    db.collection("sms_tasks").document(docId).delete();
                }
                if (context instanceof android.app.Activity) {
                    Handler handler = new Handler(context.getMainLooper());
                    handler.post(() -> {
                        TaskFragment.showFailUI(((android.app.Activity) context).findViewById(android.R.id.content),
                                phone, failCount >= 2);
                    });
                }
                break;
        }

        if (userId != null && phone != null) {
            Map<String, Object> log = new HashMap<>();
            log.put("userId", userId);
            log.put("phone", phone);
            log.put("timestamp", System.currentTimeMillis());
            log.put("status", status);
            db.collection("sent_logs").add(log);
        }
    }
}