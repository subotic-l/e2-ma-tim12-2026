package com.example.slagalica.data;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GameSessionManager {

    private static final String TAG = "GameSessionManager";
    private static final String MATCHES_COLLECTION = "matches";

    public static final String GAME_TYPE_WHO_KNOWS = "who_knows_knows";
    public static final String GAME_TYPE_SPOJNICE = "spojnice";
    public static final String GAME_TYPE_MOJ_BROJ = "moj_broj";
    public static final String GAME_TYPE_KORAK_PO_KORAK = "korak_po_korak";
    public static final String GAME_TYPE_ASOCIJACIJE = "asocijacije";
    public static final String GAME_TYPE_SKOCKO = "skocko";

    private final FirebaseFirestore db;
    private String matchId;
    private int myPlayerNumber;
    private DocumentReference matchDocRef;
    private ListenerRegistration listener;
    private StateListener stateListener;

    public GameSessionManager() {
        this.db = FirebaseFirestore.getInstance();
    }

    public interface StateListener {
        void onStateChanged(Map<String, Object> fullState);
        void onMatchEnded(Map<String, Object> finalState);
        void onError(String error);
    }

    public String getMatchId() {
        return matchId;
    }

    public int getMyPlayerNumber() {
        return myPlayerNumber;
    }

    public DocumentReference getMatchDocRef() {
        return matchDocRef;
    }

    public Task<String> createMatch(String playerId, String playerName) {
        return createMatch(playerId, playerName, "");
    }

    public Task<String> createMatch(String playerId, String playerName, String avatarUrl) {
        Map<String, Object> matchData = new HashMap<>();
        matchData.put("player1Id", playerId);
        matchData.put("player1Name", playerName);
        matchData.put("player1Avatar", avatarUrl != null ? avatarUrl : "");
        matchData.put("player2Id", "");
        matchData.put("player2Name", "");
        matchData.put("player2Avatar", "");
        matchData.put("status", "waiting");
        matchData.put("createdAt", FieldValue.serverTimestamp());
        matchData.put("currentGameIndex", 0);
        matchData.put("totalGames", 6);
        matchData.put("player1Score", 0);
        matchData.put("player2Score", 0);
        matchData.put("gameState", new HashMap<>());

        return db.collection(MATCHES_COLLECTION).add(matchData)
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        this.matchId = task.getResult().getId();
                        this.matchDocRef = db.collection(MATCHES_COLLECTION).document(matchId);
                        this.myPlayerNumber = 1;
                        return matchId;
                    } else {
                        throw task.getException();
                    }
                });
    }

    public void attachToMatch(String matchId, int playerNumber) {
        this.matchId = matchId;
        this.myPlayerNumber = playerNumber;
        this.matchDocRef = db.collection(MATCHES_COLLECTION).document(matchId);
    }

    public Task<String> tryClaimMatch(String matchDocId, String myPlayerId, String myPlayerName) {
        return tryClaimMatch(matchDocId, myPlayerId, myPlayerName, "");
    }

    public Task<String> tryClaimMatch(String matchDocId, String myPlayerId, String myPlayerName, String avatarUrl) {
        DocumentReference ref = db.collection(MATCHES_COLLECTION).document(matchDocId);

        return db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            String status = snapshot.getString("status");
            if (!"waiting".equals(status)) {
                return null;
            }
            transaction.update(ref,
                    "player2Id", myPlayerId,
                    "player2Name", myPlayerName,
                    "player2Avatar", avatarUrl != null ? avatarUrl : "",
                    "status", "playing"
            );
            return matchDocId;
        }).continueWith(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                this.matchId = task.getResult();
                this.matchDocRef = db.collection(MATCHES_COLLECTION).document(matchId);
                this.myPlayerNumber = 2;
                return matchId;
            } else if (task.isSuccessful() && task.getResult() == null) {
                throw new RuntimeException("Match already taken");
            } else {
                throw new RuntimeException(task.getException());
            }
        });
    }

    public Task<java.util.List<DocumentSnapshot>> findWaitingMatches(String excludePlayerId) {
        return db.collection(MATCHES_COLLECTION)
                .whereEqualTo("status", "waiting")
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return task.getResult().getDocuments();
                    }
                    throw task.getException();
                });
    }

    public void listenToMatch(StateListener listener) {
        this.stateListener = listener;
        if (matchDocRef == null) return;

        if (listener != null) {
            removeListener();
            this.listener = matchDocRef.addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    Log.e(TAG, "Listen error", error);
                    if (stateListener != null) {
                        stateListener.onError(error.getMessage());
                    }
                    return;
                }
                if (snapshot != null && snapshot.exists()) {
                    Map<String, Object> data = snapshot.getData();
                    if (stateListener != null) {
                        String status = (String) data.get("status");
                        if ("finished".equals(status) || "forfeit".equals(status)) {
                            stateListener.onMatchEnded(data);
                        } else {
                            stateListener.onStateChanged(data);
                        }
                    }
                }
            });
        }
    }

    public void updateGameState(Map<String, Object> gameStateUpdate) {
        if (matchDocRef == null) return;
        Map<String, Object> flat = new HashMap<>();
        for (Map.Entry<String, Object> e : gameStateUpdate.entrySet()) {
            flat.put("gameState." + e.getKey(), e.getValue());
        }
        matchDocRef.update(flat)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update game state", e));
    }

    public void setGameState(Map<String, Object> gameState) {
        if (matchDocRef == null) return;
        matchDocRef.update("gameState", gameState)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to set game state", e));
    }

    public void updateField(String fieldPath, Object value) {
        if (matchDocRef == null) return;
        matchDocRef.update(fieldPath, value)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update " + fieldPath, e));
    }

    public void finishCurrentGame(int gameIndex, int player1GameScore, int player2GameScore,
                                   int totalPlayer1Score, int totalPlayer2Score) {
        finishCurrentGame(gameIndex, player1GameScore, player2GameScore,
                totalPlayer1Score, totalPlayer2Score, 1, null);
    }

    public void finishCurrentGame(int gameIndex, int player1GameScore, int player2GameScore,
                                   int totalPlayer1Score, int totalPlayer2Score, int totalGames) {
        finishCurrentGame(gameIndex, player1GameScore, player2GameScore,
                totalPlayer1Score, totalPlayer2Score, totalGames, null);
    }

    public void finishCurrentGame(int gameIndex, int player1GameScore, int player2GameScore,
                                   int totalPlayer1Score, int totalPlayer2Score, int totalGames,
                                   Map<String, Object> gameStats) {
        if (matchDocRef == null) return;

        Map<String, Object> resultEntry = new HashMap<>();
        resultEntry.put("gameIndex", gameIndex);
        resultEntry.put("player1Score", player1GameScore);
        resultEntry.put("player2Score", player2GameScore);

        int nextGameIndex = gameIndex + 1;
        boolean isLastGame = (nextGameIndex >= totalGames);

        Map<String, Object> updates = new HashMap<>();
        updates.put("gameState", new HashMap<>());

        if (isLastGame) {
            updates.put("status", "finished");
        } else {
            updates.put("status", "playing");
            updates.put("currentGameIndex", nextGameIndex);
        }
        updates.put("player1Score", totalPlayer1Score);
        updates.put("player2Score", totalPlayer2Score);
        updates.put("lastGameResult", resultEntry);

        if (gameStats != null) {
            updates.put("gamesStats." + gameIndex, gameStats);
        }

        matchDocRef.update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to finish game", e));
    }

    public static Task<Void> saveMatchHistoryToUser(String userId, Map<String, Object> matchData) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return db.collection("users").document(userId)
                .collection("match_history")
                .add(matchData)
                .continueWith(task -> null);
    }

    public void forfeitMatch() {
        if (matchDocRef == null) return;
        matchDocRef.update("status", "forfeit", "forfeitBy", myPlayerNumber);
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
