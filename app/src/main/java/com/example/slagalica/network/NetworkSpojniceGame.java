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
import com.example.slagalica.MatchingGame;
import com.example.slagalica.R;
import com.example.slagalica.data.GameSessionManager;
import com.example.slagalica.data.SpojniceRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkSpojniceGame extends AppCompatActivity {

    private static final int TOTAL_ITEMS = 5;
    private static final int ROUND_TIME = 30;
    private static final int WRONG_FLASH_MS = 400;
    private static final int END_DELAY_MS = 2000;
    private static final int P1_MATCH_COLOR = 0xFF1565C0;
    private static final int P2_MATCH_COLOR = 0xFFE65100;

    private static final String PHASE_INIT = "init";
    private static final String PHASE_R1_PLAY = "round1_play";
    private static final String PHASE_R1_STEAL = "round1_steal";
    private static final String PHASE_R2_PLAY = "round2_play";
    private static final String PHASE_R2_STEAL = "round2_steal";
    private static final String PHASE_DONE = "done";

    private GameSessionManager sm;
    private int me, opp, gameIdx;
    private String matchId;
    private SpojniceRepository spojniceRepository;
    private int previousP1Score = 0;
    private int previousP2Score = 0;

    private List<MatchingGame> games;
    private MatchingGame currentGame;
    private int[] shuffleOrder;
    private int[] shuffleOrder0;
    private int[] shuffleOrder1;

    private boolean gamesLoaded = false;
    private boolean iAmFinisher = false;
    private boolean finished = false;

    // Synced state (mirrors Firestore gameState)
    private String syncPhase = PHASE_INIT;
    private int syncActivePlayer = 1;
    private boolean[] syncLeftMatched = new boolean[TOTAL_ITEMS];
    private boolean[] syncRightUsed = new boolean[TOTAL_ITEMS];
    private int[] syncMatchedByPlayer = new int[TOTAL_ITEMS];
    private int[] syncRightMatchedByPlayer = new int[TOTAL_ITEMS];
    private int syncCurrentLeft = 0;
    private long syncP1Score = 0;
    private long syncP2Score = 0;

    private String lastPhase = "";
    private boolean isMyTurn = false;

    private boolean localFlashWrong = false;
    private int flashRightIndex = -1;

    private long syncP1ConnectedTotal = 0;
    private long syncP2ConnectedTotal = 0;
    private long syncP1Opportunities = 0;
    private long syncP2Opportunities = 0;

    private int localMyPts = 0;
    private int localOppPts = 0;

    private CountDownTimer timer;
    private boolean timerRunning = false;
    private int remainingSec = ROUND_TIME;

    private TextView timerView, instrView, myNameView, oppNameView, myScoreView, oppScoreView;
    private android.widget.ImageView myAvatarView, oppAvatarView;
    private com.google.android.material.button.MaterialButton[] leftBtns;
    private com.google.android.material.button.MaterialButton[] rightBtns;

    private int defaultColor, defaultBorder, selectedColor, selectedBorder;
    private int correctColor, correctBorder, wrongColor, wrongBorder;

    private String myName, myAvatar;
    private int totalGames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_matching_game);
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
        previousP1Score = i.getIntExtra("previousPlayer1Score", 0);
        previousP2Score = i.getIntExtra("previousPlayer2Score", 0);
        opp = me == 1 ? 2 : 1;
        boolean spectator = i.getBooleanExtra("isSpectator", false);

        timerView = findViewById(R.id.timerTextView);
        instrView = findViewById(R.id.instructionsTextView);
        leftBtns = new com.google.android.material.button.MaterialButton[TOTAL_ITEMS];
        rightBtns = new com.google.android.material.button.MaterialButton[TOTAL_ITEMS];
        leftBtns[0] = findViewById(R.id.leftButton1);
        leftBtns[1] = findViewById(R.id.leftButton2);
        leftBtns[2] = findViewById(R.id.leftButton3);
        leftBtns[3] = findViewById(R.id.leftButton4);
        leftBtns[4] = findViewById(R.id.leftButton5);
        rightBtns[0] = findViewById(R.id.rightButton1);
        rightBtns[1] = findViewById(R.id.rightButton2);
        rightBtns[2] = findViewById(R.id.rightButton3);
        rightBtns[3] = findViewById(R.id.rightButton4);
        rightBtns[4] = findViewById(R.id.rightButton5);

        for (int j = 0; j < TOTAL_ITEMS; j++) {
            final int f = j;
            rightBtns[j].setOnClickListener(v -> onRightClicked(f));
        }

        myNameView = findViewById(R.id.playerOneName);
        oppNameView = findViewById(R.id.playerTwoName);
        myScoreView = findViewById(R.id.playerOneScore);
        oppScoreView = findViewById(R.id.playerTwoScore);
        myAvatarView = findViewById(R.id.playerOneAvatar);
        oppAvatarView = findViewById(R.id.playerTwoAvatar);

        myName = i.getStringExtra("myPlayerName");
        if (myName == null || myName.isEmpty()) myName = me == 1 ? "Igrač 1" : "Igrač 2";
        myAvatar = i.getStringExtra("myAvatarUrl");

        defaultColor = ContextCompat.getColor(this, R.color.button_default_color);
        defaultBorder = ContextCompat.getColor(this, R.color.button_default_border);
        selectedColor = ContextCompat.getColor(this, R.color.button_selected_color);
        selectedBorder = ContextCompat.getColor(this, R.color.button_selected_border);
        correctColor = ContextCompat.getColor(this, R.color.correct_answer_color);
        correctBorder = ContextCompat.getColor(this, R.color.correct_answer_border);
        wrongColor = ContextCompat.getColor(this, R.color.wrong_answer_color);
        wrongBorder = ContextCompat.getColor(this, R.color.wrong_answer_border);

        for (Button b : leftBtns) b.setVisibility(View.GONE);
        for (Button b : rightBtns) b.setVisibility(View.GONE);
        timerView.setVisibility(View.GONE);
        instrView.setText("Priprema...");

        spojniceRepository = new SpojniceRepository();
        if (me == 1) {
            loadGamesFromFirestore();
        }

        sm = new GameSessionManager();
        sm.attachToMatch(matchId, me);
        sm.listenToMatch(createListener());

        if (spectator) {
            for (Button b : leftBtns) b.setEnabled(false);
            for (Button b : rightBtns) b.setEnabled(false);
        }
    }

    private GameSessionManager.StateListener createListener() {
        return new GameSessionManager.StateListener() {
            public void onStateChanged(Map<String, Object> full) {
                if (finished || isFinishing()) return;

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

                // First time receiving games from state
                if (!gamesLoaded) {
                    if (gs.containsKey("games") && gs.get("games") != null) {
                        try {
                            games = deserializeGamesOnly(gs);
                            if (games.isEmpty()) {
                                instrView.setText("Greška pri učitavanju igre");
                                return;
                            }
                            // Read both shuffle orders from state (stored once by P1)
                            shuffleOrder0 = readIntArray(gs.get("shuffleOrder0"), TOTAL_ITEMS);
                            shuffleOrder1 = readIntArray(gs.get("shuffleOrder1"), TOTAL_ITEMS);
                            // Set initial game/shuffle based on current phase
                            boolean inR2 = PHASE_R2_PLAY.equals(gs.get("phase")) || PHASE_R2_STEAL.equals(gs.get("phase"));
                            currentGame = inR2 && games.size() > 1 ? games.get(1) : games.get(0);
                            shuffleOrder = inR2 ? shuffleOrder1 : shuffleOrder0;

                            if (me == 2) {
                                markReady(gs);
                            }
                            gamesLoaded = true;
                        } catch (Exception e) {
                            instrView.setText("Greška pri učitavanju igre");
                        }
                    }
                    return;
                }

                // After games loaded, forward phase changes to UI
                if (PHASE_INIT.equals(gs.get("phase"))) {
                    handleInitPhase(gs);
                    return;
                }

                runOnUiThread(() -> {
                    syncFromState(gs);
                    updateUI();
                });
            }

            public void onMatchEnded(Map<String, Object> f) {
                if (finished) return;
                finished = true;
                if (timer != null) timer.cancel();
                sm.cleanup();
                setResult(RESULT_OK);
                finish();
            }

            public void onError(String e) {}
        };
    }

    private void handleInitPhase(Map<String, Object> gs) {
        Object readyObj = gs.get("playerReady");
        int ready = readyObj instanceof Long ? ((Long) readyObj).intValue() : 0;
        if (me == 1 && ready == 3) {
            startRound1();
        }
    }

    private void markReady(Map<String, Object> gs) {
        int cur = 0;
        Object r = gs.get("playerReady");
        if (r instanceof Long) cur = ((Long) r).intValue();
        int newVal = (me == 1) ? (cur | 1) : (cur | 2);
        sm.updateField("gameState.playerReady", (long) newVal);
    }

    private void syncFromState(Map<String, Object> gs) {
        syncPhase = (String) gs.get("phase");
        if (syncPhase == null) syncPhase = PHASE_INIT;

        Object ap = gs.get("activePlayer");
        syncActivePlayer = ap instanceof Long ? ((Long) ap).intValue() : 1;

        syncLeftMatched = readBoolArray(gs.get("leftMatched"), TOTAL_ITEMS);
        syncRightUsed = readBoolArray(gs.get("rightUsed"), TOTAL_ITEMS);
        syncMatchedByPlayer = readIntArray(gs.get("matchedByPlayer"), TOTAL_ITEMS);
        syncRightMatchedByPlayer = readIntArray(gs.get("rightMatchedByPlayer"), TOTAL_ITEMS);

        Object cli = gs.get("currentLeftIndex");
        syncCurrentLeft = cli instanceof Long ? ((Long) cli).intValue() : 0;

        Object p1 = gs.get("p1Score");
        Object p2 = gs.get("p2Score");
        syncP1Score = p1 instanceof Long ? (Long) p1 : 0;
        syncP2Score = p2 instanceof Long ? (Long) p2 : 0;

        // Select correct game & shuffle for current phase
        boolean inR2 = PHASE_R2_PLAY.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase);
        shuffleOrder = inR2 ? shuffleOrder1 : shuffleOrder0;
        if (games != null && !games.isEmpty()) {
            currentGame = inR2 && games.size() > 1 ? games.get(1) : games.get(0);
        }

        syncP1ConnectedTotal = gs.get("p1ConnectedTotal") instanceof Long ? (Long) gs.get("p1ConnectedTotal") : 0;
        syncP2ConnectedTotal = gs.get("p2ConnectedTotal") instanceof Long ? (Long) gs.get("p2ConnectedTotal") : 0;
        syncP1Opportunities = gs.get("p1Opportunities") instanceof Long ? (Long) gs.get("p1Opportunities") : 0;
        syncP2Opportunities = gs.get("p2Opportunities") instanceof Long ? (Long) gs.get("p2Opportunities") : 0;

        localMyPts = (int) (me == 1 ? syncP1Score : syncP2Score);
        localOppPts = (int) (me == 1 ? syncP2Score : syncP1Score);
    }

    private void updateUI() {
        isMyTurn = (syncActivePlayer == me);

        boolean isDone = PHASE_DONE.equals(syncPhase);
        boolean isInit = PHASE_INIT.equals(syncPhase);
        boolean isR1Play = PHASE_R1_PLAY.equals(syncPhase);
        boolean isR1Steal = PHASE_R1_STEAL.equals(syncPhase);
        boolean isR2Play = PHASE_R2_PLAY.equals(syncPhase);
        boolean isR2Steal = PHASE_R2_STEAL.equals(syncPhase);

        updateScoreDisplay();

        // Detect phase change and trigger side effects
        if (!syncPhase.equals(lastPhase)) {
            lastPhase = syncPhase;
            onPhaseChanged();
        }

        if (isDone) {
            instrView.setText("Kraj igre");
            timerView.setVisibility(View.GONE);
            if (iAmFinisher) finishGame();
            return;
        }

        if (isInit) {
            instrView.setText("Priprema...");
            timerView.setVisibility(View.GONE);
            return;
        }

        timerView.setVisibility(View.VISIBLE);
        int sec = timerRunning ? remainingSec : ROUND_TIME;
        timerView.setText(String.valueOf(sec));
        timerView.setTextColor(sec <= 5 ? 0xFFFF0000 : 0xFFFFFFFF);

        // Instructions: game context + round info
        String roundInfo;
        if (isR1Play) {
            roundInfo = isMyTurn ? "Runda 1 - Tvoj potez" : "Runda 1 - Čekanje da protivnik odigra...";
        } else if (isR1Steal) {
            roundInfo = isMyTurn ? "Runda 1 - Pokušaj ti!" : "Runda 1 - Čekanje da protivnik pokuša...";
        } else if (isR2Play) {
            roundInfo = isMyTurn ? "Runda 2 - Tvoj potez" : "Runda 2 - Čekanje da protivnik odigra...";
        } else if (isR2Steal) {
            roundInfo = isMyTurn ? "Runda 2 - Pokušaj ti!" : "Runda 2 - Čekanje da protivnik pokuša...";
        } else {
            roundInfo = "";
        }
        String instructions = currentGame != null ? currentGame.instructions : "";
        if (!instructions.isEmpty()) {
            instrView.setText(instructions + "\n" + roundInfo);
        } else {
            instrView.setText(roundInfo);
        }

        // Make buttons visible
        for (int i = 0; i < TOTAL_ITEMS; i++) {
            leftBtns[i].setVisibility(View.VISIBLE);
            rightBtns[i].setVisibility(View.VISIBLE);
        }

        // Set button text from current game data
        if (currentGame != null) {
            for (int i = 0; i < TOTAL_ITEMS; i++) {
                leftBtns[i].setText(currentGame.leftItems.get(i));
            }
            if (shuffleOrder != null) {
                for (int i = 0; i < TOTAL_ITEMS; i++) {
                    rightBtns[i].setText(currentGame.rightItems.get(shuffleOrder[i]));
                }
            }
        }

        // Left buttons state
        for (int i = 0; i < TOTAL_ITEMS; i++) {
            boolean matched = syncLeftMatched[i];
            boolean isCurrent = (i == syncCurrentLeft && syncCurrentLeft >= 0 && !matched);

            leftBtns[i].setEnabled(false);

            if (matched) {
                int who = syncMatchedByPlayer[i];
                int bgColor = who == 1 ? P1_MATCH_COLOR : P2_MATCH_COLOR;
                leftBtns[i].setBackgroundTintList(ColorStateList.valueOf(bgColor));
                leftBtns[i].setStrokeColor(ColorStateList.valueOf(correctBorder));
            } else if (isCurrent) {
                leftBtns[i].setBackgroundTintList(ColorStateList.valueOf(selectedColor));
                leftBtns[i].setStrokeColor(ColorStateList.valueOf(selectedBorder));
            } else {
                resetBtn(leftBtns[i]);
            }
        }

        // Right buttons state
        for (int i = 0; i < TOTAL_ITEMS; i++) {
            boolean used = syncRightUsed[i];

            if (used) {
                int who = syncRightMatchedByPlayer[i];
                int bgColor = who == 1 ? P1_MATCH_COLOR : P2_MATCH_COLOR;
                rightBtns[i].setEnabled(false);
                rightBtns[i].setBackgroundTintList(ColorStateList.valueOf(bgColor));
                rightBtns[i].setStrokeColor(ColorStateList.valueOf(correctBorder));
            } else {
                rightBtns[i].setEnabled(isMyTurn);
                if (localFlashWrong && i == flashRightIndex) {
                    rightBtns[i].setBackgroundTintList(ColorStateList.valueOf(wrongColor));
                    rightBtns[i].setStrokeColor(ColorStateList.valueOf(wrongBorder));
                } else {
                    resetBtn(rightBtns[i]);
                }
            }
        }
    }

    private void onPhaseChanged() {
        if (timer != null) timer.cancel();
        timerRunning = false;
        localFlashWrong = false;

        boolean inR2 = PHASE_R2_PLAY.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase);
        if (inR2 && games != null && games.size() > 1 && currentGame != games.get(1)) {
            currentGame = games.get(1);
            shuffleOrder = shuffleOrder1;
        } else if (!inR2 && games != null && !games.isEmpty() && currentGame != games.get(0)) {
            currentGame = games.get(0);
            shuffleOrder = shuffleOrder0;
        }

        // Both players start the timer so they see the same countdown
        startTimer(ROUND_TIME);
    }

    private void startRound1() {
        currentGame = games.get(0);
        shuffleOrder = shuffleOrder0;

        Map<String, Object> s = new HashMap<>();
        s.put("phase", PHASE_R1_PLAY);
        s.put("activePlayer", 1L);
        s.put("currentLeftIndex", 0L);
        s.put("leftMatched", zeroLongs(TOTAL_ITEMS));
        s.put("rightUsed", zeroLongs(TOTAL_ITEMS));
        s.put("matchedByPlayer", zeroLongs(TOTAL_ITEMS));
        s.put("rightMatchedByPlayer", zeroLongs(TOTAL_ITEMS));
        s.put("p1Score", 0L);
        s.put("p2Score", 0L);
        s.put("p1ConnectedTotal", 0L);
        s.put("p2ConnectedTotal", 0L);
        s.put("p1Opportunities", (long) TOTAL_ITEMS);
        s.put("p2Opportunities", 0L);
        s.put("games", serializeGames(games));
        s.put("shuffleOrder0", toLongList(shuffleOrder0));
        s.put("shuffleOrder1", toLongList(shuffleOrder1));
        s.put("playerReady", 3L);
        sm.setGameState(s);
    }

    private void onRightClicked(int displayedRightIndex) {
        if (finished || !isMyTurn) return;
        if (!timerRunning) return;
        if (syncCurrentLeft < 0 || syncCurrentLeft >= TOTAL_ITEMS) return;
        if (syncLeftMatched[syncCurrentLeft]) return;
        if (!rightBtns[displayedRightIndex].isEnabled()) return;

        handleClick(syncCurrentLeft, displayedRightIndex);
    }

    private void handleClick(int leftIndex, int displayedRightIndex) {
        int originalRightIndex = shuffleOrder[displayedRightIndex];
        boolean correct = currentGame.isCorrectMatch(leftIndex, originalRightIndex);

        if (correct) {
            syncLeftMatched[leftIndex] = true;
            syncRightUsed[displayedRightIndex] = true;
            syncMatchedByPlayer[leftIndex] = me;
            syncRightMatchedByPlayer[displayedRightIndex] = me;
            if (me == 1) { syncP1Score += 2; syncP1ConnectedTotal++; }
            else { syncP2Score += 2; syncP2ConnectedTotal++; }
            localMyPts = (int) (me == 1 ? syncP1Score : syncP2Score);
        } else {
            flashRightIndex = displayedRightIndex;
            localFlashWrong = true;
            updateUI();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                localFlashWrong = false;
                resetBtn(rightBtns[displayedRightIndex]);
                updateUI();
            }, WRONG_FLASH_MS);
        }

        int nextLeft = findNextLeft(leftIndex);

        if (nextLeft == -1) {
            // Phase done for the current player
            if (timer != null) timer.cancel();
            timerRunning = false;
            String nextPhase = determineNextPhase();
            syncCurrentLeft = -1;
            writeState();
            if (nextPhase != null) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    endPhase(nextPhase);
                }, END_DELAY_MS);
            }
            return;
        }

        syncCurrentLeft = nextLeft;
        writeState();
    }

    private int findNextLeft(int current) {
        boolean isPlay = PHASE_R1_PLAY.equals(syncPhase) || PHASE_R2_PLAY.equals(syncPhase);

        if (isPlay) {
            if (current >= TOTAL_ITEMS - 1) return -1;
            return current + 1;
        }

        // Steal: find next unmatched left item
        for (int i = current + 1; i < TOTAL_ITEMS; i++) {
            if (!syncLeftMatched[i]) return i;
        }
        return -1;
    }

    private String determineNextPhase() {
        boolean isPlay = PHASE_R1_PLAY.equals(syncPhase) || PHASE_R2_PLAY.equals(syncPhase);
        boolean isSteal = PHASE_R1_STEAL.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase);

        if (isPlay) {
            boolean hasUnmatched = false;
            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (!syncLeftMatched[i]) { hasUnmatched = true; break; }
            }
            if (hasUnmatched) {
                return PHASE_R1_PLAY.equals(syncPhase) ? PHASE_R1_STEAL : PHASE_R2_STEAL;
            } else {
                return PHASE_R1_PLAY.equals(syncPhase) ? PHASE_R2_PLAY : PHASE_DONE;
            }
        }

        if (isSteal) {
            return PHASE_R1_STEAL.equals(syncPhase) ? PHASE_R2_PLAY : PHASE_DONE;
        }

        return PHASE_DONE;
    }

    private void endPhase(String nextPhase) {
        if (timer != null) timer.cancel();
        timerRunning = false;

        if (PHASE_DONE.equals(nextPhase)) {
            Map<String, Object> u = new HashMap<>();
            u.put("phase", PHASE_DONE);
            u.put("p1Score", syncP1Score);
            u.put("p2Score", syncP2Score);
            u.put("p1ConnectedTotal", syncP1ConnectedTotal);
            u.put("p2ConnectedTotal", syncP2ConnectedTotal);
            u.put("p1Opportunities", syncP1Opportunities);
            u.put("p2Opportunities", syncP2Opportunities);
            sm.updateGameState(u);
            iAmFinisher = true;
            return;
        }

        boolean moveToR2 = PHASE_R2_PLAY.equals(nextPhase);
        int nextActive;
        if (PHASE_R1_STEAL.equals(nextPhase) || PHASE_R2_STEAL.equals(nextPhase)) {
            nextActive = opp;
        } else {
            nextActive = moveToR2 ? 2 : 1;
        }

        int firstLeft;
        if (PHASE_R1_PLAY.equals(nextPhase) || PHASE_R2_PLAY.equals(nextPhase)) {
            firstLeft = 0;
        } else {
            firstLeft = -1;
            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (!syncLeftMatched[i]) { firstLeft = i; break; }
            }
        }

        if (moveToR2) {
            shuffleOrder = shuffleOrder1;
            if (games != null && games.size() > 1) currentGame = games.get(1);
            syncLeftMatched = new boolean[TOTAL_ITEMS];
            syncRightUsed = new boolean[TOTAL_ITEMS];
            syncMatchedByPlayer = new int[TOTAL_ITEMS];
            syncRightMatchedByPlayer = new int[TOTAL_ITEMS];
            firstLeft = 0;
        }

        syncActivePlayer = nextActive;
        syncCurrentLeft = firstLeft;

        if (PHASE_R1_STEAL.equals(nextPhase)) {
            int remaining = 0;
            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (!syncLeftMatched[i]) remaining++;
            }
            syncP2Opportunities += remaining;
        } else if (PHASE_R2_PLAY.equals(nextPhase)) {
            syncP2Opportunities += TOTAL_ITEMS;
        } else if (PHASE_R2_STEAL.equals(nextPhase)) {
            int remaining = 0;
            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (!syncLeftMatched[i]) remaining++;
            }
            syncP1Opportunities += remaining;
        }

        Map<String, Object> u = new HashMap<>();
        u.put("phase", nextPhase);
        u.put("activePlayer", (long) nextActive);
        u.put("currentLeftIndex", (long) firstLeft);
        u.put("leftMatched", toLongList(syncLeftMatched));
        u.put("rightUsed", toLongList(syncRightUsed));
        u.put("matchedByPlayer", toLongList(syncMatchedByPlayer));
        u.put("rightMatchedByPlayer", toLongList(syncRightMatchedByPlayer));
        u.put("p1Score", syncP1Score);
        u.put("p2Score", syncP2Score);
        u.put("p1ConnectedTotal", syncP1ConnectedTotal);
        u.put("p2ConnectedTotal", syncP2ConnectedTotal);
        u.put("p1Opportunities", syncP1Opportunities);
        u.put("p2Opportunities", syncP2Opportunities);
        sm.updateGameState(u);
    }

    private void writeState() {
        Map<String, Object> u = new HashMap<>();
        u.put("phase", syncPhase);
        u.put("activePlayer", (long) syncActivePlayer);
        u.put("currentLeftIndex", (long) syncCurrentLeft);
        u.put("leftMatched", toLongList(syncLeftMatched));
        u.put("rightUsed", toLongList(syncRightUsed));
        u.put("matchedByPlayer", toLongList(syncMatchedByPlayer));
        u.put("rightMatchedByPlayer", toLongList(syncRightMatchedByPlayer));
        u.put("p1Score", syncP1Score);
        u.put("p2Score", syncP2Score);
        u.put("p1ConnectedTotal", syncP1ConnectedTotal);
        u.put("p2ConnectedTotal", syncP2ConnectedTotal);
        u.put("p1Opportunities", syncP1Opportunities);
        u.put("p2Opportunities", syncP2Opportunities);
        sm.updateGameState(u);
    }

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        remainingSec = seconds;
        timerRunning = true;
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            public void onTick(long m) {
                remainingSec = (int) (m / 1000) + 1;
                timerView.setText(String.valueOf(remainingSec));
                timerView.setTextColor(remainingSec <= 5 ? 0xFFFF0000 : 0xFFFFFFFF);
            }
            public void onFinish() {
                timerRunning = false;
                timerView.setText("0");
                timerView.setTextColor(0xFFFF0000);
                if (!isMyTurn || finished) return;
                handleTimerExpiry();
            }
        }.start();
    }

    private void handleTimerExpiry() {
        boolean isPlay = PHASE_R1_PLAY.equals(syncPhase) || PHASE_R2_PLAY.equals(syncPhase);
        boolean isSteal = PHASE_R1_STEAL.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase);

        final String nextPhase;
        if (isPlay) {
            boolean hasUnmatched = false;
            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (!syncLeftMatched[i]) { hasUnmatched = true; break; }
            }
            if (hasUnmatched) {
                nextPhase = PHASE_R1_PLAY.equals(syncPhase) ? PHASE_R1_STEAL : PHASE_R2_STEAL;
            } else {
                nextPhase = PHASE_R1_PLAY.equals(syncPhase) ? PHASE_R2_PLAY : PHASE_DONE;
            }
        } else if (isSteal) {
            nextPhase = PHASE_R1_STEAL.equals(syncPhase) ? PHASE_R2_PLAY : PHASE_DONE;
        } else {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            endPhase(nextPhase);
        }, END_DELAY_MS);
    }

    private void finishGame() {
        if (finished) return;
        finished = true;
        if (timer != null) { timer.cancel(); timer = null; }
        int totalP1 = previousP1Score + (int) syncP1Score;
        int totalP2 = previousP2Score + (int) syncP2Score;
        Map<String, Object> stats = new HashMap<>();
        stats.put("gameType", GameSessionManager.GAME_TYPE_SPOJNICE);
        stats.put("p1Connected", syncP1ConnectedTotal);
        stats.put("p2Connected", syncP2ConnectedTotal);
        stats.put("p1Opportunities", syncP1Opportunities);
        stats.put("p2Opportunities", syncP2Opportunities);
        stats.put("player1Score", (long) syncP1Score);
        stats.put("player2Score", (long) syncP2Score);
        sm.finishCurrentGame(gameIdx, (int) syncP1Score, (int) syncP2Score, totalP1, totalP2, 6, stats);
        sm.cleanup();
        setResult(RESULT_OK);
        finish();
    }

    private void updateScoreDisplay() {
        int myTotal = (me == 1 ? previousP1Score : previousP2Score) + localMyPts;
        int oppTotal = (me == 1 ? previousP2Score : previousP1Score) + localOppPts;
        myScoreView.setText(String.valueOf(myTotal));
        oppScoreView.setText(String.valueOf(oppTotal));
    }

    private void loadAvatar(android.widget.ImageView iv, String url) {
        Glide.with(this)
                .load(url != null && !url.isEmpty() ? url : R.drawable.default_profile)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(iv);
    }

    private void loadGamesFromFirestore() {
        spojniceRepository.getRandomGames()
                .addOnSuccessListener(loaded -> {
                    if (loaded == null || loaded.isEmpty()) {
                        instrView.setText("Greška pri učitavanju spojnica");
                        return;
                    }
                    games = loaded;
                    currentGame = games.get(0);
                    shuffleOrder0 = generateShuffleArray();
                    shuffleOrder1 = generateShuffleArray();
                    shuffleOrder = shuffleOrder0;
                    iAmFinisher = true;
                    writeInitialState();
                })
                .addOnFailureListener(e -> {
                    instrView.setText("Greška pri učitavanju spojnica");
                });
    }

    private void writeInitialState() {
        Map<String, Object> gs = new HashMap<>();
        gs.put("phase", PHASE_INIT);
        gs.put("games", serializeGames(games));
        gs.put("shuffleOrder0", toLongList(shuffleOrder0));
        gs.put("shuffleOrder1", toLongList(shuffleOrder1));
        gs.put("playerReady", 1L);
        gs.put("activePlayer", 1L);
        gs.put("p1Score", 0L);
        gs.put("p2Score", 0L);
        gs.put("p1ConnectedTotal", 0L);
        gs.put("p2ConnectedTotal", 0L);
        gs.put("p1Opportunities", 0L);
        gs.put("p2Opportunities", 0L);
        gs.put("leftMatched", zeroLongs(TOTAL_ITEMS));
        gs.put("rightUsed", zeroLongs(TOTAL_ITEMS));
        gs.put("matchedByPlayer", zeroLongs(TOTAL_ITEMS));
        gs.put("rightMatchedByPlayer", zeroLongs(TOTAL_ITEMS));
        gs.put("currentLeftIndex", -1L);
        sm.setGameState(gs);
    }

    private int[] generateShuffleArray() {
        int[] arr = new int[TOTAL_ITEMS];
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < TOTAL_ITEMS; i++) indices.add(i);
        Collections.shuffle(indices);
        for (int i = 0; i < TOTAL_ITEMS; i++) arr[i] = indices.get(i);
        return arr;
    }

    // --- Serialization ---

    private List<Map<String, Object>> serializeGames(List<MatchingGame> gameList) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MatchingGame g : gameList) {
            Map<String, Object> m = new HashMap<>();
            m.put("instructions", g.instructions);
            m.put("leftItems", g.leftItems);
            m.put("rightItems", g.rightItems);
            List<Long> correct = new ArrayList<>();
            for (Integer c : g.correctMatches) correct.add(c.longValue());
            m.put("correctIndices", correct);
            result.add(m);
        }
        return result;
    }

    private List<MatchingGame> deserializeGamesOnly(Map<String, Object> gs) {
        List<MatchingGame> result = new ArrayList<>();
        Object gamesObj = gs.get("games");
        if (!(gamesObj instanceof List)) return result;
        List<?> gameMaps = (List<?>) gamesObj;
        for (Object obj : gameMaps) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) obj;
            String instructions = (String) m.get("instructions");
            List<String> leftItems = (List<String>) m.get("leftItems");
            List<String> rightItems = (List<String>) m.get("rightItems");
            List<Long> correctRaw = (List<Long>) m.get("correctIndices");
            if (instructions == null || leftItems == null || rightItems == null || correctRaw == null) continue;
            List<Integer> correctMatches = new ArrayList<>();
            for (Long v : correctRaw) correctMatches.add(v.intValue());
            result.add(new MatchingGame(instructions, leftItems, rightItems, correctMatches));
        }
        return result;
    }

    private boolean[] readBoolArray(Object obj, int len) {
        boolean[] result = new boolean[len];
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            for (int i = 0; i < Math.min(list.size(), len); i++) {
                result[i] = list.get(i) instanceof Long && (Long) list.get(i) == 1L;
            }
        }
        return result;
    }

    private int[] readIntArray(Object obj, int len) {
        int[] result = new int[len];
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            for (int i = 0; i < Math.min(list.size(), len); i++) {
                Object v = list.get(i);
                result[i] = v instanceof Long ? ((Long) v).intValue() : 0;
            }
        }
        return result;
    }

    private List<Long> toLongList(boolean[] arr) {
        List<Long> result = new ArrayList<>();
        for (boolean b : arr) result.add(b ? 1L : 0L);
        return result;
    }

    private List<Long> toLongList(int[] arr) {
        List<Long> result = new ArrayList<>();
        for (int v : arr) result.add((long) v);
        return result;
    }

    private List<Long> zeroLongs(int n) {
        List<Long> r = new ArrayList<>();
        for (int i = 0; i < n; i++) r.add(0L);
        return r;
    }

    private void resetBtn(com.google.android.material.button.MaterialButton btn) {
        btn.setBackgroundTintList(ColorStateList.valueOf(defaultColor));
        btn.setStrokeColor(ColorStateList.valueOf(defaultBorder));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (sm != null) sm.cleanup();
    }
}
