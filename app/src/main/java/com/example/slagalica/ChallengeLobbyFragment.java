package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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

public class ChallengeLobbyFragment extends Fragment {

    private static final String ARG_REGION_NAME = "region_name";
    private static final String ARG_REGION_CODE = "region_code";
    private static final String ARG_ALL_REGIONS = "all_regions";

    private ChallengeManager challengeManager;
    private RecyclerView activeRecyclerView, finishedRecyclerView;
    private ChallengeAdapter activeAdapter, finishedAdapter;
    private List<DocumentSnapshot> activeChallenges = new ArrayList<>();
    private List<DocumentSnapshot> finishedChallenges = new ArrayList<>();
    private String regionName;
    private String regionCode;
    private FirebaseUser user;

    private TextView tabActive, tabFinished;
    private TextView textNoActive, textNoFinished;

    public static ChallengeLobbyFragment newInstance(String regionCode, String regionName, boolean allRegions) {
        ChallengeLobbyFragment fragment = new ChallengeLobbyFragment();
        Bundle args = new Bundle();
        args.putString(ARG_REGION_NAME, regionName);
        args.putString(ARG_REGION_CODE, regionCode);
        args.putBoolean(ARG_ALL_REGIONS, allRegions);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            regionName = args.getString(ARG_REGION_NAME, "");
            regionCode = args.getString(ARG_REGION_CODE, "");
            boolean allRegions = args.getBoolean(ARG_ALL_REGIONS, false);
            if (allRegions) {
                regionName = "";
                regionCode = "";
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_challenge_lobby, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        challengeManager = new ChallengeManager();
        user = FirebaseAuth.getInstance().getCurrentUser();

        ((TextView) view.findViewById(R.id.textRegionName)).setText(
                regionName != null && !regionName.isEmpty() ? regionName : "Svi regioni");

        view.findViewById(R.id.challengeBackButton).setOnClickListener(v -> close());

        tabActive = view.findViewById(R.id.tabActive);
        tabFinished = view.findViewById(R.id.tabFinished);
        textNoActive = view.findViewById(R.id.textNoActive);
        textNoFinished = view.findViewById(R.id.textNoFinished);

        activeRecyclerView = view.findViewById(R.id.activeRecyclerView);
        activeRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        activeAdapter = new ChallengeAdapter(true);
        activeRecyclerView.setAdapter(activeAdapter);

        finishedRecyclerView = view.findViewById(R.id.finishedRecyclerView);
        finishedRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        finishedAdapter = new ChallengeAdapter(false);
        finishedRecyclerView.setAdapter(finishedAdapter);

        tabActive.setOnClickListener(v -> selectTab(true));
        tabFinished.setOnClickListener(v -> selectTab(false));

        view.findViewById(R.id.buttonCreateChallenge).setOnClickListener(v -> showCreateDialog());

        selectTab(true);
        loadActive();
        loadFinished();
    }

    private void close() {
        if (getParentFragment() instanceof RegionsFragment) {
            ((RegionsFragment) getParentFragment()).closeChallengeLobby();
        }
    }

    private void selectTab(boolean active) {
        if (active) {
            tabActive.setBackgroundResource(R.drawable.rounded_white_small);
            tabActive.setTextColor(0xFF333333);
            tabFinished.setBackgroundResource(0);
            tabFinished.setTextColor(0xFFFFFFFF);
            activeRecyclerView.setVisibility(View.VISIBLE);
            finishedRecyclerView.setVisibility(View.GONE);
            textNoActive.setVisibility(activeChallenges.isEmpty() ? View.VISIBLE : View.GONE);
            textNoFinished.setVisibility(View.GONE);
        } else {
            tabFinished.setBackgroundResource(R.drawable.rounded_white_small);
            tabFinished.setTextColor(0xFF333333);
            tabActive.setBackgroundResource(0);
            tabActive.setTextColor(0xFFFFFFFF);
            finishedRecyclerView.setVisibility(View.VISIBLE);
            activeRecyclerView.setVisibility(View.GONE);
            textNoFinished.setVisibility(finishedChallenges.isEmpty() ? View.VISIBLE : View.GONE);
            textNoActive.setVisibility(View.GONE);
        }
    }

    private void loadActive() {
        if (user == null) return;
        String queryRegion = regionName != null && !regionName.isEmpty() ? regionName : null;
        challengeManager.findActiveChallenges(queryRegion, user.getUid())
                .addOnSuccessListener(docs -> {
                    activeChallenges.clear();
                    activeChallenges.addAll(docs);
                    activeAdapter.notifyDataSetChanged();
                    textNoActive.setVisibility(activeChallenges.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadFinished() {
        if (user == null) return;
        challengeManager.findFinishedChallenges(user.getUid())
                .addOnSuccessListener(docs -> {
                    finishedChallenges.clear();
                    finishedChallenges.addAll(docs);
                    finishedAdapter.notifyDataSetChanged();
                    textNoFinished.setVisibility(finishedChallenges.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showCreateDialog() {
        if (user == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_challenge, null);
        TextInputEditText starsInput = dialogView.findViewById(R.id.inputBetAmount);
        TextInputEditText tokensInput = dialogView.findViewById(R.id.inputTokenBet);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Kreiraj izazov")
                .setView(dialogView)
                .setPositiveButton("Kreiraj", (dialog, which) -> {
                    int starsBet = 1;
                    int tokensBet = 0;
                    try {
                        starsBet = Integer.parseInt(starsInput.getText().toString().trim());
                        if (starsBet < 1) starsBet = 1;
                        if (starsBet > 10) starsBet = 10;
                    } catch (NumberFormatException ignored) {}
                    try {
                        tokensBet = Integer.parseInt(tokensInput.getText().toString().trim());
                        if (tokensBet < 0) tokensBet = 0;
                        if (tokensBet > 2) tokensBet = 2;
                    } catch (NumberFormatException ignored) {}

                    final int finalStarsBet = starsBet;
                    final int finalTokensBet = tokensBet;

                    final String hostRegionName;
                    String rawRegion = regionName != null && !regionName.isEmpty() ? regionName
                            : (getArguments() != null ? getArguments().getString(ARG_REGION_NAME) : null);
                    hostRegionName = (rawRegion != null && !rawRegion.isEmpty()) ? rawRegion : "Srbija";

                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(user.getUid())
                            .get()
                            .addOnSuccessListener(doc -> {
                                String hostName = doc.getString("username");
                                if (hostName == null || hostName.isEmpty()) hostName = "Igrač";
                                challengeManager.createChallenge(user.getUid(), hostName,
                                                hostRegionName, finalStarsBet, finalTokensBet)
                                        .addOnSuccessListener(id -> {
                                            String msg = "Izazov kreiran!";
                                            if (finalStarsBet > 0) msg += " " + finalStarsBet + " ⭐";
                                            if (finalTokensBet > 0) msg += " " + finalTokensBet + " 🪙";
                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                                            loadActive();
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void joinChallenge(String challengeDocId) {
        if (user == null) return;
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String playerName = doc.getString("username");
                    if (playerName == null || playerName.isEmpty()) playerName = "Igrač";
                    challengeManager.joinChallenge(challengeDocId, user.getUid(), playerName)
                            .addOnSuccessListener(id -> {
                                Toast.makeText(requireContext(), "Pridružili ste se izazovu!", Toast.LENGTH_SHORT).show();
                                loadActive();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void startChallenge(String challengeDocId) {
        challengeManager.attachToChallenge(challengeDocId);
        challengeManager.startChallenge()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), "Izazov započet!", Toast.LENGTH_SHORT).show();
                    loadActive();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void playChallenge(String challengeDocId) {
        Intent intent = new Intent(requireActivity(), ChallengePlayActivity.class);
        intent.putExtra("challenge_id", challengeDocId);
        startActivity(intent);
    }

    private void showDetailDialog(DocumentSnapshot doc) {
        String hostName = doc.getString("hostName");
        Long starsBet = doc.getLong("starsBet");
        Long tokensBet = doc.getLong("tokensBet");
        if (starsBet == null) starsBet = 0L;
        if (tokensBet == null) tokensBet = 0L;
        String status = doc.getString("status");

        Map<String, Object> participants = (Map<String, Object>) doc.get("participants");
        int totalPlayers = participants != null ? participants.size() : 0;
        long totalStarsPot = starsBet * totalPlayers;
        long totalTokensPot = tokensBet * totalPlayers;

        View view = getLayoutInflater().inflate(R.layout.dialog_challenge_detail, null);

        ((TextView) view.findViewById(R.id.dialogHostName)).setText(
                "Domaćin: " + (hostName != null ? hostName : "Nepoznat"));

        StringBuilder betInfo = new StringBuilder("Ulog: ");
        if (starsBet > 0) betInfo.append(starsBet).append(" ⭐");
        if (tokensBet > 0) {
            if (starsBet > 0) betInfo.append(" + ");
            betInfo.append(tokensBet).append(" 🪙");
        }
        betInfo.append(" po igraču");
        ((TextView) view.findViewById(R.id.dialogBetInfo)).setText(betInfo.toString());

        StringBuilder potInfo = new StringBuilder("Ukupan pot: ");
        if (totalStarsPot > 0) potInfo.append(totalStarsPot).append(" ⭐");
        if (totalTokensPot > 0) {
            if (totalStarsPot > 0) potInfo.append(" + ");
            potInfo.append(totalTokensPot).append(" 🪙");
        }
        ((TextView) view.findViewById(R.id.dialogPotInfo)).setText(potInfo.toString());

        StringBuilder prizeInfo = new StringBuilder();
        if (totalPlayers > 0) {
            long winnerStars = (long) (totalStarsPot * 0.75);
            long winnerTokens = (long) (totalTokensPot * 0.75);
            prizeInfo.append("1. mesto: ");
            if (winnerStars > 0) prizeInfo.append(winnerStars).append(" ⭐");
            if (winnerTokens > 0) {
                if (winnerStars > 0) prizeInfo.append(" + ");
                prizeInfo.append(winnerTokens).append(" 🪙");
            }
            prizeInfo.append(" (75%)\n");
            prizeInfo.append("2. mesto: nazad ulog (");
            if (starsBet > 0) prizeInfo.append(starsBet).append(" ⭐");
            if (tokensBet > 0) {
                if (starsBet > 0) prizeInfo.append(" + ");
                prizeInfo.append(tokensBet).append(" 🪙");
            }
            prizeInfo.append(")");
        }
        ((TextView) view.findViewById(R.id.dialogPrizeInfo)).setText(prizeInfo.toString());

        StringBuilder playersText = new StringBuilder();
        if (participants != null && !participants.isEmpty()) {
            List<PlayerEntry> playerList = new ArrayList<>();
            for (Map.Entry<String, Object> entry : participants.entrySet()) {
                Map<String, Object> p = (Map<String, Object>) entry.getValue();
                String name = p != null ? (String) p.get("playerName") : "?";
                Object scoreObj = p != null ? p.get("score") : null;
                Boolean finished = p != null ? (Boolean) p.get("finished") : false;
                int score = 0;
                if (scoreObj instanceof Long) score = ((Long) scoreObj).intValue();
                playerList.add(new PlayerEntry(name, score, finished != null && finished));
            }

            playerList.sort((a, b) -> Integer.compare(b.score, a.score));

            int rank = 1;
            for (PlayerEntry pe : playerList) {
                playersText.append(rank).append(". ").append(pe.name);
                if (pe.finished) {
                    playersText.append(" — ").append(pe.score).append(" poena");
                } else {
                    playersText.append(" — još nije završio/la");
                }
                playersText.append("\n");
                rank++;
            }
        } else {
            playersText.append("Nema igrača");
        }
        ((TextView) view.findViewById(R.id.dialogPlayers)).setText(playersText.toString().trim());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Detalji izazova")
                .setView(view)
                .setPositiveButton("OK", null)
                .show();
    }

    private static class PlayerEntry {
        final String name;
        final int score;
        final boolean finished;

        PlayerEntry(String name, int score, boolean finished) {
            this.name = name;
            this.score = score;
            this.finished = finished;
        }
    }

    private class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.ViewHolder> {

        private final boolean isActive;

        ChallengeAdapter(boolean isActive) {
            this.isActive = isActive;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_challenge, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            List<DocumentSnapshot> list = isActive ? activeChallenges : finishedChallenges;
            DocumentSnapshot doc = list.get(position);
            String hostId = doc.getString("hostId");
            String hostName = doc.getString("hostName");
            Long starsBet = doc.getLong("starsBet");
            Long tokensBet = doc.getLong("tokensBet");
            if (starsBet == null) starsBet = 0L;
            if (tokensBet == null) tokensBet = 0L;
            String status = doc.getString("status");
            String challengeDocId = doc.getId();

            Map<String, Object> participants = (Map<String, Object>) doc.get("participants");
            int participantCount = participants != null ? participants.size() : 0;

            holder.hostText.setText(hostName != null ? hostName : "Nepoznat");

            StringBuilder betText = new StringBuilder();
            if (starsBet > 0) betText.append(starsBet).append(" ⭐");
            if (tokensBet > 0) {
                if (betText.length() > 0) betText.append(" + ");
                betText.append(tokensBet).append(" 🪙");
            }
            String region = doc.getString("region");
            if (region != null && !region.isEmpty()) {
                if (betText.length() > 0) betText.append(" · ");
                betText.append(region);
            }
            holder.betText.setText(betText.length() > 0 ? betText.toString() : "?");

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
            } else if ("finished".equals(status)) {
                holder.statusText.setText("Status: Završen");
            } else {
                holder.statusText.setText("Status: Čeka se " + (ChallengeManager.MAX_PARTICIPANTS - participantCount) + " igrača");
            }

            MaterialButton actionBtn = holder.actionButton;

            if (isActive) {
                boolean isHost = user != null && user.getUid().equals(hostId);
                boolean joined = user != null && participants != null && participants.containsKey(user.getUid());

                if ("waiting".equals(status)) {
                    if (isHost) {
                        if (participantCount >= ChallengeManager.MIN_PARTICIPANTS) {
                            actionBtn.setText("Započni");
                            actionBtn.setVisibility(View.VISIBLE);
                            actionBtn.setEnabled(true);
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
                        actionBtn.setEnabled(true);
                        actionBtn.setOnClickListener(v -> joinChallenge(challengeDocId));
                    } else {
                        actionBtn.setVisibility(View.GONE);
                    }
                } else if ("playing".equals(status)) {
                    if (joined) {
                        actionBtn.setText("Igraj");
                        actionBtn.setVisibility(View.VISIBLE);
                        actionBtn.setEnabled(true);
                        actionBtn.setOnClickListener(v -> playChallenge(challengeDocId));
                    } else {
                        actionBtn.setVisibility(View.GONE);
                    }
                } else {
                    actionBtn.setVisibility(View.GONE);
                }
            } else {
                actionBtn.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> showDetailDialog(doc));
        }

        @Override
        public int getItemCount() {
            return isActive ? activeChallenges.size() : finishedChallenges.size();
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
}
