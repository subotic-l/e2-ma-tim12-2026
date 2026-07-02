package com.example.slagalica;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.data.QuestionRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class WhoKnowsKnows extends AppCompatActivity {

    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private CountDownTimer timer;
    private int timeRemaining = 5;
    private TextView timerText;
    private TextView questionTextView;
    private MaterialButton[] answerButtons;
    private int score = 0;
    private boolean resultSent = false;
    private QuestionRepository questionRepository;
    private TextView playerNameView, playerScoreView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_who_knows_knows);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        timerText = findViewById(R.id.timerText);
        questionTextView = findViewById(R.id.questionTextView);
        playerNameView = findViewById(R.id.playerOneName);
        playerScoreView = findViewById(R.id.playerOneScore);
        String pn = getIntent().getStringExtra("playerName");
        playerNameView.setText(pn != null ? pn : "Ko zna zna");
        playerScoreView.setText("0");
        answerButtons = new MaterialButton[] {
                findViewById(R.id.answerButton1),
                findViewById(R.id.answerButton2),
                findViewById(R.id.answerButton3),
                findViewById(R.id.answerButton4)
        };

        for (int i = 0; i < answerButtons.length; i++) {
            final int index = i;
            answerButtons[i].setOnClickListener(v -> onAnswerSelected(index));
        }

        questionRepository = new QuestionRepository();
        loadQuestionsFromFirestore();

        setupQuitButton();
    }

    private void setupQuitButton() {
        ImageButton quitBtn = findViewById(R.id.quitGameButton);
        if (quitBtn != null) {
            quitBtn.setVisibility(View.VISIBLE);
            quitBtn.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle("Napusti igru")
                    .setMessage("Da li ste sigurni?")
                    .setPositiveButton("Napusti", (d, w) -> {
                        resultSent = true;
                        finish();
                    })
                    .setNegativeButton("Nastavi", null)
                    .show());
        }
    }

    private void loadQuestionsFromFirestore() {
        questionTextView.setText("Učitavanje pitanja...");
        disableAnswerButtons();
        questionRepository.getRandomQuestions()
                .addOnSuccessListener(loaded -> {
                    questions = loaded;
                    if (questions.isEmpty()) {
                        questionTextView.setText("Greška: nema pitanja u bazi");
                        return;
                    }
                    displayQuestion();
                    startTimer();
                })
                .addOnFailureListener(e -> {
                    questionTextView.setText("Greška pri učitavanju pitanja");
                });
    }

    private void displayQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }

        Question current = questions.get(currentQuestionIndex);
        int questionNumber = currentQuestionIndex + 1;
        String numberedQuestion = questionNumber + ". " + current.questionText;
        questionTextView.setText(numberedQuestion);

        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(current.answers.get(i));
            answerButtons[i].setEnabled(true);
            answerButtons[i].setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.button_default_color));
                answerButtons[i].setStrokeColor(
                    ContextCompat.getColorStateList(this, R.color.button_default_border));
        }

        timeRemaining = 5;
        timerText.setText(String.valueOf(timeRemaining));
    }

    private void startTimer() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemaining = (int) (millisUntilFinished / 1000);
                timerText.setText(String.valueOf(timeRemaining+1));
            }

            @Override
            public void onFinish() {
                timerText.setText("0");
                showCorrectAnswerOnTimeUp();
                new Handler(Looper.getMainLooper()).postDelayed(() -> nextQuestion(), 2000);
            }
        }.start();
    }

    private void onAnswerSelected(int selectedIndex) {
        if (timer != null) {
            timer.cancel();
        }
        disableAnswerButtons();
        checkAnswer(selectedIndex);

        new Handler(Looper.getMainLooper()).postDelayed(this::nextQuestion, 2000);
    }

    private void checkAnswer(int selectedIndex) {
        Question currentQuestion = questions.get(currentQuestionIndex);
        int correctIndex = currentQuestion.correctAnswerIndex;

        int correctBg = ContextCompat.getColor(this, R.color.correct_answer_color);
        int correctBorder = ContextCompat.getColor(this, R.color.correct_answer_border);
        int wrongBg = ContextCompat.getColor(this, R.color.wrong_answer_color);
        int wrongBorder = ContextCompat.getColor(this, R.color.wrong_answer_border);

        if (selectedIndex == correctIndex) {
            score += 10;
            answerButtons[selectedIndex].setBackgroundTintList(
                    ColorStateList.valueOf(correctBg));
            answerButtons[selectedIndex].setStrokeColor(
                    ColorStateList.valueOf(correctBorder));
        } else {
            score -= 5;
            answerButtons[selectedIndex].setBackgroundTintList(
                    ColorStateList.valueOf(wrongBg));
            answerButtons[selectedIndex].setStrokeColor(
                    ColorStateList.valueOf(wrongBorder));

            answerButtons[correctIndex].setBackgroundTintList(
                    ColorStateList.valueOf(correctBg));
            answerButtons[correctIndex].setStrokeColor(
                    ColorStateList.valueOf(correctBorder));
        }
        updateScoreHeader();
    }

    private void showCorrectAnswerOnTimeUp() {
        disableAnswerButtons();

        int correctIndex = questions.get(currentQuestionIndex).correctAnswerIndex;
        answerButtons[correctIndex].setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.correct_answer_color)));
        answerButtons[correctIndex].setStrokeColor(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.correct_answer_border)));
    }

    private void nextQuestion() {
        currentQuestionIndex++;
        if (currentQuestionIndex < questions.size()) {
            displayQuestion();
            startTimer();
        } else {
            endGame();
        }
    }

    private void endGame() {
        if (timer != null) {
            timer.cancel();
        }
        timerText.setText("Kraj");
        disableAnswerButtons();
        questionTextView.setText("Igra je završena!");

        new Handler(Looper.getMainLooper()).postDelayed(this::finishWithScore, 2000);
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

    private void disableAnswerButtons() {
        for (Button button : answerButtons) {
            button.setEnabled(false);
        }
    }

    private void updateScoreHeader() {
        playerScoreView.setText(String.valueOf(score));
    }
}
