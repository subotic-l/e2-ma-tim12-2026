package com.example.slagalica;

import android.os.Handler;
import android.os.Looper;

public class InactivityWatcher {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutTask;
    private final long timeoutMs;
    private boolean cancelled = false;

    public InactivityWatcher(long timeoutMs, Runnable onTimeout) {
        this.timeoutMs = timeoutMs;
        timeoutTask = () -> {
            if (!cancelled) {
                onTimeout.run();
            }
        };
    }

    public void start() {
        cancelled = false;
        handler.removeCallbacks(timeoutTask);
        handler.postDelayed(timeoutTask, timeoutMs);
    }

    public void reset() {
        if (cancelled) return;
        handler.removeCallbacks(timeoutTask);
        handler.postDelayed(timeoutTask, timeoutMs);
    }

    public void cancel() {
        cancelled = true;
        handler.removeCallbacks(timeoutTask);
    }
}
