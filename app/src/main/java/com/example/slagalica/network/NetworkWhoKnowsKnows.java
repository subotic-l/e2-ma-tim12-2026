package com.example.slagalica.network;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.slagalica.Question;
import com.example.slagalica.R;
import com.example.slagalica.data.GameSessionManager;
import com.example.slagalica.data.QuestionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkWhoKnowsKnows extends AppCompatActivity {

    private static final int TOTAL_Q = 5;
    private static final int Q_TIME_MS = 5000;

    private GameSessionManager sm;
    private int me, opp, gameIdx;
    private String matchId;
    private List<Question> questions;
    private int curQ = 0;
    private boolean gameStarted = false;
    private boolean answered = false;
    private int myAns = -1;
    private long myTime = -1;
    private boolean waitReveal = false;
    private int localMyPts = 0, localOppPts = 0;
    private boolean done = false;
    private boolean iAmFinisher = false;
    private QuestionRepository questionRepository;

    private TextView timerView, qView, myNameView, oppNameView, myScoreView, oppScoreView;
    private android.widget.ImageView myAvatarView, oppAvatarView;
    private com.google.android.material.button.MaterialButton[] btns;
    private CountDownTimer timer;
    private long remain = Q_TIME_MS;

    private String myName, myAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_who_knows_knows);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        Intent i = getIntent();
        matchId = i.getStringExtra("matchId");
        me = i.getIntExtra("myPlayerNumber", 1);
        gameIdx = i.getIntExtra("gameIndex", 0);
        opp = me == 1 ? 2 : 1;

        timerView = findViewById(R.id.timerTextView);
        qView = findViewById(R.id.questionTextView);
        btns = new com.google.android.material.button.MaterialButton[]{
                findViewById(R.id.answerButton1), findViewById(R.id.answerButton2),
                findViewById(R.id.answerButton3), findViewById(R.id.answerButton4)
        };
        for (int j = 0; j < btns.length; j++) {
            final int f = j;
            btns[j].setOnClickListener(v -> pick(f));
        }

        myNameView = findViewById(R.id.playerOneName);
        oppNameView = findViewById(R.id.playerTwoName);
        myScoreView = findViewById(R.id.playerOneScore);
        oppScoreView = findViewById(R.id.playerTwoScore);
        myAvatarView = findViewById(R.id.playerOneAvatar);
        oppAvatarView = findViewById(R.id.playerTwoAvatar);

        myName = i.getStringExtra("myPlayerName");
        if (myName == null || myName.isEmpty()) myName = "Igrač 1";
        myAvatar = i.getStringExtra("myAvatarUrl");

        for (Button b : btns) b.setVisibility(View.GONE);
        timerView.setVisibility(View.GONE);
        qView.setText("Priprema...");

        questionRepository = new QuestionRepository();
        if (me == 1) {
            loadQuestionsFromFirestore();
        }

        sm = new GameSessionManager();
        sm.attachToMatch(matchId, me);
        sm.listenToMatch(createL());
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
                if (gs == null || gs.isEmpty()) {
                    return;
                }

                if (questions == null) {
                    if (gs.containsKey("questions")) {
                        questions = deserializeQuestions(gs);
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
                        updates.put("phase", "q");
                        sm.updateGameState(updates);
                        runOnUiThread(() -> startNewQ());
                    }
                    return;
                }

                runOnUiThread(() -> process(gs));
            }
            public void onMatchEnded(Map<String, Object> f) {
                if (done) return;
                done = true; if (timer != null) timer.cancel();
                sm.cleanup(); setResult(RESULT_OK); finish();
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
        if (!gs.containsKey("curQ") || done) return;
        long cq = (long) gs.get("curQ");
        if (cq >= TOTAL_Q) {
            if (iAmFinisher) finishGame();
            return;
        }

        long p1Pts = gs.containsKey("p1Pts") ? (long) gs.get("p1Pts") : 0;
        long p2Pts = gs.containsKey("p2Pts") ? (long) gs.get("p2Pts") : 0;
        localMyPts = (int) (me == 1 ? p1Pts : p2Pts);
        localOppPts = (int) (me == 1 ? p2Pts : p1Pts);
        updateScoreDisplay();

        if (cq > curQ) {
            curQ = (int) cq;
            waitReveal = false;
            startNewQ();
            return;
        }
        if (cq < curQ) return;
        if (!gameStarted && cq >= 0) {
            gameStarted = true;
            curQ = (int) cq;
            startNewQ();
            return;
        }

        String phase = (String) gs.get("phase");
        if ("reveal".equals(phase) && !waitReveal) {
            showReveal(gs);
            return;
        }
        if ("q".equals(phase)) {
            long p1a = gs.containsKey("p1Ans") ? (long) gs.get("p1Ans") : -2;
            long p2a = gs.containsKey("p2Ans") ? (long) gs.get("p2Ans") : -2;
            if (p1a != -2 && p2a != -2 && iAmFinisher) {
                sm.updateField("gameState.phase", "reveal");
            }
        }
    }

    private void startNewQ() {
        for (Button b : btns) b.setVisibility(View.VISIBLE);
        timerView.setVisibility(View.VISIBLE);

        myAns = -1; myTime = -1; answered = false;
        Question q = questions.get(curQ);
        qView.setText((curQ + 1) + ". " + q.questionText);
        for (int j = 0; j < btns.length; j++) {
            btns[j].setText(q.answers.get(j));
            btns[j].setEnabled(true);
            btns[j].setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.button_default_color));
            btns[j].setStrokeColor(
                    ContextCompat.getColorStateList(this, R.color.button_default_border));
        }
        timerView.setText("5");
        timerView.setTextColor(0xFFFFFFFF);
        startQTimer();
    }

    private void startQTimer() {
        if (timer != null) timer.cancel();
        remain = Q_TIME_MS;
        timer = new CountDownTimer(Q_TIME_MS, 100) {
            public void onTick(long m) {
                remain = m;
                int sec = (int) (m / 1000) + 1;
                timerView.setText(String.valueOf(sec));
                if (sec <= 2) timerView.setTextColor(0xFFFF0000);
                else timerView.setTextColor(0xFFFFFFFF);
            }
            public void onFinish() {
                timerView.setText("0");
                timerView.setTextColor(0xFFFF0000);
                if (!answered) {
                    answered = true; myAns = -1; myTime = Q_TIME_MS;
                    writeAns();
                }
            }
        }.start();
    }

    private void pick(int idx) {
        if (answered) return;
        answered = true; myAns = idx; myTime = Q_TIME_MS - remain;
        if (timer != null) { timer.cancel(); timer = null; }
        if (me == 1) {
            btns[idx].setBackgroundTintList(ColorStateList.valueOf(0x662196F3));
            btns[idx].setStrokeColor(ColorStateList.valueOf(0xFF1565C0));
        } else {
            btns[idx].setBackgroundTintList(ColorStateList.valueOf(0x66FFAA00));
            btns[idx].setStrokeColor(ColorStateList.valueOf(0xFFFF8800));
        }
        for (int j = 0; j < btns.length; j++) if (j != idx) btns[j].setEnabled(false);
        writeAns();
    }

    private void writeAns() {
        String p = me == 1 ? "p1" : "p2";
        Map<String, Object> gs = new HashMap<>();
        gs.put(p + "Ans", (long) myAns);
        gs.put(p + "Time", myTime);
        sm.updateGameState(gs);
    }

    private void showReveal(Map<String, Object> gs) {
        waitReveal = true;
        if (timer != null) { timer.cancel(); timer = null; }
        for (Button b : btns) b.setEnabled(false);

        long p1a = gs.containsKey("p1Ans") ? (long) gs.get("p1Ans") : -2;
        long p2a = gs.containsKey("p2Ans") ? (long) gs.get("p2Ans") : -2;
        long p1t = gs.containsKey("p1Time") ? (long) gs.get("p1Time") : -1;
        long p2t = gs.containsKey("p2Time") ? (long) gs.get("p2Time") : -1;
        Question q = questions.get(curQ);
        int correct = q.correctAnswerIndex;

        int p1pts = pts((int) p1a, (int) p2a, p1t, p2t, correct);
        int p2pts = pts((int) p2a, (int) p1a, p2t, p1t, correct);
        localMyPts += (me == 1 ? p1pts : p2pts);
        localOppPts += (me == 1 ? p2pts : p1pts);
        updateScoreDisplay();

        int cBdr = ContextCompat.getColor(this, R.color.correct_answer_border);
        int wBdr = ContextCompat.getColor(this, R.color.wrong_answer_border);

        for (int j = 0; j < 4; j++) {
            boolean isCorrect = (j == correct);
            boolean p1Selected = ((int) p1a == j);
            boolean p2Selected = ((int) p2a == j);

            btns[j].setStrokeColor(ColorStateList.valueOf(isCorrect ? cBdr : wBdr));
            btns[j].setStrokeWidth(10);

            if (p1Selected && p2Selected) {
                btns[j].setBackgroundTintList(ColorStateList.valueOf(0xFF9C27B0));
            } else if (p1Selected) {
                btns[j].setBackgroundTintList(ColorStateList.valueOf(0xFF2196F3));
            } else if (p2Selected) {
                btns[j].setBackgroundTintList(ColorStateList.valueOf(0xFFFF9800));
            } else {
                btns[j].setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.button_default_color));
            }
        }

        qView.setText((curQ + 1) + ". " + q.questionText);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (done) return;
            if (iAmFinisher) {
                int next = curQ + 1;
                if (next >= TOTAL_Q) {
                    Map<String, Object> gs2 = new HashMap<>();
                    gs2.put("curQ", (long) TOTAL_Q);
                    gs2.put("phase", "done");
                    gs2.put("p1Pts", (long) localMyPts);
                    gs2.put("p2Pts", (long) localOppPts);
                    sm.setGameState(gs2);
                } else {
                    Map<String, Object> ns = new HashMap<>();
                    ns.put("curQ", (long) next);
                    ns.put("phase", "q");
                    ns.put("p1Ans", -2L);
                    ns.put("p2Ans", -2L);
                    ns.put("p1Time", -1L);
                    ns.put("p2Time", -1L);
                    ns.put("p1Pts", (long) localMyPts);
                    ns.put("p2Pts", (long) localOppPts);
                    sm.setGameState(ns);
                }
            }
        }, 3000);
    }

    private int pts(int myA, int oppA, long myT, long oppT, int correct) {
        boolean mc = myA == correct, oc = oppA == correct;
        if (mc && oc) return myT <= oppT ? 10 : 0;
        if (mc) return 10;
        if (myA >= 0) return -5;
        return 0;
    }

    private void finishGame() {
        if (done) return; done = true;
        if (timer != null) { timer.cancel(); timer = null; }
        sm.finishCurrentGame(gameIdx, localMyPts, localOppPts, localMyPts, localOppPts, 4);
        sm.cleanup();
        setResult(RESULT_OK);
        finish();
    }

    private void updateScoreDisplay() {
        myScoreView.setText(String.valueOf(localMyPts));
        oppScoreView.setText(String.valueOf(localOppPts));
    }

    private void writeInitialState() {
        gameStarted = true;
        curQ = 0;
        iAmFinisher = true;
        Map<String, Object> gs2 = new HashMap<>();
        gs2.put("curQ", 0L);
        gs2.put("phase", "loading");
        gs2.put("p1Ans", -2L);
        gs2.put("p2Ans", -2L);
        gs2.put("p1Time", -1L);
        gs2.put("p2Time", -1L);
        gs2.put("p1Pts", 0L);
        gs2.put("p2Pts", 0L);
        gs2.put("questions", serializeQuestions(questions));
        gs2.put("player1Ready", true);
        sm.setGameState(gs2);
    }

    private void loadQuestionsFromFirestore() {
        questionRepository.getRandomQuestions()
                .addOnSuccessListener(loaded -> {
                    questions = loaded;
                    writeInitialState();
                })
                .addOnFailureListener(e -> {
                    questions = new ArrayList<>();
                });
    }

    private List<Map<String, Object>> serializeQuestions(List<Question> qs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Question q : qs) {
            Map<String, Object> m = new HashMap<>();
            m.put("questionText", q.questionText);
            m.put("answers", q.answers);
            m.put("correctAnswerIndex", (long) q.correctAnswerIndex);
            result.add(m);
        }
        return result;
    }

    private List<Question> deserializeQuestions(Map<String, Object> gs) {
        List<Map<String, Object>> qMaps = (List<Map<String, Object>>) gs.get("questions");
        List<Question> result = new ArrayList<>();
        for (Map<String, Object> m : qMaps) {
            String qText = (String) m.get("questionText");
            List<String> answers = (List<String>) m.get("answers");
            Long correctIdx = (Long) m.get("correctAnswerIndex");
            if (qText != null && answers != null && correctIdx != null) {
                result.add(new Question(qText, answers, correctIdx.intValue()));
            }
        }
        return result;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (sm != null) sm.cleanup();
    }
}
