package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.slagalica.data.GameSessionManager;
import com.example.slagalica.data.TournamentManager;
import com.example.slagalica.network.NetworkAsocijacijeGame;
import com.example.slagalica.network.NetworkSkockoGame;
import com.example.slagalica.network.NetworkNumbersGame;
import com.example.slagalica.network.NetworkSpojniceGame;
import com.example.slagalica.network.NetworkStepByStep;
import com.example.slagalica.network.NetworkWhoKnowsKnows;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class NetworkMatchActivity extends AppCompatActivity {

    private final List<Class<?>> gameOrder = Arrays.asList(
            NetworkWhoKnowsKnows.class,
            NetworkSpojniceGame.class,
            NetworkNumbersGame.class,
            NetworkStepByStep.class,
            NetworkAsocijacijeGame.class,
            NetworkSkockoGame.class
    );

    private GameSessionManager sessionManager;
    private int myPlayerNumber;
    private String myPlayerId;
    private String myPlayerName;
    private String matchId;
    private int currentGameIndex = -1;

    private boolean isTournamentMatch;
    private boolean isTournamentSpectator;
    private String tournamentId;
    private String tournamentRound;
    private boolean isFriendMatch;

    private TextView networkStatusText;
    private ProgressBar waitingProgressBar;
    private ActivityResultLauncher<Intent> gameLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_network_match);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    androidx.core.graphics.Insets sb = insets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
                    return insets;
                });

        Intent intent = getIntent();
        matchId = intent.getStringExtra("matchId");
        myPlayerNumber = intent.getIntExtra("myPlayerNumber", 1);
        myPlayerId = intent.getStringExtra("myPlayerId");
        myPlayerName = intent.getStringExtra("myPlayerName");

        isTournamentMatch = intent.getBooleanExtra("isTournamentMatch", false);
        isTournamentSpectator = intent.getBooleanExtra("isTournamentSpectator", false);
        tournamentId = intent.getStringExtra("tournamentId");
        tournamentRound = intent.getStringExtra("tournamentRound");
        isFriendMatch = intent.getBooleanExtra("isFriendMatch", false);

        networkStatusText = findViewById(R.id.networkStatusText);
        waitingProgressBar = findViewById(R.id.waitingProgressBar);

        sessionManager = new GameSessionManager();
        sessionManager.attachToMatch(matchId, myPlayerNumber);

        networkStatusText.setText("Povezivanje sa protivnikom...");
        waitingProgressBar.setVisibility(android.view.View.VISIBLE);

        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isFinishing()) return;
                    sessionManager.listenToMatch(createStateListener());
                }
        );

        sessionManager.listenToMatch(createStateListener());
    }

    private GameSessionManager.StateListener createStateListener() {
        return new GameSessionManager.StateListener() {
            @Override
            public void onStateChanged(Map<String, Object> fullState) {
                if (isFinishing()) return;
                runOnUiThread(() -> handleState(fullState));
            }

            @Override
            public void onMatchEnded(Map<String, Object> finalState) {
                if (isFinishing()) return;
                runOnUiThread(() -> showFinalSummary(finalState));
            }

            @Override
            public void onError(String error) {
            }
        };
    }

    private void handleState(Map<String, Object> state) {
        String status = (String) state.get("status");
        if ("finished".equals(status) || "forfeit".equals(status)) {
            showFinalSummary(state);
            return;
        }

        if ("playing".equals(status)) {
            long idx = state.containsKey("currentGameIndex") ? (long) state.get("currentGameIndex") : 0;
            int nextGame = (int) idx;
                if (nextGame > currentGameIndex) {
                    currentGameIndex = nextGame;
                    waitingProgressBar.setVisibility(android.view.View.GONE);
                    networkStatusText.setText("Igra " + (currentGameIndex + 1) + "/" + gameOrder.size());
                    launchGame(currentGameIndex, state);
                }
        }
    }

    private void launchGame(int gameIndex, Map<String, Object> state) {
        if (gameIndex >= gameOrder.size()) return;
        Intent intent = new Intent(this, gameOrder.get(gameIndex));
        intent.putExtra("matchId", matchId);
        intent.putExtra("myPlayerNumber", myPlayerNumber);
        intent.putExtra("myPlayerId", myPlayerId);
        intent.putExtra("myPlayerName", myPlayerName);
        intent.putExtra("myAvatarUrl", getIntent().getStringExtra("myAvatarUrl"));
        intent.putExtra("gameIndex", gameIndex);
        intent.putExtra("totalGames", gameOrder.size());
        intent.putExtra("isSpectator", isTournamentSpectator);
        if (state != null) {
            long p1 = state.containsKey("player1Score") ? (long) state.get("player1Score") : 0;
            long p2 = state.containsKey("player2Score") ? (long) state.get("player2Score") : 0;
            intent.putExtra("previousPlayer1Score", (int) p1);
            intent.putExtra("previousPlayer2Score", (int) p2);
        }
        gameLauncher.launch(intent);
    }

    public static void loadAvatarStatic(android.widget.ImageView iv, String url) {
        if (iv == null) return;
        android.content.Context ctx = iv.getContext();
        Glide.with(ctx)
                .load(url != null && !url.isEmpty() ? url : R.drawable.default_profile)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(iv);
    }

    private void processTournamentRewards(long p1Score, long p2Score, String p1Id, String p2Id) {
        try {
            com.google.firebase.firestore.FirebaseFirestore fb = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            boolean isFinal = "final".equals(tournamentRound);
            long myScore = myPlayerNumber == 1 ? p1Score : p2Score;
            boolean iWon = myPlayerNumber == 1 ? p1Score > p2Score : p2Score > p1Score;
            int starsDelta = com.example.slagalica.service.UserService.calculateStarsDelta((int) myScore, iWon);

            DocumentReference userRef = fb.collection("users").document(myPlayerId);

            if (isFinal) {
                if (iWon) {
                    userRef.update(
                            "stars", FieldValue.increment(starsDelta + 10),
                            "totalStarsEarned", FieldValue.increment(Math.max(0, starsDelta) + 10),
                            "monthlyStars", FieldValue.increment(Math.max(0, starsDelta) + 10),
                            "tokens", FieldValue.increment(3)
                    );
                } else {
                    userRef.update(
                            "stars", FieldValue.increment(starsDelta),
                            "totalStarsEarned", FieldValue.increment(Math.max(0, starsDelta)),
                            "monthlyStars", FieldValue.increment(starsDelta)
                    );
                }
            } else {
                if (iWon) {
                    userRef.update(
                            "stars", FieldValue.increment(starsDelta),
                            "totalStarsEarned", FieldValue.increment(Math.max(0, starsDelta)),
                            "monthlyStars", FieldValue.increment(starsDelta),
                            "tokens", FieldValue.increment(2)
                    );
                }
                // Semi loser: nothing
            }

            // Update league
            userRef.get().addOnSuccessListener(doc -> {
                Long ns = doc.getLong("stars");
                if (ns != null) {
                    int nl = com.example.slagalica.LeagueHelper.getLeagueIndex(ns);
                    userRef.update("league", nl);
                }
            });
        } catch (Exception e) {
            Log.e("TournamentRewards", "Error processing rewards", e);
        }
    }

    private void showFinalSummary(Map<String, Object> state) {
        if (isFinishing()) return;
        sessionManager.cleanup();

        long p1Score = state.containsKey("player1Score") ? (long) state.get("player1Score") : 0;
        long p2Score = state.containsKey("player2Score") ? (long) state.get("player2Score") : 0;
        String p1Name = state.containsKey("player1Name") ? (String) state.get("player1Name") : "Igrač 1";
        String p2Name = state.containsKey("player2Name") ? (String) state.get("player2Name") : "Igrač 2";
        String winner = p1Score > p2Score ? p1Name : (p2Score > p1Score ? p2Name : "Nerešeno");

        String opponentName = myPlayerNumber == 1 ? p2Name : p1Name;
        String result = (p1Score == p2Score) ? "Nerešeno" :
                (myPlayerNumber == 1 && p1Score > p2Score) || (myPlayerNumber == 2 && p2Score > p1Score)
                        ? "Pobedili ste" : "Izgubili ste";
        NotificationHelper.show(this, SlagalicaApp.CHANNEL_GENERAL, "Partija završena",
                result + " protiv " + opponentName + " (" + p1Score + ":" + p2Score + ")", myPlayerId);

        String p1Id = (String) state.get("player1Id");
        String p2Id = (String) state.get("player2Id");
        if (p1Id != null && p2Id != null && myPlayerNumber == 1 && !isFriendMatch) {
            Map<String, Object> matchHistory1 = new HashMap<>();
            matchHistory1.put("matchId", matchId);
            matchHistory1.put("player1Id", p1Id);
            matchHistory1.put("player2Id", p2Id);
            matchHistory1.put("opponentName", p2Name);
            matchHistory1.put("opponentId", p2Id);
            matchHistory1.put("timestamp", FieldValue.serverTimestamp());
            matchHistory1.put("won", p1Score > p2Score);
            matchHistory1.put("draw", p1Score == p2Score);
            matchHistory1.put("myScore", (int) p1Score);
            matchHistory1.put("opponentScore", (int) p2Score);

            Map<String, Object> matchHistory2 = new HashMap<>();
            matchHistory2.put("matchId", matchId);
            matchHistory2.put("player1Id", p1Id);
            matchHistory2.put("player2Id", p2Id);
            matchHistory2.put("opponentName", p1Name);
            matchHistory2.put("opponentId", p1Id);
            matchHistory2.put("timestamp", FieldValue.serverTimestamp());
            matchHistory2.put("won", p2Score > p1Score);
            matchHistory2.put("draw", p1Score == p2Score);
            matchHistory2.put("myScore", (int) p2Score);
            matchHistory2.put("opponentScore", (int) p1Score);

            Object gamesStatsObj = state.get("gamesStats");
            if (gamesStatsObj instanceof Map) {
                matchHistory1.put("games", gamesStatsObj);
                matchHistory2.put("games", gamesStatsObj);
            }

            GameSessionManager.saveMatchHistoryToUser(p1Id, matchHistory1);
            GameSessionManager.saveMatchHistoryToUser(p2Id, matchHistory2);
        }

        if (isTournamentMatch && tournamentId != null && tournamentRound != null) {
            if (!isTournamentSpectator) {
                String winnerId;
                if (p1Score > p2Score) {
                    winnerId = p1Id;
                } else if (p2Score > p1Score) {
                    winnerId = p2Id;
                } else {
                    winnerId = Math.random() < 0.5 ? p1Id : p2Id;
                }
                TournamentManager tm = new TournamentManager();
                tm.attachToTournament(tournamentId);
                if ("final".equals(tournamentRound)) {
                    tm.setFinalWinner(winnerId);
                    // Daily mission: tournament win
                    String myId2 = myPlayerNumber == 1 ? p1Id : p2Id;
                    if (winnerId.equals(myId2)) {
                        new com.example.slagalica.data.DailyMissionManager()
                                .markMissionDone(myId2, com.example.slagalica.data.DailyMissionManager.Mission.WIN_TOURNAMENT);
                    }
                } else {
                    String winnerName = winnerId.equals(p1Id) ? p1Name : p2Name;
                    String winnerAvatar = "";
                    tm.setSemiWinner(tournamentRound, winnerId, winnerName, winnerAvatar);
                }
                tm.cleanup();

                // Tournament reward processing
                processTournamentRewards(p1Score, p2Score, p1Id, p2Id);
            }
            finish();
            return;
        }

        Intent intent = new Intent(this, NetworkMatchSummaryActivity.class);
        intent.putExtra("player1Name", p1Name);
        intent.putExtra("player2Name", p2Name);
        intent.putExtra("player1Score", (int) p1Score);
        intent.putExtra("player2Score", (int) p2Score);
        intent.putExtra("winner", winner);
        intent.putExtra("myPlayerNumber", myPlayerNumber);
        intent.putExtra("myPlayerId", myPlayerId);
        intent.putExtra("matchId", matchId);
        intent.putExtra("player1Id", p1Id);
        intent.putExtra("player2Id", p2Id);
        intent.putExtra("isFriendMatch", isFriendMatch);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionManager != null) {
            sessionManager.cleanup();
        }
    }
}
