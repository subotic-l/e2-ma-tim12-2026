package com.example.slagalica.network;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.GameSessionManager;
import com.example.slagalica.data.StepByStepRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.*;

public class NetworkStepByStep extends AppCompatActivity {

    private static final int STEP_TIME = 10000;
    private static final int MAX_STEPS = 7;

    private GameSessionManager sm;
    private int me, opp, gameIdx;
    private String matchId;

    private TextView timerView, pointsView;
    private TextView[] cluesView;
    private TextInputEditText input;
    private MaterialButton btn;

    private CountDownTimer timer;

    private int step = 0;
    private int round = 0;
    private boolean myTurn = false;
    private boolean stealPhase = false;
    private boolean iAmFinisher = false;

    private int myScore = 0, oppScore = 0;

    private boolean done = false;
    private StepByStepRepository repo;

    private List<String> clues = new ArrayList<>();
    private String answer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_by_step);

        Intent i = getIntent();
        matchId = i.getStringExtra("matchId");
        me = i.getIntExtra("myPlayerNumber", 1);
        gameIdx = i.getIntExtra("gameIndex", 0);
        opp = me == 1 ? 2 : 1;

        timerView = findViewById(R.id.timerTextView);
        pointsView = findViewById(R.id.pointsTextView);
        input = findViewById(R.id.guessInput);
        btn = findViewById(R.id.submitGuessButton);
        repo = new StepByStepRepository();

        cluesView = new TextView[]{
                findViewById(R.id.clue1),
                findViewById(R.id.clue2),
                findViewById(R.id.clue3),
                findViewById(R.id.clue4),
                findViewById(R.id.clue5),
                findViewById(R.id.clue6),
                findViewById(R.id.clue7)
        };

        btn.setOnClickListener(v -> submit());

        sm = new GameSessionManager();
        sm.attachToMatch(matchId, me);
        sm.listenToMatch(createListener());

        if (me == 1) {
            initGame();
        }
    }

    private GameSessionManager.StateListener createListener() {
        return new GameSessionManager.StateListener() {
            @Override
            public void onStateChanged(Map<String, Object> full) {

                if (done) return;

                Map<String, Object> gs = (Map<String, Object>) full.get("gameState");
                if (gs == null) return;

                step = ((Long) gs.getOrDefault("step", 0L)).intValue();
                round = ((Long) gs.getOrDefault("round", 0L)).intValue();
                String phase = (String) gs.getOrDefault("phase", "PLAY");

                int currentPlayer = ((Long) gs.getOrDefault("currentPlayer", 1L)).intValue();

                myTurn = currentPlayer == me;
                stealPhase = "STEAL".equals(phase);

                myScore = ((Long) gs.getOrDefault(me == 1 ? "p1Score" : "p2Score", 0L)).intValue();
                oppScore = ((Long) gs.getOrDefault(me == 1 ? "p2Score" : "p1Score", 0L)).intValue();

                runOnUiThread(() -> updateUI());

                if (myTurn && !"FINISHED".equals(phase)) {
                    startTimer();
                } else {
                    stopTimer();
                }

                if ("FINISHED".equals(phase)) {
                    finishGame();
                }
            }

            @Override public void onMatchEnded(Map<String, Object> f) {}
            @Override public void onError(String e) {}
        };
    }

    private void initGame() {
        iAmFinisher = true;
        loadGameFromFirebase();
        Map<String, Object> gs = new HashMap<>();
        gs.put("step", 0L);
        gs.put("round", 0L);
        gs.put("phase", "PLAY");
        gs.put("currentPlayer", 1L);
        gs.put("p1Score", 0L);
        gs.put("p2Score", 0L);

        sm.setGameState(gs);
    }

    private void loadGameFromFirebase() {
        repo.getRandomGame()
                .addOnSuccessListener(game -> {

                    clues = game.getClues();
                    answer = game.getAnswer();

                    runOnUiThread(this::updateUI);
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                });
    }

    private void updateUI() {

        for (int i = 0; i < MAX_STEPS; i++) {
            if (i <= step) cluesView[i].setText(clues.get(i));
            else cluesView[i].setText("(zatvoreno)");
        }

        int points = Math.max(0, 20 - step * 2);
        pointsView.setText("Bodovi: " + points);

    }

    private void startTimer() {
        if (timer != null) timer.cancel();

        timer = new CountDownTimer(STEP_TIME, 1000) {
            public void onTick(long ms) {
                timerView.setText("Vreme: " + (ms / 1000 + 1));
            }

            public void onFinish() {
                next();
            }
        }.start();
    }

    private void stopTimer() {
        if (timer != null) timer.cancel();
    }

    private void submit() {
        String g = input.getText() != null ? input.getText().toString().trim() : "";

        if (g.equalsIgnoreCase(answer)) {

            int pts = stealPhase ? 5 : Math.max(0, 20 - step * 2);

            addScore(pts);
            finishRound();

        } else {
            input.setText("");
        }
    }

    private void next() {

        if (!myTurn) return;

        if (step < 6) {
            sm.updateField("gameState.step", (long) (step + 1));
        } else {

            if (!stealPhase) {
                sm.updateField("gameState.phase", "STEAL");
                switchPlayer();
            } else {
                finishRound();
            }
        }
    }

    private void switchPlayer() {
        int next = (me == 1) ? 2 : 1;
        sm.updateField("gameState.currentPlayer", (long) next);
    }

    private void addScore(int pts) {
        String key = me == 1 ? "p1Score" : "p2Score";
        int newScore = myScore + pts;
        sm.updateField("gameState." + key, (long) newScore);
    }

    private void finishRound() {

        if (round == 0) {
            Map<String, Object> up = new HashMap<>();
            up.put("round", 1L);
            up.put("step", 0L);
            up.put("phase", "PLAY");
            up.put("currentPlayer", (long) opp);
            sm.updateGameState(up);
        } else {
            sm.updateField("gameState.phase", "FINISHED");
        }
    }

    private void finishGame() {
        if (done) return;
        done = true;

        sm.finishCurrentGame(gameIdx, myScore, oppScore, myScore, oppScore);
        sm.cleanup();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            setResult(RESULT_OK);
            finish();
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (sm != null) sm.cleanup();
    }
}