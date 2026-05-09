package com.example.slagalica;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;

public class AsocijacijeGameActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        setupViews();
        setupClickListeners();
        setupWordButtons();

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

        builder.setPositiveButton("Potvrdi", (dialog, which) -> {
            String guess = input.getText().toString().trim().toUpperCase();
            if (guess.equals(groupSolutions[groupIndex])) {
                openGroup(groupIndex);
            } else {
                Toast.makeText(this, "Nije tačno! Probaj ponovo.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Otkaži", null);
        builder.show();
    }

    private void openGroup(int groupIndex) {
        solvedGroups[groupIndex] = true;
        for (int i = 0; i < 4; i++) {
            wordButtons[groupIndex][i].setText(groups[groupIndex][i]);
            wordButtons[groupIndex][i].setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
        }
        solutionButtons[groupIndex].setText(groupSolutions[groupIndex]);
        solutionButtons[groupIndex].setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
    }

    private void showFinalGuessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("KONAČNO REŠENJE");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        builder.setView(input);

        builder.setPositiveButton("Potvrdi", (dialog, which) -> {
            String guess = input.getText().toString().trim().toUpperCase();
            if (guess.equals(finalWord)) {
                if (countDownTimer != null) {
                    countDownTimer.cancel();
                }
                openAll();
            } else {
                Toast.makeText(this, "Nije tačno!", Toast.LENGTH_SHORT).show();
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

                wordButtons[group][word].setOnClickListener(v -> {

                    wordButtons[g][w].setText(groups[g][w]);

                    wordButtons[g][w].setBackgroundTintList(
                            getColorStateList(android.R.color.holo_blue_light)
                    );
                });
            }
        }
    }

    private void openAll() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        for (int i = 0; i < 4; i++) {
            if (!solvedGroups[i]) openGroup(i);
        }

        btnFinal.setText(finalWord);
        btnFinal.setBackgroundTintList(getColorStateList(android.R.color.holo_orange_dark));

        Toast.makeText(this, "POBEDA!", Toast.LENGTH_LONG).show();
    }
}