package com.example.slagalica;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class SlagalicaApp extends Application {

    public static final String CHANNEL_CHAT = "chat";
    public static final String CHANNEL_RANKING = "ranking";
    public static final String CHANNEL_REWARDS = "rewards";
    public static final String CHANNEL_GENERAL = "general";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        String cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME;
        String apiKey = BuildConfig.CLOUDINARY_API_KEY;
        String apiSecret = BuildConfig.CLOUDINARY_API_SECRET;

        if (cloudName.isEmpty()) {
            throw new IllegalStateException(
                    "Cloudinary cloud name not configured. Add cloudinary.cloud_name to local.properties");
        }

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        if (!apiKey.isEmpty()) config.put("api_key", apiKey);
        if (!apiSecret.isEmpty()) config.put("api_secret", apiSecret);
        MediaManager.init(this, config);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_CHAT, "Čet", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_RANKING, "Rang lista", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_REWARDS, "Nagrade", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_GENERAL, "Ostalo", NotificationManager.IMPORTANCE_LOW));
    }
}
