package com.example.slagalica;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class NumbersGameActivity extends AppCompatActivity implements SensorEventListener {

    private static final String DIVIDE_SYMBOL = "÷";
    private static final float SHAKE_THRESHOLD = 15.0f;

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
    private TextView timerText;
    private CountDownTimer gameTimer;

    private final Stack<MaterialButton> usedNumberButtons = new Stack<>();
    private final List<Token> tokens = new ArrayList<>();
    private int openParensCount = 0;
    private int score = 0;
    private int baseScore = 0;
    private boolean resultSent = false;
    private boolean submitted = false;
    private TextView playerNameView, playerScoreView;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;

    private enum TokenType { NUMBER, OPERATOR, OPEN_PAREN, CLOSE_PAREN }

    private static class Token {
        final String text;
        final TokenType type;
        final MaterialButton button; // only for numbers

        Token(String text, TokenType type, MaterialButton button) {
            this.text = text;
            this.type = type;
            this.button = button;
        }
    }

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
        playerNameView = findViewById(R.id.playerOneName);
        playerScoreView = findViewById(R.id.playerOneScore);
        String pn = getIntent().getStringExtra("playerName");
        playerNameView.setText(pn != null ? pn : "Moj broj");
        baseScore = getIntent().getIntExtra("totalScore", 0);
        playerScoreView.setText(String.valueOf(baseScore));
        stopTimerTextView = findViewById(R.id.stopTimerTextView);
        timerText = findViewById(R.id.timerText);
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

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        startNewGame();

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
                        submitted = true;
                        finish();
                    })
                    .setNegativeButton("Nastavi", null)
                    .show());
        }
    }

    private void startNewGame() {
        game = NumbersGame.createRandom();
        stopStage = 0;
        score = 0;
        targetTextView.setText(getString(R.string.question_mark));
        expressionTextView.setText("");
        tokens.clear();
        openParensCount = 0;
        usedNumberButtons.clear();

        timerText.setText("60");
        cancelGameTimer();

        for (MaterialButton b : numberButtons) {
            b.setText(getString(R.string.question_mark));
            b.setEnabled(false);
            b.setAlpha(1f);
        }

        stopButton.setVisibility(View.VISIBLE);
        stopTimerRow.setVisibility(View.VISIBLE);

        registerShakeListener();
        startAutoStopTimer();
    }

    private void registerShakeListener() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void unregisterShakeListener() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > 1000) {
                lastShakeTime = now;
                runOnUiThread(() -> stopButton.performClick());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
        unregisterShakeListener();
        for (int i = 0; i < numberButtons.length; i++) {
            numberButtons[i].setText(String.valueOf(game.numbers.get(i)));
            numberButtons[i].setEnabled(true);
        }
        cancelAutoStopTimer();
        startGameTimer();
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

    private void startGameTimer() {
        if (gameTimer != null) gameTimer.cancel();
        gameTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                timerText.setText(String.valueOf(seconds));
            }

            @Override
            public void onFinish() {
                if (submitted) return;
                submitted = true;
                timerText.setText("0");
                disableAllInputs();
                Toast.makeText(NumbersGameActivity.this, "Vreme je isteklo!", Toast.LENGTH_SHORT).show();
                expressionTextView.setText("");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        NumbersGameActivity.this::finishWithScore,
                        1500
                );
            }
        }.start();
    }

    private void disableAllInputs() {
        for (MaterialButton b : numberButtons) if (b != null) b.setEnabled(false);
        confirmButton.setEnabled(false);
        findViewById(R.id.clearButton).setEnabled(false);
        int[] opIds = {R.id.btnOpen, R.id.btnClose, R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide};
        for (int id : opIds) { View v = findViewById(id); if (v != null) v.setEnabled(false); }
    }

    private void cancelGameTimer() {
        if (gameTimer != null) gameTimer.cancel();
    }

    private void setupNumberButtons() {
        for (MaterialButton b : numberButtons) {
            b.setOnClickListener(v -> {
                if (b.isEnabled() && canAddNumber()) {
                    String value = b.getText().toString();
                    tokens.add(new Token(value, TokenType.NUMBER, b));
                    appendExpressionText();
                    b.setEnabled(false);
                    b.setAlpha(0.5f);
                    usedNumberButtons.push(b);
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
                handleOperator(op);
            });
        }

        MaterialButton clearBtn = findViewById(R.id.clearButton);
        clearBtn.setOnClickListener(v -> removeLastToken());
    }

    private void handleOperator(String op) {
        if (op.equals("(")) {
            if (canAddOpenParen()) {
                tokens.add(new Token(op, TokenType.OPEN_PAREN, null));
                openParensCount++;
                appendExpressionText();
            }
            return;
        }

        if (op.equals(")")) {
            if (canAddCloseParen()) {
                tokens.add(new Token(op, TokenType.CLOSE_PAREN, null));
                openParensCount--;
                appendExpressionText();
            }
            return;
        }

        if (op.equals(DIVIDE_SYMBOL)) op = "/";

        if (canAddOperator()) {
            tokens.add(new Token(op, TokenType.OPERATOR, null));
            appendExpressionText();
        }
    }

    private void removeLastToken() {
        if (tokens.isEmpty()) return;

        Token removed = tokens.remove(tokens.size() - 1);
        if (removed.type == TokenType.OPEN_PAREN) {
            openParensCount = Math.max(0, openParensCount - 1);
        } else if (removed.type == TokenType.CLOSE_PAREN) {
            openParensCount++;
        }

        if (removed.type == TokenType.NUMBER && removed.button != null) {
            removed.button.setEnabled(true);
            removed.button.setAlpha(1f);
            if (!usedNumberButtons.isEmpty() && usedNumberButtons.peek() == removed.button) {
                usedNumberButtons.pop();
            }
        }

        appendExpressionText();
    }

    private boolean canAddNumber() {
        if (tokens.isEmpty()) return true;
        TokenType last = tokens.get(tokens.size() - 1).type;
        return last == TokenType.OPERATOR || last == TokenType.OPEN_PAREN;
    }

    private boolean canAddOperator() {
        if (tokens.isEmpty()) return false;
        TokenType last = tokens.get(tokens.size() - 1).type;
        return last == TokenType.NUMBER || last == TokenType.CLOSE_PAREN;
    }

    private boolean canAddOpenParen() {
        if (tokens.isEmpty()) return true;
        TokenType last = tokens.get(tokens.size() - 1).type;
        return last == TokenType.OPERATOR || last == TokenType.OPEN_PAREN;
    }

    private boolean canAddCloseParen() {
        if (openParensCount <= 0) return false;
        if (tokens.isEmpty()) return false;
        TokenType last = tokens.get(tokens.size() - 1).type;
        return last == TokenType.NUMBER || last == TokenType.CLOSE_PAREN;
    }

    private void appendExpressionText() {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (sb.length() > 0) sb.append(" ");
            if (t.type == TokenType.OPERATOR && t.text.equals("/")) {
                sb.append(DIVIDE_SYMBOL);
            } else {
                sb.append(t.text);
            }
        }
        expressionTextView.setText(sb.toString());
    }

    private void setupConfirmButton() {
        confirmButton.setOnClickListener(v -> {
            if (submitted) return;
            String expr = buildEvalExpression();
            if (expr.isEmpty()) return;

            submitted = true;
            disableAllInputs();
            cancelGameTimer();

            try {
                double result = evaluate(expr);
                if (Math.abs(result - game.targetNumber) < 0.0001) {
                    Toast.makeText(this, "Tačno! +10 bodova", Toast.LENGTH_SHORT).show();
                    score = 10;
                } else {
                    Toast.makeText(this, "Pokušaj: " + formatResult(result), Toast.LENGTH_SHORT).show();
                    score = 0;
                }
                playerScoreView.setText(String.valueOf(baseScore + score));
            } catch (Exception e) {
                Toast.makeText(this, "Neispravan izraz", Toast.LENGTH_SHORT).show();
            }

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    NumbersGameActivity.this::finishWithScore,
                    2000
            );
        });
    }

    private void finishWithScore() {
        if (resultSent) {
            finish();
            return;
        }
        unregisterShakeListener();
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
        unregisterShakeListener();
        super.finish();
    }

    private String buildEvalExpression() {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (t.type == TokenType.OPERATOR && t.text.equals(DIVIDE_SYMBOL)) {
                sb.append("/");
            } else {
                sb.append(t.text);
            }
        }
        return sb.toString();
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
