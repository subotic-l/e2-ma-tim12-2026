package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView emptyText;
    private MaterialButton btnAll, btnUnread;
    private FirebaseFirestore db;
    private String uid;
    private String currentFilter = "all";
    private List<DocumentSnapshot> allDocs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        container = findViewById(R.id.notificationContainer);
        emptyText = findViewById(R.id.emptyText);
        btnAll = findViewById(R.id.btnAll);
        btnUnread = findViewById(R.id.btnUnread);
        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { finish(); return; }

        btnAll.setOnClickListener(v -> { currentFilter = "all"; updateButtonStyles(); renderNotifications(); });
        btnUnread.setOnClickListener(v -> { currentFilter = "unread"; updateButtonStyles(); renderNotifications(); });

        findViewById(R.id.btnTestNotification).setOnClickListener(v -> sendTestNotification());

        updateButtonStyles();
        loadNotifications();
    }

    private void sendTestNotification() {
        NotificationHelper.show(this, SlagalicaApp.CHANNEL_CHAT, "Čet",
                "Imate novu poruku od Marka", uid);
        NotificationHelper.show(this, SlagalicaApp.CHANNEL_RANKING, "Rang lista",
                "Napredovali ste na 3. mesto!", uid);
        NotificationHelper.show(this, SlagalicaApp.CHANNEL_REWARDS, "Nagrade",
                "Osvojili ste 100 poena!", uid);
        NotificationHelper.show(this, SlagalicaApp.CHANNEL_GENERAL, "Ostalo",
                "Novi poziv za prijatelja", uid);
    }

    private void updateButtonStyles() {
        boolean allActive = "all".equals(currentFilter);
        btnAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(allActive ? 0xFF1976D2 : 0x00000000));
        btnUnread.setBackgroundTintList(android.content.res.ColorStateList.valueOf(!allActive ? 0xFF1976D2 : 0x00000000));
    }

    private void loadNotifications() {
        db.collection("users").document(uid).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    allDocs = snapshots.getDocuments();
                    renderNotifications();
                })
                .addOnFailureListener(e -> emptyText.setVisibility(View.VISIBLE));
    }

    private void renderNotifications() {
        container.removeAllViews();
        List<DocumentSnapshot> filtered = new ArrayList<>();

        for (DocumentSnapshot doc : allDocs) {
            boolean isRead = doc.getBoolean("read") != null && doc.getBoolean("read");
            if ("unread".equals(currentFilter) && isRead) continue;
            filtered.add(doc);
        }

        if (filtered.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        emptyText.setVisibility(View.GONE);

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        for (DocumentSnapshot doc : filtered) {
            String message = doc.getString("message");
            Boolean read = doc.getBoolean("read");
            String docId = doc.getId();
            Timestamp ts = doc.getTimestamp("createdAt");
            String timeStr = ts != null ? sdf.format(ts.toDate()) : "";

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(32, 24, 32, 24);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 16;
            item.setLayoutParams(lp);
            item.setBackgroundColor(read != null && read
                    ? 0xFF1565C0 : 0xFF1976D2);

            if (read == null || !read) {
                item.setOnClickListener(v -> markAsRead(docId, item));
            }

            String channel = doc.getString("channel");

            TextView channelView = new TextView(this);
            channelView.setText(channel != null ? channel : "general");
            channelView.setTextColor(0xFFB0BEC5);
            channelView.setTextSize(11);
            channelView.setTypeface(null, android.graphics.Typeface.ITALIC);

            TextView msgView = new TextView(this);
            msgView.setText(message);
            msgView.setTextColor(0xFFFFFFFF);
            msgView.setTextSize(16);
            msgView.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView statusView = new TextView(this);
            statusView.setText(read != null && read ? "Pročitano" : "Nepročitano");
            statusView.setTextColor(read != null && read ? 0xFFB0BEC5 : 0xFFFFEB3B);
            statusView.setTextSize(12);

            TextView timeView = new TextView(this);
            timeView.setText(timeStr);
            timeView.setTextColor(0xFFB0BEC5);
            timeView.setTextSize(13);

            item.addView(channelView);
            item.addView(msgView);
            item.addView(statusView);
            item.addView(timeView);
            container.addView(item);
        }
    }

    private void markAsRead(String docId, LinearLayout itemView) {
        Map<String, Object> update = new HashMap<>();
        update.put("read", true);
        db.collection("users").document(uid).collection("notifications").document(docId)
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    itemView.setBackgroundColor(0xFF1565C0);
                    itemView.setOnClickListener(null);
                    if ("unread".equals(currentFilter)) {
                        container.removeView(itemView);
                        if (container.getChildCount() == 0) {
                            emptyText.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }
}
