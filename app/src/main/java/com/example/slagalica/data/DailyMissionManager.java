package com.example.slagalica.data;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DailyMissionManager {

    private static final String TAG = "DailyMissionManager";
    private static final int STARS_PER_MISSION = 3;
    private static final int BONUS_STARS = 3;
    private static final int BONUS_TOKENS = 2;

    public enum Mission {
        WIN_GAME, SEND_CHAT, PLAY_FRIEND, WIN_TOURNAMENT
    }

    public interface DailyMissionsData {
        boolean isWinGameDone();
        boolean isSendChatDone();
        boolean isPlayFriendDone();
        boolean isWinTournamentDone();
        boolean isAllCompletedBonusClaimed();
        String getDate();
        int getClaimedCount();
    }

    private final FirebaseFirestore db;

    public DailyMissionManager() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private DocumentReference getMissionDocRef(String uid) {
        return db.collection("users")
                .document(uid)
                .collection("dailyMissions")
                .document(getTodayDate());
    }

    public Task<DailyMissionsData> loadMissions(String uid) {
        return getMissionDocRef(uid).get().continueWith(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                DocumentSnapshot doc = task.getResult();
                Map<String, Object> d = doc.getData();
                return new DailyMissionsData() {
                    @Override public boolean isWinGameDone() { return getBool(d, "winGame"); }
                    @Override public boolean isSendChatDone() { return getBool(d, "sendChat"); }
                    @Override public boolean isPlayFriendDone() { return getBool(d, "playFriend"); }
                    @Override public boolean isWinTournamentDone() { return getBool(d, "winTournament"); }
                    @Override public boolean isAllCompletedBonusClaimed() { return getBool(d, "allCompletedBonusClaimed"); }
                    @Override public String getDate() { return (String) d.get("date"); }
                    @Override public int getClaimedCount() {
                        int c = 0;
                        if (isWinGameDone()) c++;
                        if (isSendChatDone()) c++;
                        if (isPlayFriendDone()) c++;
                        if (isWinTournamentDone()) c++;
                        return c;
                    }
                };
            }
            return null;
        });
    }

    private boolean getBool(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return false;
    }

    public Task<Void> markMissionDone(String uid, Mission mission) {
        DocumentReference ref = getMissionDocRef(uid);
        return db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            Map<String, Object> data = snap.exists() ? snap.getData() : new HashMap<>();
            if (data == null) data = new HashMap<>();

            String missionKey = getMissionKey(mission);
            if (Boolean.TRUE.equals(data.get(missionKey))) {
                return null;
            }

            data.put(missionKey, true);
            data.put("date", getTodayDate());

            int doneCount = countDone(data);
            boolean allDone = doneCount >= 4;

            if (snap.exists()) {
                transaction.update(ref, missionKey, true);
                transaction.update(ref, "date", getTodayDate());
                if (allDone) {
                    transaction.update(ref, "allCompleted", true);
                }
                transaction.update(ref, "updatedAt", Timestamp.now());
            } else {
                data.put("allCompleted", allDone);
                data.put("allCompletedBonusClaimed", false);
                data.put("createdAt", Timestamp.now());
                data.put("updatedAt", Timestamp.now());
                transaction.set(ref, data);
            }

            // Grant stars for mission
            String uid2 = ref.getParent().getParent().getId();
            DocumentReference userRef = db.collection("users").document(uid2);
            transaction.update(userRef, "stars", com.google.firebase.firestore.FieldValue.increment(STARS_PER_MISSION));
            transaction.update(userRef, "totalStarsEarned", com.google.firebase.firestore.FieldValue.increment(STARS_PER_MISSION));
            transaction.update(userRef, "monthlyStars", com.google.firebase.firestore.FieldValue.increment(STARS_PER_MISSION));

            // If all 4 done and bonus not yet claimed, grant bonus
            if (allDone && !Boolean.TRUE.equals(data.get("allCompletedBonusClaimed"))) {
                transaction.update(ref, "allCompletedBonusClaimed", true);
                transaction.update(userRef, "stars", com.google.firebase.firestore.FieldValue.increment(BONUS_STARS));
                transaction.update(userRef, "totalStarsEarned", com.google.firebase.firestore.FieldValue.increment(BONUS_STARS));
                transaction.update(userRef, "monthlyStars", com.google.firebase.firestore.FieldValue.increment(BONUS_STARS));
                transaction.update(userRef, "tokens", com.google.firebase.firestore.FieldValue.increment(BONUS_TOKENS));
            }

            return null;
        });
    }

    private String getMissionKey(Mission m) {
        switch (m) {
            case WIN_GAME: return "winGame";
            case SEND_CHAT: return "sendChat";
            case PLAY_FRIEND: return "playFriend";
            case WIN_TOURNAMENT: return "winTournament";
        }
        return "";
    }

    private int countDone(Map<String, Object> data) {
        int c = 0;
        if (Boolean.TRUE.equals(data.get("winGame"))) c++;
        if (Boolean.TRUE.equals(data.get("sendChat"))) c++;
        if (Boolean.TRUE.equals(data.get("playFriend"))) c++;
        if (Boolean.TRUE.equals(data.get("winTournament"))) c++;
        return c;
    }
}
