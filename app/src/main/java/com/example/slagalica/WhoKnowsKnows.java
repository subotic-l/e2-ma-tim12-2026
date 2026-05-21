package com.example.slagalica;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WhoKnowsKnows extends AppCompatActivity {

    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private CountDownTimer timer;
    private int timeRemaining = 5;
    private TextView timerTextView;
    private TextView questionTextView;
    private MaterialButton[] answerButtons;
    private int score = 0;
    private boolean resultSent = false;
    private int basePlayerOneScore = 0;
    private int basePlayerTwoScore = 0;
    private TextView playerOneScoreView;
    private TextView playerTwoScoreView;

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
        MatchUiHelper.bindPlayerHeader(this, getIntent());

        timerTextView = findViewById(R.id.timerTextView);
        questionTextView = findViewById(R.id.questionTextView);
        playerOneScoreView = findViewById(R.id.playerOneScore);
        playerTwoScoreView = findViewById(R.id.playerTwoScore);
        Intent intent = getIntent();
        if (intent != null) {
            basePlayerOneScore = intent.getIntExtra(MatchConstants.EXTRA_PLAYER_ONE_SCORE, 0);
            basePlayerTwoScore = intent.getIntExtra(MatchConstants.EXTRA_PLAYER_TWO_SCORE, 0);
        }
        updateScoreHeader();
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

        loadQuestions();
        displayQuestion();
        startTimer();
    }

    private void loadQuestions() {
        questions = new ArrayList<>();
        questions.add(new Question(
                "U kom veku je vođena bitka kod Vučijeg dola?",
                Arrays.asList("X", "XVI", "XVII", "XIX"), 3));
        questions.add(new Question(
                "Pored kog grada se nalazi aerodrom 'Marko Polo'?",
                Arrays.asList("Venecija", "Skoplje", "Atina", "Đenova"), 0));
        questions.add(new Question(
                "Na kojem instrumentu je svirao Džon Koltrejn?",
                Arrays.asList("gitara", "bubnjevi", "saksofon", "klavir"), 2));
        questions.add(new Question(
                "Šta je bedeker?",
                Arrays.asList("kolač", "vodič za turiste", "građevinska mešalica", "rekvizit u karlingu"), 1));
        questions.add(new Question(
                "Na zastavi koje države se nalazi stablo kedra?",
                Arrays.asList("Libije", "Sirije", "Izraela", "Libana"), 3));
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
        timerTextView.setText(String.valueOf(timeRemaining));
    }

    private void startTimer() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemaining = (int) (millisUntilFinished / 1000);
                timerTextView.setText(String.valueOf(timeRemaining+1));
            }

            @Override
            public void onFinish() {
                timerTextView.setText("0");
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
        timerTextView.setText("Kraj");
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
        if (playerOneScoreView != null) {
            playerOneScoreView.setText(String.valueOf(basePlayerOneScore + score));
        }
        if (playerTwoScoreView != null) {
            playerTwoScoreView.setText(String.valueOf(basePlayerTwoScore));
        }
    }
}
