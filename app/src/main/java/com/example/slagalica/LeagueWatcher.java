package com.example.slagalica;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public final class LeagueWatcher {

    private static final String TAG = "LeagueWatcher";

    private static ListenerRegistration registration;
    private static long lastStars = -1;
    private static long lastLeague = -1;

    public static void startWatching(final Context context) {
        stopWatching();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        final DocumentReference userRef = FirebaseFirestore.getInstance()
                .collection("users").document(uid);

        registration = userRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen error", error);
                return;
            }
            if (snapshot == null || !snapshot.exists()) return;

            Long stars = snapshot.getLong("stars");
            Long currentLeague = snapshot.getLong("league");
            if (stars == null) stars = 0L;
            if (currentLeague == null) currentLeague = 0L;

            // Skip first fire – record baseline
            if (lastStars == -1) {
                lastStars = stars;
                lastLeague = currentLeague;
                return;
            }

            // Only act when stars actually changed
            if (stars == lastStars) return;
            lastStars = stars;

            long correctLeague = LeagueHelper.getLeagueIndex(stars);
            if (correctLeague != currentLeague) {
                userRef.update("league", correctLeague)
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update league", e));

                if (lastLeague != -1 && lastLeague != correctLeague) {
                    onLeagueChanged(context, (int) lastLeague, (int) correctLeague);
                }
                lastLeague = correctLeague;
            }
        });
    }

    public static void stopWatching() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        lastStars = -1;
        lastLeague = -1;
    }

    private static void onLeagueChanged(Context context, int oldLeague, int newLeague) {
        String oldName = LeagueHelper.getLeagueNameByIndex(oldLeague);
        String newName = LeagueHelper.getLeagueNameByIndex(newLeague);
        boolean promoted = newLeague > oldLeague;

        String title = promoted ? "Napredovanje!" : "Pad lige";
        String message = promoted
                ? "Napredovali ste iz lige \"" + oldName + "\" u ligu \"" + newName + "\"!"
                : "Pali ste iz lige \"" + oldName + "\" u ligu \"" + newName + "\".";

        NotificationHelper.show(context, SlagalicaApp.CHANNEL_GENERAL,
                title, message, null);

        Log.i(TAG, "League changed: " + oldName + " -> " + newName);
    }
}
