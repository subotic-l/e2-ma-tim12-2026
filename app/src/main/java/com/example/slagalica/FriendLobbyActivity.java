package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.data.GameSessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class FriendLobbyActivity extends AppCompatActivity {

    private static final String INVITATIONS_COLLECTION = "friend_invitations";
    private static final long AUTO_DECLINE_DELAY_MS = 10_000;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private String myPlayerId;
    private String myPlayerName;
    private String myAvatarUrl;

    private String targetFriendId;
    private String targetFriendName;

    private String invitationId;
    private boolean isSendingInvitation;
    private boolean activityActive;
    private ListenerRegistration invitationListener;
    private Handler autoDeclineHandler;
    private Runnable autoDeclineRunnable;

    private TextView invitationTitle;
    private TextView invitationStatusText;
    private ProgressBar invitationProgressBar;
    private MaterialButton cancelButton;
    private MaterialButton backButton;

    private View incomingLayout;
    private TextView incomingText;
    private MaterialButton acceptButton;
    private MaterialButton declineButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_friend_lobby);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        activityActive = true;
        autoDeclineHandler = new Handler(Looper.getMainLooper());

        invitationTitle = findViewById(R.id.invitationTitle);
        invitationStatusText = findViewById(R.id.invitationStatusText);
        invitationProgressBar = findViewById(R.id.invitationProgressBar);
        cancelButton = findViewById(R.id.buttonCancelInvitation);
        backButton = findViewById(R.id.buttonBackToFriends);
        incomingLayout = findViewById(R.id.incomingInvitationLayout);
        incomingText = findViewById(R.id.incomingInvitationText);
        acceptButton = findViewById(R.id.buttonAcceptInvitation);
        declineButton = findViewById(R.id.buttonDeclineInvitation);

        backButton.setOnClickListener(v -> cleanupAndFinish());
        cancelButton.setOnClickListener(v -> cancelInvitation());
        acceptButton.setOnClickListener(v -> acceptInvitation());
        declineButton.setOnClickListener(v -> declineInvitation());

        if (currentUser == null) {
            Toast.makeText(this, "Morate biti prijavljeni", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        myPlayerId = currentUser.getUid();

        Intent intent = getIntent();
        targetFriendId = intent.getStringExtra("friendId");
        targetFriendName = intent.getStringExtra("friendName");

        loadMyProfile(() -> {
            if (targetFriendId != null) {
                sendInvitation(targetFriendId, targetFriendName);
            } else {
                listenForIncomingInvitations();
            }
        });
    }

    private void loadMyProfile(Runnable afterLoad) {
        db.collection("users").document(myPlayerId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        myPlayerName = doc.getString("username");
                        myAvatarUrl = doc.getString("avatarUrl");
                        if (myAvatarUrl == null) myAvatarUrl = "";
                    }
                    if (afterLoad != null) afterLoad.run();
                })
                .addOnFailureListener(e -> {
                    if (afterLoad != null) afterLoad.run();
                });
    }

    private void sendInvitation(String friendId, String friendName) {
        isSendingInvitation = true;
        invitationTitle.setText("Slanje poziva");
        invitationStatusText.setText("Šaljem poziv igraču " + (friendName != null ? friendName : "Nepoznat") + "...");
        invitationProgressBar.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);

        Map<String, Object> invitation = new HashMap<>();
        invitation.put("fromId", myPlayerId);
        invitation.put("fromName", myPlayerName != null ? myPlayerName : "Nepoznat");
        invitation.put("fromAvatar", myAvatarUrl != null ? myAvatarUrl : "");
        invitation.put("toId", friendId);
        invitation.put("status", "pending");
        invitation.put("createdAt", FieldValue.serverTimestamp());
        invitation.put("expiresAt", System.currentTimeMillis() + AUTO_DECLINE_DELAY_MS);

        db.collection(INVITATIONS_COLLECTION)
                .add(invitation)
                .addOnSuccessListener(doc -> {
                    if (!activityActive) return;
                    invitationId = doc.getId();
                    invitationStatusText.setText("Poziv poslat. Čekam odgovor...");

                    storeNotification(friendId);

                    autoDeclineRunnable = () -> {
                        if (!activityActive || invitationId == null) return;
                        invitationStatusText.setText("Prijatelj nije odgovorio. Poziv je istekao.");
                        invitationProgressBar.setVisibility(View.GONE);
                        cancelButton.setVisibility(View.GONE);
                        db.collection(INVITATIONS_COLLECTION).document(invitationId)
                                .update("status", "expired");
                    };
                    autoDeclineHandler.postDelayed(autoDeclineRunnable, AUTO_DECLINE_DELAY_MS);

                    listenToInvitationStatus();
                })
                .addOnFailureListener(e -> {
                    if (!activityActive) return;
                    Toast.makeText(this, "Greška pri slanju poziva: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    cleanupAndFinish();
                });
    }

    private void storeNotification(String friendId) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("message", "Poziv za prijateljsku partiju: " +
                (myPlayerName != null ? myPlayerName : "Nepoznat") + " vas poziva na partiju!");
        notif.put("createdAt", Timestamp.now());
        notif.put("read", false);
        notif.put("channel", SlagalicaApp.CHANNEL_GENERAL);
        db.collection("users").document(friendId).collection("notifications").add(notif);
    }

    private void listenToInvitationStatus() {
        if (invitationId == null) return;

        DocumentReference invRef = db.collection(INVITATIONS_COLLECTION).document(invitationId);
        invitationListener = invRef.addSnapshotListener((snapshot, error) -> {
            if (!activityActive || snapshot == null || error != null) return;

            String status = snapshot.getString("status");
            if (status == null) return;

            switch (status) {
                case "accepted": {
                    String matchId = snapshot.getString("matchId");
                    if (matchId != null) {
                        autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
                        claimMatchAndStart(matchId);
                    }
                    break;
                }
                case "declined": {
                    autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
                    invitationStatusText.setText("Prijatelj je odbio poziv.");
                    invitationProgressBar.setVisibility(View.GONE);
                    cancelButton.setVisibility(View.GONE);
                    removeListener();
                    break;
                }
                case "cancelled": {
                    autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
                    invitationStatusText.setText("Poziv je otkazan.");
                    invitationProgressBar.setVisibility(View.GONE);
                    removeListener();
                    break;
                }
                case "expired": {
                    autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
                    invitationStatusText.setText("Poziv je istekao.");
                    invitationProgressBar.setVisibility(View.GONE);
                    cancelButton.setVisibility(View.GONE);
                    removeListener();
                    break;
                }
            }
        });
    }

    private void claimMatchAndStart(String matchId) {
        invitationStatusText.setText("Povezivanje sa protivnikom...");

        GameSessionManager tmpSession = new GameSessionManager();
        tmpSession.tryClaimMatch(matchId, myPlayerId, myPlayerName, myAvatarUrl)
                .addOnSuccessListener(claimedId -> {
                    if (activityActive) startFriendMatch(matchId, 2);
                })
                .addOnFailureListener(e -> {
                    if (!activityActive) return;
                    invitationStatusText.setText("Greška: neko je već preuzeo partiju.");
                    invitationProgressBar.setVisibility(View.GONE);
                });
    }

    private void cancelInvitation() {
        if (invitationId == null) return;

        autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
        db.collection(INVITATIONS_COLLECTION).document(invitationId)
                .update("status", "cancelled");

        invitationStatusText.setText("Poziv otkazan.");
        invitationProgressBar.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        removeListener();
    }

    private void listenForIncomingInvitations() {
        invitationTitle.setText("Prijateljska partija");

        invitationListener = db.collection(INVITATIONS_COLLECTION)
                .whereEqualTo("toId", myPlayerId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, error) -> {
                    if (!activityActive || error != null || snapshots == null) return;

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String fromName = doc.getString("fromName");
                        Long expiresAt = doc.getLong("expiresAt");
                        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                            doc.getReference().update("status", "expired");
                            continue;
                        }

                        invitationId = doc.getId();
                        incomingLayout.setVisibility(View.VISIBLE);
                        incomingText.setText((fromName != null ? fromName : "Neko") +
                                " vas poziva na prijateljsku partiju!");
                        invitationProgressBar.setVisibility(View.GONE);

                        autoDeclineRunnable = () -> {
                            if (!activityActive || invitationId == null) return;
                            db.collection(INVITATIONS_COLLECTION).document(invitationId)
                                    .update("status", "expired");
                            incomingLayout.setVisibility(View.GONE);
                            invitationStatusText.setText("Poziv je istekao.");
                        };
                        autoDeclineHandler.postDelayed(autoDeclineRunnable, AUTO_DECLINE_DELAY_MS);

                        if (invitationListener != null) {
                            invitationListener.remove();
                        }
                        listenToSingleInvitation();
                        break;
                    }
                });
    }

    private void listenToSingleInvitation() {
        if (invitationId == null) return;

        DocumentReference invRef = db.collection(INVITATIONS_COLLECTION).document(invitationId);
        invitationListener = invRef.addSnapshotListener((snapshot, error) -> {
            if (!activityActive || snapshot == null || error != null) return;

            String status = snapshot.getString("status");
            if (status == null) return;

            if ("expired".equals(status)) {
                autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
                incomingLayout.setVisibility(View.GONE);
                invitationStatusText.setText("Poziv je istekao.");
                invitationProgressBar.setVisibility(View.GONE);
            } else if ("cancelled".equals(status)) {
                autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
                incomingLayout.setVisibility(View.GONE);
                invitationStatusText.setText("Poziv je otkazan.");
                invitationProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private void acceptInvitation() {
        if (invitationId == null) return;

        autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
        incomingLayout.setVisibility(View.GONE);
        invitationStatusText.setText("Prihvatam poziv...");
        invitationProgressBar.setVisibility(View.VISIBLE);

        GameSessionManager sessionManager = new GameSessionManager();
        sessionManager.createMatch(myPlayerId,
                        myPlayerName != null ? myPlayerName : "Nepoznat",
                        myAvatarUrl != null ? myAvatarUrl : "")
                .addOnSuccessListener(matchId -> {
                    if (!activityActive) return;

                    Map<String, Object> matchUpdates = new HashMap<>();
                    matchUpdates.put("isFriendMatch", true);
                    db.collection("matches").document(matchId)
                            .update(matchUpdates);

                    Map<String, Object> invUpdates = new HashMap<>();
                    invUpdates.put("status", "accepted");
                    invUpdates.put("matchId", matchId);
                    db.collection(INVITATIONS_COLLECTION).document(invitationId)
                            .update(invUpdates);

                    startFriendMatch(matchId, 1);
                })
                .addOnFailureListener(e -> {
                    if (!activityActive) return;
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    cleanupAndFinish();
                });
    }

    private void declineInvitation() {
        if (invitationId == null) return;

        autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
        db.collection(INVITATIONS_COLLECTION).document(invitationId)
                .update("status", "declined");

        incomingLayout.setVisibility(View.GONE);
        invitationStatusText.setText("Poziv odbijen.");
        invitationProgressBar.setVisibility(View.GONE);
        removeListener();
    }

    private void startFriendMatch(String matchId, int playerNumber) {
        autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
        removeListener();

        Intent intent = new Intent(this, NetworkMatchActivity.class);
        intent.putExtra("matchId", matchId);
        intent.putExtra("myPlayerNumber", playerNumber);
        intent.putExtra("myPlayerId", myPlayerId);
        intent.putExtra("myPlayerName", myPlayerName != null ? myPlayerName : "Nepoznat");
        intent.putExtra("myAvatarUrl", myAvatarUrl != null ? myAvatarUrl : "");
        intent.putExtra("isFriendMatch", true);
        startActivity(intent);
        finish();
    }

    private void removeListener() {
        if (invitationListener != null) {
            invitationListener.remove();
            invitationListener = null;
        }
    }

    private void cleanupAndFinish() {
        removeListener();
        autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activityActive = false;
        removeListener();
        autoDeclineHandler.removeCallbacks(autoDeclineRunnable);
    }
}
