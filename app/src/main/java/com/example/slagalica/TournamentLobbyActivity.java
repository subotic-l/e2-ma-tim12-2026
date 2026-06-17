package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.data.TournamentManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;

public class TournamentLobbyActivity extends AppCompatActivity {

    private TextView statusText;
    private ProgressBar progressBar;
    private Button cancelButton;

    private android.widget.ImageView[] avatars = new android.widget.ImageView[4];
    private TextView[] names = new TextView[4];
    private TextView[] leagues = new TextView[4];

    private TournamentManager tournamentManager;
    private String myPlayerId, myPlayerName, myAvatarUrl;
    private String myTournamentId;
    private boolean activityActive = false;
    private String launchedMatchId;

    private ActivityResultLauncher<Intent> matchLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tournament_lobby);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        statusText = findViewById(R.id.lobbyStatusText);
        progressBar = findViewById(R.id.searchProgressBar);
        cancelButton = findViewById(R.id.buttonCancel);

        avatars[0] = findViewById(R.id.avatar1);
        avatars[1] = findViewById(R.id.avatar2);
        avatars[2] = findViewById(R.id.avatar3);
        avatars[3] = findViewById(R.id.avatar4);
        names[0] = findViewById(R.id.name1);
        names[1] = findViewById(R.id.name2);
        names[2] = findViewById(R.id.name3);
        names[3] = findViewById(R.id.name4);
        leagues[0] = findViewById(R.id.league1);
        leagues[1] = findViewById(R.id.league2);
        leagues[2] = findViewById(R.id.league3);
        leagues[3] = findViewById(R.id.league4);

        Intent intent = getIntent();
        myPlayerName = intent != null ? intent.getStringExtra("playerName") : null;
        myAvatarUrl = intent != null ? intent.getStringExtra("avatarUrl") : null;
        if (myAvatarUrl == null) myAvatarUrl = "";

        FirebaseAuth auth = FirebaseAuth.getInstance();
        myPlayerId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (myPlayerId == null || myPlayerName == null) {
            Toast.makeText(this, "Morate biti prijavljeni", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tournamentManager = new TournamentManager();
        activityActive = true;

        matchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!activityActive) return;
                    tournamentManager.attachToTournament(myTournamentId);
                    tournamentManager.listenToTournament(createTournamentListener());
                }
        );

        cancelButton.setOnClickListener(v -> cancelTournament());

        startSearching();
    }

    private void startSearching() {
        statusText.setText("Tražim turnir...");

        tournamentManager.findWaitingTournaments(myPlayerId)
                .addOnSuccessListener(documents -> {
                    if (!activityActive) return;

                    for (DocumentSnapshot doc : documents) {
                        String docId = doc.getId();
                        List<Map<String, Object>> participants =
                                (List<Map<String, Object>>) doc.get("participants");
                        int count = participants != null ? participants.size() : 0;

                        if (count < 4) {
                            tournamentManager.joinTournament(docId, myPlayerId, myPlayerName, myAvatarUrl)
                                    .addOnSuccessListener(tId -> {
                                        if (!activityActive) return;
                                        myTournamentId = tId;
                                        listenToTournament();
                                    })
                                    .addOnFailureListener(e -> {
                                        if (!activityActive) return;
                                        createAndWait();
                                    });
                            return;
                        }
                    }
                    createAndWait();
                })
                .addOnFailureListener(e -> {
                    if (!activityActive) return;
                    createAndWait();
                });
    }

    private void createAndWait() {
        statusText.setText("Čekam igrače...");
        tournamentManager.createTournament(myPlayerId, myPlayerName, myAvatarUrl)
                .addOnSuccessListener(tId -> {
                    if (!activityActive) return;
                    myTournamentId = tId;
                    listenToTournament();
                })
                .addOnFailureListener(e -> {
                    if (!activityActive) return;
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    @SuppressWarnings("unchecked")
    private void tryStartTournament(Map<String, Object> data) {
        List<Map<String, Object>> participants =
                (List<Map<String, Object>>) data.get("participants");
        if (participants == null || participants.size() < 4) return;

        statusText.setText("Pokrećem turnir...");
        tournamentManager.startTournament(participants)
                .addOnFailureListener(e -> {
                    // Neko drugi je već pokrenuo — okej
                });
    }

    private void listenToTournament() {
        tournamentManager.listenToTournament(createTournamentListener());
    }

    private TournamentManager.TournamentListener createTournamentListener() {
        return new TournamentManager.TournamentListener() {
            @Override
            public void onStateChanged(Map<String, Object> data) {
                if (!activityActive) return;
                String status = (String) data.get("status");
                switch (status) {
                    case "waiting":
                        updatePlayerSlots(data);
                        tryStartTournament(data);
                        break;
                    case "semifinals":
                    case "final":
                        progressBar.setVisibility(android.view.View.GONE);
                        handleMatchPhase(data, status);
                        break;
                }
            }

            @Override
            public void onTournamentEnded(Map<String, Object> data) {
                if (!activityActive) return;
                showTournamentSummary(data);
            }

            @Override
            public void onError(String error) {
                if (!activityActive) return;
                Toast.makeText(TournamentLobbyActivity.this, error, Toast.LENGTH_SHORT).show();
            }

            @SuppressWarnings("unchecked")
            private void handleMatchPhase(Map<String, Object> data, String phase) {
                Map<String, Object> bracket = (Map<String, Object>) data.get("bracket");
                if (bracket == null) return;

                String roundKey = phase.equals("final") ? "final" : null;
                boolean isSpectator = false;

                if (roundKey == null) {
                    // Odredi u kom sam polufinalu
                    Map<String, Object> semi1 = (Map<String, Object>) bracket.get("semi1");
                    Map<String, Object> semi2 = (Map<String, Object>) bracket.get("semi2");
                    boolean inSemi1 = semi1 != null && (myPlayerId.equals(semi1.get("player1Id"))
                            || myPlayerId.equals(semi1.get("player2Id")));
                    boolean inSemi2 = semi2 != null && (myPlayerId.equals(semi2.get("player1Id"))
                            || myPlayerId.equals(semi2.get("player2Id")));
                    if (inSemi1) roundKey = "semi1";
                    else if (inSemi2) roundKey = "semi2";
                } else {
                    // Finale — proveri da li sam igrač ili gledalac
                    Map<String, Object> finalRound = (Map<String, Object>) bracket.get("final");
                    if (finalRound != null) {
                        String fp1 = (String) finalRound.get("player1Id");
                        String fp2 = (String) finalRound.get("player2Id");
                        if (!myPlayerId.equals(fp1) && !myPlayerId.equals(fp2)) {
                            isSpectator = true;
                        }
                    }
                }

                if (roundKey == null) return;

                Map<String, Object> roundData = (Map<String, Object>) bracket.get(roundKey);
                if (roundData == null) return;

                String matchId = (String) roundData.get("matchId");
                String matchStatus = (String) roundData.get("status");

                // Spektator u finalu — samo čekamo kraj
                if (isSpectator && "final".equals(phase)) {
                    statusText.setText("Finale je u toku, čekamo rezultat...");
                    return;
                }

                // Ako je meč u toku, nije već pokrenut → pokreni
                if ("playing".equals(matchStatus) && !matchId.equals(launchedMatchId)) {
                    launchedMatchId = matchId;
                    tournamentManager.removeListener();
                    String p1Id = (String) roundData.get("player1Id");
                    int myPlayerNumber = myPlayerId.equals(p1Id) ? 1 : 2;

                    Intent intent = new Intent(TournamentLobbyActivity.this, NetworkMatchActivity.class);
                    intent.putExtra("matchId", matchId);
                    intent.putExtra("myPlayerNumber", myPlayerNumber);
                    intent.putExtra("myPlayerId", myPlayerId);
                    intent.putExtra("myPlayerName", myPlayerName);
                    intent.putExtra("myAvatarUrl", myAvatarUrl);
                    intent.putExtra("isTournamentMatch", true);
                    intent.putExtra("isTournamentSpectator", false);
                    intent.putExtra("tournamentId", myTournamentId);
                    intent.putExtra("tournamentRound", roundKey);
                    matchLauncher.launch(intent);
                    return;
                }

                // Ako je meč završen i u semifinalima smo → pokušaj napredovanje
                if ("done".equals(matchStatus) && "semifinals".equals(phase)) {
                    statusText.setText("Čekanje drugog polufinala...");
                    tournamentManager.tryAdvanceToFinal()
                            .addOnFailureListener(e -> {
                                // Drugi semi još nije gotov — ovo je očekivano
                            });
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void updatePlayerSlots(Map<String, Object> data) {
        List<Map<String, Object>> participants =
                (List<Map<String, Object>>) data.get("participants");
        if (participants == null) return;

        statusText.setText("Čekam igrače (" + participants.size() + "/4)");

        for (int i = 0; i < 4; i++) {
            if (i < participants.size()) {
                Map<String, Object> p = participants.get(i);
                String name = (String) p.get("playerName");
                String avatar = (String) p.get("playerAvatar");
                names[i].setText(name != null ? name : "?");
                names[i].setVisibility(android.view.View.VISIBLE);
                if (avatar != null && !avatar.isEmpty()) {
                    NetworkMatchActivity.loadAvatarStatic(avatars[i], avatar);
                } else {
                    avatars[i].setImageResource(R.drawable.ic_profile);
                }
            } else {
                names[i].setText("?");
                names[i].setVisibility(android.view.View.VISIBLE);
                avatars[i].setImageResource(R.drawable.ic_profile);
            }
        }
    }

    private void showTournamentSummary(Map<String, Object> data) {
        tournamentManager.cleanup();

        @SuppressWarnings("unchecked")
        Map<String, Object> bracket = (Map<String, Object>) data.get("bracket");
        String winnerId = null;
        if (bracket != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> finalRound = (Map<String, Object>) bracket.get("final");
            if (finalRound != null) {
                winnerId = (String) finalRound.get("winnerId");
            }
        }

        String winnerName = "Nepoznat";
        if (winnerId != null && myPlayerId.equals(winnerId)) {
            winnerName = myPlayerName;
        } else {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> participants =
                    (List<Map<String, Object>>) data.get("participants");
            if (participants != null) {
                for (Map<String, Object> p : participants) {
                    if (winnerId != null && winnerId.equals(p.get("playerId"))) {
                        winnerName = (String) p.get("playerName");
                        break;
                    }
                }
            }
        }

        if (myPlayerId.equals(winnerId)) {
            statusText.setText("Pobedili ste turnir!");
        } else {
            statusText.setText("Pobednik: " + winnerName);
        }
        progressBar.setVisibility(android.view.View.GONE);
        cancelButton.setText("Nazad");
        cancelButton.setOnClickListener(v -> finish());
    }

    private void cancelTournament() {
        tournamentManager.cleanup();
        finish();
    }

    @Override
    protected void onDestroy() {
        activityActive = false;
        if (tournamentManager != null) {
            tournamentManager.cleanup();
        }
        super.onDestroy();
    }
}
