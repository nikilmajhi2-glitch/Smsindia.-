package com.smsindia.app.workers;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.smsindia.app.MainActivity;
import com.smsindia.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SmsWorker extends Worker {

    private static final String CHANNEL_ID = "sms_worker_channel";
    private static final String TAG = "SmsWorker";
    private final Context context;
    private final FirebaseFirestore db;
    private final String uid;

    public SmsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        this.db = FirebaseFirestore.getInstance();

        SharedPreferences prefs = context.getSharedPreferences("SMSINDIA_USER", Context.MODE_PRIVATE);
        this.uid = prefs.getString("mobile", "");
    }

    @NonNull
    @Override
    public Result doWork() {
        if (uid.isEmpty()) {
            Log.e(TAG, "No user ID");
            return Result.failure();
        }

        setForegroundAsync(createForegroundInfo("SMS Service Active"));

        List<Map<String, Object>> tasks = loadTasksSync();
        if (tasks.isEmpty()) {
            return Result.success();
        }

        SmsManager sms = SmsManager.getDefault();
        int sent = 0;

        for (Map<String, Object> t : tasks) {
            if (isStopped()) break;

            String phone = (String) t.get("phone");
            String msg = (String) t.get("message");
            String docId = (String) t.get("id");

            if (phone == null || msg == null || docId == null) continue;

            try {
                Intent delivered = new Intent("com.smsindia.SMS_DELIVERED");
                delivered.putExtra("userId", uid);
                delivered.putExtra("docId", docId);
                delivered.putExtra("phone", phone);

                PendingIntent pi = PendingIntent.getBroadcast(
                        context, docId.hashCode(), delivered,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                sms.sendTextMessage(phone, null, msg, null, pi);
                sent++;

                setForegroundAsync(createForegroundInfo("Sent " + sent + "/" + tasks.size()));

                Thread.sleep(1500);
            } catch (Exception e) {
                Log.e(TAG, "Send failed", e);
            }
        }

        return Result.success();
    }

    private List<Map<String, Object>> loadTasksSync() {
        final List<Map<String, Object>> tasks = new ArrayList<>();
        final Object lock = new Object();

        db.collection("sms_tasks")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Map<String, Object> data = doc.getData();
                        data.put("id", doc.getId());
                        tasks.add(data);
                    }
                    synchronized (lock) { lock.notify(); }
                })
                .addOnFailureListener(e -> {
                    synchronized (lock) {
                        lock.notify();
                    }
                });

        try {
            synchronized (lock) { lock.wait(5000); }
        } catch (InterruptedException ignored) {}

        return tasks;
    }

    private ForegroundInfo createForegroundInfo(String content) {
        createChannel();

        Intent i = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("SMSIndia")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        return new ForegroundInfo(1, n);
    }

    private void createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "SMS Worker", NotificationManager.IMPORTANCE_LOW);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}