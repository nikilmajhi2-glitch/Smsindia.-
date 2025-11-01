package com.smsindia.app.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.smsindia.app.R;

public class DeliveryLogActivity extends AppCompatActivity {

    private LinearLayout logsContainer;
    private FirebaseFirestore db;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_logs);

        logsContainer = findViewById(R.id.logs_container);
        db = FirebaseFirestore.getInstance();

        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        uid = prefs.getString("mobile", "");

        if (uid.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadLogs();
    }

    private void loadLogs() {
        db.collection("sent_logs")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    logsContainer.removeAllViews();
                    if (snapshot.isEmpty()) {
                        addText("No logs found yet");
                        return;
                    }
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String phone = doc.getString("phone");
                        Long timestamp = doc.getLong("timestamp");
                        if (phone == null || timestamp == null) continue;

                        String formatted = android.text.format.DateFormat.format("dd MMM, hh:mm a", timestamp).toString();
                        addText("Phone: " + phone + "  •  " + formatted);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load logs: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void addText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setPadding(20, 10, 20, 10);
        logsContainer.addView(tv);
    }
}