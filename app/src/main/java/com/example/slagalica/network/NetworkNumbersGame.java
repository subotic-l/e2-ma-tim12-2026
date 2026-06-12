package com.example.slagalica.network;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.slagalica.NumbersGame;
import com.example.slagalica.R;
import com.example.slagalica.data.GameSessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class NetworkNumbersGame extends AppCompatActivity {

    private static final int GAME_TIME_MS = 60000;

    private GameSessionManager sm;
    private int me, opp, gameIdx, totalGames;
    private String matchId;
    private NumbersGame game;
    private boolean gameStarted = false;
    private boolean submitted = false;
    private String myExpression = "";
    private double myResult = 0;
    private boolean myCorrect = false;
    private long myTime = -1;
    private boolean waitReveal = false;
    private int localMyPts = 0, localOppPts = 0;
    private boolean done = false;
    private boolean iAmFinisher = false;

    private TextView timerView, targetView, exprView, myNameView, oppNameView, myScoreView, oppScoreView;
    private android.widget.ImageView myAvatarView, oppAvatarView;
    private MaterialButton[] numBtns;
    private MaterialButton confirmBtn, clearBtn, stopBtn;
    private LinearLayout stopTimerRow;
    private CountDownTimer gameTimer;
    private long gameRemain = GAME_TIME_MS;

    private String myName, myAvatar;

    // Expression building state
    private final Stack<MaterialButton> usedNumberButtons = new Stack<>();
    private final List<Token> tokens = new ArrayList<>();
    private int openParensCount = 0;

    private enum TokenType { NUMBER, OPERATOR, OPEN_PAREN, CLOSE_PAREN }

    private static class Token {
        final String text;
        final TokenType type;
        final MaterialButton button;
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
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        Intent i = getIntent();
        matchId = i.getStringExtra("matchId");
        me = i.getIntExtra("myPlayerNumber", 1);
        gameIdx = i.getIntExtra("gameIndex", 0);
        totalGames = i.getIntExtra("totalGames", 3);
        opp = me == 1 ? 2 : 1;

        timerView = findViewById(R.id.gameTimerTextView);
        targetView = findViewById(R.id.targetTextView);
        exprView = findViewById(R.id.expressionTextView);

        numBtns = new MaterialButton[]{
                findViewById(R.id.numButton1), findViewById(R.id.numButton2),
                findViewById(R.id.numButton5), findViewById(R.id.numButton4),
                findViewById(R.id.numButton3), findViewById(R.id.numButton6)
        };

        confirmBtn = findViewById(R.id.confirmButton);
        clearBtn = findViewById(R.id.clearButton);
        stopBtn = findViewById(R.id.stopButton);
        stopTimerRow = findViewById(R.id.stopTimerRow);

        stopBtn.setVisibility(View.GONE);
        stopTimerRow.setVisibility(View.GONE);

        myNameView = findViewById(R.id.playerOneName);
        oppNameView = findViewById(R.id.playerTwoName);
        myScoreView = findViewById(R.id.playerOneScore);
        oppScoreView = findViewById(R.id.playerTwoScore);
        myAvatarView = findViewById(R.id.playerOneAvatar);
        oppAvatarView = findViewById(R.id.playerTwoAvatar);

        myName = i.getStringExtra("myPlayerName");
        if (myName == null || myName.isEmpty()) myName = "Igrač 1";
        myAvatar = i.getStringExtra("myAvatarUrl");

        setupNumberButtons();
        setupOperatorButtons();
        confirmBtn.setOnClickListener(v -> submit());
        clearBtn.setOnClickListener(v -> removeLastToken());

        targetView.setText("?");
        exprView.setText("");
        timerView.setText("60");
        confirmBtn.setEnabled(false);
        clearBtn.setEnabled(false);
        disableNumberButtons();

        sm = new GameSessionManager();
        sm.attachToMatch(matchId, me);

        if (me == 1) {
            initGame();
        }

        sm.listenToMatch(createL());
    }

    private void disableNumberButtons() {
        for (MaterialButton b : numBtns) b.setEnabled(false);
    }

    private GameSessionManager.StateListener createL() {
        return new GameSessionManager.StateListener() {
            public void onStateChanged(Map<String, Object> full) {
                if (done || isFinishing()) return;

                String p1n = (String) full.get("player1Name");
                String p2n = (String) full.get("player2Name");
                String p1a = (String) full.get("player1Avatar");
                String p2a = (String) full.get("player2Avatar");

                if (me == 1) {
                    myNameView.setText(p1n != null ? p1n : myName);
                    myNameView.setTextColor(0xFF1565C0);
                    oppNameView.setText(p2n != null ? p2n : "Protivnik");
                    oppNameView.setTextColor(0xFFE65100);
                    loadAvatar(myAvatarView, myAvatar);
                    if (p2a != null) loadAvatar(oppAvatarView, p2a);
                } else {
                    myNameView.setText(p2n != null ? p2n : myName);
                    myNameView.setTextColor(0xFFE65100);
                    oppNameView.setText(p1n != null ? p1n : "Protivnik");
                    oppNameView.setTextColor(0xFF1565C0);
                    loadAvatar(myAvatarView, myAvatar);
                    if (p1a != null) loadAvatar(oppAvatarView, p1a);
                }

                Map<String, Object> gs = (Map<String, Object>) full.get("gameState");
                if (gs == null || gs.isEmpty()) return;

                if (game == null) {
                    if (gs.containsKey("target") && gs.containsKey("numbers")) {
                        int target = ((Long) gs.get("target")).intValue();
                        List<Long> numsRaw = (List<Long>) gs.get("numbers");
                        List<Integer> nums = new ArrayList<>();
                        for (Long v : numsRaw) nums.add(v.intValue());
                        game = new NumbersGame(target, nums);
                        if (me == 2) {
                            sm.updateField("gameState.player2Ready", true);
                        }
                    }
                    return;
                }

                String phase = (String) gs.get("phase");
                if ("loading".equals(phase)) {
                    boolean p1r = Boolean.TRUE.equals(gs.get("player1Ready"));
                    boolean p2r = Boolean.TRUE.equals(gs.get("player2Ready"));
                    if (me == 1 && p1r && p2r) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("phase", "play");
                        sm.updateGameState(updates);
                        runOnUiThread(() -> startPlay());
                    }
                    return;
                }

                if ("play".equals(phase) && !gameStarted) {
                    runOnUiThread(() -> startPlay());
                    return;
                }

                runOnUiThread(() -> process(gs));
            }

            public void onMatchEnded(Map<String, Object> f) {
                if (done) return;
                done = true;
                if (gameTimer != null) gameTimer.cancel();
                sm.cleanup();
                setResult(RESULT_OK);
                finish();
            }

            public void onError(String e) {}
        };
    }

    private void loadAvatar(android.widget.ImageView iv, String url) {
        Glide.with(this)
                .load(url != null && !url.isEmpty() ? url : R.drawable.default_profile)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(iv);
    }

    private void process(Map<String, Object> gs) {
        if (done) return;

        long p1Pts = gs.containsKey("p1Pts") ? (long) gs.get("p1Pts") : 0;
        long p2Pts = gs.containsKey("p2Pts") ? (long) gs.get("p2Pts") : 0;
        localMyPts = (int) (me == 1 ? p1Pts : p2Pts);
        localOppPts = (int) (me == 1 ? p2Pts : p1Pts);
        updateScoreDisplay();

        String phase = (String) gs.get("phase");
        if ("done".equals(phase)) {
            if (iAmFinisher) finishGame();
            return;
        }
        if ("reveal".equals(phase) && !waitReveal) {
            showReveal(gs);
            return;
        }
        if ("play".equals(phase)) {
            long p1s = gs.containsKey("p1Submitted") ? (long) gs.get("p1Submitted") : 0;
            long p2s = gs.containsKey("p2Submitted") ? (long) gs.get("p2Submitted") : 0;
            if (p1s != 0 && p2s != 0 && iAmFinisher) {
                sm.updateField("gameState.phase", "reveal");
            }
        }
    }

    private void startPlay() {
        if (gameStarted) return;
        gameStarted = true;

        targetView.setText(String.valueOf(game.targetNumber));
        for (int i = 0; i < numBtns.length; i++) {
            numBtns[i].setText(String.valueOf(game.numbers.get(i)));
            numBtns[i].setEnabled(true);
            numBtns[i].setAlpha(1f);
        }
        confirmBtn.setEnabled(true);
        clearBtn.setEnabled(true);

        startGameTimer();
    }

    private void startGameTimer() {
        if (gameTimer != null) gameTimer.cancel();
        gameRemain = GAME_TIME_MS;
        gameTimer = new CountDownTimer(GAME_TIME_MS, 100) {
            public void onTick(long m) {
                gameRemain = m;
                int sec = (int) (m / 1000) + 1;
                timerView.setText(String.valueOf(sec));
                if (sec <= 5) timerView.setTextColor(0xFFFF0000);
                else timerView.setTextColor(0xFFFFFFFF);
            }
            public void onFinish() {
                timerView.setText("0");
                timerView.setTextColor(0xFFFF0000);
                if (!submitted) {
                    submitted = true;
                    myExpression = "";
                    myCorrect = false;
                    myTime = GAME_TIME_MS;
                    writeSubmission();
                }
            }
        }.start();
    }

    private void submit() {
        if (submitted) return;
        String expr = buildEvalExpression();
        if (expr.isEmpty()) {
            Toast.makeText(this, "Unesite izraz", Toast.LENGTH_SHORT).show();
            return;
        }
        submitted = true;
        myExpression = expr;
        myTime = GAME_TIME_MS - gameRemain;
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        try {
            double result = evaluate(expr);
            myResult = result;
            myCorrect = Math.abs(result - game.targetNumber) < 0.0001;
        } catch (Exception e) {
            myCorrect = false;
        }
        disableAllInputs();
        writeSubmission();
    }

    private void disableAllInputs() {
        for (MaterialButton b : numBtns) b.setEnabled(false);
        confirmBtn.setEnabled(false);
        clearBtn.setEnabled(false);
    }

    private void writeSubmission() {
        String p = me == 1 ? "p1" : "p2";
        Map<String, Object> gs = new HashMap<>();
        gs.put(p + "Submitted", 1L);
        gs.put(p + "Expression", myExpression);
        gs.put(p + "Correct", myCorrect);
        gs.put(p + "Time", myTime);
        sm.updateGameState(gs);
    }

    private void showReveal(Map<String, Object> gs) {
        waitReveal = true;
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        disableAllInputs();

        boolean p1c = Boolean.TRUE.equals(gs.get("p1Correct"));
        boolean p2c = Boolean.TRUE.equals(gs.get("p2Correct"));
        long p1t = gs.containsKey("p1Time") ? (long) gs.get("p1Time") : -1;
        long p2t = gs.containsKey("p2Time") ? (long) gs.get("p2Time") : -1;

        int p1pts = calcPts(p1c, p2c, p1t, p2t);
        int p2pts = calcPts(p2c, p1c, p2t, p1t);
        localMyPts += (me == 1 ? p1pts : p2pts);
        localOppPts += (me == 1 ? p2pts : p1pts);
        updateScoreDisplay();

        String msg;
        if (p1c && p2c) {
            msg = "Oboje tačno!";
        } else if (p1c) {
            msg = "Igrač 1 je tačno pogodio!";
        } else if (p2c) {
            msg = "Igrač 2 je tačno pogodio!";
        } else {
            msg = "Niko nije pogodio.";
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (done) return;
            if (iAmFinisher) {
                Map<String, Object> ns = new HashMap<>();
                ns.put("phase", "done");
                ns.put("p1Pts", (long) localMyPts);
                ns.put("p2Pts", (long) localOppPts);
                sm.setGameState(ns);
            }
        }, 3000);
    }

    private int calcPts(boolean myCorrect, boolean oppCorrect, long myTime, long oppTime) {
        if (myCorrect && oppCorrect) return myTime <= oppTime ? 10 : 0;
        if (myCorrect) return 10;
        if (!myCorrect && submitted) return -5;
        return 0;
    }

    private void initGame() {
        iAmFinisher = true;
        game = NumbersGame.createRandom();
        Map<String, Object> gs = new HashMap<>();
        gs.put("target", (long) game.targetNumber);
        List<Long> nums = new ArrayList<>();
        for (Integer v : game.numbers) nums.add(v.longValue());
        gs.put("numbers", nums);
        gs.put("phase", "loading");
        gs.put("player1Ready", true);
        gs.put("p1Submitted", 0L);
        gs.put("p2Submitted", 0L);
        gs.put("p1Correct", false);
        gs.put("p2Correct", false);
        gs.put("p1Time", -1L);
        gs.put("p2Time", -1L);
        gs.put("p1Pts", 0L);
        gs.put("p2Pts", 0L);
        sm.setGameState(gs);
    }

    private void finishGame() {
        if (done) return;
        done = true;
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        sm.finishCurrentGame(gameIdx, localMyPts, localOppPts, localMyPts, localOppPts, totalGames);
        sm.cleanup();
        setResult(RESULT_OK);
        finish();
    }

    private void updateScoreDisplay() {
        myScoreView.setText(String.valueOf(localMyPts));
        oppScoreView.setText(String.valueOf(localOppPts));
    }

    // --- Expression building (mirrored from NumbersGameActivity) ---

    private void setupNumberButtons() {
        for (MaterialButton b : numBtns) {
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
        if ("÷".equals(op)) op = "/";
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
                sb.append("÷");
            } else {
                sb.append(t.text);
            }
        }
        exprView.setText(sb.toString());
    }

    private String buildEvalExpression() {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (t.type == TokenType.OPERATOR && t.text.equals("÷")) {
                sb.append("/");
            } else {
                sb.append(t.text);
            }
        }
        return sb.toString();
    }

    // Expression evaluator
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameTimer != null) gameTimer.cancel();
        if (sm != null) sm.cleanup();
    }
}
