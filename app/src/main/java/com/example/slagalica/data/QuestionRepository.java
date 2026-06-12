package com.example.slagalica.data;

import com.example.slagalica.Question;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class QuestionRepository {

    private static final String QUESTIONS_COLLECTION = "koznazna_questions";
    private static final int QUESTIONS_PER_GAME = 5;

    private final FirebaseFirestore db;

    public QuestionRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public Task<List<Question>> getRandomQuestions() {
        return db.collection(QUESTIONS_COLLECTION)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw task.getException();
                    }
                    List<Question> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String questionText = doc.getString("questionText");
                        List<String> answers = (List<String>) doc.get("answers");
                        Long correctIdx = doc.getLong("correctAnswerIndex");
                        if (questionText != null && answers != null && correctIdx != null) {
                            all.add(new Question(questionText, answers, correctIdx.intValue()));
                        }
                    }
                    if (all.size() <= QUESTIONS_PER_GAME) {
                        return all;
                    }
                    Collections.shuffle(all);
                    return all.subList(0, QUESTIONS_PER_GAME);
                });
    }
}
