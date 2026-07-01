package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.data.ChallengeManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

public class ChallengePlayActivity extends AppCompatActivity {

    private final List<Class<?>> gameOrder = Arrays.asList(
            WhoKnowsKnows.class,
            MatchingGameActivity.class,
            AsocijacijeGameActivity.class,
            SkockoGameActivity.class,
            StepByStepActivity.class,
            NumbersGameActivity.class
    );

    private int currentGameIndex = 0;
    private int totalScore = 0;
    private String challengeId;
    private ChallengeManager challengeManager;
    private FirebaseUser user;

    private ActivityResultLauncher<Intent> gameLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_match_play);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        challengeManager = new ChallengeManager();
        user = FirebaseAuth.getInstance().getCurrentUser();
        challengeId = getIntent().getStringExtra("challenge_id");

        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    int score = 0;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        score = result.getData().getIntExtra(MatchConstants.EXTRA_GAME_SCORE, 0);
                    }
                    totalScore += score;
                    currentGameIndex++;
                    startNextGame();
                }
        );

        startNextGame();
    }

    private void startNextGame() {
        if (currentGameIndex >= gameOrder.size()) {
            submitResult();
            return;
        }

        Intent intent = new Intent(this, gameOrder.get(currentGameIndex));
        gameLauncher.launch(intent);
    }

    private void submitResult() {
        if (user == null || challengeId == null) {
            Toast.makeText(this, "Greška pri slanju rezultata", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        challengeManager.attachToChallenge(challengeId);
        challengeManager.submitScore(user.getUid(), totalScore)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Rezultat poslat! Ukupno: " + totalScore, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(task -> {
                    Intent intent = new Intent(this, MatchSummaryActivity.class);
                    intent.putExtra(MatchConstants.EXTRA_GAME_SCORE, totalScore);
                    startActivity(intent);
                    finish();
                });
    }
}
