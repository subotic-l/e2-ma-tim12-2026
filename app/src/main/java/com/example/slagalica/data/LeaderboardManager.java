package com.example.slagalica.data;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.example.slagalica.Region;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LeaderboardManager {

    private static final String TAG = "LeaderboardManager";
    private static final String LEADERBOARDS_COLLECTION = "leaderboards";
    private static final String SCORES_SUBCOLLECTION = "scores";
    private static final String METADATA_DOC = "metadata";

    private final FirebaseFirestore db;

    public LeaderboardManager() {
        this.db = FirebaseFirestore.getInstance();
    }

    public enum Period { WEEKLY, MONTHLY }

    public static String getCycleId(Period period) {
        Calendar cal = Calendar.getInstance();
        if (period == Period.WEEKLY) {
            int weekOfYear = cal.get(Calendar.WEEK_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            return String.format(Locale.US, "weekly_%d_W%02d", year, weekOfYear);
        } else {
            return new SimpleDateFormat("'monthly'_yyyy_MM", Locale.US).format(new Date());
        }
    }

    public static String getCycleIdForDate(Period period, Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        if (period == Period.WEEKLY) {
            int weekOfYear = cal.get(Calendar.WEEK_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            return String.format(Locale.US, "weekly_%d_W%02d", year, weekOfYear);
        } else {
            return new SimpleDateFormat("'monthly'_yyyy_MM", Locale.US).format(date);
        }
    }

    public static String getCycleDateRange(String cycleId) {
        try {
            String[] parts = cycleId.split("_");
            Calendar cal = Calendar.getInstance();
            if (cycleId.startsWith("weekly")) {
                int year = Integer.parseInt(parts[1]);
                int week = Integer.parseInt(parts[2].substring(1));
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.WEEK_OF_YEAR, week);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                Date start = cal.getTime();
                cal.add(Calendar.DAY_OF_WEEK, 6);
                Date end = cal.getTime();
                SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.", Locale.forLanguageTag("sr"));
                return fmt.format(start) + " - " + fmt.format(end);
            } else {
                int year = Integer.parseInt(parts[1]);
                int month = Integer.parseInt(parts[2]);
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month - 1);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                Date start = cal.getTime();
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                Date end = cal.getTime();
                SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy.", Locale.forLanguageTag("sr"));
                return fmt.format(start) + " - " + fmt.format(end);
            }
        } catch (Exception e) {
            return cycleId;
        }
    }

    public interface LeaderboardEntry {
        String getUserId();
        int getStars();
        String getUserName();
        String getAvatarUrl();
        int getGamesPlayed();
        int getLeague();
    }

    public Task<Void> updateScore(String uid, String userName, String avatarUrl, int starsGained, int league) {

        List<Task<Void>> tasks = new ArrayList<>();
        for (Period period : Period.values()) {
            tasks.add(updateScoreForCycle(getCycleId(period), uid, userName, avatarUrl, starsGained, league));
        }
        return Tasks.whenAll(tasks);
    }

    private Task<Void> updateScoreForCycle(String cycleId, String uid,
                                            String userName, String avatarUrl, int starsGained, int league) {
        DocumentReference scoreRef = db.collection(LEADERBOARDS_COLLECTION)
                .document(cycleId)
                .collection(SCORES_SUBCOLLECTION)
                .document(uid);

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(scoreRef);
            Map<String, Object> updates = new HashMap<>();
            updates.put("userName", userName);
            updates.put("avatarUrl", avatarUrl != null ? avatarUrl : "");
            updates.put("league", league);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            if (snap.exists()) {
                Long gamesPlayed = snap.getLong("gamesPlayed");
                updates.put("gamesPlayed", (gamesPlayed != null ? gamesPlayed : 0) + 1);
                transaction.update(scoreRef, updates);
                transaction.update(scoreRef, "stars", FieldValue.increment(starsGained));
            } else {
                updates.put("stars", starsGained);
                updates.put("gamesPlayed", 1);
                transaction.set(scoreRef, updates);
            }
            return null;
        });
    }

    public Task<List<LeaderboardEntry>> getTopPlayers(Period period, int limit) {
        String cycleId = getCycleId(period);
        return db.collection(LEADERBOARDS_COLLECTION)
                .document(cycleId)
                .collection(SCORES_SUBCOLLECTION)
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .continueWith(task -> {
                    List<LeaderboardEntry> result = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Map<String, Object> d = doc.getData();
                            if (d == null) continue;
                            long s = d.containsKey("stars") ? (long) d.get("stars") : 0;
                            String name = (String) d.get("userName");
                            String avatar = (String) d.get("avatarUrl");
                            long gp = d.containsKey("gamesPlayed") ? (long) d.get("gamesPlayed") : 0;
                            long league = d.containsKey("league") ? (long) d.get("league") : 0;
                            String uid = doc.getId();
                            result.add(new LeaderboardEntry() {
                                @Override public String getUserId() { return uid; }
                                @Override public int getStars() { return (int) s; }
                                @Override public String getUserName() { return name; }
                                @Override public String getAvatarUrl() { return avatar; }
                                @Override public int getGamesPlayed() { return (int) gp; }
                                @Override public int getLeague() { return (int) league; }
                            });
                        }
                    }
                    return result;
                });
    }

    public Task<LeaderboardEntry> getPlayerScore(Period period, String uid) {
        String cycleId = getCycleId(period);
        return db.collection(LEADERBOARDS_COLLECTION)
                .document(cycleId)
                .collection(SCORES_SUBCOLLECTION)
                .document(uid)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        DocumentSnapshot doc = task.getResult();
                        Map<String, Object> d = doc.getData();
                        long s = d != null && d.containsKey("stars") ? (long) d.get("stars") : 0;
                        String name = d != null ? (String) d.get("userName") : null;
                        String avatar = d != null ? (String) d.get("avatarUrl") : null;
                        long gp = d != null && d.containsKey("gamesPlayed") ? (long) d.get("gamesPlayed") : 0;
                        long league = d != null && d.containsKey("league") ? (long) d.get("league") : 0;
                        String uid2 = doc.getId();
                        return (LeaderboardEntry) new LeaderboardEntry() {
                            @Override public String getUserId() { return uid2; }
                            @Override public int getStars() { return (int) s; }
                            @Override public String getUserName() { return name; }
                            @Override public String getAvatarUrl() { return avatar; }
                            @Override public int getGamesPlayed() { return (int) gp; }
                            @Override public int getLeague() { return (int) league; }
                        };
                    }
                    return null;
                });
    }

    public Task<Integer> getPlayerRank(Period period, String uid) {
        String cycleId = getCycleId(period);
        return db.collection(LEADERBOARDS_COLLECTION)
                .document(cycleId)
                .collection(SCORES_SUBCOLLECTION)
                .orderBy("stars", Query.Direction.DESCENDING)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return -1;
                    List<DocumentSnapshot> docs = task.getResult().getDocuments();
                    for (int i = 0; i < docs.size(); i++) {
                        if (docs.get(i).getId().equals(uid)) return i + 1;
                    }
                    return -1;
                });
    }

    public static boolean hasCycleEnded(String oldCycleId) {
        String currentWeekly = getCycleId(Period.WEEKLY);
        String currentMonthly = getCycleId(Period.MONTHLY);
        return (oldCycleId.startsWith("weekly") && !oldCycleId.equals(currentWeekly))
                || (oldCycleId.startsWith("monthly") && !oldCycleId.equals(currentMonthly));
    }

    public Task<Boolean> tryDistributeRewards(String oldCycleId) {
        DocumentReference metadataRef = db.collection(LEADERBOARDS_COLLECTION)
                .document(oldCycleId);

        // First check if already distributed (outside transaction to keep it fast)
        return metadataRef.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                return Tasks.forResult(false);
            }
            DocumentSnapshot meta = task.getResult();
            Boolean distributed = meta.getBoolean("distributed");
            if (distributed != null && distributed) return Tasks.forResult(false);

            boolean isMonthly = oldCycleId.startsWith("monthly");

            // Read top 10 outside transaction
            return db.collection(LEADERBOARDS_COLLECTION)
                    .document(oldCycleId)
                    .collection(SCORES_SUBCOLLECTION)
                    .orderBy("stars", Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .continueWithTask(queryTask -> {
                        if (!queryTask.isSuccessful() || queryTask.getResult() == null) {
                            return Tasks.forResult(false);
                        }
                        List<DocumentSnapshot> topDocs = queryTask.getResult().getDocuments();

                        boolean isWeekly = oldCycleId.startsWith("weekly");
                        int[] rewards = isWeekly
                                ? new int[]{5, 3, 2, 1, 1, 1, 1, 1, 1, 1}
                                : new int[]{10, 6, 4, 2, 2, 2, 2, 2, 2, 2};

                        // Transaction: set distributed + update tokens
                        final List<Map<String, Object>>[] winnersRef = new List[]{null};
                        return db.runTransaction((Transaction.Function<Boolean>) t -> {
                            DocumentSnapshot metaCheck = t.get(metadataRef);
                            if (Boolean.TRUE.equals(metaCheck.getBoolean("distributed"))) {
                                return false;
                            }

                            List<Map<String, Object>> winners = new ArrayList<>();
                            for (int i = 0; i < topDocs.size() && i < 10; i++) {
                                DocumentSnapshot doc = topDocs.get(i);
                                String winnerUid = doc.getId();
                                int tokenReward = rewards[i];
                                DocumentReference userRef = db.collection("users").document(winnerUid);
                                t.update(userRef, "tokens", FieldValue.increment(tokenReward));

                                Map<String, Object> w = new HashMap<>();
                                w.put("uid", winnerUid);
                                w.put("rank", i + 1);
                                w.put("tokenReward", tokenReward);
                                w.put("stars", doc.getLong("stars"));
                                winners.add(w);
                            }

                            Map<String, Object> metaUpdate = new HashMap<>();
                            metaUpdate.put("distributed", true);
                            metaUpdate.put("distributedAt", FieldValue.serverTimestamp());
                            metaUpdate.put("winners", winners);
                            t.set(metadataRef, metaUpdate);
                            winnersRef[0] = winners;
                            return true;
                        }).continueWithTask(txTask -> {
                            if (!txTask.isSuccessful() || !Boolean.TRUE.equals(txTask.getResult())) {
                                return Tasks.forResult(false);
                            }

                            // For monthly cycles: apply 30% star penalty + reset region data
                            if (isMonthly) {
                                applyMonthlyPenalty(oldCycleId, winnersRef[0]);
                                resetMonthlyRegionData(oldCycleId, true);
                            }
                            return Tasks.forResult(true);
                        });
                    });
        });
    }

    private void applyMonthlyPenalty(String cycleId, List<Map<String, Object>> winners) {
        // Collect UIDs of users who played at least one match (have a score doc in this cycle)
        db.collection(LEADERBOARDS_COLLECTION)
                .document(cycleId)
                .collection(SCORES_SUBCOLLECTION)
                .get()
                .addOnSuccessListener(snapshots -> {
                    Set<String> playedUserIds = new HashSet<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        playedUserIds.add(doc.getId());
                    }

                    // Query all users and apply 30% penalty to those who didn't play
                    db.collection("users")
                            .get()
                            .addOnSuccessListener(userDocs -> {
                                for (DocumentSnapshot userDoc : userDocs.getDocuments()) {
                                    String uid = userDoc.getId();
                                    if (playedUserIds.contains(uid)) continue;

                                    Long currentStars = userDoc.getLong("stars");
                                    if (currentStars == null || currentStars <= 0) continue;
                                    long reduction = Math.max(1, (long) (currentStars * 0.3));
                                    db.collection("users").document(uid)
                                            .update("stars", FieldValue.increment(-reduction));
                                }
                            });
                });
    }

    public void checkAndResetRegionData() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        String prevMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
        String prevCycleId = "monthly_" + prevMonth.replace("-", "_");

        // Check if region rankings for previous month already saved
        db.collection("region_rankings").document(prevMonth + "_BEOGRAD").get()
                .addOnSuccessListener(rankingDoc -> {
                    if (rankingDoc.exists()) return;
                    resetMonthlyRegionData(prevCycleId, false);
                });
    }

    private void resetMonthlyRegionData(String oldCycleId, boolean resetStars) {
        // Parse month: "monthly_2026_06" -> "2026-06"
        String oldMonth = oldCycleId.replace("monthly_", "").replace("_", "-");
        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());

        db.collection("users").get().addOnSuccessListener(userDocs -> {
            Map<String, Long> regionStars = new HashMap<>();
            Map<String, String> uidToCode = new HashMap<>();

            for (DocumentSnapshot userDoc : userDocs.getDocuments()) {
                String regionName = userDoc.getString("region");
                if (regionName == null) continue;
                String code = RegionRepository.getNameToCode(regionName);
                if (code == null) continue;
                uidToCode.put(userDoc.getId(), code);
                Long ms = userDoc.getLong("monthlyStars");
                regionStars.merge(code, ms != null ? ms : 0L, Long::sum);
            }

            // Sort by stars descending
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(regionStars.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            Map<String, Long> regionRanks = new HashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                regionRanks.put(sorted.get(i).getKey(), (long) (i + 1));
            }

            WriteBatch batch = db.batch();

            // Save region rankings
            for (int i = 0; i < sorted.size(); i++) {
                String code = sorted.get(i).getKey();
                Region r = RegionRepository.get(code);
                String regionName = r != null ? r.getName() : code;
                Map<String, Object> data = new HashMap<>();
                data.put("regionCode", code);
                data.put("regionName", regionName);
                data.put("rank", (long) (i + 1));
                data.put("monthlyStars", sorted.get(i).getValue());
                data.put("month", oldMonth);
                batch.set(db.collection("region_rankings").document(oldMonth + "_" + code), data);
            }

            // Update each user: lastMonthRegionRank, optionally reset monthlyStars
            for (DocumentSnapshot userDoc : userDocs.getDocuments()) {
                String uid = userDoc.getId();
                String code = uidToCode.get(uid);
                long regionRank = code != null ? regionRanks.getOrDefault(code, 0L) : 0L;
                DocumentReference ref = db.collection("users").document(uid);
                batch.update(ref, "lastMonthRegionRank", regionRank);
                if (resetStars) {
                    batch.update(ref, "monthlyStars", 0L);
                    batch.update(ref, "lastMonthlyReset", currentMonth);
                }
            }

            batch.commit().addOnFailureListener(e ->
                    Log.e(TAG, "Failed to reset monthly region data", e));
        }).addOnFailureListener(e ->
                Log.e(TAG, "Failed to load users for region reset", e));
    }

    public Task<Map<String, Object>> getCycleMetadata(String cycleId) {
        return db.collection(LEADERBOARDS_COLLECTION)
                .document(cycleId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        return task.getResult().getData();
                    }
                    return null;
                });
    }
}
