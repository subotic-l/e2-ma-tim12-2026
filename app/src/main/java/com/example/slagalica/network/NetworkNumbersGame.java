package com.example.slagalica.network;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import com.example.slagalica.data.AvatarHelper;
import com.example.slagalica.data.GameSessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class NetworkNumbersGame extends AppCompatActivity implements SensorEventListener {

    private static final int STOP_TIME_MS = 5000;
    private static final int GAME_TIME_MS = 60000;

    private GameSessionManager sm;
    private int me, opp, gameIdx;//, totalGames;
    private String matchId;
    private int previousP1Score = 0, previousP2Score = 0;

    private NumbersGame game;
    private int round = 1;
    private String phase = "";
    private int revealer = 1;
    private boolean iAmFinisher = false;
    private boolean done = false;

    private boolean submitted = false;
    private boolean playInitialized = false;
    private boolean phaseCompleted = false;
    private double myResult = 0;

    private int round1Score = 0, round2Score = 0;
    private int lastDisplayedR1Score = 0, lastDisplayedR2Score = 0;
    private int p1FoundExactCount = 0;
    private int p2FoundExactCount = 0;

    private TextView timerView, targetView, exprView, myNameView, oppNameView, myScoreView, oppScoreView;
    private android.widget.ImageView myAvatarView, oppAvatarView;
    private TextView stopTimerView, instrView;
    private LinearLayout stopTimerRow;
    private MaterialButton stopBtn, confirmBtn, clearBtn;
    private MaterialButton[] numBtns;
    private MaterialButton[] opBtns;

    private CountDownTimer stopTimer, gameTimer;

    private final Stack<MaterialButton> usedNumberButtons = new Stack<>();
    private final List<Token> tokens = new ArrayList<>();
    private int openParensCount = 0;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD = 15.0f;

    private String myName, myAvatar, myPlayerId;

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
        //totalGames = i.getIntExtra("totalGames", 3);
        previousP1Score = i.getIntExtra("previousPlayer1Score", 0);
        previousP2Score = i.getIntExtra("previousPlayer2Score", 0);
        opp = me == 1 ? 2 : 1;
        boolean spectator = i.getBooleanExtra("isSpectator", false);

        timerView = findViewById(R.id.timerText);
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
        stopTimerView = findViewById(R.id.stopTimerTextView);

        myNameView = findViewById(R.id.playerOneName);
        oppNameView = findViewById(R.id.playerTwoName);
        myScoreView = findViewById(R.id.playerOneScore);
        oppScoreView = findViewById(R.id.playerTwoScore);
        myAvatarView = findViewById(R.id.playerOneAvatar);
        oppAvatarView = findViewById(R.id.playerTwoAvatar);

        myName = i.getStringExtra("myPlayerName");
        if (myName == null || myName.isEmpty()) myName = "Igrač 1";
        myAvatar = i.getStringExtra("myAvatarUrl");
        myPlayerId = i.getStringExtra("myPlayerId");

        instrView = findViewById(R.id.instructionsTextView);
        if (instrView != null) {
            instrView.setVisibility(View.VISIBLE);
            instrView.setText("Priprema...");
        }

        setupNumberButtons();
        setupOperatorButtons();
        confirmBtn.setOnClickListener(v -> submit());
        clearBtn.setOnClickListener(v -> removeLastToken());
        stopBtn.setOnClickListener(v -> onStopClick());

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        showWaitingState();

        sm = new GameSessionManager();
        sm.attachToMatch(matchId, me);

        if (me == 1) {
            initRound1();
        }

        sm.listenToMatch(createL());

        if (spectator) {
            for (MaterialButton b : numBtns) b.setEnabled(false);
            if (opBtns != null) for (MaterialButton b : opBtns) b.setEnabled(false);
            confirmBtn.setEnabled(false);
            clearBtn.setEnabled(false);
            stopBtn.setEnabled(false);
        }
    }

    private void showWaitingState() {
        targetView.setText("?");
        exprView.setText("");
        timerView.setVisibility(View.GONE);
        stopBtn.setVisibility(View.GONE);
        stopTimerRow.setVisibility(View.GONE);
        confirmBtn.setEnabled(false);
        clearBtn.setEnabled(false);
        disableNumberButtons();
        hideOperatorButtons();
    }

    private void hideOperatorButtons() {
        int[] ids = { R.id.btnOpen, R.id.btnClose, R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide };
        for (int id : ids) findViewById(id).setVisibility(View.GONE);
    }

    private void showOperatorButtons() {
        int[] ids = { R.id.btnOpen, R.id.btnClose, R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide };
        for (int id : ids) findViewById(id).setVisibility(View.VISIBLE);
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
                runOnUiThread(this::onStopClick);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void initRound1() {
        round = 1;
        revealer = 1;
        game = NumbersGame.createRandom();
        Map<String, Object> gs = new HashMap<>();
        gs.put("phase", "reveal_target");
        gs.put("round", 1L);
        gs.put("revealer", 1L);
        gs.put("p1Submitted", 0L);
        gs.put("p2Submitted", 0L);
        gs.put("p1Result", 0.0);
        gs.put("p2Result", 0.0);
        gs.put("round1Score", 0L);
        gs.put("round2Score", 0L);
        sm.setGameState(gs);
    }

    private GameSessionManager.StateListener createL() {
        return new GameSessionManager.StateListener() {
            public void onStateChanged(Map<String, Object> full) {
                if (done || isFinishing()) return;

                String p1n = (String) full.get("player1Name");
                String p2n = (String) full.get("player2Name");
                String p1a = (String) full.get("player1Avatar");
                String p2a = (String) full.get("player2Avatar");
                String p1id = (String) full.get("player1Id");
                String p2id = (String) full.get("player2Id");

                if (me == 1) {
                    myNameView.setText(p1n != null ? p1n : myName);
                    myNameView.setTextColor(0xFF1565C0);
                    oppNameView.setText(p2n != null ? p2n : "Protivnik");
                    oppNameView.setTextColor(0xFFE65100);
                    loadAvatar(myAvatarView, myPlayerId, myAvatar);
                    if (p2a != null) loadAvatar(oppAvatarView, p2id, p2a);
                } else {
                    myNameView.setText(p2n != null ? p2n : myName);
                    myNameView.setTextColor(0xFFE65100);
                    oppNameView.setText(p1n != null ? p1n : "Protivnik");
                    oppNameView.setTextColor(0xFF1565C0);
                    loadAvatar(myAvatarView, myPlayerId, myAvatar);
                    if (p1a != null) loadAvatar(oppAvatarView, p1id, p1a);
                }

                Map<String, Object> gs = (Map<String, Object>) full.get("gameState");
                if (gs == null || gs.isEmpty()) return;
                if (gs.get("phase") == null) return;

                runOnUiThread(() -> {
                    phase = (String) gs.get("phase");
                    round = gs.containsKey("round") ? ((Long) gs.get("round")).intValue() : 1;
                    revealer = gs.containsKey("revealer") ? ((Long) gs.get("revealer")).intValue() : 1;
                    iAmFinisher = (revealer == me);
                    boolean iAmRevealer = (revealer == me);

                    switch (phase) {
                        case "reveal_target":
                            handleRevealTarget(iAmRevealer);
                            break;
                        case "reveal_numbers":
                            handleRevealNumbers(gs, iAmRevealer);
                            break;
                        case "play":
                            handlePlay(gs);
                            break;
                        case "result":
                            handleResult(gs);
                            break;
                        case "done":
                            finishGame();
                            break;
                    }
                });
            }

            public void onMatchEnded(Map<String, Object> f) {
                if (done) return;
                done = true;
                if (gameTimer != null) gameTimer.cancel();
                if (stopTimer != null) stopTimer.cancel();
                unregisterShakeListener();
                sm.cleanup();
                setResult(RESULT_OK);
                finish();
            }

            public void onError(String e) {}
        };
    }

    private void loadAvatar(android.widget.ImageView iv, String uid, String url) {
        AvatarHelper.loadAvatar(iv, uid, url);
    }

    // --- Phase Handlers ---

    private void handleRevealTarget(boolean iAmRevealer) {
        game = null;
        submitted = false;
        playInitialized = false;
        phaseCompleted = false;
        tokens.clear();
        openParensCount = 0;
        usedNumberButtons.clear();
        targetView.setText("?");
        instrView.setText(iAmRevealer
                ? "Klikni STOP ili protresi za otkrivanje broja"
                : "Čekanje da protivnik otkrije broj...");
        stopBtn.setVisibility(iAmRevealer ? View.VISIBLE : View.GONE);
        stopTimerRow.setVisibility(iAmRevealer ? View.VISIBLE : View.GONE);
        confirmBtn.setEnabled(false);
        clearBtn.setEnabled(false);
        disableNumberButtons();
        hideOperatorButtons();
        exprView.setText("");
        for (MaterialButton b : numBtns) { b.setText(""); b.setAlpha(1f); }
        if (iAmRevealer) {
            if (game == null) {
                game = NumbersGame.createRandom();
            }
            registerShakeListener();
            startStopTimer();
        } else {
            unregisterShakeListener();
        }
    }

    private void handleRevealNumbers(Map<String, Object> gs, boolean iAmRevealer) {
        if (gs.containsKey("target")) {
            targetView.setText(String.valueOf((long) gs.get("target")));
        }
        instrView.setText(iAmRevealer
                ? "Klikni STOP ili protresi za otkrivanje brojeva"
                : "Čekanje da protivnik otkrije brojeve...");
        stopBtn.setVisibility(iAmRevealer ? View.VISIBLE : View.GONE);
        stopTimerRow.setVisibility(iAmRevealer ? View.VISIBLE : View.GONE);
        confirmBtn.setEnabled(false);
        clearBtn.setEnabled(false);
        disableNumberButtons();
        hideOperatorButtons();
        playInitialized = false;
        exprView.setText("");
        for (MaterialButton b : numBtns) { b.setText(""); b.setAlpha(1f); }
        if (iAmRevealer) {
            registerShakeListener();
            startStopTimer();
        } else {
            unregisterShakeListener();
        }
    }

    private void handlePlay(Map<String, Object> gs) {
        unregisterShakeListener();
        stopBtn.setVisibility(View.GONE);
        stopTimerRow.setVisibility(View.GONE);

        if (!playInitialized) {
            playInitialized = true;
            if (gs.containsKey("target")) {
                targetView.setText(String.valueOf((long) gs.get("target")));
            }
            if (gs.containsKey("numbers")) {
                List<Long> numsRaw = (List<Long>) gs.get("numbers");
                for (int i = 0; i < numBtns.length && i < numsRaw.size(); i++) {
                    numBtns[i].setText(String.valueOf(numsRaw.get(i)));
                    numBtns[i].setEnabled(true);
                    numBtns[i].setAlpha(1f);
                }
            }
            instrView.setText("Kreiraj izraz i potvrdi!");
            confirmBtn.setEnabled(true);
            clearBtn.setEnabled(true);
            showOperatorButtons();
            if (opBtns != null) for (MaterialButton b : opBtns) b.setEnabled(true);
            timerView.setVisibility(View.VISIBLE);
            startPlayTimer();
        }

        if (phaseCompleted) return;
        checkAndScore(gs);
    }

    private void handleResult(Map<String, Object> gs) {
        unregisterShakeListener();
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        if (stopTimer != null) { stopTimer.cancel(); stopTimer = null; }

        stopBtn.setVisibility(View.GONE);
        stopTimerRow.setVisibility(View.GONE);
        confirmBtn.setEnabled(false);
        clearBtn.setEnabled(false);
        disableNumberButtons();
        hideOperatorButtons();

        int r1Score = gs.containsKey("round1Score") ? ((Long) gs.get("round1Score")).intValue() : 0;
        int r2Score = gs.containsKey("round2Score") ? ((Long) gs.get("round2Score")).intValue() : 0;
        round1Score = r1Score;
        round2Score = r2Score;

        int totalMy = previousP1Score + (me == 1 ? r1Score : r2Score);
        int totalOpp = previousP2Score + (me == 1 ? r2Score : r1Score);
        myScoreView.setText(String.valueOf(totalMy));
        oppScoreView.setText(String.valueOf(totalOpp));

        int target = gs.containsKey("target") ? ((Long) gs.get("target")).intValue() : 0;
        double p1r = gs.containsKey("p1Result") ? ((Number) gs.get("p1Result")).doubleValue() : 0;
        double p2r = gs.containsKey("p2Result") ? ((Number) gs.get("p2Result")).doubleValue() : 0;
        boolean p1sub = gs.containsKey("p1Submitted") && (Long) gs.get("p1Submitted") == 1;
        boolean p2sub = gs.containsKey("p2Submitted") && (Long) gs.get("p2Submitted") == 1;

        boolean p1Exact = p1sub && Math.abs(p1r - target) < 0.0001;
        boolean p2Exact = p2sub && Math.abs(p2r - target) < 0.0001;
        if (p1Exact) p1FoundExactCount++;
        if (p2Exact) p2FoundExactCount++;

        String myRes = formatResult(me == 1 ? p1r : p2r, target, me == 1 ? p1sub : p2sub);
        String oppRes = formatResult(me == 1 ? p2r : p1r, target, me == 1 ? p2sub : p1sub);

        int p1RoundScore = r1Score - lastDisplayedR1Score;
        int p2RoundScore = r2Score - lastDisplayedR2Score;
        lastDisplayedR1Score = r1Score;
        lastDisplayedR2Score = r2Score;

        int myRoundScore = me == 1 ? p1RoundScore : p2RoundScore;
        int oppRoundScore = me == 1 ? p2RoundScore : p1RoundScore;

        String msg = "Runda " + round + " - Cilj: " + target + "\n"
                + "Ti: " + myRes + " (" + myRoundScore + " poena)\n"
                + "Protivnik: " + oppRes + " (" + oppRoundScore + " poena)";
        instrView.setText(msg);

        if (iAmFinisher) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (done) return;
                if (round == 1) {
                    startRound2();
                } else {
                    finishGameState();
                }
            }, 7000);
        }
    }

    private String formatResult(double result, int target, boolean submitted) {
        if (!submitted) return "nije uneo";
        boolean exact = Math.abs(result - target) < 0.0001;
        if (exact) return String.format("%.0f (ta\u010Dno!)", result);
        return String.format("%.0f (razlika %.0f)", result, Math.abs(result - target));
    }

    // --- Round Management ---

    private void startRound2() {
        round = 2;
        revealer = 2;
        game = null;
        submitted = false;
        playInitialized = false;
        phaseCompleted = false;
        tokens.clear();
        openParensCount = 0;
        usedNumberButtons.clear();

        Map<String, Object> gs = new HashMap<>();
        gs.put("phase", "reveal_target");
        gs.put("round", 2L);
        gs.put("revealer", 2L);
        gs.put("p1Submitted", 0L);
        gs.put("p2Submitted", 0L);
        gs.put("p1Result", 0.0);
        gs.put("p2Result", 0.0);
        gs.put("round1Score", (long) round1Score);
        gs.put("round2Score", (long) round2Score);
        sm.setGameState(gs);
    }

    private void finishGameState() {
        if (done) return;
        Map<String, Object> ns = new HashMap<>();
        ns.put("phase", "done");
        ns.put("round", (long) round);
        ns.put("round1Score", (long) round1Score);
        ns.put("round2Score", (long) round2Score);
        sm.updateGameState(ns);
    }

    // --- STOP Mechanism ---

    private void onStopClick() {
        if ("reveal_target".equals(phase) && revealer == me) {
            if (stopTimer != null) stopTimer.cancel();
            unregisterShakeListener();
            Map<String, Object> up = new HashMap<>();
            up.put("phase", "reveal_numbers");
            if (game != null) up.put("target", (long) game.targetNumber);
            sm.updateGameState(up);
        } else if ("reveal_numbers".equals(phase) && revealer == me) {
            if (stopTimer != null) stopTimer.cancel();
            unregisterShakeListener();
            Map<String, Object> up = new HashMap<>();
            up.put("phase", "play");
            if (game != null) {
                up.put("target", (long) game.targetNumber);
                List<Long> nums = new ArrayList<>();
                for (Integer v : game.numbers) nums.add(v.longValue());
                up.put("numbers", nums);
            }
            sm.updateGameState(up);
        }
    }

    private void startStopTimer() {
        if (stopTimer != null) stopTimer.cancel();
        stopTimerView.setText("5");
        stopTimer = new CountDownTimer(STOP_TIME_MS, 100) {
            public void onTick(long m) {
                stopTimerView.setText(String.valueOf((int) (m / 1000) + 1));
            }
            public void onFinish() {
                stopTimerView.setText("0");
                runOnUiThread(() -> onStopClick());
            }
        }.start();
    }

    // --- Play Phase ---

    private void startPlayTimer() {
        if (gameTimer != null) gameTimer.cancel();
        gameTimer = new CountDownTimer(GAME_TIME_MS, 100) {
            public void onTick(long m) {
                int sec = (int) (m / 1000) + 1;
                timerView.setText(String.valueOf(sec));
                timerView.setTextColor(sec <= 5 ? 0xFFFF0000 : 0xFFFFFFFF);
            }
            public void onFinish() {
                timerView.setText("0");
                timerView.setTextColor(0xFFFF0000);
                runOnUiThread(() -> {
                    if (!submitted && !done) {
                        submitted = true;
                        myResult = 0;
                        writeSubmission();
                    }
                });
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
        try {
            myResult = evaluate(expr);
        } catch (Exception e) {
            myResult = 0;
        }
        disableAllInputs();
        writeSubmission();
        if (iAmFinisher) {
            checkBothSubmitted();
        }
    }

    private void writeSubmission() {
        String p = me == 1 ? "p1" : "p2";
        sm.updateField("gameState." + p + "Submitted", 1L);
        sm.updateField("gameState." + p + "Result", myResult);
    }

    private void checkAndScore(Map<String, Object> gs) {
        long p1s = gs.containsKey("p1Submitted") ? (long) gs.get("p1Submitted") : 0;
        long p2s = gs.containsKey("p2Submitted") ? (long) gs.get("p2Submitted") : 0;

        if (p1s != 0 && p2s != 0 && iAmFinisher) {
            double p1r = gs.containsKey("p1Result") ? ((Number) gs.get("p1Result")).doubleValue() : 0;
            double p2r = gs.containsKey("p2Result") ? ((Number) gs.get("p2Result")).doubleValue() : 0;
            int targetVal = gs.containsKey("target") ? ((Long) gs.get("target")).intValue() : 0;

            int[] scores = calculateRoundScore(p1r, p2r, targetVal, revealer,
                    p1s != 0, p2s != 0);
            int r1s = round == 1 ? scores[0] : round1Score + scores[0];
            int r2s = round == 1 ? scores[1] : round2Score + scores[1];
            round1Score = r1s;
            round2Score = r2s;

            phaseCompleted = true;
            Map<String, Object> res = new HashMap<>();
            res.put("phase", "result");
            res.put("round", (long) round);
            res.put("round1Score", (long) r1s);
            res.put("round2Score", (long) r2s);
            sm.updateGameState(res);
        }
    }

    private void checkBothSubmitted() {
        // Called by finisher after submitting locally.
        // The next onStateChanged will trigger checkAndScore.
    }

    private int[] calculateRoundScore(double p1r, double p2r, int target,
                                       int roundRevealer, boolean p1sub, boolean p2sub) {
        boolean p1Hit = p1sub && Math.abs(p1r - target) < 0.0001;
        boolean p2Hit = p2sub && Math.abs(p2r - target) < 0.0001;

        if (roundRevealer == 1) {
            if (p1Hit) return new int[]{10, 0};
            if (p2Hit) return new int[]{0, 10};
        } else {
            if (p2Hit) return new int[]{0, 10};
            if (p1Hit) return new int[]{10, 0};
        }

        if (!p1sub && !p2sub) return new int[]{0, 0};
        if (!p1sub) return new int[]{0, 5};
        if (!p2sub) return new int[]{5, 0};

        double d1 = Math.abs(p1r - target);
        double d2 = Math.abs(p2r - target);

        if (d1 < d2) return new int[]{5, 0};
        if (d2 < d1) return new int[]{0, 5};

        return roundRevealer == 1 ? new int[]{5, 0} : new int[]{0, 5};
    }

    // --- UI Helpers ---

    private void disableAllInputs() {
        for (MaterialButton b : numBtns) b.setEnabled(false);
        if (opBtns != null) for (MaterialButton b : opBtns) b.setEnabled(false);
        confirmBtn.setEnabled(false);
        clearBtn.setEnabled(false);
    }

    private void disableNumberButtons() {
        for (MaterialButton b : numBtns) b.setEnabled(false);
    }

    private void finishGame() {
        if (done) return;
        done = true;
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        if (stopTimer != null) { stopTimer.cancel(); stopTimer = null; }
        unregisterShakeListener();

        int totalP1 = previousP1Score + round1Score;
        int totalP2 = previousP2Score + round2Score;
        Map<String, Object> stats = new HashMap<>();
        stats.put("gameType", GameSessionManager.GAME_TYPE_MOJ_BROJ);
        stats.put("p1FoundExact", (long) p1FoundExactCount);
        stats.put("p2FoundExact", (long) p2FoundExactCount);
        stats.put("totalRounds", 2L);
        stats.put("player1Score", (long) round1Score);
        stats.put("player2Score", (long) round2Score);
        sm.finishCurrentGame(gameIdx, round1Score, round2Score, totalP1, totalP2, 6, stats);
        sm.cleanup();
        setResult(RESULT_OK);
        finish();
    }

    // --- Expression Building ---

    private void setupNumberButtons() {
        for (MaterialButton b : numBtns) {
            b.setOnClickListener(v -> {
                if (b.isEnabled() && canAddNumber()) {
                    tokens.add(new Token(b.getText().toString(), TokenType.NUMBER, b));
                    appendExpressionText();
                    b.setEnabled(false);
                    b.setAlpha(0.5f);
                    usedNumberButtons.push(b);
                }
            });
        }
    }

    private void setupOperatorButtons() {
        int[] ids = { R.id.btnOpen, R.id.btnClose, R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide };
        opBtns = new MaterialButton[ids.length];
        for (int i = 0; i < ids.length; i++) {
            MaterialButton b = findViewById(ids[i]);
            opBtns[i] = b;
            b.setOnClickListener(v -> handleOperator(b.getText().toString()));
        }
    }

    private void handleOperator(String op) {
        if ("(".equals(op)) {
            if (canAddOpenParen()) {
                tokens.add(new Token(op, TokenType.OPEN_PAREN, null));
                openParensCount++;
                appendExpressionText();
            }
            return;
        }
        if (")".equals(op)) {
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
        if (removed.type == TokenType.OPEN_PAREN) openParensCount = Math.max(0, openParensCount - 1);
        else if (removed.type == TokenType.CLOSE_PAREN) openParensCount++;
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
        return openParensCount > 0 && !tokens.isEmpty()
                && (tokens.get(tokens.size() - 1).type == TokenType.NUMBER
                || tokens.get(tokens.size() - 1).type == TokenType.CLOSE_PAREN);
    }

    private void appendExpressionText() {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(t.type == TokenType.OPERATOR && t.text.equals("/") ? "÷" : t.text);
        }
        exprView.setText(sb.toString());
    }

    private String buildEvalExpression() {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            sb.append(t.type == TokenType.OPERATOR && t.text.equals("÷") ? "/" : t.text);
        }
        return sb.toString();
    }

    private double evaluate(String expression) {
        Stack<Double> values = new Stack<>();
        Stack<Character> ops = new Stack<>();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (Character.isDigit(c)) {
                StringBuilder sb = new StringBuilder();
                while (i < expression.length() && Character.isDigit(expression.charAt(i))) sb.append(expression.charAt(i++));
                i--;
                values.push(Double.parseDouble(sb.toString()));
            } else if (c == '(') ops.push(c);
            else if (c == ')') {
                while (ops.peek() != '(') values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                ops.pop();
            } else if (c == '+' || c == '-' || c == '*' || c == '/') {
                while (!ops.empty() && hasPrecedence(c, ops.peek())) values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                ops.push(c);
            }
        }
        while (!ops.empty()) values.push(applyOp(ops.pop(), values.pop(), values.pop()));
        return values.pop();
    }

    private boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        return !((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-'));
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
        if (stopTimer != null) stopTimer.cancel();
        unregisterShakeListener();
        if (sm != null) sm.cleanup();
    }
}
