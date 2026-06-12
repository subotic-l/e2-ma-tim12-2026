package com.example.slagalica.data;

import com.example.slagalica.StepByStepGame;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StepByStepRepository {

    private static final String COLLECTION = "step_by_step_games";

    private final FirebaseFirestore db;

    public StepByStepRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public Task<StepByStepGame> getRandomGame() {
        return db.collection(COLLECTION)
                .get()
                .continueWith(task -> {

                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw task.getException();
                    }

                    List<StepByStepGame> games = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : task.getResult()) {

                        String answer = doc.getString("answer");
                        List<String> clues = (List<String>) doc.get("clues");

                        if (answer != null && clues != null) {
                            games.add(new StepByStepGame(clues,answer));
                        }
                    }

                    if (games.isEmpty()) {
                        throw new IllegalStateException("No StepByStep games in Firestore");
                    }

                    Collections.shuffle(games);
                    return games.get(0);
                });
    }

    public Task<List<StepByStepGame>> getTwoRandomGames() {
        return db.collection(COLLECTION)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw task.getException();
                    }

                    List<StepByStepGame> games = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String answer = doc.getString("answer");
                        List<String> clues = (List<String>) doc.get("clues");

                        if (answer != null && clues != null) {
                            games.add(new StepByStepGame(clues, answer));
                        }
                    }

                    if (games.size() < 2) {
                        throw new IllegalStateException("Need at least 2 StepByStep games in Firestore");
                    }

                    Collections.shuffle(games);
                    return games.subList(0, 2);
                });
    }
}