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
    private final Handler handler = new Handler();
    private boolean isRunning = false;
    private final List<Map<String, Object>> tasks = new ArrayList<>();

    private FirebaseFirestore db;
    private String uid;

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();

        // GET UID FROM SharedPreferences (NOT FirebaseAuth)
        SharedPreferences prefs = getSharedPreferences("SMSINDIA_USER", MODE_PRIVATE);
        uid = prefs.getString("mobile", "");

        if (uid.isEmpty()) {
            stopSelf();
            return;
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            stopSending();
            return START_NOT_STICKY;
        }

        startForeground(1, buildNotification("Preparing to send SMS..."));
        loadTasks();
        return START_NOT_STICKY;
    }

    private void loadTasks() {
        db.collection("sms_tasks")
                .get()
                .addOnSuccessListener(snapshot -> {
                    tasks.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        tasks.add(doc.getData());
                    }
                    handler.post(() -> Toast.makeText(this, "Loaded " + tasks.size() + " tasks", Toast.LENGTH_SHORT).show());
                    startSending();
                })
                .addOnFailureListener(e -> {
                    handler.post(() -> Toast.makeText(this, "Failed to load tasks: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    stopSelf();
                });
    }

    private void startSending() {
        if (isRunning || tasks.isEmpty()) {
            stopSelf();
            return;
        }
        isRunning = true;

        new Thread(() -> {
            SmsManager sms = SmsManager.getDefault();
            int sent = 0;

            for (Map<String, Object> t : tasks) {
                if (!isRunning) break;

                String phone = (String) t.get("phone");
                String msg = (String) t.get("message");
                if (phone == null || msg == null) continue;

                try {
                    Intent delivered = new Intent("com.smsindia.SMS_DELIVERED");
                    delivered.putExtra("userId", uid);
                    delivered.putExtra("phone", phone);

                    PendingIntent deliveredPI = PendingIntent.getBroadcast(
                            this,
                            phone.hashCode(),
                            delivered,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    sms.sendTextMessage(phone, null, msg, null, deliveredPI);
                    sent++;

                    // Update notification
                    Notification n = buildNotification("Sending... (" + sent + "/" + tasks.size() + ")");
                    getSystemService(NotificationManager.class).notify(1, n);

                    Thread.sleep(1500); // Respect rate limit
                } catch (Exception e) {
                    handler.post(() -> Toast.makeText(this, "Send failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }

            isRunning = false;
            stopForeground(true);
            stopSelf();
        }).start();
    }

    private void stopSending() {
        isRunning = false;
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification(String msg) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMSIndia Service")
                .setContentText(msg)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "SMS Foreground Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}