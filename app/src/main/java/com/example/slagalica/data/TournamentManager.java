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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentManager {

    private static final String TAG = "TournamentManager";
    private static final String TOURNAMENTS_COLLECTION = "tournaments";
    private static final String MATCHES_COLLECTION = "matches";

    private final FirebaseFirestore db;
    private String tournamentId;
    private DocumentReference tournamentDocRef;
    private ListenerRegistration listener;

    public TournamentManager() {
        this.db = FirebaseFirestore.getInstance();
    }

    public String getTournamentId() {
        return tournamentId;
    }

    // ── Kreiranje turnira ──

    public Task<String> createTournament(String playerId, String playerName, String playerAvatar) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "waiting");
        data.put("creatorId", playerId);
        data.put("createdAt", FieldValue.serverTimestamp());

        List<Map<String, Object>> participants = new ArrayList<>();
        Map<String, Object> me = new HashMap<>();
        me.put("playerId", playerId);
        me.put("playerName", playerName);
        me.put("playerAvatar", playerAvatar != null ? playerAvatar : "");
            me.put("joinedAt", com.google.firebase.Timestamp.now());
        participants.add(me);
        data.put("participants", participants);

        data.put("bracket", new HashMap<String, Object>() {{
            put("semi1", new HashMap<String, Object>() {{
                put("matchId", "");
                put("status", "waiting");
                put("player1Id", "");
                put("player2Id", "");
                put("winnerId", "");
            }});
            put("semi2", new HashMap<String, Object>() {{
                put("matchId", "");
                put("status", "waiting");
                put("player1Id", "");
                put("player2Id", "");
                put("winnerId", "");
            }});
            put("final", new HashMap<String, Object>() {{
                put("matchId", "");
                put("status", "waiting");
                put("player1Id", "");
                put("player2Id", "");
                put("winnerId", "");
            }});
        }});

        return db.collection(TOURNAMENTS_COLLECTION).add(data)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        this.tournamentId = task.getResult().getId();
                        this.tournamentDocRef = db.collection(TOURNAMENTS_COLLECTION).document(tournamentId);
                        return tournamentId;
                    } else {
                        throw task.getException();
                    }
                });
    }

    // ── Pridruživanje turniru ──

    public Task<String> joinTournament(String tournamentDocId, String playerId, String playerName, String playerAvatar) {
        DocumentReference ref = db.collection(TOURNAMENTS_COLLECTION).document(tournamentDocId);

        return db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            String status = snap.getString("status");
            if (!"waiting".equals(status)) return null;

            List<Map<String, Object>> participants =
                    (List<Map<String, Object>>) snap.get("participants");
            if (participants != null && participants.size() >= 4) return null;

            Map<String, Object> newPlayer = new HashMap<>();
            newPlayer.put("playerId", playerId);
            newPlayer.put("playerName", playerName);
            newPlayer.put("playerAvatar", playerAvatar != null ? playerAvatar : "");
            newPlayer.put("joinedAt", com.google.firebase.Timestamp.now());

            List<Map<String, Object>> updated = new ArrayList<>();
            if (participants != null) updated.addAll(participants);
            updated.add(newPlayer);
            transaction.update(ref, "participants", updated);
            return tournamentDocId;
        }).continueWith(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                this.tournamentId = task.getResult();
                this.tournamentDocRef = ref;
                return tournamentId;
            } else if (task.isSuccessful() && task.getResult() == null) {
                throw new RuntimeException("Tournament full or already started");
            } else {
                throw new RuntimeException(task.getException());
            }
        });
    }

    // ── Pronalaženje turnira na čekanju ──

    public Task<List<DocumentSnapshot>> findWaitingTournaments(String excludePlayerId) {
        return db.collection(TOURNAMENTS_COLLECTION)
                .whereEqualTo("status", "waiting")
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        List<DocumentSnapshot> result = new ArrayList<>();
                        long now = System.currentTimeMillis();
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            String creatorId = doc.getString("creatorId");
                            if (creatorId != null && creatorId.equals(excludePlayerId)) continue;

                            List<Map<String, Object>> participants =
                                    (List<Map<String, Object>>) doc.get("participants");
                            int count = participants != null ? participants.size() : 0;
                            if (count >= 4) continue;

                            Timestamp ts = doc.getTimestamp("createdAt");
                            if (ts == null) continue;
                            if (now - ts.toDate().getTime() > 30_000) continue;

                            result.add(doc);
                        }
                        return result;
                    }
                    throw task.getException();
                });
    }

    // ── Pokretanje turnira (kad se skupi 4) ──

    public Task<Void> startTournament(List<Map<String, Object>> participants) {
        if (participants == null || participants.size() != 4) {
            return Tasks.forException(new RuntimeException("Need exactly 4 players"));
        }

        // Shuffle for random pairing
        List<Map<String, Object>> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);

        Map<String, Object> pA = shuffled.get(0);
        Map<String, Object> pB = shuffled.get(1);
        Map<String, Object> pC = shuffled.get(2);
        Map<String, Object> pD = shuffled.get(3);

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(tournamentDocRef);
            String status = snap.getString("status");
            if (!"waiting".equals(status)) {
                throw new RuntimeException("Tournament already started");
            }

            // Kreiraj 2 match dokumenta za semifinale
            String match1Id = db.collection(MATCHES_COLLECTION).document().getId();
            String match2Id = db.collection(MATCHES_COLLECTION).document().getId();

            Map<String, Object> match1 = buildMatchDoc(pA, pB);
            Map<String, Object> match2 = buildMatchDoc(pC, pD);

            transaction.set(db.collection(MATCHES_COLLECTION).document(match1Id), match1);
            transaction.set(db.collection(MATCHES_COLLECTION).document(match2Id), match2);

            // Ažuriraj tournament bracket
            Map<String, Object> bracketUpdate = new HashMap<>();
            bracketUpdate.put("bracket.semi1.matchId", match1Id);
            bracketUpdate.put("bracket.semi1.status", "playing");
            bracketUpdate.put("bracket.semi1.player1Id", pA.get("playerId"));
            bracketUpdate.put("bracket.semi1.player2Id", pB.get("playerId"));
            bracketUpdate.put("bracket.semi2.matchId", match2Id);
            bracketUpdate.put("bracket.semi2.status", "playing");
            bracketUpdate.put("bracket.semi2.player1Id", pC.get("playerId"));
            bracketUpdate.put("bracket.semi2.player2Id", pD.get("playerId"));
            bracketUpdate.put("status", "semifinals");

            transaction.update(tournamentDocRef, bracketUpdate);
            return null;
        });
    }

    private Map<String, Object> buildMatchDoc(Map<String, Object> p1, Map<String, Object> p2) {
        Map<String, Object> match = new HashMap<>();
        match.put("player1Id", p1.get("playerId"));
        match.put("player1Name", p1.get("playerName"));
        match.put("player1Avatar", p1.get("playerAvatar"));
        match.put("player2Id", p2.get("playerId"));
        match.put("player2Name", p2.get("playerName"));
        match.put("player2Avatar", p2.get("playerAvatar"));
        match.put("status", "playing");
        match.put("createdAt", FieldValue.serverTimestamp());
        match.put("currentGameIndex", 0);
        match.put("totalGames", 6);
        match.put("player1Score", 0);
        match.put("player2Score", 0);
        match.put("gameState", new HashMap<>());
        match.put("isTournamentMatch", true);
        match.put("tournamentId", tournamentId);
        return match;
    }

    // ── Osluškivanje turnira ──

    public interface TournamentListener {
        void onStateChanged(Map<String, Object> tournamentData);
        void onTournamentEnded(Map<String, Object> tournamentData);
        void onError(String error);
    }

    public void listenToTournament(TournamentListener listener) {
        if (tournamentDocRef == null) return;
        removeListener();
        this.listener = tournamentDocRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen error", error);
                listener.onError(error.getMessage());
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                String status = (String) data.get("status");
                if ("completed".equals(status)) {
                    listener.onTournamentEnded(data);
                } else {
                    listener.onStateChanged(data);
                }
            }
        });
    }

    public void attachToTournament(String tournamentId) {
        this.tournamentId = tournamentId;
        this.tournamentDocRef = db.collection(TOURNAMENTS_COLLECTION).document(tournamentId);
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

    // ── Helper za unos pobednika u bracket ──

    public void setSemiWinner(String round, String winnerId, String winnerName, String winnerAvatar) {
        if (tournamentDocRef == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("bracket." + round + ".winnerId", winnerId);
        updates.put("bracket." + round + ".winnerName", winnerName);
        updates.put("bracket." + round + ".winnerAvatar", winnerAvatar != null ? winnerAvatar : "");
        updates.put("bracket." + round + ".status", "done");
        tournamentDocRef.update(updates);
    }

    public Task<Void> createFinalMatch(String winner1Id, String winner1Name, String winner1Avatar,
                                        String winner2Id, String winner2Name, String winner2Avatar) {
        if (tournamentDocRef == null) {
            return Tasks.forException(new RuntimeException("No tournament attached"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(tournamentDocRef);
            String status = snap.getString("status");
            if (!"semifinals".equals(status)) {
                throw new RuntimeException("Not in semifinals phase");
            }

            String finalMatchId = db.collection(MATCHES_COLLECTION).document().getId();

            Map<String, Object> p1Map = new HashMap<>();
            p1Map.put("playerId", winner1Id);
            p1Map.put("playerName", winner1Name);
            p1Map.put("playerAvatar", winner1Avatar != null ? winner1Avatar : "");

            Map<String, Object> p2Map = new HashMap<>();
            p2Map.put("playerId", winner2Id);
            p2Map.put("playerName", winner2Name);
            p2Map.put("playerAvatar", winner2Avatar != null ? winner2Avatar : "");

            Map<String, Object> finalMatch = buildMatchDoc(p1Map, p2Map);
            transaction.set(db.collection(MATCHES_COLLECTION).document(finalMatchId), finalMatch);

            Map<String, Object> bracketUpdate = new HashMap<>();
            bracketUpdate.put("bracket.final.matchId", finalMatchId);
            bracketUpdate.put("bracket.final.status", "playing");
            bracketUpdate.put("bracket.final.player1Id", winner1Id);
            bracketUpdate.put("bracket.final.player2Id", winner2Id);
            bracketUpdate.put("status", "final");

            transaction.update(tournamentDocRef, bracketUpdate);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    public Task<Void> tryAdvanceToFinal() {
        if (tournamentDocRef == null) {
            return Tasks.forException(new RuntimeException("No tournament attached"));
        }

        return tournamentDocRef.get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            Map<String, Object> data = task.getResult().getData();
            if (data == null) throw new RuntimeException("Tournament not found");

            Map<String, Object> bracket = (Map<String, Object>) data.get("bracket");
            if (bracket == null) throw new RuntimeException("No bracket");

            Map<String, Object> semi1 = (Map<String, Object>) bracket.get("semi1");
            Map<String, Object> semi2 = (Map<String, Object>) bracket.get("semi2");

            String w1Id = semi1 != null ? (String) semi1.get("winnerId") : null;
            String w2Id = semi2 != null ? (String) semi2.get("winnerId") : null;

            if (w1Id == null || w1Id.isEmpty() || w2Id == null || w2Id.isEmpty()) {
                return Tasks.forException(new RuntimeException("Both semis not yet done"));
            }

            String w1Name = semi1.containsKey("winnerName") ? (String) semi1.get("winnerName") : w1Id;
            String w1Avatar = semi1.containsKey("winnerAvatar") ? (String) semi1.get("winnerAvatar") : "";
            String w2Name = semi2.containsKey("winnerName") ? (String) semi2.get("winnerName") : w2Id;
            String w2Avatar = semi2.containsKey("winnerAvatar") ? (String) semi2.get("winnerAvatar") : "";

            return createFinalMatch(w1Id, w1Name, w1Avatar, w2Id, w2Name, w2Avatar);
        });
    }

    public void setFinalWinner(String winnerId) {
        if (tournamentDocRef == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("bracket.final.winnerId", winnerId);
        updates.put("bracket.final.status", "done");
        updates.put("status", "completed");
        tournamentDocRef.update(updates);
    }

    public void setFinalWinnerWithLoser(String winnerId, int winnerScore, String loserId, int loserScore) {
        if (tournamentDocRef == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("bracket.final.winnerId", winnerId);
        updates.put("bracket.final.status", "done");
        updates.put("bracket.final.winnerScore", winnerScore);
        updates.put("bracket.final.loserId", loserId);
        updates.put("bracket.final.loserScore", loserScore);
        updates.put("status", "completed");
        tournamentDocRef.update(updates);
    }
}
