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

import com.example.slagalica.data.AvatarHelper;
import com.example.slagalica.data.TournamentManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
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
    private android.widget.LinearLayout slotsContainer, row1, row2;
    private TextView vsText;
    private android.widget.LinearLayout finaleLayout;
    private android.widget.ImageView finaleWinnerAvatar, finaleLoserAvatar;
    private TextView finaleWinnerName, finaleLoserName;

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

        slotsContainer = findViewById(R.id.slotsContainer);
        row1 = findViewById(R.id.row1);
        row2 = findViewById(R.id.row2);
        vsText = findViewById(R.id.vsText);
        finaleLayout = findViewById(R.id.finaleLayout);
        finaleWinnerAvatar = findViewById(R.id.finaleWinnerAvatar);
        finaleWinnerName = findViewById(R.id.finaleWinnerName);
        finaleLoserAvatar = findViewById(R.id.finaleLoserAvatar);
        finaleLoserName = findViewById(R.id.finaleLoserName);

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
        // Check and deduct token cost
        FirebaseFirestore.getInstance().collection("users").document(myPlayerId).get()
                .addOnSuccessListener(doc -> {
                    if (!activityActive) return;
                    Long tokens = doc.getLong("tokens");
                    if (tokens == null || tokens < 3) {
                        Toast.makeText(this, "Potrebno je 3 tokena za učešće u turniru",
                                Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    FirebaseFirestore.getInstance().collection("users").document(myPlayerId)
                            .update("tokens", FieldValue.increment(-3));
                    doSearch();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Greška pri proveri tokena", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void doSearch() {
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

                    // Animacija prelaza u finale
                    if ("final".equals(phase)) {
                        animateSemiToFinal(data, intent);
                    } else {
                        matchLauncher.launch(intent);
                    }
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
    private void animateSemiToFinal(Map<String, Object> data, Intent finalIntent) {
        Map<String, Object> bracket = (Map<String, Object>) data.get("bracket");
        List<Map<String, Object>> participants = (List<Map<String, Object>>) data.get("participants");
        if (bracket == null || participants == null) return;

        Map<String, Object> semi1 = (Map<String, Object>) bracket.get("semi1");
        Map<String, Object> semi2 = (Map<String, Object>) bracket.get("semi2");
        if (semi1 == null || semi2 == null) return;

        String w1Id = (String) semi1.get("winnerId");
        String w2Id = (String) semi2.get("winnerId");
        if (w1Id == null || w2Id == null) return;

        if (participants.size() < 4) return;
        String pid0 = (String) participants.get(0).get("playerId");
        String pid2 = (String) participants.get(2).get("playerId");
        int loseSlot1 = w1Id.equals(pid0) ? 1 : 0;
        int loseSlot2 = w2Id.equals(pid2) ? 3 : 2;

        statusText.setText("Finale!");

        // Fade out losers
        for (int ls : new int[]{loseSlot1, loseSlot2}) {
            if (ls >= 0 && ls < avatars.length) {
                avatars[ls].animate().alpha(0f).translationY(200f).setDuration(500).start();
                names[ls].animate().alpha(0f).setDuration(500).start();
                if (ls < leagues.length && leagues[ls] != null) {
                    leagues[ls].animate().alpha(0f).setDuration(500).start();
                }
            }
        }

        // Find winner/loser display data for finaleLayout
        String[] finNames = {"Igrač", "Igrač"};
        String[] finAvatars = {"", ""};
        String[] finIds = {"", ""};
        int fi = 0;
        for (Map<String, Object> p : participants) {
            String pid = (String) p.get("playerId");
            if (pid == null) continue;
            if (pid.equals(w1Id) || pid.equals(w2Id)) {
                finNames[fi] = (String) p.get("playerName");
                finAvatars[fi] = (String) p.get("playerAvatar");
                finIds[fi] = pid;
                fi++;
            }
        }
        final String fn1 = finNames[0], fv1 = finAvatars[0], fi1 = finIds[0];
        final String fn2 = finNames[1], fv2 = finAvatars[1], fi2 = finIds[1];

        // Populate finaleLayout and show it after losers fade out
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            slotsContainer.setVisibility(android.view.View.GONE);
            vsText.setVisibility(android.view.View.GONE);

            // Winner (first finalist) goes top, second goes bottom
            finaleWinnerName.setText(fn1);
            if (fv1 != null && !fv1.isEmpty()) {
                AvatarHelper.loadAvatar(finaleWinnerAvatar, fi1, fv1);
            }
            finaleLoserName.setText(fn2);
            if (fv2 != null && !fv2.isEmpty()) {
                AvatarHelper.loadAvatar(finaleLoserAvatar, fi2, fv2);
            }

            finaleLayout.setVisibility(android.view.View.VISIBLE);
            finaleLayout.setAlpha(0f);
            finaleLayout.animate().alpha(1f).setDuration(400).start();

            // Pulse both finalists
            finaleWinnerAvatar.post(() -> {
                android.animation.ObjectAnimator px = android.animation.ObjectAnimator.ofFloat(
                        finaleWinnerAvatar, "scaleX", 1f, 1.2f, 1f);
                android.animation.ObjectAnimator py = android.animation.ObjectAnimator.ofFloat(
                        finaleWinnerAvatar, "scaleY", 1f, 1.2f, 1f);
                px.setDuration(500); py.setDuration(500);
                px.setRepeatCount(1); py.setRepeatCount(1);
                px.start(); py.start();
            });
            finaleLoserAvatar.post(() -> {
                android.animation.ObjectAnimator px = android.animation.ObjectAnimator.ofFloat(
                        finaleLoserAvatar, "scaleX", 1f, 1.1f, 1f);
                android.animation.ObjectAnimator py = android.animation.ObjectAnimator.ofFloat(
                        finaleLoserAvatar, "scaleY", 1f, 1.1f, 1f);
                px.setDuration(500); py.setDuration(500);
                px.setRepeatCount(1); py.setRepeatCount(1);
                px.start(); py.start();
            });
        }, 500);

        try {
            android.net.Uri uri = android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, uri);
            if (r != null) r.play();
        } catch (Exception ignored) {}

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (activityActive) matchLauncher.launch(finalIntent);
        }, 1500);
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
                String pid = (String) p.get("playerId");
                names[i].setText(name != null ? name : "?");
                names[i].setVisibility(android.view.View.VISIBLE);
                AvatarHelper.loadAvatar(avatars[i], pid, avatar);
                // Load league
                if (pid != null) {
                    final int slotIdx = i;
                    FirebaseFirestore.getInstance().collection("users").document(pid).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists() && activityActive) {
                                    Long l = doc.getLong("league");
                                    int idx = l != null ? l.intValue() : 0;
                                    if (slotIdx < leagues.length) {
                                        leagues[slotIdx].setText(LeagueHelper.getLeagueNameByIndex(idx));
                                        leagues[slotIdx].setVisibility(android.view.View.VISIBLE);
                                    }
                                }
                            });
                }
            } else {
                names[i].setText("?");
                names[i].setVisibility(android.view.View.VISIBLE);
                avatars[i].setImageResource(R.drawable.ic_profile);
                leagues[i].setText("");
                leagues[i].setVisibility(android.view.View.GONE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void showTournamentSummary(Map<String, Object> data) {
        tournamentManager.cleanup();

        Map<String, Object> bracket = (Map<String, Object>) data.get("bracket");
        List<Map<String, Object>> participants = (List<Map<String, Object>>) data.get("participants");
        Map<String, Object> finalRound = bracket != null ? (Map<String, Object>) bracket.get("final") : null;
        String winnerId = finalRound != null ? (String) finalRound.get("winnerId") : null;
        String finalist1Id = finalRound != null ? (String) finalRound.get("player1Id") : null;
        String finalist2Id = finalRound != null ? (String) finalRound.get("player2Id") : null;

        boolean iWonTournament = myPlayerId.equals(winnerId);
        progressBar.setVisibility(android.view.View.GONE);
        cancelButton.setText("Nazad");
        cancelButton.setOnClickListener(v -> finish());

        // Find winner/loser display data
        String winnerDisplayName = "Nepoznat", winnerAvatar = "", loserDisplayName = "Drugi", loserAvatar = "";
        String loserId = "";
        if (participants != null) {
            for (Map<String, Object> p : participants) {
                String pid = (String) p.get("playerId");
                if (pid == null) continue;
                if (pid.equals(winnerId)) {
                    winnerDisplayName = (String) p.get("playerName");
                    winnerAvatar = (String) p.get("playerAvatar");
                } else if (pid.equals(finalist1Id) || pid.equals(finalist2Id)) {
                    loserDisplayName = (String) p.get("playerName");
                    loserAvatar = (String) p.get("playerAvatar");
                    loserId = pid;
                }
            }
        }
        statusText.setText(iWonTournament ? "Pobedili ste turnir!" : "Pobednik: " + winnerDisplayName);

        // Hide original slot grid, show centered finaleLayout
        slotsContainer.setVisibility(android.view.View.GONE);
        vsText.setVisibility(android.view.View.GONE);

        // Populate winner
        AvatarHelper.loadAvatar(finaleWinnerAvatar, winnerId, winnerAvatar);
        finaleWinnerName.setText(winnerDisplayName);

        // Populate loser
        AvatarHelper.loadAvatar(finaleLoserAvatar, loserId, loserAvatar);
        finaleLoserName.setText(loserDisplayName);

        // Show finaleLayout
        finaleLayout.setVisibility(android.view.View.VISIBLE);

        // Pulse winner
        finaleWinnerAvatar.post(() -> {
            android.animation.ObjectAnimator px = android.animation.ObjectAnimator.ofFloat(
                    finaleWinnerAvatar, "scaleX", 1f, 1.3f, 1f);
            android.animation.ObjectAnimator py = android.animation.ObjectAnimator.ofFloat(
                    finaleWinnerAvatar, "scaleY", 1f, 1.3f, 1f);
            px.setDuration(600); py.setDuration(600);
            px.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            py.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            px.start(); py.start();

            if (iWonTournament) {
                try {
                    android.net.Uri uri = android.media.RingtoneManager.getDefaultUri(
                            android.media.RingtoneManager.TYPE_NOTIFICATION);
                    android.media.Ringtone r = android.media.RingtoneManager.getRingtone(
                            TournamentLobbyActivity.this, uri);
                    if (r != null) r.play();
                } catch (Exception ignored) {}
            }

            finaleWinnerAvatar.setBackgroundResource(R.drawable.profile_frame_gold);
        });

        // Dim loser
        finaleLoserAvatar.setAlpha(0.6f);
        finaleLoserName.setAlpha(0.6f);
        finaleLoserAvatar.setBackgroundResource(R.drawable.profile_frame);
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
