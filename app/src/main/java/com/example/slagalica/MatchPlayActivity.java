package com.example.slagalica;

import android.app.AlertDialog;
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

import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.List;

public class MatchPlayActivity extends AppCompatActivity {

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
    private boolean finished = false;

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

        MaterialButton quitButton = findViewById(R.id.quitMatchButton);
        if (quitButton != null) {
            quitButton.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Napusti partiju")
                        .setMessage("Da li ste sigurni da želite da napustite partiju? Gubite partiju i ne dobijate zvezde.")
                        .setPositiveButton("Napusti", (dialog, which) -> forfeitMatch())
                        .setNegativeButton("Nastavi", null)
                        .show();
            });
        }

        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (finished) return;
                    if (result.getResultCode() == RESULT_CANCELED) {
                        finished = true;
                        Intent homeIntent = new Intent(this, MainActivity.class);
                        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(homeIntent);
                        finish();
                        return;
                    }
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

    private void forfeitMatch() {
        finished = true;
        Toast.makeText(this, "Napustili ste partiju!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MatchSummaryActivity.class);
        intent.putExtra(MatchConstants.EXTRA_GAME_SCORE, totalScore);
        intent.putExtra("forfeit", true);
        startActivity(intent);
        finish();
    }

    private void startNextGame() {
        if (finished || currentGameIndex >= gameOrder.size()) {
            showSummary();
            return;
        }

        Intent intent = new Intent(this, gameOrder.get(currentGameIndex));
        intent.putExtra("playerName", getIntent().getStringExtra("playerName"));
        intent.putExtra("totalScore", totalScore);
        gameLauncher.launch(intent);
    }

    private void showSummary() {
        if (finished) return;
        Intent intent = new Intent(this, MatchSummaryActivity.class);
        intent.putExtra(MatchConstants.EXTRA_GAME_SCORE, totalScore);
        startActivity(intent);
        finish();
    }
}
