package com.smsindia.app.workers;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.smsindia.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SmsWorker — sends SMS and triggers delivery broadcast.
 * Balance update only happens in SmsDeliveryReceiver after real delivery confirmation.
 * Runs as a Foreground Worker for Android 14/15 stability.
 */
public class SmsWorker extends Worker {

    private static final String CHANNEL_ID = "sms_worker_channel";
    private final FirebaseFirestore db;
    private final Context context;

    public SmsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        // Keep the worker alive on Android 15
        setForegroundAsync(createForegroundInfo());

        String userId = getInputData().getString("userId");
        if (userId == null || userId.isEmpty()) {
            showToast("Missing user ID");
            return Result.failure();
        }

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                showToast("SMS permission not granted!");
                return Result.failure();
            }

            SmsManager smsManager = SmsManager.getDefault();
            int totalSent = 0;

            while (!isStopped()) {
                List<Map<String, Object>> tasks = fetchTasks();
                if (tasks == null || tasks.isEmpty()) {
                    showToast("No more SMS tasks");
                    break;
                }

                for (Map<String, Object> task : tasks) {
                    if (isStopped()) break;

                    String phone = (String) task.get("phone");
                    String message = (String) task.get("message");
                    String docId = (String) task.get("id");

                    if (phone == null || message == null || phone.isEmpty() || message.isEmpty())
                        continue;

                    try {
                        // Delivery Broadcast
                        String DELIVERED_ACTION = "com.smsindia.SMS_DELIVERED";

                        Intent deliveredIntent = new Intent(DELIVERED_ACTION);
                        deliveredIntent.setPackage(context.getPackageName());
                        deliveredIntent.putExtra("userId", userId);
                        deliveredIntent.putExtra("docId", docId);
                        deliveredIntent.putExtra("phone", phone);

                        PendingIntent deliveredPI = PendingIntent.getBroadcast(
                                context,
                                docId.hashCode(),
                                deliveredIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        );

                        // Send SMS — balance updates only after delivery
                        smsManager.sendTextMessage(phone, null, message, null, deliveredPI);
                        totalSent++;
                        showToast("Sent to " + phone);

                        // Short delay to prevent spam detection
                        Thread.sleep(1500);

                    } catch (Exception e) {
                        showToast("Send failed: " + e.getMessage());
                    }
                }
            }

            showToast("Task complete. Total sent: " + totalSent);
            return Result.success();

        } catch (Exception e) {
            showToast("Error: " + e.getMessage());
            return Result.failure();
        }
    }

    /**
     * Foreground notification for Android 15 stability.
     */
    private ForegroundInfo createForegroundInfo() {
        String title = "SMSIndia is sending messages";
        String text = "Running SMS delivery tasks...";

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS Sending Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps SMSIndia active while sending messages.");
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.sym_action_chat)  // FIXED: No ic_sms
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return new ForegroundInfo(1, notification.build());
    }

    /**
     * Fetch up to 5 pending SMS tasks.
     */
    private List<Map<String, Object>> fetchTasks() throws InterruptedException {
        final Object lock = new Object();
        final List<Map<String, Object>>[] result = new List[1];

        db.collection("sms_tasks").limit(5).get()
                .addOnSuccessListener(snapshot -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("id", doc.getId());
                            list.add(data);
                        }
                    }
                    result[0] = list;
                    synchronized (lock) {
                        lock.notify();
                    }
                })
                .addOnFailureListener(e -> {
                    result[0] = null;
                    synchronized (lock) {
                        lock.notify();
                    }
                });

        synchronized (lock) {
            lock.wait(4000);
        }
        return result[0];
    }

    private void showToast(String msg) {
        new android.os.Handler(context.getMainLooper())
                .post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }
}