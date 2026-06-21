package com.example.slagalica;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.data.ChallengeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeLobbyActivity extends AppCompatActivity {

    private ChallengeManager challengeManager;
    private RecyclerView recyclerView;
    private ChallengeAdapter adapter;
    private List<DocumentSnapshot> challenges = new ArrayList<>();
    private String regionName;
    private String regionCode;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_challenge_lobby);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        challengeManager = new ChallengeManager();
        user = FirebaseAuth.getInstance().getCurrentUser();

        Intent intent = getIntent();
        regionName = intent != null ? intent.getStringExtra("region_name") : "";
        regionCode = intent != null ? intent.getStringExtra("region_code") : "";

        TopBarHelper.loadAndUpdateTopBar(this);

        ((TextView) findViewById(R.id.textRegionName)).setText(regionName);

        recyclerView = findViewById(R.id.challengeRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChallengeAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.buttonCreateChallenge).setOnClickListener(v -> showCreateDialog());

        loadChallenges();
    }

    private void loadChallenges() {
        if (user == null) return;
        challengeManager.findActiveChallenges(regionName, user.getUid())
                .addOnSuccessListener(docs -> {
                    challenges.clear();
                    challenges.addAll(docs);
                    adapter.notifyDataSetChanged();
                    findViewById(R.id.textNoChallenges).setVisibility(
                            challenges.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showCreateDialog() {
        if (user == null) return;

        View view = getLayoutInflater().inflate(R.layout.dialog_create_challenge, null);
        TextInputEditText betInput = view.findViewById(R.id.inputBetAmount);
        MaterialButton starsBtn = view.findViewById(R.id.buttonStars);
        MaterialButton tokensBtn = view.findViewById(R.id.buttonTokens);
        final String[] currency = {"stars"};

        starsBtn.setOnClickListener(v -> { currency[0] = "stars"; starsBtn.setAlpha(1f); tokensBtn.setAlpha(0.6f); });
        tokensBtn.setOnClickListener(v -> { currency[0] = "tokens"; tokensBtn.setAlpha(1f); starsBtn.setAlpha(0.6f); });

        new MaterialAlertDialogBuilder(this)
                .setTitle("Kreiraj izazov")
                .setView(view)
                .setPositiveButton("Kreiraj", (dialog, which) -> {
                    String betStr = betInput.getText() != null ? betInput.getText().toString().trim() : "";
                    int bet = 1;
                    try {
                        bet = Integer.parseInt(betStr);
                        if (bet < 1) bet = 1;
                    } catch (NumberFormatException ignored) {}

                    challengeManager.createChallenge(user.getUid(),
                                    user.getDisplayName() != null ? user.getDisplayName() : "Igrač",
                                    regionName, bet, currency[0])
                            .addOnSuccessListener(id -> {
                                Toast.makeText(this, "Izazov kreiran!", Toast.LENGTH_SHORT).show();
                                loadChallenges();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_challenge, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = challenges.get(position);
            String hostId = doc.getString("hostId");
            String hostName = doc.getString("hostName");
            Long betAmount = doc.getLong("betAmount");
            String currencyType = doc.getString("currencyType");
            String status = doc.getString("status");
            String challengeDocId = doc.getId();

            Map<String, Object> participants = (Map<String, Object>) doc.get("participants");
            int participantCount = participants != null ? participants.size() : 0;

            holder.hostText.setText(hostName != null ? hostName : "Nepoznat");

            String betText = betAmount != null ? betAmount + ("stars".equals(currencyType) ? " ⭐" : " 🪙") : "?";
            holder.betText.setText(betText);

            StringBuilder parts = new StringBuilder("Igrači: ");
            if (participants != null) {
                boolean first = true;
                for (Map.Entry<String, Object> entry : participants.entrySet()) {
                    Map<String, Object> p = (Map<String, Object>) entry.getValue();
                    String name = p != null ? (String) p.get("playerName") : "?";
                    if (!first) parts.append(", ");
                    parts.append(name);
                    first = false;
                }
            }
            holder.participantsText.setText(parts.toString());

            if ("playing".equals(status)) {
                holder.statusText.setText("Status: U toku");
            } else {
                holder.statusText.setText("Status: Čeka se " + (ChallengeManager.MAX_PARTICIPANTS - participantCount) + " igrača");
            }

            boolean isHost = user != null && user.getUid().equals(hostId);
            boolean joined = user != null && participants != null && participants.containsKey(user.getUid());

            MaterialButton actionBtn = holder.actionButton;

            if ("waiting".equals(status)) {
                if (isHost) {
                    if (participantCount >= ChallengeManager.MIN_PARTICIPANTS) {
                        actionBtn.setText("Započni");
                        actionBtn.setVisibility(View.VISIBLE);
                        actionBtn.setOnClickListener(v -> startChallenge(challengeDocId));
                    } else {
                        actionBtn.setText("Čekam...");
                        actionBtn.setVisibility(View.VISIBLE);
                        actionBtn.setEnabled(false);
                    }
                } else if (joined) {
                    actionBtn.setText("Pridružen");
                    actionBtn.setVisibility(View.VISIBLE);
                    actionBtn.setEnabled(false);
                } else if (participantCount < ChallengeManager.MAX_PARTICIPANTS) {
                    actionBtn.setText("Pridruži se");
                    actionBtn.setVisibility(View.VISIBLE);
                    actionBtn.setOnClickListener(v -> joinChallenge(challengeDocId));
                } else {
                    actionBtn.setVisibility(View.GONE);
                }
            } else if ("playing".equals(status)) {
                if (joined) {
                    actionBtn.setText("Igraj");
                    actionBtn.setVisibility(View.VISIBLE);
                    actionBtn.setOnClickListener(v -> playChallenge(challengeDocId));
                } else {
                    actionBtn.setVisibility(View.GONE);
                }
            } else {
                actionBtn.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return challenges.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView hostText, betText, participantsText, statusText;
            MaterialButton actionButton;

            ViewHolder(View v) {
                super(v);
                hostText = v.findViewById(R.id.textHostName);
                betText = v.findViewById(R.id.textBetAmount);
                participantsText = v.findViewById(R.id.textParticipants);
                statusText = v.findViewById(R.id.textStatus);
                actionButton = v.findViewById(R.id.buttonAction);
            }
        }
    }

    private void joinChallenge(String challengeDocId) {
        if (user == null) return;
        challengeManager.joinChallenge(challengeDocId, user.getUid(),
                        user.getDisplayName() != null ? user.getDisplayName() : "Igrač")
                .addOnSuccessListener(id -> {
                    Toast.makeText(this, "Pridružili ste se izazovu!", Toast.LENGTH_SHORT).show();
                    loadChallenges();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void startChallenge(String challengeDocId) {
        challengeManager.attachToChallenge(challengeDocId);
        challengeManager.startChallenge()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Izazov započet!", Toast.LENGTH_SHORT).show();
                    loadChallenges();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void playChallenge(String challengeDocId) {
        Intent intent = new Intent(this, ChallengePlayActivity.class);
        intent.putExtra("challenge_id", challengeDocId);
        startActivity(intent);
    }
}
