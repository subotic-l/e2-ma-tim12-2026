package com.example.slagalica.data;

import com.example.slagalica.MatchingGame;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpojniceRepository {

    private static final String COLLECTION = "spojnice";
    private static final int GAMES_PER_MATCH = 2;

    private final FirebaseFirestore db;

    public SpojniceRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public Task<List<MatchingGame>> getRandomGames() {
        return db.collection(COLLECTION)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw task.getException();
                    }
                    List<MatchingGame> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String instructions = doc.getString("instructions");
                        List<String> leftItems = (List<String>) doc.get("leftItems");
                        List<String> rightItems = (List<String>) doc.get("rightItems");
                        List<Long> correctRaw = (List<Long>) doc.get("correctMatches");
                        if (instructions == null || leftItems == null || rightItems == null || correctRaw == null) {
                            continue;
                        }
                        List<Integer> correctMatches = new ArrayList<>();
                        for (Long val : correctRaw) {
                            correctMatches.add(val.intValue());
                        }
                        all.add(new MatchingGame(instructions, leftItems, rightItems, correctMatches));
                    }
                    if (all.isEmpty()) {
                        return all;
                    }
                    Collections.shuffle(all);
                    int count = Math.min(GAMES_PER_MATCH, all.size());
                    if (count == 1) {
                        all.add(all.get(0));
                        return all;
                    }
                    return all.subList(0, count);
                });
    }
}
