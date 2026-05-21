package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.graphics.Color;

public class AsocijacijeGameActivity extends AppCompatActivity {

    private static final int GROUP_POINTS = 2;
    private static final int FINAL_POINTS = 10;

    private final String[][] groups = {
            {"MAK", "NIT", "RANA", "ZUB"},
            {"BALA", "DETELINA", "SLAMA", "SUVO"},
            {"KONAC", "IGLA", "UŠI", "INSULIN"},
            {"ZID", "GLIKOGEN", "PROTEIN", "HORMON"}
    };

    private final String[] groupSolutions = {"KONAC", "SENO", "IGLA", "DIJABETES"};
    private final String finalWord = "IGLA";
    private TextView timerText;
    private int timeLeft = 180;
    private android.os.CountDownTimer countDownTimer;

    private Button[] solutionButtons = new Button[4];
    private Button[][] wordButtons = new Button[4][4];

    private Button btnFinal;

    private boolean[] solvedGroups = new boolean[4];
    private boolean[][] openedFields = new boolean[4][4];
    private int[] openedCounts = new int[4];
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private int basePlayerOneScore = 0;
    private int basePlayerTwoScore = 0;
    private int activePlayer = MatchConstants.PLAYER_ONE;
    private int nextOpenPlayer = MatchConstants.PLAYER_ONE;
    private int lastOpenedPlayer = 0;
    private TextView playerOneScoreView;
    private TextView playerTwoScoreView;
    private android.view.View playerOneContainer;
    private android.view.View playerTwoContainer;
    private boolean resultSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_asocijacije);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        MatchUiHelper.bindPlayerHeader(this, getIntent());
        Intent intent = getIntent();
        if (intent != null) {
            basePlayerOneScore = intent.getIntExtra(MatchConstants.EXTRA_PLAYER_ONE_SCORE, 0);
            basePlayerTwoScore = intent.getIntExtra(MatchConstants.EXTRA_PLAYER_TWO_SCORE, 0);
            activePlayer = intent.getIntExtra(MatchConstants.EXTRA_ACTIVE_PLAYER, MatchConstants.PLAYER_ONE);
            nextOpenPlayer = activePlayer;
        }

        setupViews();
        setupClickListeners();
        setupWordButtons();
        updateHeaderScores();
        updateActivePlayerIndicator();

        timerText = findViewById(R.id.timerText);
        startTimer();
    }

    private void startTimer() {

        countDownTimer = new android.os.CountDownTimer(180000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {

                timeLeft = (int) (millisUntilFinished / 1000);

                timerText.setText(String.valueOf(timeLeft));

                if (timeLeft <= 20) {
                    timerText.setTextColor(Color.RED);
                } else {
                    timerText.setTextColor(Color.WHITE);
                }
            }

            @Override
            public void onFinish() {

                timerText.setText("0");

                Toast.makeText(
                        AsocijacijeGameActivity.this,
                        "Vreme je isteklo!",
                        Toast.LENGTH_LONG
                ).show();

                revealAllRed();

                for (Button b : solutionButtons) {
                    b.setEnabled(false);
                }

                btnFinal.setEnabled(false);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        AsocijacijeGameActivity.this::finishWithScore,
                        2000
                );
            }
        }.start();
    }

    private void setupViews() {
        solutionButtons[0] = findViewById(R.id.btnA);
        solutionButtons[1] = findViewById(R.id.btnB);
        solutionButtons[2] = findViewById(R.id.btnC);
        solutionButtons[3] = findViewById(R.id.btnD);

        // Word buttons
        wordButtons[0][0] = findViewById(R.id.a1); wordButtons[0][1] = findViewById(R.id.a2);
        wordButtons[0][2] = findViewById(R.id.a3); wordButtons[0][3] = findViewById(R.id.a4);

        wordButtons[1][0] = findViewById(R.id.b1); wordButtons[1][1] = findViewById(R.id.b2);
        wordButtons[1][2] = findViewById(R.id.b3); wordButtons[1][3] = findViewById(R.id.b4);

        wordButtons[2][0] = findViewById(R.id.c1); wordButtons[2][1] = findViewById(R.id.c2);
        wordButtons[2][2] = findViewById(R.id.c3); wordButtons[2][3] = findViewById(R.id.c4);

        wordButtons[3][0] = findViewById(R.id.d1); wordButtons[3][1] = findViewById(R.id.d2);
        wordButtons[3][2] = findViewById(R.id.d3); wordButtons[3][3] = findViewById(R.id.d4);

        btnFinal = findViewById(R.id.btnFinal);
        playerOneScoreView = findViewById(R.id.playerOneScore);
        playerTwoScoreView = findViewById(R.id.playerTwoScore);
        playerOneContainer = findViewById(R.id.playerOneContainer);
        playerTwoContainer = findViewById(R.id.playerTwoContainer);
    }

    private void setupClickListeners() {
        for (int i = 0; i < 4; i++) {
            final int index = i;
            solutionButtons[i].setOnClickListener(v -> showGuessDialog(index));
        }

        btnFinal.setOnClickListener(v -> showFinalGuessDialog());
    }

    private void showGuessDialog(int groupIndex) {
        if (solvedGroups[groupIndex]) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rešenje za kolonu " + (char)('A' + groupIndex));

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        builder.setView(input);

        int guessingPlayer = getGuessingPlayer();
        builder.setPositiveButton("Potvrdi", (dialog, which) -> {
            String guess = input.getText().toString().trim().toUpperCase();
            if (guess.equals(groupSolutions[groupIndex])) {
                openGroup(groupIndex, guessingPlayer);
            } else {
                Toast.makeText(this, "Nije tačno! Probaj ponovo.", Toast.LENGTH_SHORT).show();
                switchToNextOpener();
            }
        });
        builder.setNegativeButton("Otkaži", null);
        builder.show();
    }

    private void openGroup(int groupIndex, int guessingPlayer) {
        int unopened = 4 - openedCounts[groupIndex];
        int points = GROUP_POINTS + Math.max(0, unopened);
        addPoints(guessingPlayer, points);
        solvedGroups[groupIndex] = true;
        for (int i = 0; i < 4; i++) {
            wordButtons[groupIndex][i].setText(groups[groupIndex][i]);
            wordButtons[groupIndex][i].setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
            wordButtons[groupIndex][i].setEnabled(false);
            openedFields[groupIndex][i] = true;
        }
        openedCounts[groupIndex] = 4;
        solutionButtons[groupIndex].setText(groupSolutions[groupIndex]);
        solutionButtons[groupIndex].setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
    }

    private void showFinalGuessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("KONAČNO REŠENJE");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        builder.setView(input);

        int guessingPlayer = getGuessingPlayer();
        builder.setPositiveButton("Potvrdi", (dialog, which) -> {
            String guess = input.getText().toString().trim().toUpperCase();
            if (guess.equals(finalWord)) {
                addPoints(guessingPlayer, FINAL_POINTS);
                if (countDownTimer != null) {
                    countDownTimer.cancel();
                }
                openAll();
            } else {
                Toast.makeText(this, "Nije tačno!", Toast.LENGTH_SHORT).show();
                switchToNextOpener();
            }
        });
        builder.setNegativeButton("Otkaži", null);
        builder.show();
    }

    private void revealAllRed() {

        for (int group = 0; group < 4; group++) {

            for (int i = 0; i < 4; i++) {

                wordButtons[group][i].setText(groups[group][i]);

                if (solvedGroups[group]) {
                    wordButtons[group][i].setBackgroundTintList(
                            getColorStateList(android.R.color.holo_green_dark)
                    );
                } else {
                    wordButtons[group][i].setBackgroundTintList(
                            getColorStateList(android.R.color.holo_red_dark)
                    );
                }
            }

            if (solvedGroups[group]) {
                solutionButtons[group].setBackgroundTintList(
                        getColorStateList(android.R.color.holo_green_dark)
                );
            } else {
                solutionButtons[group].setBackgroundTintList(
                        getColorStateList(android.R.color.holo_red_dark)
                );
            }

            solutionButtons[group].setText(groupSolutions[group]);
        }

        btnFinal.setText(finalWord);
        btnFinal.setBackgroundTintList(
                getColorStateList(android.R.color.holo_red_dark)
        );
    }

    private void setupWordButtons() {

        for (int group = 0; group < 4; group++) {

            for (int word = 0; word < 4; word++) {

                final int g = group;
                final int w = word;

                wordButtons[group][word].setOnClickListener(v -> handleOpenWord(g, w));
            }
        }
    }

    private void openAll() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        for (int i = 0; i < 4; i++) {
            if (!solvedGroups[i]) openGroup(i, getGuessingPlayer());
        }

        btnFinal.setText(finalWord);
        btnFinal.setBackgroundTintList(getColorStateList(android.R.color.holo_orange_dark));

        Toast.makeText(this, "POBEDA!", Toast.LENGTH_LONG).show();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finishWithScore, 2000);
    }

    private void finishWithScore() {
        if (resultSent) {
            finish();
            return;
        }
        resultSent = true;
        Intent data = new Intent();
        data.putExtra(MatchConstants.EXTRA_GAME_SCORE, playerOneScore + playerTwoScore);
        data.putExtra(MatchConstants.EXTRA_GAME_SCORE_PLAYER_ONE, playerOneScore);
        data.putExtra(MatchConstants.EXTRA_GAME_SCORE_PLAYER_TWO, playerTwoScore);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void finish() {
        if (!resultSent) {
            Intent data = new Intent();
            data.putExtra(MatchConstants.EXTRA_GAME_SCORE, playerOneScore + playerTwoScore);
            data.putExtra(MatchConstants.EXTRA_GAME_SCORE_PLAYER_ONE, playerOneScore);
            data.putExtra(MatchConstants.EXTRA_GAME_SCORE_PLAYER_TWO, playerTwoScore);
            setResult(RESULT_OK, data);
            resultSent = true;
        }
        super.finish();
    }

    private void handleOpenWord(int group, int word) {
        if (solvedGroups[group] || openedFields[group][word]) return;
        wordButtons[group][word].setText(groups[group][word]);
        wordButtons[group][word].setBackgroundTintList(
                getColorStateList(android.R.color.holo_blue_light)
        );
        wordButtons[group][word].setEnabled(false);
        openedFields[group][word] = true;
        openedCounts[group]++;
        lastOpenedPlayer = activePlayer;
        nextOpenPlayer = otherPlayer(nextOpenPlayer);
        activePlayer = nextOpenPlayer;
        updateActivePlayerIndicator();
    }

    private int getGuessingPlayer() {
        return lastOpenedPlayer != 0 ? lastOpenedPlayer : activePlayer;
    }

    private void addPoints(int player, int points) {
        if (player == MatchConstants.PLAYER_ONE) {
            playerOneScore += points;
        } else {
            playerTwoScore += points;
        }
        updateHeaderScores();
    }

    private void switchToNextOpener() {
        activePlayer = nextOpenPlayer;
        lastOpenedPlayer = 0;
        updateActivePlayerIndicator();
    }

    private int otherPlayer(int player) {
        return player == MatchConstants.PLAYER_ONE ? MatchConstants.PLAYER_TWO : MatchConstants.PLAYER_ONE;
    }

    private void updateHeaderScores() {
        if (playerOneScoreView != null) {
            playerOneScoreView.setText(String.valueOf(basePlayerOneScore + playerOneScore));
        }
        if (playerTwoScoreView != null) {
            playerTwoScoreView.setText(String.valueOf(basePlayerTwoScore + playerTwoScore));
        }
    }

    private void updateActivePlayerIndicator() {
        if (playerOneContainer != null && playerTwoContainer != null) {
            playerOneContainer.setAlpha(activePlayer == MatchConstants.PLAYER_ONE ? 1f : 0.6f);
            playerTwoContainer.setAlpha(activePlayer == MatchConstants.PLAYER_TWO ? 1f : 0.6f);
        }
    }
}
