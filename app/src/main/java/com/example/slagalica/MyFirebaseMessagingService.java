package com.example.slagalica;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String FCM_PREFS = "fcm_prefs";
    private static final String KEY_FCM_TOKEN = "fcm_token";

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
        String channelId = data.getOrDefault("channelId", SlagalicaApp.CHANNEL_GENERAL);
        String title = data.getOrDefault("title", "Slagalica");
        String body = data.getOrDefault("body", "");
        String uid = data.getOrDefault("uid", null);

        NotificationHelper.show(this, channelId, title, body, uid);
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
