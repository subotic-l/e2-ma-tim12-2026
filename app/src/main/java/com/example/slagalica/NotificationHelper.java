package com.example.slagalica;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationHelper {

    private static final AtomicInteger notifId = new AtomicInteger(2000);
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private static String pendingChannelId;
    private static String pendingTitle;
    private static String pendingBody;
    private static String pendingUid;

    public static void show(Context context, String channelId, String title, String body, String uid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (!nm.areNotificationsEnabled()) {
                pendingChannelId = channelId;
                pendingTitle = title;
                pendingBody = body;
                pendingUid = uid;
                if (context instanceof Activity) {
                    ActivityCompat.requestPermissions(
                            (Activity) context,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            PERMISSION_REQUEST_CODE);
                }
                return;
            }
        }

        showInternal(context, channelId, title, body, uid);
    }

    public static void onRequestPermissionsResult(Context context, int requestCode, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE && pendingChannelId != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showInternal(context, pendingChannelId, pendingTitle, pendingBody, pendingUid);
            }
            pendingChannelId = null;
            pendingTitle = null;
            pendingBody = null;
            pendingUid = null;
        }
    }

    private static void showInternal(Context context, String channelId, String title, String body, String uid) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true);
        nm.notify(notifId.incrementAndGet(), builder.build());

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
