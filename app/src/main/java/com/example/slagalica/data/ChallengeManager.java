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
import java.util.Arrays;
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
    public static final int MIN_PARTICIPANTS = 4;

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
        int starsBet = "stars".equals(currencyType) ? betAmount : 0;
        int tokensBet = "tokens".equals(currencyType) ? betAmount : 0;
        return createChallenge(hostId, hostName, region, starsBet, tokensBet);
    }

    public Task<String> createChallenge(String hostId, String hostName, String region,
                                         int starsBet, int tokensBet) {
        DocumentReference hostRef = db.collection("users").document(hostId);

        return db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot hostSnap = transaction.get(hostRef);
            Long starBalance = hostSnap.getLong("stars");
            Long tokenBalance = hostSnap.getLong("tokens");
            if (starBalance == null || starBalance < starsBet) {
                throw new RuntimeException("Nedovoljno zvezda");
            }
            if (tokenBalance == null || tokenBalance < tokensBet) {
                throw new RuntimeException("Nedovoljno tokena");
            }

            transaction.update(hostRef, "stars", FieldValue.increment(-starsBet));
            if (tokensBet > 0) {
                transaction.update(hostRef, "tokens", FieldValue.increment(-tokensBet));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("region", region);
            data.put("hostId", hostId);
            data.put("hostName", hostName);
            data.put("starsBet", (long) starsBet);
            data.put("tokensBet", (long) tokensBet);
            data.put("status", "waiting");
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("winnerId", "");

            Map<String, Object> hostEntry = new HashMap<>();
            hostEntry.put("playerName", hostName);
            hostEntry.put("score", null);
            hostEntry.put("finished", false);
            Map<String, Object> participants = new HashMap<>();
            participants.put(hostId, hostEntry);
            data.put("participants", participants);

            DocumentReference newRef = db.collection(CHALLENGES_COLLECTION).document();
            transaction.set(newRef, data);

            this.challengeId = newRef.getId();
            this.challengeDocRef = newRef;
            return challengeId;
        });
    }

    public Task<String> joinChallenge(String challengeDocId, String playerId, String playerName) {
        DocumentReference ref = db.collection(CHALLENGES_COLLECTION).document(challengeDocId);
        DocumentReference playerRef = db.collection("users").document(playerId);

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

            Long starsBet = snap.getLong("starsBet");
            Long tokensBet = snap.getLong("tokensBet");
            if (starsBet == null) starsBet = 0L;
            if (tokensBet == null) tokensBet = 0L;

            DocumentSnapshot playerSnap = transaction.get(playerRef);
            Long starBalance = playerSnap.getLong("stars");
            Long tokenBalance = playerSnap.getLong("tokens");
            if (starBalance == null || starBalance < starsBet) {
                throw new RuntimeException("Nedovoljno zvezda");
            }
            if (tokenBalance == null || tokenBalance < tokensBet) {
                throw new RuntimeException("Nedovoljno tokena");
            }

            transaction.update(playerRef, "stars", FieldValue.increment(-starsBet));
            if (tokensBet > 0) {
                transaction.update(playerRef, "tokens", FieldValue.increment(-tokensBet));
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
        com.google.firebase.firestore.Query query = db.collection(CHALLENGES_COLLECTION);
        if (region != null && !region.isEmpty()) {
            query = query.whereEqualTo("region", region);
        }
        return query
                .whereIn("status", Arrays.asList("waiting", "playing"))
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

    public Task<List<DocumentSnapshot>> findFinishedChallenges(String excludePlayerId) {
        return db.collection(CHALLENGES_COLLECTION)
                .whereEqualTo("status", "finished")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return task.getResult().getDocuments();
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

            int finishedCount = 0;
            for (Map.Entry<String, Object> entry : participants.entrySet()) {
                Map<String, Object> p = (Map<String, Object>) entry.getValue();
                if (p != null && Boolean.TRUE.equals(p.get("finished"))) {
                    finishedCount++;
                }
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("participants." + playerId + ".score", score);
            updates.put("participants." + playerId + ".finished", true);
            transaction.update(challengeDocRef, updates);

            int total = participants.size();
            if (finishedCount + 1 >= total) {
                Map<String, Object> updatedParticipants = new HashMap<>(participants);
                Map<String, Object> myData = new HashMap<>((Map<String, Object>) participants.get(playerId));
                myData.put("score", score);
                myData.put("finished", true);
                updatedParticipants.put(playerId, myData);

                String[] result = determineWinnerAndRunnerUp(updatedParticipants);
                String winnerId = result[0];
                String runnerUpId = result[1];

                Long starsBet = snap.getLong("starsBet");
                Long tokensBet = snap.getLong("tokensBet");
                if (starsBet == null) starsBet = 0L;
                if (tokensBet == null) tokensBet = 0L;

                distributeRewards(transaction, winnerId, runnerUpId, starsBet, tokensBet, participants.size());

                transaction.update(challengeDocRef, "status", "finished", "winnerId", winnerId);
            }

            return null;
        });
    }

    private void distributeRewards(Transaction transaction, String winnerId, String runnerUpId,
                                    long starsBet, long tokensBet, int totalPlayers) {
        if (winnerId != null && !winnerId.isEmpty()) {
            DocumentReference winnerRef = db.collection("users").document(winnerId);

            if (starsBet > 0) {
                long totalStarsPot = starsBet * totalPlayers;
                long winnerStarsShare = (long) (totalStarsPot * 0.75);
                transaction.update(winnerRef, "stars", FieldValue.increment(winnerStarsShare));
            }
            if (tokensBet > 0) {
                long totalTokensPot = tokensBet * totalPlayers;
                long winnerTokensShare = (long) (totalTokensPot * 0.75);
                transaction.update(winnerRef, "tokens", FieldValue.increment(winnerTokensShare));
            }
        }

        if (runnerUpId != null && !runnerUpId.isEmpty() && !runnerUpId.equals(winnerId)) {
            DocumentReference runnerRef = db.collection("users").document(runnerUpId);
            if (starsBet > 0) {
                transaction.update(runnerRef, "stars", FieldValue.increment(starsBet));
            }
            if (tokensBet > 0) {
                transaction.update(runnerRef, "tokens", FieldValue.increment(tokensBet));
            }
        }
    }

    private String[] determineWinnerAndRunnerUp(Map<String, Object> participants) {
        String winnerId = "", runnerUpId = "";
        int bestScore = -1, secondBest = -1;
        if (participants == null) return new String[]{winnerId, runnerUpId};
        for (Map.Entry<String, Object> entry : participants.entrySet()) {
            Map<String, Object> p = (Map<String, Object>) entry.getValue();
            if (p == null) continue;
            Object scoreObj = p.get("score");
            if (scoreObj instanceof Long) {
                int s = ((Long) scoreObj).intValue();
                if (s > bestScore) {
                    secondBest = bestScore;
                    runnerUpId = winnerId;
                    bestScore = s;
                    winnerId = entry.getKey();
                } else if (s > secondBest) {
                    secondBest = s;
                    runnerUpId = entry.getKey();
                }
            }
        }
        return new String[]{winnerId, runnerUpId};
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
