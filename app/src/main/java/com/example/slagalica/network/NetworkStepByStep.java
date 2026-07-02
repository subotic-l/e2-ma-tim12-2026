package com.example.slagalica.network;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.slagalica.R;
import com.example.slagalica.StepByStepGame;
import com.example.slagalica.data.GameSessionManager;
import com.example.slagalica.data.StepByStepRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.*;

public class NetworkStepByStep extends AppCompatActivity {

    private static final int TOTAL_TIME_MS = 70000;
    private static final int STEAL_TIME_MS = 10000;

    private GameSessionManager sm;
    private int me, opp, gameIdx;
    private String matchId;

    private TextView timerView, pointsView;
    private TextView[] cluesView;
    private TextInputEditText input;
    private MaterialButton btn;

    private TextView myNameView, oppNameView, myScoreView, oppScoreView;
    private android.widget.ImageView myAvatarView, oppAvatarView;

    private CountDownTimer timer;

    private int step = 0;
    private int round = 0;
    private boolean myTurn = false;
    private boolean stealPhase = false;
    private boolean done = false;

    private int myScore = 0;
    private int oppScore = 0;

    private StepByStepRepository repo;
    //private int totalGames;
    private int previousP1Score = 0;
    private int previousP2Score = 0;

    private List<String> clues = new ArrayList<>();
    private String answer = "";
    private List<String> round2Clues = new ArrayList<>();
    private String round2Answer = "";

