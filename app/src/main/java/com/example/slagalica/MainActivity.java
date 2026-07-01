package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.data.LeaderboardManager;
import com.example.slagalica.service.UserService;

import java.util.List;
import java.util.Map;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {

    NavigationHelper navHelper;
    private UserService userService;
    private ListenerRegistration invitationListener;
    private String lastShownInvitationId;
    private boolean isInGame;
    private boolean isInForeground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, ime.bottom);
            return insets;
        });

        userService = new UserService();

        navHelper = new NavigationHelper(this, R.id.navigation_home)
                .addFragmentTab(R.id.navigation_home, HomeFragment.class)
                .addFragmentTab(R.id.navigation_profile, ProfileFragment.class)
                .addFragmentTab(R.id.navigation_stats, RegionsFragment.class)
                .addFragmentTab(R.id.navigation_friends, FriendsFragment.class)
                .addFragmentTab(R.id.navigation_rankings, RangListaFragment.class);

        navHelper.setup(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
        isInGame = false;
        if (navHelper != null) {
            navHelper.onResume();
        }
        userService.grantDailyTokensIfNeeded();
        userService.updateLastSeen();
        TopBarHelper.loadAndUpdateTopBar(this);
        listenForFriendInvitations();
        checkLeaderboardCycles();
    }

    private void checkLeaderboardCycles() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        android.content.SharedPreferences prefs = getSharedPreferences("leaderboard_prefs", MODE_PRIVATE);
        String lastWeekly = prefs.getString("last_weekly_cycle", "");
        String lastMonthly = prefs.getString("last_monthly_cycle", "");
        String currentWeekly = LeaderboardManager.getCycleId(LeaderboardManager.Period.WEEKLY);
        String currentMonthly = LeaderboardManager.getCycleId(LeaderboardManager.Period.MONTHLY);

        if (!lastWeekly.isEmpty() && !lastWeekly.equals(currentWeekly)) {
            LeaderboardManager lm = new LeaderboardManager();
            lm.tryDistributeRewards(lastWeekly).addOnSuccessListener(distributed ->
                checkUserWonReward(lastWeekly)
            );
        }

        if (!lastMonthly.isEmpty() && !lastMonthly.equals(currentMonthly)) {
            LeaderboardManager lm = new LeaderboardManager();
            lm.tryDistributeRewards(lastMonthly).addOnSuccessListener(distributed ->
                checkUserWonReward(lastMonthly)
            );
        }

        prefs.edit()
                .putString("last_weekly_cycle", currentWeekly)
                .putString("last_monthly_cycle", currentMonthly)
                .apply();
    }

    private void checkUserWonReward(String cycleId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        new LeaderboardManager().getCycleMetadata(cycleId)
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

                            android.content.SharedPreferences prefs = getSharedPreferences("leaderboard_prefs", MODE_PRIVATE);
                            String notified = prefs.getString("notified_reward_" + cycleId, "");
                            if (!notified.equals(uid)) {
                                prefs.edit().putString("notified_reward_" + cycleId, uid).apply();
                                String periodLabel = cycleId.startsWith("weekly") ? "nedeljnoj" : "mesečnoj";
                                NotificationHelper.show(this, SlagalicaApp.CHANNEL_REWARDS,
                                        "Nagrada za rang listu!",
                                        "Osvojili ste #" + rank + ". mesto na " + periodLabel
                                                + " rang listi! Nagrada: " + tokenReward + " tokena.",
                                        uid);
                            }
                            // Show reward dialog (once, independent of notification)
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

    private void showRewardDialog(int rank, int tokenReward, String cycleId) {
        String periodLabel = cycleId.startsWith("weekly") ? "nedeljnoj" : "mesečnoj";
        String message = "Osvojili ste #" + rank + ". mesto na " + periodLabel + " rang listi!\n"
                + "Nagrada: " + tokenReward + " tokena";

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_reward, null);

        android.widget.TextView msgView = dialogView.findViewById(R.id.rewardMessage);
        android.widget.ImageView starView = dialogView.findViewById(R.id.rewardStar);
        com.google.android.material.button.MaterialButton okBtn = dialogView.findViewById(R.id.rewardOkButton);

        msgView.setText(message);

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

        // Play sound
        try {
            android.net.Uri uri = android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, uri);
            if (r != null) r.play();
        } catch (Exception ignored) {}

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
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

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeInvitationListener();
    }

    public void setInGame(boolean inGame) {
        this.isInGame = inGame;
    }

    private void listenForFriendInvitations() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        if (invitationListener != null) return;

        invitationListener = FirebaseFirestore.getInstance()
                .collection("friend_invitations")
                .whereEqualTo("toId", user.getUid())
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String invitationId = doc.getId();
                        if (invitationId.equals(lastShownInvitationId)) continue;

                        Long expiresAt = doc.getLong("expiresAt");
                        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                            doc.getReference().update("status", "expired");
                            continue;
                        }

                        if (isFinishing() || isInGame) return;

                        lastShownInvitationId = invitationId;
                        String fromName = doc.getString("fromName");

                        if (isInForeground) {
                            showInvitationDialog(invitationId, fromName);
                        } else {
                            NotificationHelper.show(MainActivity.this,
                                    SlagalicaApp.CHANNEL_GENERAL,
                                    "Poziv za partiju",
                                    (fromName != null ? fromName : "Neko") +
                                            " vas poziva na prijateljsku partiju Slagalice!",
                                    user.getUid());
                        }
                        break;
                    }
                });
    }

    private void showInvitationDialog(String invitationId, String fromName) {
        if (isFinishing()) return;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Poziv za partiju")
                .setMessage((fromName != null ? fromName : "Neko") +
                        " vas poziva na prijateljsku partiju!")
                .setCancelable(false)
                .setPositiveButton("Prihvati", (dialog, which) -> openFriendLobby())
                .setNegativeButton("Odbij", (dialog, which) -> declineInvitation(invitationId))
                .show();
    }

    private void openFriendLobby() {
        Intent intent = new Intent(this, FriendLobbyActivity.class);
        startActivity(intent);
    }

    private void declineInvitation(String invitationId) {
        FirebaseFirestore.getInstance()
                .collection("friend_invitations").document(invitationId)
                .update("status", "declined");
    }

    private void removeInvitationListener() {
        if (invitationListener != null) {
            invitationListener.remove();
            invitationListener = null;
        }
    }
}
