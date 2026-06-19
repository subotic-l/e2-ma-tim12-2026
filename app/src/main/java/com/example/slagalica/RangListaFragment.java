package com.example.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.data.LeaderboardManager;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RangListaFragment extends Fragment {

    private LeaderboardManager leaderboardManager;
    private LeaderboardManager.Period currentPeriod = LeaderboardManager.Period.WEEKLY;
    private RecyclerView recyclerView;
    private LeaderboardAdapter adapter;
    private TextView tabWeekly, tabMonthly, cycleDateRange, playerStatusText;
    private ProgressBar loadingSpinner;
    private Handler refreshHandler;
    private static final long REFRESH_INTERVAL_MS = 2 * 60 * 1000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rang_lista, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        leaderboardManager = new LeaderboardManager();

        tabWeekly = view.findViewById(R.id.tabWeekly);
        tabMonthly = view.findViewById(R.id.tabMonthly);
        cycleDateRange = view.findViewById(R.id.cycleDateRange);
        playerStatusText = view.findViewById(R.id.playerStatusText);
        loadingSpinner = view.findViewById(R.id.loadingSpinner);
        recyclerView = view.findViewById(R.id.leaderboardList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LeaderboardAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        refreshHandler = new Handler();

        tabWeekly.setOnClickListener(v -> {
            if (currentPeriod == LeaderboardManager.Period.WEEKLY) return;
            currentPeriod = LeaderboardManager.Period.WEEKLY;
            updateTabStyles();
            loadLeaderboard();
        });

        tabMonthly.setOnClickListener(v -> {
            if (currentPeriod == LeaderboardManager.Period.MONTHLY) return;
            currentPeriod = LeaderboardManager.Period.MONTHLY;
            updateTabStyles();
            loadLeaderboard();
        });

        updateTabStyles();
        loadLeaderboard();
        //showRewardDialog(1, 10, LeaderboardManager.getCycleId(LeaderboardManager.Period.WEEKLY));
        startAutoRefresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkCycleEnd();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (refreshHandler != null) {
            refreshHandler.removeCallbacksAndMessages(null);
        }
    }

    private void updateTabStyles() {
        if (currentPeriod == LeaderboardManager.Period.WEEKLY) {
            tabWeekly.setBackgroundResource(R.drawable.tab_selected);
            tabMonthly.setBackgroundResource(R.drawable.tab_unselected);
        } else {
            tabWeekly.setBackgroundResource(R.drawable.tab_unselected);
            tabMonthly.setBackgroundResource(R.drawable.tab_selected);
        }
    }

    private void loadLeaderboard() {
        String cycleId = LeaderboardManager.getCycleId(currentPeriod);
        cycleDateRange.setText("Ciklus: " + LeaderboardManager.getCycleDateRange(cycleId));
        loadingSpinner.setVisibility(View.VISIBLE);

        leaderboardManager.getTopPlayers(currentPeriod, 100)
                .addOnSuccessListener(entries -> {
                    loadingSpinner.setVisibility(View.GONE);
                    adapter.updateList(entries);
                    updatePlayerStatus(entries);
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    playerStatusText.setText("Greška pri učitavanju rang liste");
                });
    }

    private void updatePlayerStatus(List<LeaderboardManager.LeaderboardEntry> topEntries) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            playerStatusText.setText("Prijavite se da biste bili rangirani");
            return;
        }

        boolean found = false;
        for (int i = 0; i < topEntries.size(); i++) {
            if (topEntries.get(i).getUserId().equals(uid)) {
                playerStatusText.setText("Vi ste na #" + (i + 1) + ". mestu sa "
                        + topEntries.get(i).getStars() + " zvezdica");
                found = true;
                break;
            }
        }

        if (!found) {
            leaderboardManager.getPlayerScore(currentPeriod, uid)
                    .addOnSuccessListener(entry -> {
                        if (entry != null && entry.getStars() > 0) {
                            leaderboardManager.getPlayerRank(currentPeriod, uid)
                                    .addOnSuccessListener(rank -> {
                                        if (rank > 0) {
                                            playerStatusText.setText("Vi ste na #" + rank + ". mestu sa "
                                                    + entry.getStars() + " zvezdica");
                                        } else {
                                            playerStatusText.setText("Niste u rang listi - odigrajte partiju");
                                        }
                                    });
                        } else {
                            playerStatusText.setText("Niste u rang listi - odigrajte partiju");
                        }
                    })
                    .addOnFailureListener(e ->
                            playerStatusText.setText("Niste u rang listi - odigrajte partiju"));
        }
    }

    private void startAutoRefresh() {
        refreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    loadLeaderboard();
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                }
            }
        }, REFRESH_INTERVAL_MS);
    }

    private void checkCycleEnd() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        android.content.SharedPreferences prefs = requireActivity()
                .getSharedPreferences("leaderboard_prefs", android.content.Context.MODE_PRIVATE);
        String lastWeekly = prefs.getString("last_weekly_cycle", "");
        String lastMonthly = prefs.getString("last_monthly_cycle", "");
        String currentWeekly = LeaderboardManager.getCycleId(LeaderboardManager.Period.WEEKLY);
        String currentMonthly = LeaderboardManager.getCycleId(LeaderboardManager.Period.MONTHLY);

        if (!lastWeekly.isEmpty() && !lastWeekly.equals(currentWeekly)) {
            distributeAndNotify(lastWeekly, prefs);
        }
        if (!lastMonthly.isEmpty() && !lastMonthly.equals(currentMonthly)) {
            distributeAndNotify(lastMonthly, prefs);
        }

        prefs.edit()
                .putString("last_weekly_cycle", currentWeekly)
                .putString("last_monthly_cycle", currentMonthly)
                .apply();
    }

    private void distributeAndNotify(final String oldCycleId,
                                     final android.content.SharedPreferences prefs) {
        leaderboardManager.tryDistributeRewards(oldCycleId)
                .addOnSuccessListener(distributed ->
                    checkUserWonReward(oldCycleId)
                );
    }

    private void checkUserWonReward(String cycleId) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        leaderboardManager.getCycleMetadata(cycleId)
                .addOnSuccessListener(metadata -> {
                    if (metadata == null) return;
                    List<Map<String, Object>> winners =
                            (List<Map<String, Object>>) metadata.get("winners");
                    if (winners == null) return;

                    for (Map<String, Object> w : winners) {
                        String winnerUid = (String) w.get("uid");
                        if (winnerUid != null && winnerUid.equals(uid)) {
                            long rank = w.containsKey("rank") ? (long) w.get("rank") : 0;
                            long tokenReward = w.containsKey("tokenReward") ? (long) w.get("tokenReward") : 0;
                            long stars = w.containsKey("stars") ? (long) w.get("stars") : 0;

                            android.content.SharedPreferences prefs = requireActivity()
                                    .getSharedPreferences("leaderboard_prefs", android.content.Context.MODE_PRIVATE);
                            String claimed = prefs.getString("claimed_reward_" + cycleId, "");
                            if (!claimed.equals(uid)) {
                                prefs.edit().putString("claimed_reward_" + cycleId, uid).apply();
                                showRewardDialog((int) rank, (int) tokenReward, cycleId);
                            }
                            break;
                        }
                    }
                });
    }

    private void playRewardSound() {
        try {
            android.net.Uri uri = android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(requireActivity(), uri);
            if (r != null) r.play();
        } catch (Exception ignored) {}
    }

    private void showRewardDialog(int rank, int tokenReward, String cycleId) {
        if (!isAdded() || getActivity() == null) return;

        String periodLabel = cycleId.startsWith("weekly") ? "nedeljnoj" : "mesečnoj";
        String message = "Osvojili ste #" + rank + ". mesto na " + periodLabel + " rang listi!\n"
                + "Nagrada: " + tokenReward + " tokena";

        // Build custom dialog with animation
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_reward, null);

        TextView titleView = dialogView.findViewById(R.id.rewardTitle);
        TextView msgView = dialogView.findViewById(R.id.rewardMessage);
        ImageView starView = dialogView.findViewById(R.id.rewardStar);
        com.google.android.material.button.MaterialButton okBtn = dialogView.findViewById(R.id.rewardOkButton);

        msgView.setText(message);

        // Pulsing animation on star
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(
                starView, "scaleX", 1f, 1.4f, 1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(
                starView, "scaleY", 1f, 1.4f, 1f);
        scaleX.setDuration(800);
        scaleY.setDuration(800);
        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleX.start();
        scaleY.start();

        // Play notification sound
        playRewardSound();

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                .setView(dialogView)
                .show();

        okBtn.setOnClickListener(v -> {
            scaleX.cancel();
            scaleY.cancel();
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> {
            scaleX.cancel();
            scaleY.cancel();
        });
    }

    private static class LeaderboardAdapter
            extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

        private List<LeaderboardManager.LeaderboardEntry> entries;

        LeaderboardAdapter(List<LeaderboardManager.LeaderboardEntry> entries) {
            this.entries = entries;
        }

        void updateList(List<LeaderboardManager.LeaderboardEntry> newEntries) {
            entries = newEntries;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_leaderboard_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int i) {
            LeaderboardManager.LeaderboardEntry e = entries.get(i);
            int rank = i + 1;
            h.rankText.setText(rank + ".");
            h.playerName.setText(e.getUserName() != null ? e.getUserName() : "Nepoznat");
            h.starCount.setText(String.valueOf(e.getStars()));
            h.leagueIcon.setImageResource(LeagueHelper.getLeagueIconByIndex(e.getLeague()));
            if (e.getAvatarUrl() != null && !e.getAvatarUrl().isEmpty()) {
                NetworkMatchActivity.loadAvatarStatic(h.avatar, e.getAvatarUrl());
            } else {
                h.avatar.setImageResource(R.drawable.ic_profile);
            }

            if (rank <= 3) {
                int color = rank == 1 ? 0xFFFFD700 : (rank == 2 ? 0xFFC0C0C0 : 0xFFCD7F32);
                h.rankText.setTextColor(color);
            } else {
                h.rankText.setTextColor(0xFFFFFFFF);
            }
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView rankText, playerName, starCount;
            ImageView avatar, leagueIcon;

            ViewHolder(View v) {
                super(v);
                rankText = v.findViewById(R.id.rankText);
                playerName = v.findViewById(R.id.playerName);
                starCount = v.findViewById(R.id.starCount);
                avatar = v.findViewById(R.id.playerAvatar);
                leagueIcon = v.findViewById(R.id.leagueIcon);
            }
        }
    }
}
