package com.example.slagalica;

import com.example.slagalica.data.RegionRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class ChatRepository {

    private final FirebaseFirestore db;

    public ChatRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void sendMessage(String regionCode, String senderId, String senderName, String text) {
        Map<String, Object> message = new HashMap<>();
        message.put("senderId", senderId);
        message.put("senderName", senderName);
        message.put("text", text);
        message.put("timestamp", FieldValue.serverTimestamp());

        db.collection("region_chats")
                .document(regionCode)
                .collection("messages")
                .add(message)
                .addOnSuccessListener(aVoid -> notifyOfflineUsers(regionCode, senderId, senderName, text));
    }

    public Query getMessagesQuery(String regionCode) {
        return db.collection("region_chats")
                .document(regionCode)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING);
    }

    private void notifyOfflineUsers(String regionCode, String senderId, String senderName, String messageText) {
        String regionName = RegionRepository.getCodeToName(regionCode);
        if (regionName == null) return;

        db.collection("users")
                .whereEqualTo("region", regionName)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String targetUid = doc.getId();
                        if (targetUid.equals(senderId)) continue;
                        Timestamp lastSeen = doc.getTimestamp("lastSeen");
                        if (lastSeen == null || lastSeen.toDate().getTime() < fiveMinutesAgo) {
                            Map<String, Object> notif = new HashMap<>();
                            notif.put("message", senderName + ": " + messageText);
                            notif.put("createdAt", FieldValue.serverTimestamp());
                            notif.put("read", false);
                            notif.put("channel", SlagalicaApp.CHANNEL_CHAT);
                            notif.put("regionCode", regionCode);
                            db.collection("users")
                                    .document(targetUid)
                                    .collection("notifications")
                                    .add(notif);
                        }
                    }
                });
    }
}
