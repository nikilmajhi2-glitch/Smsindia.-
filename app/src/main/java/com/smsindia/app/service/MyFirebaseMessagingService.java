package com.smsindia.app.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.smsindia.app.MainActivity;
import com.smsindia.app.R;

/**
 * ✅ Handles push notifications from Firebase
 * Use to trigger remote SMS syncs or alerts.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "firebase_channel";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        String title = message.getNotification() != null ? message.getNotification().getTitle() : "SMSIndia";
        String body = message.getNotification() != null ? message.getNotification().getBody() : "New update received";

        showNotification(title, body);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // Optional: Save token to Firestore for admin control
    }

    private void showNotification(String title, String msg) {
        createChannel();
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name) // <-- FIXED, drawable only
                .setContentTitle(title)
                .setContentText(msg)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Firebase Updates", NotificationManager.IMPORTANCE_DEFAULT);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
}