package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.service.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {

    private NavigationHelper navHelper;
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
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        userService = new UserService();

        navHelper = new NavigationHelper(this, R.id.navigation_home)
                .addFragmentTab(R.id.navigation_home, HomeFragment.class)
                .addFragmentTab(R.id.navigation_profile, ProfileFragment.class)
                .addFragmentTab(R.id.navigation_stats, RegionsFragment.class)
                .addFragmentTab(R.id.navigation_friends, FriendsFragment.class)
                .addSoonTab(R.id.navigation_rankings);

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
