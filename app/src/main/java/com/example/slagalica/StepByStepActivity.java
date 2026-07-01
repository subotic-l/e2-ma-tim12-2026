package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.List;

public class StepByStepActivity extends AppCompatActivity {

    private StepByStepGame game;
    private TextView pointsTextView;
    private TextView timerTextView;
    private TextView[] clueViews;
    private TextInputEditText guessInput;
    private MaterialButton submitButton;

    private CountDownTimer timer;
    private int currentStep = 0;
    private int currentPoints = 20;
    private static final int STEP_TIME_MS = 10000;
    private int score = 0;
    private boolean resultSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_step_by_step);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        MatchUiHelper.bindHeader(this, "Korak po korak", 0);

        pointsTextView = findViewById(R.id.pointsTextView);
        timerTextView = findViewById(R.id.timerTextView);
        guessInput = findViewById(R.id.guessInput);
        submitButton = findViewById(R.id.submitGuessButton);

        clueViews = new TextView[] {
                findViewById(R.id.clue1),
                findViewById(R.id.clue2),
                findViewById(R.id.clue3),
                findViewById(R.id.clue4),
                findViewById(R.id.clue5),
                findViewById(R.id.clue6),
                findViewById(R.id.clue7)
        };

        loadGame();
        resetBoard();
        revealCurrentClue();
        startTimer();

        submitButton.setOnClickListener(v -> checkGuess());
    }

    private void loadGame() {
        List<String> clues = Arrays.asList(
                "Najpoznatiji srpski naučnik",
                "Rođen u Smiljanu",
                "Radio u SAD",
                "Poznat po izmeničnoj struji",
                "Ima jedinicu mere po njemu",
                "Ime mu je Nikola",
                "Prezime mu je Tesla"
        );
        game = new StepByStepGame(clues, "Tesla");
    }

    private void resetBoard() {
        currentStep = 0;
        currentPoints = 20;
        updatePoints();
        for (TextView clueView : clueViews) {
            clueView.setText("(zatvoreno)");
        }
    }

    private void updatePoints() {
        pointsTextView.setText("Bodovi: " + currentPoints);
    }

    private void revealCurrentClue() {
        if (currentStep < game.maxSteps()) {
            clueViews[currentStep].setText(game.clues.get(currentStep));
        }
    }

    private void revealAllClues() {
        for (int i = 0; i < clueViews.length && i < game.maxSteps(); i++) {
            clueViews[i].setText(game.clues.get(i));
        }
    }

    private void startTimer() {
        if (timer != null) timer.cancel();

        timer = new CountDownTimer(STEP_TIME_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000) + 1;
                timerTextView.setText("Vreme: " + seconds);
            }

            @Override
            public void onFinish() {
                timerTextView.setText("Vreme: 0");
                moveToNextStepOrFinish();
            }
        }.start();
    }

    private void checkGuess() {
        String guess = guessInput.getText() != null ? guessInput.getText().toString().trim() : "";
        if (guess.equalsIgnoreCase(game.answer)) {
            finishGameCorrect();
        } else {
            guessInput.setText("");
            moveToNextStepOrFinish();
        }
    }

    private void moveToNextStepOrFinish() {
        currentStep++;
        currentPoints = Math.max(0, currentPoints - 2);
        updatePoints();

        if (currentStep < game.maxSteps()) {
            revealCurrentClue();
            startTimer();
        } else {
            finishGameFail();
        }
    }

    private void finishGameCorrect() {
        if (timer != null) timer.cancel();
        submitButton.setEnabled(false);
        revealAllClues();
        guessInput.setText(game.answer);
        timerTextView.setText("Tačno!");
        score = currentPoints;
        MatchUiHelper.updateScore(this, score);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finishWithScore, 2000);
    }

    private void finishGameFail() {
        if (timer != null) timer.cancel();
        submitButton.setEnabled(false);
        revealAllClues();
        guessInput.setText(game.answer);
        timerTextView.setText("Kraj");
        score = 0;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finishWithScore, 2000);
    }

    private void finishWithScore() {
        if (resultSent) {
            finish();
            return;
        }
        resultSent = true;
        Intent data = new Intent();
        data.putExtra(MatchConstants.EXTRA_GAME_SCORE, score);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void finish() {
        if (!resultSent) {
            Intent data = new Intent();
            data.putExtra(MatchConstants.EXTRA_GAME_SCORE, score);
            setResult(RESULT_OK, data);
            resultSent = true;
        }
        super.finish();
    }
}
