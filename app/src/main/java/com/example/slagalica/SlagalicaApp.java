package com.example.slagalica;

import android.app.Application;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class SlagalicaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

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
}
