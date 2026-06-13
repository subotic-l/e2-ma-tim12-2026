package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.GameSessionManager;
import com.example.slagalica.network.NetworkAsocijacijeGame;
import com.example.slagalica.network.NetworkSkockoGame;
import com.example.slagalica.network.NetworkNumbersGame;
import com.example.slagalica.network.NetworkSpojniceGame;
import com.example.slagalica.network.NetworkStepByStep;
import com.example.slagalica.network.NetworkWhoKnowsKnows;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
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
        if (state != null) {
            long p1 = state.containsKey("player1Score") ? (long) state.get("player1Score") : 0;
            long p2 = state.containsKey("player2Score") ? (long) state.get("player2Score") : 0;
            intent.putExtra("previousPlayer1Score", (int) p1);
            intent.putExtra("previousPlayer2Score", (int) p2);
        }
        gameLauncher.launch(intent);
    }

    private boolean notificationWritten = false;

    private void writeMatchNotification(Map<String, Object> state) {
        String p1Id = (String) state.get("player1Id");
        String p2Id = (String) state.get("player2Id");
        String p1Name = (String) state.get("player1Name");
        String p2Name = (String) state.get("player2Name");
        if (p1Id == null || p2Id == null || p1Name == null || p2Name == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> notif1 = new HashMap<>();
        notif1.put("message", "Igrali ste sa " + p2Name);
        notif1.put("createdAt", FieldValue.serverTimestamp());
        notif1.put("read", false);

        Map<String, Object> notif2 = new HashMap<>();
        notif2.put("message", "Igrali ste sa " + p1Name);
        notif2.put("createdAt", FieldValue.serverTimestamp());
        notif2.put("read", false);

        db.collection("users").document(p1Id).collection("notifications").add(notif1);
        db.collection("users").document(p2Id).collection("notifications").add(notif2);
    }

    private void showFinalSummary(Map<String, Object> state) {
        if (isFinishing()) return;
        sessionManager.cleanup();

        long p1Score = state.containsKey("player1Score") ? (long) state.get("player1Score") : 0;
        long p2Score = state.containsKey("player2Score") ? (long) state.get("player2Score") : 0;
        String p1Name = state.containsKey("player1Name") ? (String) state.get("player1Name") : "Igrač 1";
        String p2Name = state.containsKey("player2Name") ? (String) state.get("player2Name") : "Igrač 2";
        String winner = p1Score > p2Score ? p1Name : (p2Score > p1Score ? p2Name : "Nerešeno");

        if (!notificationWritten && myPlayerNumber == 1) {
            notificationWritten = true;
            writeMatchNotification(state);

            String p1Id = (String) state.get("player1Id");
            String p2Id = (String) state.get("player2Id");
            if (p1Id != null && p2Id != null) {
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
        }

        Intent intent = new Intent(this, NetworkMatchSummaryActivity.class);
        intent.putExtra("player1Name", p1Name);
        intent.putExtra("player2Name", p2Name);
        intent.putExtra("player1Score", (int) p1Score);
        intent.putExtra("player2Score", (int) p2Score);
        intent.putExtra("winner", winner);
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
