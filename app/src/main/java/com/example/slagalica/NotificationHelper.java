package com.example.slagalica;

import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationHelper {

    private static final AtomicInteger notifId = new AtomicInteger(2000);

    public static void show(Context context, String channelId, String title, String body, String uid) {
        // 1. Sistemska notifikacija
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true);
        nm.notify(notifId.incrementAndGet(), builder.build());

        // 2. Upis u Firestore
        if (uid != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("message", title + ": " + body);
            data.put("createdAt", Timestamp.now());
            data.put("read", false);
            data.put("channel", channelId);
            FirebaseFirestore.getInstance()
                    .collection("users").document(uid).collection("notifications")
                    .add(data);
        }
    }
}
