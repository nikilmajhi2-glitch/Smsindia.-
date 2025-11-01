package com.smsindia.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class SmsDeliveryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String userId = intent.getStringExtra("userId");  // This is mobile number
        String docId = intent.getStringExtra("docId");
        String phone = intent.getStringExtra("phone");
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String status = "failed";

        switch (getResultCode()) {
            case android.app.Activity.RESULT_OK:
                status = "delivered";
                Toast.makeText(context, "SMS Delivered to " + phone, Toast.LENGTH_SHORT).show();

                // UPDATE BALANCE (userId = mobile)
                if (userId != null) {
                    db.collection("users")
                            .whereEqualTo("mobile", userId)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (!snapshot.isEmpty()) {
                                    String realUid = snapshot.getDocuments().get(0).getId();
                                    db.collection("users").document(realUid)
                                            .update("balance", FieldValue.increment(0.16));
                                }
                            });
                }

                // DELETE TASK
                if (docId != null) {
                    db.collection("sms_tasks").document(docId).delete();
                }
                break;

            default:
                Toast.makeText(context, "SMS Failed: " + phone, Toast.LENGTH_SHORT).show();
                break;
        }

        // LOG WITH PHONE
        if (userId != null && phone != null) {
            Map<String, Object> log = new HashMap<>();
            log.put("userId", userId);
            log.put("phone", phone);
            log.put("timestamp", System.currentTimeMillis());
            log.put("status", status);
            db.collection("sent_logs").add(log);  // Matches DeliveryLogActivity
        }
    }
}