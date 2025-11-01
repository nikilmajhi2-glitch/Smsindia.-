package com.smsindia.app.workers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
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
            return Result.failure(new Data.Builder()
                    .putString("error", "User not logged in")
                    .build());
        }

        setForegroundAsync(createForegroundInfo("SMS Service Active"));

        List<Map<String, Object>> tasks = loadTasksSync();

        if (tasks.isEmpty()) {
            Log.d(TAG, "No tasks assigned");
            setProgressAsync(new Data.Builder().putInt("sent", 0).putInt("total", 0).build());
            setForegroundAsync(createForegroundInfo("No tasks available"));
            return Result.success();
        }

        SmsManager sms = SmsManager.getDefault();
        int sent = 0;

        try {
            for (Map<String, Object> t : tasks) {
                if (isStopped()) {
                    Log.d(TAG, "Worker stopped by user");
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

                    PendingIntent pi = PendingIntent.getBroadcast(
                            context, docId.hashCode(), delivered,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    sms.sendTextMessage(phone, null, msg, null, pi);
                    sent++;

                    setProgressAsync(new Data.Builder()
                            .putInt("sent", sent)
                            .putInt("total", tasks.size())
                            .build());

                    setForegroundAsync(createForegroundInfo("Sent " + sent + "/" + tasks.size()));

                    Thread.sleep(1200);
                } catch (SecurityException e) {
                    Log.e(TAG, "SMS permission denied", e);
                    return Result.failure(new Data.Builder()
                            .putString("error", "SMS permission denied")
                            .build());
                } catch (Exception e) {
                    Log.e(TAG, "Send failed for " + docId, e);
                    // Continue with next
                }
            }

            Log.d(TAG, "Completed: " + sent + "/" + tasks.size());
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Worker crashed", e);
            return Result.failure(new Data.Builder()
                    .putString("error", e.getMessage() != null ? e.getMessage() : "Unknown error")
                    .build());
        }
    }

    private List<Map<String, Object>> loadTasksSync() {
        final List<Map<String, Object>> tasks = new ArrayList<>();
        final Object lock = new Object();

        Log.d(TAG, "Step 1: Querying global sms_tasks (limit 100)...");

        db.collection("sms_tasks")
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int size = querySnapshot.size();
                    Log.d(TAG, "Step 1 SUCCESS: Found " + size + " global tasks");

                    if (size == 0) {
                        Log.d(TAG, "No tasks in global pool");
                        synchronized (lock) { lock.notify(); }
                        return;
                    }

                    List<DocumentReference> globalRefs = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        globalRefs.add(doc.getReference());
                    }

                    Log.d(TAG, "Step 2: Running transaction on " + globalRefs.size() + " docs...");

                    db.runTransaction(transaction -> {
                        List<Map<String, Object>> assigned = new ArrayList<>();

                        for (DocumentReference ref : globalRefs) {
                            try {
                                DocumentSnapshot doc = transaction.get(ref);
                                if (!doc.exists()) {
                                    Log.w(TAG, "Doc deleted during transaction: " + ref.getId());
                                    continue;
                                }

                                Map<String, Object> data = doc.getData();
                                if (data == null) continue;

                                data.put("id", doc.getId());
                                assigned.add(data);

                                DocumentReference userRef = db.collection("users")
                                        .document(uid)
                                        .collection("sms_tasks")
                                        .document(doc.getId());
                                transaction.set(userRef, data);

                                transaction.delete(ref);

                            } catch (Exception e) {
                                Log.e(TAG, "Transaction error on doc: " + ref.getId(), e);
                                throw e;
                            }
                        }
                        return assigned;
                    })
                    .addOnSuccessListener(result -> {
                        tasks.addAll(result);
                        Log.d(TAG, "TRANSACTION SUCCESS: Assigned " + result.size() + " tasks");
                        synchronized (lock) { lock.notify(); }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "TRANSACTION FAILED: " + e.getMessage(), e);
                        synchronized (lock) { lock.notify(); }
                    });

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "QUERY FAILED: " + e.getMessage(), e);
                    synchronized (lock) { lock.notify(); }
                });

        try {
            synchronized (lock) { lock.wait(15000); }
        } catch (InterruptedException ignored) {}

        Log.d(TAG, "Returning " + tasks.size() + " tasks to send");
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
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}