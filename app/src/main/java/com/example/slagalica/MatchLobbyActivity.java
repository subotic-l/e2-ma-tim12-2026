package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.data.GameSessionManager;
import com.example.slagalica.service.UserService;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;
import java.util.UUID;

public class MatchLobbyActivity extends AppCompatActivity {

    private TextView lobbyStatusText;
    private Button buttonCancelSearch;
    private ProgressBar searchProgressBar;
    private boolean activityActive = false;

    private GameSessionManager sessionManager;
    private UserService userService;
    private String myPlayerId;
    private String myPlayerName;
    private String myAvatarUrl;
    private String myMatchId;
    private boolean isSearching = false;
    private Handler searchHandler;
    private Runnable searchRunnable;
    private boolean listeningToOwnMatch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_match_lobby);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        lobbyStatusText = findViewById(R.id.lobbyStatusText);
        buttonCancelSearch = findViewById(R.id.buttonCancelSearch);
        searchProgressBar = findViewById(R.id.searchProgressBar);
        searchProgressBar.setVisibility(android.view.View.VISIBLE);

        Intent intent = getIntent();
        myPlayerName = intent != null ? intent.getStringExtra("playerName") : null;
        if (myPlayerName == null || myPlayerName.isEmpty()) {
            Toast.makeText(this, "Unesite ime igrača", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            myPlayerId = auth.getCurrentUser().getUid();
        } else {
            myPlayerId = "anon_" + UUID.randomUUID();
        }

        String pid = intent != null ? intent.getStringExtra("playerId") : null;
        if (pid != null) myPlayerId = pid;
        myAvatarUrl = intent != null ? intent.getStringExtra("avatarUrl") : null;
        if (myAvatarUrl == null) myAvatarUrl = "";

        sessionManager = new GameSessionManager();
        userService = new UserService();
        activityActive = true;
        searchHandler = new Handler(Looper.getMainLooper());

        buttonCancelSearch.setOnClickListener(v -> finish());
        startSearching();
    }

    private void startSearching() {
        if (isSearching) return;
        isSearching = true;

        lobbyStatusText.setText("Tražim protivnika...");

        // Step 1: Try to find and claim an existing waiting match
        sessionManager.findWaitingMatches(myPlayerId)
                .addOnSuccessListener(documents -> {
                    if (!activityActive || !isSearching) return;

                    boolean claimed = false;
                    long now = System.currentTimeMillis();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : documents) {
                        String docId = doc.getId();
                        String p1Id = doc.getString("player1Id");
                        if (p1Id == null || p1Id.equals(myPlayerId)) continue;

                        // Skip matches older than 10 seconds (stale from previous sessions)
                        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");
                        if (createdAt == null) continue;
                        if (now - createdAt.toDate().getTime() > 10_000) continue;

                        // Found a match, try to claim it
                        sessionManager.tryClaimMatch(docId, myPlayerId, myPlayerName, myAvatarUrl)
                                .addOnSuccessListener(claimedMatchId -> {
                                    if (!activityActive) return;
                                    stopPolling();
                                    sessionManager.cleanup();
                                    String opponentName = doc.getString("player1Name");
                                    navigateToMatch(2, opponentName != null ? opponentName : "Protivnik");
                                })
                                .addOnFailureListener(e -> {
                                    // Claim failed (someone else took it), try creating own match
                                    if (!activityActive || !isSearching) return;
                                    createAndWait();
                                });
                        claimed = true;
                        break;
                    }

                    if (!claimed) {
                        // No existing matches found, create our own and wait
                        createAndWait();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!activityActive || !isSearching) return;
                    createAndWait();
                });
    }

    private void createAndWait() {
        lobbyStatusText.setText("Čekam protivnika...");

        sessionManager.createMatch(myPlayerId, myPlayerName, myAvatarUrl)
                .addOnSuccessListener(matchId -> {
                    if (!activityActive) return;
                    myMatchId = matchId;

                    sessionManager.listenToMatch(new GameSessionManager.StateListener() {
                        @Override
                        public void onStateChanged(Map<String, Object> fullState) {
                            if (!activityActive) return;
                            String status = (String) fullState.get("status");
                            if ("playing".equals(status)) {
                                String opponentName = (String) fullState.get("player2Name");
                                stopPolling();
                                navigateToMatch(1, opponentName != null ? opponentName : "Protivnik");
                            }
                        }

                        @Override
                        public void onMatchEnded(Map<String, Object> finalState) {
                        }

                        @Override
                        public void onError(String error) {
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (!activityActive) return;
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    stopSearching();
                });
    }

    private void stopPolling() {
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }

    private void stopSearching() {
        isSearching = false;
        stopPolling();
        sessionManager.cleanup();
        if (myMatchId != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("matches").document(myMatchId)
                    .delete();
            myMatchId = null;
        }
        searchProgressBar.setVisibility(android.view.View.GONE);
        lobbyStatusText.setText("Pretraga otkazana");
    }

    private void navigateToMatch(int playerNumber, String opponentName) {
        isSearching = false;
        stopPolling();
        sessionManager.cleanup();

        // Optimistically update local cache (Firestore deduction is async)
        TopBarHelper.decrementTokenCache(this);

        // Deduct 1 token for this match
        userService.deductToken().addOnFailureListener(e ->
                Log.w("MatchLobby", "Token deduction failed", e));

        Intent intent = new Intent(this, NetworkMatchActivity.class);
        intent.putExtra("matchId", sessionManager.getMatchId());
        intent.putExtra("myPlayerNumber", playerNumber);
        intent.putExtra("myPlayerId", myPlayerId);
        intent.putExtra("myPlayerName", myPlayerName);
        intent.putExtra("myAvatarUrl", myAvatarUrl != null ? myAvatarUrl : "");
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activityActive = false;
        if (isSearching) {
            stopSearching();
        }
        stopPolling();
        sessionManager.cleanup();
    }
}
