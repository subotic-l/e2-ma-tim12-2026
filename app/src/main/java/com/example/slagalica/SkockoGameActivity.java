package com.example.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.SkockoGame;

import java.util.Arrays;
import java.util.Random;

public class SkockoGameActivity extends AppCompatActivity {

    private SkockoGame game = new SkockoGame();

    private ImageView[][] board = new ImageView[6][4];
    private ImageView[][] feedback = new ImageView[6][4];
    private Button[] submitButtons = new Button[6];
    private boolean[] rowChecked = new boolean[6];
    private LinearLayout[] feedbackContainers = new LinearLayout[6];
    private ImageView[] solutionViews = new ImageView[4];

    private int timeLeft = 35;
    private android.os.CountDownTimer countDownTimer;
    private TextView timerText;

    private final String[] symbols = {"S", "T", "K", "P", "H", "Z"};
    private final int[] drawableIds = {
            R.drawable.ic_skocko,
            R.drawable.ic_tref,
            R.drawable.ic_karo,
            R.drawable.ic_pik,
            R.drawable.ic_herc,
            R.drawable.ic_zvezda
    };

    private int currentRow = 0;
    private int currentCol = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        timerText = findViewById(R.id.timerText);
        startTimer();
        generateSolution();
        buildGameBoard();
        setupChoiceButtons();
        setupSolutionRow();
    }

    private void startTimer() {
        countDownTimer = new android.os.CountDownTimer(35000, 1000) {

            public void onTick(long millisUntilFinished) {
                timeLeft = (int) (millisUntilFinished / 1000);
                timerText.setText(String.valueOf(timeLeft));

                if (timeLeft <= 10) {
                    timerText.setTextColor(Color.RED);
                } else {
                    timerText.setTextColor(Color.WHITE);
                }
            }

            public void onFinish() {
                timerText.setText("0");
                gameOver(false);
            }
        }.start();
    }

    private void generateSolution() {
        Random rand = new Random();
        for (int i = 0; i < 4; i++) {
            game.solution[i] = symbols[rand.nextInt(symbols.length)];
        }
    }

    private void setupSolutionRow() {
        for (int i = 0; i < 4; i++) {
            int viewId = getResources().getIdentifier("sol" + i, "id", getPackageName());
            solutionViews[i] = findViewById(viewId);

            if (solutionViews[i] != null) {
                solutionViews[i].setImageResource(R.drawable.cell_background);
            } else {
                Toast.makeText(this, "Greška: sol" + i + " nije pronađen u layoutu!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void buildGameBoard() {
        LinearLayout container = findViewById(R.id.gameContainer);
        container.removeAllViews();

        for (int row = 0; row < 6; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            );
            rowLayout.setLayoutParams(lp);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);
            rowLayout.setPadding(10, 16, 10, 16);

            // Polja za simbole
            LinearLayout fieldsLayout = new LinearLayout(this);
            fieldsLayout.setOrientation(LinearLayout.HORIZONTAL);
            fieldsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            for (int col = 0; col < 4; col++) {
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, 200);   // dobra veličina
                params.setMargins(5, 6, 5, 6);
                iv.setLayoutParams(params);
                iv.setBackgroundResource(R.drawable.cell_background);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                final int finalRow = row;
                final int finalCol = col;
                iv.setOnClickListener(v -> removeSymbol(finalRow, finalCol));

                board[row][col] = iv;
                fieldsLayout.addView(iv);
            }

            Button submitBtn = new Button(this);

            submitBtn.setText("?");
            submitBtn.setTextSize(40);
            submitBtn.setTextColor(Color.BLACK);
            submitBtn.setGravity(Gravity.CENTER);
            submitBtn.setPadding(0, 0, 0, 0);
            submitBtn.setEnabled(false);
            submitBtn.setVisibility(row == 0 ? View.VISIBLE : View.INVISIBLE);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(140, 140);
            btnParams.setMargins(12, 0, 8, 0);
            submitBtn.setLayoutParams(btnParams);

            final int finalRowSubmit = row;
            submitBtn.setOnClickListener(v -> checkRow(finalRowSubmit));
            submitButtons[row] = submitBtn;

            LinearLayout feedbackLayout = createFeedbackLayoutImproved(row);

            rowLayout.addView(fieldsLayout);
            rowLayout.addView(submitBtn);
            rowLayout.addView(feedbackLayout);

            container.addView(rowLayout);
        }
    }

    private LinearLayout createFeedbackLayoutImproved(int row) {
        LinearLayout feedbackLayout = new LinearLayout(this);
        feedbackLayout.setOrientation(LinearLayout.VERTICAL);
        feedbackLayout.setPadding(18, 10, 0, 10);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(0, 0, 0, 6);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < 4; i++) {
            ImageView fb = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(60, 60);
            params.setMargins(3, 3, 3, 3);
            fb.setLayoutParams(params);
            fb.setBackgroundResource(R.drawable.feedback_circle);
            fb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

            if (i < 2) top.addView(fb);
            else bottom.addView(fb);

            feedback[row][i] = fb;
        }

        feedbackLayout.addView(top);
        feedbackLayout.addView(bottom);
        feedbackContainers[row] = feedbackLayout;
        feedbackLayout.setVisibility(View.GONE);
        return feedbackLayout;
    }

    private void setupChoiceButtons() {
        findViewById(R.id.btnSkocko).setOnClickListener(v -> addSymbol("S"));
        findViewById(R.id.btnTref).setOnClickListener(v -> addSymbol("T"));
        findViewById(R.id.btnKaro).setOnClickListener(v -> addSymbol("K"));
        findViewById(R.id.btnPik).setOnClickListener(v -> addSymbol("P"));
        findViewById(R.id.btnHerc).setOnClickListener(v -> addSymbol("H"));
        findViewById(R.id.btnZvezda).setOnClickListener(v -> addSymbol("Z"));
    }

    private void addSymbol(String symbol) {
        if (currentRow >= 6 || currentCol >= 4) return;

        game.guesses[currentRow][currentCol] = symbol;

        int index = Arrays.asList(symbols).indexOf(symbol);
        if (index >= 0) {
            board[currentRow][currentCol].setImageResource(drawableIds[index]);
        }

        currentCol++;

        if (currentCol == 4) {
            submitButtons[currentRow].setEnabled(true);
        }
    }

    private void removeSymbol(int row, int col) {
        if (row != currentRow || col < 0 || col >= currentCol) return;

        board[row][col].setImageResource(0);
        game.guesses[row][col] = null;

        for (int i = col; i < 3; i++) {
            game.guesses[row][i] = game.guesses[row][i + 1];
            if (game.guesses[row][i] != null) {
                int idx = Arrays.asList(symbols).indexOf(game.guesses[row][i]);
                board[row][i].setImageResource(drawableIds[idx]);
            } else {
                board[row][i].setImageResource(0);
            }
        }
        board[row][3].setImageResource(0);
        game.guesses[row][3] = null;

        currentCol = Math.max(0, currentCol - 1);
        submitButtons[row].setEnabled(currentCol == 4);
    }

    private void checkRow(int row) {
        if (row != currentRow) return;

        for (int i = 0; i < 4; i++) {
            if (game.guesses[row][i] == null) {
                Toast.makeText(this, "Popunite sva 4 polja!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (!rowChecked[row])
            feedbackContainers[row].setVisibility(View.VISIBLE);
        submitButtons[row].setVisibility(View.GONE);
        rowChecked[row] = true;

        int black = 0;
        int white = 0;

        boolean[] usedSolution = new boolean[4];
        boolean[] usedGuess = new boolean[4];

        for (int i = 0; i < 4; i++) {
            if (game.guesses[row][i].equals(game.solution[i])) {
                black++;
                usedSolution[i] = true;
                usedGuess[i] = true;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (usedGuess[i]) continue;
            for (int j = 0; j < 4; j++) {
                if (!usedSolution[j] && game.guesses[row][i].equals(game.solution[j])) {
                    white++;
                    usedSolution[j] = true;
                    break;
                }
            }
        }

        int fbIndex = 0;
        for (int i = 0; i < black; i++) {
            feedback[row][fbIndex++].setBackgroundResource(R.drawable.feedback_circle_red);
        }
        for (int i = 0; i < white; i++) {
            feedback[row][fbIndex++].setBackgroundResource(R.drawable.feedback_circle_yellow);
        }

        if (black == 4) {
            showSolution(true);
            return;
        }

        currentRow++;
        currentCol = 0;

        if (currentRow < 6) {
            submitButtons[currentRow].setVisibility(View.VISIBLE);
        } else {
            showSolution(false);
        }
    }

    private void gameOver(boolean win) {

        for (Button b : submitButtons) {
            b.setEnabled(false);
        }

        for (int i = 0; i < 4; i++) {
            int idx = Arrays.asList(symbols).indexOf(game.solution[i]);
            solutionViews[i].setImageResource(drawableIds[idx]);
        }

        Toast.makeText(this,
                win ? "Pobeda!" : "Vreme isteklo!",
                Toast.LENGTH_LONG).show();
    }

    private void showSolution(boolean won) {
        for (int i = 0; i < 4; i++) {
            int idx = Arrays.asList(symbols).indexOf(game.solution[i]);
            solutionViews[i].setImageResource(drawableIds[idx]);
        }

        String poruka = won ? "Čestitamo! Pogodili ste rešenje!" : "Game Over! Rešenje je prikazano.";
        Toast.makeText(this, poruka, Toast.LENGTH_LONG).show();

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        gameOver(won);
    }
}