    private boolean isTimerRunning = false;
    private boolean roundEnding = false;
    private String myName, myAvatar;
    private int p1StepFound = -1, p2StepFound = -1;
    private boolean p1StealSuccess = false, p2StealSuccess = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_by_step);

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
        pointsView = findViewById(R.id.pointsTextView);
        input = findViewById(R.id.guessInput);
        btn = findViewById(R.id.submitGuessButton);

        myNameView = findViewById(R.id.playerOneName);
        oppNameView = findViewById(R.id.playerTwoName);
        myScoreView = findViewById(R.id.playerOneScore);
        oppScoreView = findViewById(R.id.playerTwoScore);
        myAvatarView = findViewById(R.id.playerOneAvatar);
        oppAvatarView = findViewById(R.id.playerTwoAvatar);

        myName = i.getStringExtra("myPlayerName");
        if (myName == null || myName.isEmpty()) myName = "Igra\u010D 1";
        myAvatar = i.getStringExtra("myAvatarUrl");

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

        if (spectator) {
            input.setEnabled(false);
            btn.setEnabled(false);
        }

        if (me == 1) {
            initGame();
        }
    }

    private GameSessionManager.StateListener createListener() {
        return new GameSessionManager.StateListener() {
            @Override
            public void onStateChanged(Map<String, Object> full) {
                if (done) return;

                String p1n = (String) full.get("player1Name");
                String p2n = (String) full.get("player2Name");
                String p1a = (String) full.get("player1Avatar");
                String p2a = (String) full.get("player2Avatar");

                runOnUiThread(() -> {
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
                });

                Map<String, Object> gs = (Map<String, Object>) full.get("gameState");
                if (gs == null) return;

                String phase = (String) gs.getOrDefault("phase", "init");

                if ("init".equals(phase)) {
                    runOnUiThread(() -> showWaiting());
                    return;
                }

                step = ((Long) gs.getOrDefault("step", 0L)).intValue();
                round = ((Long) gs.getOrDefault("round", 0L)).intValue();
                Object p1sf = gs.get("p1StepFound");
                if (p1sf instanceof Long) p1StepFound = ((Long) p1sf).intValue();
                Object p2sf = gs.get("p2StepFound");
                if (p2sf instanceof Long) p2StepFound = ((Long) p2sf).intValue();
                stealPhase = "steal".equals(phase);

                int currentPlayer = ((Long) gs.getOrDefault("currentPlayer", 1L)).intValue();
                myTurn = currentPlayer == me;

                myScore = ((Long) gs.getOrDefault(me == 1 ? "p1Score" : "p2Score", 0L)).intValue();
                oppScore = ((Long) gs.getOrDefault(me == 1 ? "p2Score" : "p1Score", 0L)).intValue();

                List<String> fbClues = (List<String>) gs.get("clues");
                if (fbClues != null && !fbClues.isEmpty()) {
                    clues = fbClues;
                }
                String fbAnswer = (String) gs.get("answer");
                if (fbAnswer != null && !fbAnswer.isEmpty()) {
                    answer = fbAnswer;
                }
                List<String> fbClues2 = (List<String>) gs.get("clues2");
                if (fbClues2 != null && !fbClues2.isEmpty()) {
                    round2Clues = fbClues2;
                }
                String fbAnswer2 = (String) gs.get("answer2");
                if (fbAnswer2 != null && !fbAnswer2.isEmpty()) {
                    round2Answer = fbAnswer2;
                }

                runOnUiThread(() -> updateUI());

                if (myTurn && !"finished".equals(phase) && !roundEnding) {
                    if (!isTimerRunning) {
                        startTimer();
                    }
                } else {
                    stopTimer();
                }

                if ("finished".equals(phase)) {
                    finishGame();
                }
            }

            @Override public void onMatchEnded(Map<String, Object> f) {
                if (done) return;
                done = true;
                stopTimer();
                if (sm != null) sm.cleanup();
                setResult(RESULT_OK);
                finish();
            }

            @Override public void onError(String e) {}
        };
    }

    private void showWaiting() {
        for (int i = 0; i < 7; i++) {
            cluesView[i].setText("");
            MaterialCardView card = (MaterialCardView) cluesView[i].getParent();
            card.setCardBackgroundColor(0xFF2D2D2D);
        }
        pointsView.setText("Bodovi: 0");
        timerView.setText("--");
        input.setEnabled(false);
        btn.setEnabled(false);
    }

    private void initGame() {
        Map<String, Object> gs = new HashMap<>();
        gs.put("phase", "init");
        gs.put("round", 0L);
        gs.put("step", 0L);
        gs.put("currentPlayer", 1L);
        gs.put("p1Score", 0L);
        gs.put("p2Score", 0L);
        sm.setGameState(gs);
        loadGameFromFirebase();
    }

    private void loadGameFromFirebase() {
        repo.getTwoRandomGames()
                .addOnSuccessListener(games -> {
                    StepByStepGame game1 = games.get(0);
                    StepByStepGame game2 = games.get(1);
                    clues = game1.getClues();
                    answer = game1.getAnswer();
                    round2Clues = game2.getClues();
                    round2Answer = game2.getAnswer();
                    Map<String, Object> up = new HashMap<>();
                    up.put("clues", game1.getClues());
                    up.put("answer", game1.getAnswer());
                    up.put("clues1", game1.getClues());
                    up.put("answer1", game1.getAnswer());
                    up.put("clues2", game2.getClues());
                    up.put("answer2", game2.getAnswer());
                    up.put("phase", "play");
                    sm.updateGameState(up);
                    runOnUiThread(this::updateUI);
                })
                .addOnFailureListener(e -> e.printStackTrace());
    }

    private void updateUI() {
        if (clues.size() < 7) {
            showWaiting();
            return;
        }

        for (int i = 0; i < 7; i++) {
            MaterialCardView card = (MaterialCardView) cluesView[i].getParent();
            if (i <= step) {
                cluesView[i].setText(clues.get(i));
                card.setCardBackgroundColor(0xFF1565C0);
            } else {
                cluesView[i].setText("");
                card.setCardBackgroundColor(0xFF2D2D2D);
            }
        }

        if (roundEnding) {
            pointsView.setText("Ta\u010Dno! +" + (stealPhase ? 5 : Math.max(0, 20 - step * 2)) + " poena");
        } else if (myTurn) {
            if (stealPhase) {
                pointsView.setText("Tvoj poku\u0161aj: 5 poena");
            } else {
                int pts = Math.max(0, 20 - step * 2);
                pointsView.setText("Ti si na potezu - " + pts + " poena");
            }
        } else if (stealPhase) {
            pointsView.setText("Uzmi poene: 5 poena - Na potezu: " + oppNameView.getText());
        } else {
            pointsView.setText("Na potezu: " + oppNameView.getText());
        }

        input.setEnabled(myTurn && !roundEnding);
        btn.setEnabled(myTurn && !roundEnding);

        int totalMy = previousP1Score + (me == 1 ? myScore : oppScore);
        int totalOpp = previousP2Score + (me == 1 ? oppScore : myScore);
        myScoreView.setText(String.valueOf(totalMy));
        oppScoreView.setText(String.valueOf(totalOpp));
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        isTimerRunning = true;

        long duration = stealPhase ? STEAL_TIME_MS : TOTAL_TIME_MS;
        long firstTrigger = stealPhase ? 0 : duration - 10000;

        timer = new CountDownTimer(duration, 1000) {
            private long nextTrigger = firstTrigger;

            public void onTick(long ms) {
                timerView.setText(String.valueOf(ms / 1000 + 1));
                timerView.setTextColor(ms <= 3000 ? 0xFFFF0000 : 0xFFFFFFFF);

                if (ms <= nextTrigger && !stealPhase && !roundEnding) {
                    nextTrigger -= 10000;
                    runOnUiThread(() -> {
                        if (step < 6) {
                            sm.updateField("gameState.step", (long) (step + 1));
                        }
                    });
                }
            }

            public void onFinish() {
                timerView.setText("0");
                timerView.setTextColor(0xFFFF0000);
                runOnUiThread(() -> next());
            }
        }.start();
    }

    private void stopTimer() {
        isTimerRunning = false;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void submit() {
        if (!myTurn) return;
        String g = input.getText() != null ? input.getText().toString().trim() : "";
        if (g.equalsIgnoreCase(answer)) {
            int pts = stealPhase ? 5 : Math.max(0, 20 - step * 2);
            addScore(pts);
            roundEnding = true;
            stopTimer();
            sm.updateField("gameState.step", 6L);
            input.setEnabled(false);
            btn.setEnabled(false);
            if (stealPhase) {
                if (round == 0) p1StealSuccess = true;
                else p2StealSuccess = true;
            } else {
                if (round == 0) p1StepFound = step;
                else p2StepFound = step;
                sm.updateField("gameState." + (round == 0 ? "p1StepFound" : "p2StepFound"), (long) step);
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                advanceRound();
            }, 2500);
        } else {
            input.setText("");
        }
    }

    private void next() {
        if (!myTurn) return;
        if (stealPhase) {
            advanceRound();
        } else if (step < 6) {
            sm.updateField("gameState.step", (long) (step + 1));
        } else {
            Map<String, Object> up = new HashMap<>();
            up.put("phase", "steal");
            up.put("currentPlayer", (long) opp);
            sm.updateGameState(up);
        }
    }

    private void addScore(int pts) {
        String key = me == 1 ? "p1Score" : "p2Score";
        int newScore = myScore + pts;
        sm.updateField("gameState." + key, (long) newScore);
    }

    private void advanceRound() {
        roundEnding = false;
        if (round == 0) {
            Map<String, Object> up = new HashMap<>();
            up.put("round", 1L);
            up.put("step", 0L);
            up.put("phase", "play");
            up.put("currentPlayer", 2L);
            if (!round2Clues.isEmpty()) up.put("clues", round2Clues);
            if (!round2Answer.isEmpty()) up.put("answer", round2Answer);
            sm.updateGameState(up);
        } else {
            sm.updateField("gameState.phase", "finished");
        }
    }

    private void finishGame() {
        if (done) return;
        done = true;
        stopTimer();

        int totalP1 = previousP1Score + (me == 1 ? myScore : oppScore);
        int totalP2 = previousP2Score + (me == 1 ? oppScore : myScore);
        int gameP1 = me == 1 ? myScore : oppScore;
        int gameP2 = me == 1 ? oppScore : myScore;
        Map<String, Object> stats = new HashMap<>();
        stats.put("gameType", GameSessionManager.GAME_TYPE_KORAK_PO_KORAK);
        stats.put("p1StepFound", (long) p1StepFound);
        stats.put("p2StepFound", (long) p2StepFound);
        stats.put("p1StealSuccess", p1StealSuccess);
        stats.put("p2StealSuccess", p2StealSuccess);
        stats.put("player1Score", (long) gameP1);
        stats.put("player2Score", (long) gameP2);
        sm.finishCurrentGame(gameIdx, gameP1, gameP2, totalP1, totalP2, 6, stats);
        sm.cleanup();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            setResult(RESULT_OK);
            finish();
        }, 1500);
    }

    private void loadAvatar(android.widget.ImageView iv, String url) {
        Glide.with(this)
                .load(url != null && !url.isEmpty() ? url : R.drawable.default_profile)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(iv);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (sm != null) sm.cleanup();
    }
}
