package com.example.slagalica;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String FCM_PREFS = "fcm_prefs";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final AtomicInteger notifId = new AtomicInteger(4000);

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        saveTokenLocally(token);
        uploadTokenToFirestore(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Map<String, String> data = message.getData();
        String type = data.get("type");

        if ("friend_invitation".equals(type)) {
            handleFriendInvitation(data);
        } else if ("chat_message".equals(type)) {
            handleChatMessage(data);
        } else {
            String channelId = data.getOrDefault("channelId", SlagalicaApp.CHANNEL_GENERAL);
            String title = data.getOrDefault("title", "Slagalica");
            String body = data.getOrDefault("body", "");
            String uid = data.getOrDefault("uid", null);
            NotificationHelper.show(this, channelId, title, body, uid);
        }
    }

    private void handleChatMessage(Map<String, String> data) {
        String title = data.getOrDefault("title", "Slagalica");
        String body = data.getOrDefault("body", "");
        NotificationHelper.show(this, SlagalicaApp.CHANNEL_CHAT, title, body, null);
    }

    private void handleFriendInvitation(Map<String, String> data) {
        String fromName = data.get("fromName");
        String fromId = data.get("fromId");
        String invitationId = data.get("invitationId");

        Intent intent = new Intent(this, FriendLobbyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("autoAcceptInvitationId", invitationId);
        intent.putExtra("autoAcceptFromId", fromId);
        intent.putExtra("autoAcceptFromName", fromName);
        intent.putExtra("autoAcceptFromAvatar", data.getOrDefault("fromAvatar", ""));

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SlagalicaApp.CHANNEL_INVITATIONS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Poziv za partiju")
                .setContentText((fromName != null ? fromName : "Neko")
                        + " vas poziva na prijateljsku partiju Slagalice!")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        nm.notify(notifId.incrementAndGet(), builder.build());
    }

    private void saveTokenLocally(String token) {
        SharedPreferences prefs = getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply();
    }

    public static void uploadTokenToFirestore(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && token != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .update("fcmToken", token);
        }
    }

    public static String getLocalToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_FCM_TOKEN, null);
    }
}
