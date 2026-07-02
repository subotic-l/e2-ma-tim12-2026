package com.example.slagalica;

import android.content.res.ColorStateList;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Arrays;

public class MatchingGameActivity extends AppCompatActivity {

    private MatchingGame matchingGame;
    private MaterialButton[] leftButtons;
    private MaterialButton[] rightButtons;
    private TextView timerTextView;
    private TextView instructionsTextView;

    private CountDownTimer timer;
    private int currentLeftIndex = 0;

    private int defaultColor;
    private int defaultBorder;
    private int selectedColor;
    private int selectedBorder;
    private int correctColor;
    private int wrongColor;
    private int correctBorder;
    private int wrongBorder;
    private int score = 0;
    private boolean resultSent = false;
    private TextView playerNameView, playerScoreView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_matching_game);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        timerTextView = findViewById(R.id.timerTextView);
        instructionsTextView = findViewById(R.id.instructionsTextView);
        playerNameView = findViewById(R.id.playerOneName);
        playerScoreView = findViewById(R.id.playerOneScore);
        playerNameView.setText("Spojnice");
        playerScoreView.setText("0");

        defaultColor = ContextCompat.getColor(this, R.color.button_default_color);
        defaultBorder = ContextCompat.getColor(this, R.color.button_default_border);
        selectedColor = ContextCompat.getColor(this, R.color.button_selected_color);
        selectedBorder = ContextCompat.getColor(this, R.color.button_selected_border);
        correctColor = ContextCompat.getColor(this, R.color.correct_answer_color);
        wrongColor = ContextCompat.getColor(this, R.color.wrong_answer_color);
        correctBorder = ContextCompat.getColor(this, R.color.correct_answer_border);
        wrongBorder = ContextCompat.getColor(this, R.color.wrong_answer_border);

        leftButtons = new MaterialButton[] {
                findViewById(R.id.leftButton1),
                findViewById(R.id.leftButton2),
                findViewById(R.id.leftButton3),
                findViewById(R.id.leftButton4),
                findViewById(R.id.leftButton5)
        };

        rightButtons = new MaterialButton[] {
                findViewById(R.id.rightButton1),
                findViewById(R.id.rightButton2),
                findViewById(R.id.rightButton3),
                findViewById(R.id.rightButton4),
                findViewById(R.id.rightButton5)
        };

        setupButtonListeners();
        loadMatchingGame();
        displayGame();
        startMatchingRound();
    }

    private void setupButtonListeners() {
        for (int i = 0; i < rightButtons.length; i++) {
            final int index = i;
            rightButtons[i].setOnClickListener(v -> onRightClicked(index));
        }
    }

    private void loadMatchingGame() {
        matchingGame = new MatchingGame(
                "Rase životnja",
                Arrays.asList(
                        "Pas", "Mačka", "Kokoška", "Govedo", "Svinja"
                ),
                Arrays.asList(
                        "Orpington", "Čau-čau", "Durok", "Adaptur", "Ragdol"
                ),
                Arrays.asList(1, 4, 0, 3, 2)
        );
    }

    private void displayGame() {
        currentLeftIndex = 0;

        instructionsTextView.setText(matchingGame.instructions);
        timerTextView.setText("30");

        for (int i = 0; i < leftButtons.length; i++) {
            leftButtons[i].setText(matchingGame.leftItems.get(i));
            resetButton(leftButtons[i]);
            leftButtons[i].setEnabled(false);
        }

        for (int i = 0; i < rightButtons.length; i++) {
            rightButtons[i].setText(matchingGame.rightItems.get(i));
            resetButton(rightButtons[i]);
            rightButtons[i].setEnabled(true);
        }
    }

    private void startMatchingRound() {
        currentLeftIndex = 0;
        highlightCurrentLeft();
        startTimer();
    }

    private void highlightCurrentLeft() {
        leftButtons[currentLeftIndex].setBackgroundTintList(ColorStateList.valueOf(selectedColor));
        leftButtons[currentLeftIndex].setStrokeColor(ColorStateList.valueOf(selectedBorder));
    }

    private void onRightClicked(int rightIndex) {
        if (currentLeftIndex >= leftButtons.length) return;

        int leftIndex = currentLeftIndex;

        if (matchingGame.isCorrectMatch(leftIndex, rightIndex)) {
            markPairCorrect(leftIndex, rightIndex);
        } else {
            markWrong(rightIndex);
        }

        moveToNextLeft();
    }

    private void moveToNextLeft() {
        currentLeftIndex++;
        if (currentLeftIndex < leftButtons.length) {
            highlightCurrentLeft();
        } else {
            finishMatchingGame();
        }
    }

    private void markPairCorrect(int leftIndex, int rightIndex) {
        score += 2;
        playerScoreView.setText(String.valueOf(score));
        leftButtons[leftIndex].setBackgroundTintList(ColorStateList.valueOf(correctColor));
        leftButtons[leftIndex].setStrokeColor(ColorStateList.valueOf(correctBorder));

        rightButtons[rightIndex].setBackgroundTintList(ColorStateList.valueOf(correctColor));
        rightButtons[rightIndex].setStrokeColor(ColorStateList.valueOf(correctBorder));
        rightButtons[rightIndex].setEnabled(false);
    }

    private void markWrong(int rightIndex) {
        leftButtons[currentLeftIndex].setBackgroundTintList(ColorStateList.valueOf(defaultColor));
        leftButtons[currentLeftIndex].setStrokeColor(ColorStateList.valueOf(defaultBorder));

        rightButtons[rightIndex].setBackgroundTintList(ColorStateList.valueOf(wrongColor));
        rightButtons[rightIndex].setStrokeColor(ColorStateList.valueOf(wrongBorder));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            resetButton(rightButtons[rightIndex]);
        }, 500);
    }

    private void resetButton(MaterialButton button) {
        button.setBackgroundTintList(ColorStateList.valueOf(defaultColor));
        button.setStrokeColor(ColorStateList.valueOf(defaultBorder));
    }

    private void startTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerTextView.setText(String.valueOf(millisUntilFinished / 1000 + 1));
            }

            @Override
            public void onFinish() {
                timerTextView.setText("0");
                finishMatchingGame();
            }
        }.start();
    }

    private void finishMatchingGame() {
        disableAllButtons();
        if (timer != null) timer.cancel();

        new Handler(Looper.getMainLooper()).postDelayed(this::showCorrectAnswers, 2000);

        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 10000);
    }

    private void disableAllButtons() {
        for (MaterialButton button : leftButtons) {
            button.setEnabled(false);
        }
        for (MaterialButton button : rightButtons) {
            button.setEnabled(false);
        }
    }

    private void showCorrectAnswers() {
        instructionsTextView.setText("Kraj! Prikaz tačnih odgovora:");
        for (int i = 0; i < leftButtons.length; i++) {
            int correctRightIndex = matchingGame.correctMatches.get(i);

            leftButtons[i].setBackgroundTintList(ColorStateList.valueOf(correctColor));
            leftButtons[i].setStrokeColor(ColorStateList.valueOf(correctBorder));

            rightButtons[i].setText(matchingGame.rightItems.get(correctRightIndex));
            rightButtons[i].setBackgroundTintList(ColorStateList.valueOf(correctColor));
            rightButtons[i].setStrokeColor(ColorStateList.valueOf(correctBorder));
            rightButtons[i].setEnabled(false);
        }
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
