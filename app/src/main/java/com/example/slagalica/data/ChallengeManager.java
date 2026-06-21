package com.example.slagalica.data;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeManager {

    private static final String TAG = "ChallengeManager";
    private static final String CHALLENGES_COLLECTION = "challenges";

    private final FirebaseFirestore db;
    private String challengeId;
    private DocumentReference challengeDocRef;
    private ListenerRegistration listener;

    public static final int MAX_PARTICIPANTS = 4;
    public static final int MIN_PARTICIPANTS = 2;

    public ChallengeManager() {
        this.db = FirebaseFirestore.getInstance();
    }

    public String getChallengeId() {
        return challengeId;
    }

    public DocumentReference getChallengeDocRef() {
        return challengeDocRef;
    }

    public Task<String> createChallenge(String hostId, String hostName, String region,
                                         int betAmount, String currencyType) {
        Map<String, Object> data = new HashMap<>();
        data.put("region", region);
        data.put("hostId", hostId);
        data.put("hostName", hostName);
        data.put("betAmount", betAmount);
        data.put("currencyType", currencyType);
        data.put("status", "waiting");
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("winnerId", "");

        Map<String, Object> hostEntry = new HashMap<>();
        hostEntry.put("playerName", hostName);
        hostEntry.put("score", null);
        hostEntry.put("finished", false);
        data.put("participants", new HashMap<String, Object>() {{
            put(hostId, hostEntry);
        }});

        return db.collection(CHALLENGES_COLLECTION).add(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        this.challengeId = task.getResult().getId();
                        this.challengeDocRef = db.collection(CHALLENGES_COLLECTION).document(challengeId);
                        return challengeId;
                    } else {
                        throw task.getException();
                    }
                });
    }

    public Task<String> joinChallenge(String challengeDocId, String playerId, String playerName) {
        DocumentReference ref = db.collection(CHALLENGES_COLLECTION).document(challengeDocId);

        return db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            String status = snap.getString("status");
            if (!"waiting".equals(status)) return null;

            Map<String, Object> participants = (Map<String, Object>) snap.get("participants");
            int count = participants != null ? participants.size() : 0;
            if (count >= MAX_PARTICIPANTS) return null;

            if (participants != null && participants.containsKey(playerId)) {
                return challengeDocId;
            }

            Map<String, Object> newPlayer = new HashMap<>();
            newPlayer.put("playerName", playerName);
            newPlayer.put("score", null);
            newPlayer.put("finished", false);

            transaction.update(ref, "participants." + playerId, newPlayer);
            return challengeDocId;
        }).continueWith(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                this.challengeId = task.getResult();
                this.challengeDocRef = ref;
                return challengeId;
            } else if (task.isSuccessful() && task.getResult() == null) {
                throw new RuntimeException("Challenge full or already started");
            } else {
                throw new RuntimeException(task.getException());
            }
        });
    }

    public Task<List<DocumentSnapshot>> findActiveChallenges(String region, String excludePlayerId) {
        return db.collection(CHALLENGES_COLLECTION)
                .whereEqualTo("region", region)
                .whereIn("status", new String[]{"waiting", "playing"})
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        List<DocumentSnapshot> result = new ArrayList<>();
                        long now = System.currentTimeMillis();
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Timestamp ts = doc.getTimestamp("createdAt");
                            if (ts == null) continue;
                            if (now - ts.toDate().getTime() > 3600_000) continue;
                            result.add(doc);
                        }
                        return result;
                    }
                    throw task.getException();
                });
    }

    public Task<Void> startChallenge() {
        if (challengeDocRef == null) {
            return Tasks.forException(new RuntimeException("No challenge attached"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(challengeDocRef);
            String status = snap.getString("status");
            if (!"waiting".equals(status)) {
                throw new RuntimeException("Challenge already started");
            }

            Map<String, Object> participants = (Map<String, Object>) snap.get("participants");
            int count = participants != null ? participants.size() : 0;
            if (count < MIN_PARTICIPANTS) {
                throw new RuntimeException("Not enough participants");
            }

            transaction.update(challengeDocRef, "status", "playing");
            return null;
        });
    }

    public Task<Void> submitScore(String playerId, int score) {
        if (challengeDocRef == null) {
            return Tasks.forException(new RuntimeException("No challenge attached"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(challengeDocRef);
            Map<String, Object> participants = (Map<String, Object>) snap.get("participants");

            if (participants == null || !participants.containsKey(playerId)) {
                throw new RuntimeException("Player not in challenge");
            }

            Map<String, Object> playerData = (Map<String, Object>) participants.get(playerId);
            if (playerData != null && Boolean.TRUE.equals(playerData.get("finished"))) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("participants." + playerId + ".score", score);
            updates.put("participants." + playerId + ".finished", true);
            transaction.update(challengeDocRef, updates);

            Map<String, Object> updatedParticipants = (Map<String, Object>) transaction.get(challengeDocRef).get("participants");
            int finishedCount = 0;
            if (updatedParticipants != null) {
                for (Object entry : updatedParticipants.values()) {
                    Map<String, Object> p = (Map<String, Object>) entry;
                    if (Boolean.TRUE.equals(p.get("finished"))) {
                        finishedCount++;
                    }
                }
            }

            int total = participants.size();
            if (finishedCount >= total) {
                String winnerId = determineWinner(updatedParticipants);
                transaction.update(challengeDocRef, "status", "finished", "winnerId", winnerId);
            }

            return null;
        });
    }

    private String determineWinner(Map<String, Object> participants) {
        String winnerId = "";
        int bestScore = -1;
        if (participants == null) return winnerId;
        for (Map.Entry<String, Object> entry : participants.entrySet()) {
            Map<String, Object> p = (Map<String, Object>) entry.getValue();
            if (p == null) continue;
            Object scoreObj = p.get("score");
            if (scoreObj instanceof Long) {
                int s = ((Long) scoreObj).intValue();
                if (s > bestScore) {
                    bestScore = s;
                    winnerId = entry.getKey();
                }
            }
        }
        return winnerId;
    }

    public void attachToChallenge(String challengeId) {
        this.challengeId = challengeId;
        this.challengeDocRef = db.collection(CHALLENGES_COLLECTION).document(challengeId);
    }

    public interface ChallengeListener {
        void onStateChanged(Map<String, Object> challengeData);
        void onChallengeEnded(Map<String, Object> challengeData);
        void onError(String error);
    }

    public void listenToChallenge(ChallengeListener listener) {
        if (challengeDocRef == null) return;
        removeListener();
        this.listener = challengeDocRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen error", error);
                listener.onError(error.getMessage());
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                String status = (String) data.get("status");
                if ("finished".equals(status)) {
                    listener.onChallengeEnded(data);
                } else {
                    listener.onStateChanged(data);
                }
            }
        });
    }

    public void removeListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    public void cleanup() {
        removeListener();
    }
}
