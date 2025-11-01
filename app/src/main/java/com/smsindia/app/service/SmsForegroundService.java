package com.smsindia.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.smsindia.app.MainActivity;
import com.smsindia.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SmsForegroundService extends Service {

    private static final String CHANNEL_ID = "smsindia_service";
    private static final String TAG = "SmsForegroundService";
    private final Handler handler = new Handler();
    private boolean isRunning = false;
    private final List<Map<String, Object>> tasks = new ArrayList<>();

    private FirebaseFirestore db;
    private String uid;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        db = FirebaseFirestore.getInstance();

        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        uid = prefs.getString("mobile", "");

        if (uid.isEmpty()) {
            Log.e(TAG, "No user ID found. Stopping service.");
            stopSelf();
            return;
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            Log.d(TAG, "Stop action received");
            stopSending();
            return START_NOT_STICKY;
        }

        // CRITICAL: Start foreground IMMEDIATELY
        startForeground(1, buildNotification("SMS Service Active"));

        loadTasks();
        return START_STICKY; // Keep service alive
    }

    private void loadTasks() {
        Log.d(TAG, "Loading tasks from Firestore...");

        db.collection("sms_tasks")
                .get()
                .addOnSuccessListener(snapshot -> {
                    tasks.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Map<String, Object> data = doc.getData();
                        data.put("id", doc.getId());
                        tasks.add(data);
                    }

                    handler.post(() -> {
                        Toast.makeText(this, "Loaded " + tasks.size() + " tasks", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Loaded " + tasks.size() + " tasks");
                    });

                    if (!tasks.isEmpty()) {
                        startSending();
                    } else {
                        stopForeground(true);
                        stopSelf();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load tasks", e);
                    handler.post(() -> Toast.makeText(this, "Failed to load tasks: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    stopForeground(true);
                    stopSelf();
                });
    }

    private void startSending() {
        if (isRunning || tasks.isEmpty()) {
            stopForeground(true);
            stopSelf();
            return;
        }

        isRunning = true;
        Log.d(TAG, "Starting SMS sending thread");

        new Thread(() -> {
            SmsManager sms = SmsManager.getDefault();
            int sent = 0;

            for (Map<String, Object> t : tasks) {
                if (!isRunning) {
                    Log.d(TAG, "Sending stopped by user");
                    break;
                }

                String phone = (String) t.get("phone");
                String msg = (String) t.get("message");
                String docId = (String) t.get("id");

                if (phone == null || msg == null || docId == null) {
                    Log.w(TAG, "Invalid task data: " + t);
                    continue;
                }

                try {
                    Intent delivered = new Intent("com.smsindia.SMS_DELIVERED");
                    delivered.putExtra("userId", uid);
                    delivered.putExtra("docId", docId);
                    delivered.putExtra("phone", phone);

                    PendingIntent deliveredPI = PendingIntent.getBroadcast(
                            this,
                            docId.hashCode(),
                            delivered,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    Log.d(TAG, "Sending SMS to: " + phone);
                    sms.sendTextMessage(phone, null, msg, null, deliveredPI);
                    sent++;

                    // UPDATE FOREGROUND NOTIFICATION
                    startForeground(1, buildNotification("Sent " + sent + "/" + tasks.size()));

                    Thread.sleep(1500); // Rate limit
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send SMS to " + phone, e);
                    handler.post(() -> Toast.makeText(this, "Send failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }

            Log.d(TAG, "SMS sending completed. Total sent: " + sent);
            isRunning = false;
            stopForeground(true);
            stopSelf();
        }).start();
    }

    private void stopSending() {
        Log.d(TAG, "stopSending called");
        isRunning = false;
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification(String contentText) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMSIndia")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS Foreground Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps SMS sending active in background");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}