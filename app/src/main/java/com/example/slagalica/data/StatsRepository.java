package com.example.slagalica.data;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatsRepository {

    private final FirebaseFirestore db;
    private final String uid;

    public StatsRepository() {
        this.db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        this.uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public Task<PlayerStats> loadStats() {
        if (uid == null) {
            return Tasks.forException(new IllegalStateException("No authenticated user"));
        }
        return db.collection("users").document(uid)
                .collection("match_history")
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    QuerySnapshot snap = task.getResult();
                    return computeStats(snap.getDocuments());
                });
    }

    private PlayerStats computeStats(List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        PlayerStats stats = new PlayerStats();

        for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
            Map<String, Object> data = doc.getData();
            if (data == null) continue;

            String p1Id = (String) data.get("player1Id");
            String p2Id = (String) data.get("player2Id");
            boolean iAmPlayer1 = uid.equals(p1Id);

            boolean won = Boolean.TRUE.equals(data.get("won"));
            boolean draw = Boolean.TRUE.equals(data.get("draw"));
            int myScore = data.get("myScore") instanceof Long ? ((Long) data.get("myScore")).intValue() : 0;

            stats.totalMatches++;

            if (won) stats.wins++;
            else if (!draw) stats.losses++;

            Map<String, Object> games = (Map<String, Object>) data.get("games");
            if (games == null) continue;

            for (int i = 0; i < 6; i++) {
                Object gameObj = games.get(String.valueOf(i));
                if (!(gameObj instanceof Map)) continue;
                Map<String, Object> game = (Map<String, Object>) gameObj;

                String gameType = (String) game.get("gameType");
                if (gameType == null) continue;

                long myGameScore = iAmPlayer1
                        ? (game.get("player1Score") instanceof Long ? (Long) game.get("player1Score") : 0)
                        : (game.get("player2Score") instanceof Long ? (Long) game.get("player2Score") : 0);

                switch (gameType) {
                    case GameSessionManager.GAME_TYPE_WHO_KNOWS: {
                        long correct = iAmPlayer1
                                ? (game.get("p1Correct") instanceof Long ? (Long) game.get("p1Correct") : 0)
                                : (game.get("p2Correct") instanceof Long ? (Long) game.get("p2Correct") : 0);
                        long wrong = iAmPlayer1
                                ? (game.get("p1Wrong") instanceof Long ? (Long) game.get("p1Wrong") : 0)
                                : (game.get("p2Wrong") instanceof Long ? (Long) game.get("p2Wrong") : 0);
                        long total = iAmPlayer1
                                ? (game.get("p1Total") instanceof Long ? (Long) game.get("p1Total") : 0)
                                : (game.get("p2Total") instanceof Long ? (Long) game.get("p2Total") : 0);
                        stats.whoKnowsCorrect += (int) correct;
                        stats.whoKnowsWrong += (int) wrong;
                        stats.whoKnowsTotal += (int) total;
                        stats.whoKnowsScoreSum += (int) myGameScore;
                        stats.whoKnowsMaxScore = Math.max(stats.whoKnowsMaxScore, (int) myGameScore);
                        stats.whoKnowsGames++;
                        break;
                    }
                    case GameSessionManager.GAME_TYPE_MOJ_BROJ: {
                        long found = iAmPlayer1
                                ? (game.get("p1FoundExact") instanceof Long ? (Long) game.get("p1FoundExact") : 0)
                                : (game.get("p2FoundExact") instanceof Long ? (Long) game.get("p2FoundExact") : 0);
                        stats.mojBrojFound += (int) found;
                        stats.mojBrojTotal += game.get("totalRounds") instanceof Long ? ((Long) game.get("totalRounds")).intValue() : 2;
                        stats.mojBrojScoreSum += (int) myGameScore;
                        stats.mojBrojMaxScore = Math.max(stats.mojBrojMaxScore, (int) myGameScore);
                        stats.mojBrojGames++;
                        break;
                    }
                    case GameSessionManager.GAME_TYPE_KORAK_PO_KORAK: {
                        long step = iAmPlayer1
                                ? (game.get("p1StepFound") instanceof Long ? (Long) game.get("p1StepFound") : -1)
                                : (game.get("p2StepFound") instanceof Long ? (Long) game.get("p2StepFound") : -1);
                        if (step >= 0) stats.korakFound++;
                        stats.korakTotal++;
                        stats.korakScoreSum += (int) myGameScore;
                        stats.korakMaxScore = Math.max(stats.korakMaxScore, (int) myGameScore);
                        stats.korakGames++;
                        int stepIdx = (int) step;
                        if (stepIdx >= 0 && stepIdx < 7) stats.korakStepCounts[stepIdx]++;
                        break;
                    }
                    case GameSessionManager.GAME_TYPE_ASOCIJACIJE: {
                        long finals = iAmPlayer1
                                ? (game.get("p1FinalSolved") instanceof Long ? (Long) game.get("p1FinalSolved") : 0)
                                : (game.get("p2FinalSolved") instanceof Long ? (Long) game.get("p2FinalSolved") : 0);
                        stats.asocijacijeSolved += (int) finals;
                        stats.asocijacijeTotal += 2;
                        stats.asocijacijeScoreSum += (int) myGameScore;
                        stats.asocijacijeMaxScore = Math.max(stats.asocijacijeMaxScore, (int) myGameScore);
                        stats.asocijacijeGames++;
                        break;
                    }
                    case GameSessionManager.GAME_TYPE_SKOCKO: {
                        long attempt = iAmPlayer1
                                ? (game.get("p1Attempt") instanceof Long ? (Long) game.get("p1Attempt") : -1)
                                : (game.get("p2Attempt") instanceof Long ? (Long) game.get("p2Attempt") : -1);
                        if (attempt > 0) stats.skockoFound++;
                        stats.skockoTotal++;
                        stats.skockoScoreSum += (int) myGameScore;
                        stats.skockoMaxScore = Math.max(stats.skockoMaxScore, (int) myGameScore);
                        stats.skockoGames++;
                        int attIdx = (int) attempt;
                        if (attIdx >= 0 && attIdx < 7) stats.skockoAttemptCounts[attIdx]++;
                        break;
                    }
                    case GameSessionManager.GAME_TYPE_SPOJNICE: {
                        long connected = iAmPlayer1
                                ? (game.get("p1Connected") instanceof Long ? (Long) game.get("p1Connected") : 0)
                                : (game.get("p2Connected") instanceof Long ? (Long) game.get("p2Connected") : 0);
                        long totalItems = game.get("totalItems") instanceof Long ? (Long) game.get("totalItems") : 5;
                        stats.spojniceConnected += (int) connected;
                        stats.spojniceTotal += (int) totalItems;
                        stats.spojniceScoreSum += (int) myGameScore;
                        stats.spojniceMaxScore = Math.max(stats.spojniceMaxScore, (int) myGameScore);
                        stats.spojniceGames++;
                        break;
                    }
                }
            }
        }

        return stats;
    }

    public static class PlayerStats {
        public int totalMatches = 0;
        public int wins = 0;
        public int losses = 0;

        public int whoKnowsCorrect = 0;
        public int whoKnowsWrong = 0;
        public int whoKnowsTotal = 0;
        public int whoKnowsScoreSum = 0;
        public int whoKnowsMaxScore = 0;
        public int whoKnowsGames = 0;

        public int mojBrojFound = 0;
        public int mojBrojTotal = 0;
        public int mojBrojScoreSum = 0;
        public int mojBrojMaxScore = 0;
        public int mojBrojGames = 0;

        public int korakFound = 0;
        public int korakTotal = 0;
        public int korakScoreSum = 0;
        public int korakMaxScore = 0;
        public int korakGames = 0;
        public int[] korakStepCounts = new int[7];

        public int asocijacijeSolved = 0;
        public int asocijacijeTotal = 0;
        public int asocijacijeScoreSum = 0;
        public int asocijacijeMaxScore = 0;
        public int asocijacijeGames = 0;

        public int skockoFound = 0;
        public int skockoTotal = 0;
        public int skockoScoreSum = 0;
        public int skockoMaxScore = 0;
        public int skockoGames = 0;
        public int[] skockoAttemptCounts = new int[7];

        public int spojniceConnected = 0;
        public int spojniceTotal = 0;
        public int spojniceScoreSum = 0;
        public int spojniceMaxScore = 0;
        public int spojniceGames = 0;
    }
}
