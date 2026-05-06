package com.example.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Stack;

public class NumbersGameActivity extends AppCompatActivity {

    private static final String DIVIDE_SYMBOL = "÷";

    private TextView targetTextView;
    private TextView expressionTextView;
    private TextView stopTimerTextView;
    private LinearLayout stopTimerRow;
    private MaterialButton stopButton;
    private MaterialButton confirmButton;

    private MaterialButton[] numberButtons;

    private NumbersGame game;
    private int stopStage = 0; // 0 = nothing shown, 1 = target shown, 2 = numbers shown
    private CountDownTimer autoStopTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_numbers_game);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        targetTextView = findViewById(R.id.targetTextView);
        expressionTextView = findViewById(R.id.expressionTextView);
        stopTimerTextView = findViewById(R.id.stopTimerTextView);
        stopTimerRow = findViewById(R.id.stopTimerRow);
        stopButton = findViewById(R.id.stopButton);
        confirmButton = findViewById(R.id.confirmButton);

        numberButtons = new MaterialButton[] {
                findViewById(R.id.numButton1),
                findViewById(R.id.numButton2),
                findViewById(R.id.numButton3),
                findViewById(R.id.numButton4),
                findViewById(R.id.numButton5),
                findViewById(R.id.numButton6)
        };

        setupOperatorButtons();
        setupNumberButtons();
        setupStopButton();
        setupConfirmButton();

        startNewGame();
    }

    private void startNewGame() {
        game = NumbersGame.createRandom();
        stopStage = 0;
        targetTextView.setText(getString(R.string.question_mark));
        expressionTextView.setText("");

        for (MaterialButton b : numberButtons) {
            b.setText(getString(R.string.question_mark));
            b.setEnabled(false);
        }

        stopButton.setVisibility(View.VISIBLE);
        stopTimerRow.setVisibility(View.VISIBLE);

        startAutoStopTimer();
    }

    private void setupStopButton() {
        stopButton.setOnClickListener(v -> {
            if (stopStage == 0) {
                showTarget();
            } else if (stopStage == 1) {
                showNumbers();
            }
        });
    }

    private void showTarget() {
        stopStage = 1;
        targetTextView.setText(String.valueOf(game.targetNumber));
        restartAutoStopTimer();
    }

    private void showNumbers() {
        stopStage = 2;
        for (int i = 0; i < numberButtons.length; i++) {
            numberButtons[i].setText(String.valueOf(game.numbers.get(i)));
            numberButtons[i].setEnabled(true);
        }
        cancelAutoStopTimer();
        stopButton.setVisibility(View.GONE);
        stopTimerRow.setVisibility(View.GONE);
    }

    private void startAutoStopTimer() {
        cancelAutoStopTimer();
        autoStopTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                stopTimerTextView.setText(String.valueOf(millisUntilFinished / 1000 + 1));
            }

            @Override
            public void onFinish() {
                stopTimerTextView.setText("0");
                if (stopStage == 0) {
                    showTarget();
                } else if (stopStage == 1) {
                    showNumbers();
                }
            }
        }.start();
    }

    private void restartAutoStopTimer() {
        startAutoStopTimer();
    }

    private void cancelAutoStopTimer() {
        if (autoStopTimer != null) autoStopTimer.cancel();
    }

    private void setupNumberButtons() {
        for (MaterialButton b : numberButtons) {
            b.setOnClickListener(v -> {
                if (b.isEnabled()) {
                    expressionTextView.append(b.getText().toString());
                }
            });
        }
    }

    private void setupOperatorButtons() {
        int[] ids = {
                R.id.btnOpen, R.id.btnClose, R.id.btnPlus,
                R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide
        };
        for (int id : ids) {
            MaterialButton b = findViewById(id);
            b.setOnClickListener(v -> {
                String op = b.getText().toString();
                if (op.equals(DIVIDE_SYMBOL)) op = "/";
                expressionTextView.append(op);
            });
        }

        MaterialButton clearBtn = findViewById(R.id.clearButton);
        clearBtn.setOnClickListener(v -> expressionTextView.setText(""));
    }

    private void setupConfirmButton() {
        confirmButton.setOnClickListener(v -> {
            String expr = expressionTextView.getText().toString().trim();
            if (expr.isEmpty()) return;

            try {
                double result = evaluate(expr);
                if (Math.abs(result - game.targetNumber) < 0.0001) {
                    Toast.makeText(this, "Tačno! +10 bodova", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Netačno (" + formatResult(result) + ")", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Neispravan izraz", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatResult(double result) {
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return String.valueOf((int) result);
        }
        return String.format("%.2f", result);
    }

    // Simple expression evaluator (+,-,*,/, parentheses)
    private double evaluate(String expression) {
        Stack<Double> values = new Stack<>();
        Stack<Character> ops = new Stack<>();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isWhitespace(c)) continue;

            if (Character.isDigit(c)) {
                StringBuilder sb = new StringBuilder();
                while (i < expression.length() && Character.isDigit(expression.charAt(i))) {
                    sb.append(expression.charAt(i++));
                }
                i--;
                values.push(Double.parseDouble(sb.toString()));
            } else if (c == '(') {
                ops.push(c);
            } else if (c == ')') {
                while (ops.peek() != '(') {
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                }
                ops.pop();
            } else if (c == '+' || c == '-' || c == '*' || c == '/') {
                while (!ops.empty() && hasPrecedence(c, ops.peek())) {
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                }
                ops.push(c);
            }
        }

        while (!ops.empty()) {
            values.push(applyOp(ops.pop(), values.pop(), values.pop()));
        }

        return values.pop();
    }

    private boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        if ((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-')) return false;
        return true;
    }

    private double applyOp(char op, double b, double a) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/': return b == 0 ? 0 : a / b;
        }
        return 0;
    }
}