package com.example.slagalica;

import android.app.Application;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class SlagalicaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", "dd3x7sscj");
        config.put("api_key", "768412737335391");
        config.put("api_secret", "4Ec3fceAufFASoFgHYkDqRSXrjg");
        MediaManager.init(this, config);
    }
}
