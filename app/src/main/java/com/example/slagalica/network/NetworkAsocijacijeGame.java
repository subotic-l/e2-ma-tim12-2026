package com.example.slagalica.network;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.slagalica.InactivityWatcher;
import com.example.slagalica.R;
import com.example.slagalica.data.GameSessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkAsocijacijeGame extends AppCompatActivity {

    private InactivityWatcher inactivityWatcher;
    private static final int GROUPS = 4;
    private static final int FIELDS = 4;
    private static final int ROUND_TIME = 120;
    private static final int REVEAL_TIME = 5;
    private static final int END_DELAY = 2000;

    private static final String PHASE_INIT      = "init";
    private static final String PHASE_R1        = "r1_play";
    private static final String PHASE_R2        = "r2_play";
    private static final String PHASE_R1_REVEAL = "r1_reveal";
    private static final String PHASE_R2_REVEAL = "r2_reveal";
    private static final String PHASE_DONE      = "done";

    private static final String[][][] ALL_GROUPS = {
            {
                    {"MAK", "NIT", "RANA", "ZUB"},
                    {"BALA", "DETELINA", "SLAMA", "SUVO"},
                    {"KONAC", "IGLA", "UŠI", "INSULIN"},
                    {"ZID", "GLIKOGEN", "PROTEIN", "HORMON"}
            },
            {
                    {"PLAŽA", "SUNCE", "MORE", "PEŠAK"},
                    {"SNEG", "SKIJE", "VETAR", "KLOBUK"},
                    {"KIŠA", "OBLAK", "MUNJA", "GRAD"},
                    {"CVEĆE", "PTICE", "LEPTIR", "DUGA"}
            }
    };
    private static final String[][] ALL_GROUP_SOL = {
            {"KONAC", "SENO", "IGLA", "DIJABETES"},
            {"LETO", "ZIMA", "OLUJA", "PROLECE"}
    };
    private static final String[] ALL_FINAL = {"IGLA", "GODISNJE DOBA"};

    private GameSessionManager sm;
    private int me, opp, gameIdx, prevP1, prevP2, totalGames;
    private boolean finished, loaded;
    private boolean pendingGuess;

    private String syncPhase = PHASE_INIT;
    private int syncActivePlayer = 1;
    private boolean[][] syncOpened = new boolean[GROUPS][FIELDS];
    private int[] syncSolved = new int[GROUPS];
    private int syncFinalSolved;
    private long syncP1, syncP2;

    private int curSet;
    private String lastPhase = "";
    private boolean isMyTurn;
    private int myPts, oppPts;
    private long p1SolvedGroups = 0, p2SolvedGroups = 0;
    private long p1FinalSolved = 0, p2FinalSolved = 0;

    private CountDownTimer timer;
    private boolean timerRun;
    private int timerSec;
    private Handler revealHandler;

    private TextView tvTimer, tvInstr, tvMyName, tvOppName, tvMyScore, tvOppScore;
    private ImageView ivMyAvatar, ivOppAvatar;
    private Button[][] wordBtns = new Button[GROUPS][FIELDS];
    private Button[] colBtns  = new Button[GROUPS];
    private Button btnFinal, btnSkip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_asocijacije);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        Intent i = getIntent();
        me       = i.getIntExtra("myPlayerNumber", 1);
        opp      = me == 1 ? 2 : 1;
        gameIdx  = i.getIntExtra("gameIndex", 0);
        prevP1   = i.getIntExtra("previousPlayer1Score", 0);
        prevP2   = i.getIntExtra("previousPlayer2Score", 0);
        totalGames = i.getIntExtra("totalGames", 6);
        boolean spectator = i.getBooleanExtra("isSpectator", false);

        tvTimer  = findViewById(R.id.timerText);
        tvInstr  = findViewById(R.id.instructionsTextView);
        tvMyName = findViewById(R.id.playerOneName);
        tvOppName= findViewById(R.id.playerTwoName);
        tvMyScore= findViewById(R.id.playerOneScore);
        tvOppScore=findViewById(R.id.playerTwoScore);
        ivMyAvatar = findViewById(R.id.playerOneAvatar);
        ivOppAvatar= findViewById(R.id.playerTwoAvatar);

        int[][] wordIds = {
                {R.id.a1, R.id.a2, R.id.a3, R.id.a4},
                {R.id.b1, R.id.b2, R.id.b3, R.id.b4},
                {R.id.c1, R.id.c2, R.id.c3, R.id.c4},
                {R.id.d1, R.id.d2, R.id.d3, R.id.d4}
        };
        int[] colIds = {R.id.btnA, R.id.btnB, R.id.btnC, R.id.btnD};

        for (int g = 0; g < GROUPS; g++) {
            for (int f = 0; f < FIELDS; f++) {
                wordBtns[g][f] = findViewById(wordIds[g][f]);
                final int gg = g, ff = f;
                wordBtns[g][f].setOnClickListener(v -> onWordClick(gg, ff));
            }
            colBtns[g] = findViewById(colIds[g]);
            final int gg = g;
            colBtns[g].setOnClickListener(v -> onColumnClick(gg));
        }
        btnFinal = findViewById(R.id.btnFinal);
        btnFinal.setOnClickListener(v -> onFinalClick());

        btnSkip = findViewById(R.id.skipButton);
        btnSkip.setOnClickListener(v -> {
            if (!isMyTurn || finished) return;
            switchTurn();
            btnSkip.setVisibility(View.GONE);
        });
        btnSkip.setVisibility(View.GONE);

        tvTimer.setVisibility(View.GONE);

        if (me == 1) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!finished) writeInit();
            }, 300);
        }

        sm = new GameSessionManager();
        sm.attachToMatch(i.getStringExtra("matchId"), me);
        sm.listenToMatch(createListener());

        if (spectator) {
            for (int g = 0; g < GROUPS; g++) {
                for (int f = 0; f < FIELDS; f++) wordBtns[g][f].setEnabled(false);
                colBtns[g].setEnabled(false);
            }
            btnFinal.setEnabled(false);
        }

        inactivityWatcher = new InactivityWatcher(60000, () -> {
            if (finished || isFinishing()) return;
            runOnUiThread(() -> {
                Toast.makeText(NetworkAsocijacijeGame.this, "Automatska predaja zbog neaktivnosti", Toast.LENGTH_SHORT).show();
                if (sm != null) { sm.forfeitMatch(); sm.cleanup(); }
                finished = true;
                if (timer != null) timer.cancel();
                if (revealHandler != null) revealHandler.removeCallbacksAndMessages(null);
                finish();
            });
        });
        inactivityWatcher.start();
        setupQuitButton();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (inactivityWatcher != null) inactivityWatcher.reset();
    }

    private void setupQuitButton() {
        ImageButton quitBtn = findViewById(R.id.quitGameButton);
        if (quitBtn != null) {
            quitBtn.setVisibility(View.VISIBLE);
            quitBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Napusti igru")
                    .setMessage("Napuštanjem igre igrač gubi partiju i ne dobija zvezde. Protivnik nastavlja partiju.")
                    .setPositiveButton("Napusti", (d, w) -> {
                        if (sm != null) { sm.forfeitMatch(); sm.cleanup(); }
                        finished = true;
                        if (timer != null) timer.cancel();
                        if (revealHandler != null) revealHandler.removeCallbacksAndMessages(null);
                        finish();
                    })
                    .setNegativeButton("Nastavi", null)
                    .show());
        }
    }

    // ───────────────────────────── LISTENER ──────────────────────────────

    private GameSessionManager.StateListener createListener() {
        return new GameSessionManager.StateListener() {
            public void onStateChanged(Map<String, Object> full) {
                if (finished || isFinishing()) return;
                try {
                    Map<String, Object> gs = (Map<String, Object>) full.get("gameState");
                    if (gs == null || gs.isEmpty()) return;

                    String p1n = (String) full.get("player1Name");
                    String p2n = (String) full.get("player2Name");
                    String p1a = (String) full.get("player1Avatar");
                    String p2a = (String) full.get("player2Avatar");
                    if (me == 1) {
                        tvMyName.setText(p1n != null ? p1n : "Igrač 1");
                        tvMyName.setTextColor(0xFF1565C0);
                        tvOppName.setText(p2n != null ? p2n : "Protivnik");
                        tvOppName.setTextColor(0xFFE65100);
                        if (p1a != null) loadAvatar(ivMyAvatar, p1a);
                        if (p2a != null) loadAvatar(ivOppAvatar, p2a);
                    } else {
                        tvMyName.setText(p2n != null ? p2n : "Igrač 2");
                        tvMyName.setTextColor(0xFFE65100);
                        tvOppName.setText(p1n != null ? p1n : "Protivnik");
                        tvOppName.setTextColor(0xFF1565C0);
                        if (p1a != null) loadAvatar(ivOppAvatar, p1a);
                        if (p2a != null) loadAvatar(ivMyAvatar, p2a);
                    }

                    if (!loaded) {
                        if (gs.containsKey("sets") && gs.get("sets") != null) {
                            if (me == 2) sm.updateField("gameState.playerReady", 3L);
                            loaded = true;
                        }
                        return;
                    }

                    if (PHASE_INIT.equals(gs.get("phase"))) {
                        Object r = gs.get("playerReady");
                        int rdy = r instanceof Long ? ((Long) r).intValue() : 0;
                        if (me == 1 && rdy == 3) startR1();
                        return;
                    }

                    runOnUiThread(() -> {
                        syncState(gs);
                        updateUI();
                    });
                } catch (Exception e) {
                    // ignore
                }
            }

            public void onMatchEnded(Map<String, Object> f) {
                endGame();
            }

            public void onError(String e) {}
        };
    }

    // ───────────────────────────── STATE ──────────────────────────────────

    private void syncState(Map<String, Object> gs) {
        syncPhase        = str(gs.get("phase"));
        syncActivePlayer = gs.get("activePlayer") instanceof Long
                ? ((Long) gs.get("activePlayer")).intValue() : 1;
        syncOpened       = readBoolMatrix(gs.get("openedFields"));
        syncSolved       = readIntArray(gs.get("solvedGroups"));
        syncFinalSolved  = gs.get("finalSolved") instanceof Long
                ? ((Long) gs.get("finalSolved")).intValue() : 0;
        syncP1 = gs.get("p1Score") instanceof Long ? (Long) gs.get("p1Score") : 0;
        syncP2 = gs.get("p2Score") instanceof Long ? (Long) gs.get("p2Score") : 0;
        myPts  = (int)(me == 1 ? syncP1 : syncP2);
        oppPts = (int)(me == 1 ? syncP2 : syncP1);
        p1SolvedGroups = gs.get("p1SolvedGroups") instanceof Long ? (Long) gs.get("p1SolvedGroups") : 0;
        p2SolvedGroups = gs.get("p2SolvedGroups") instanceof Long ? (Long) gs.get("p2SolvedGroups") : 0;
        p1FinalSolved = gs.get("p1FinalSolved") instanceof Long ? (Long) gs.get("p1FinalSolved") : 0;
        p2FinalSolved = gs.get("p2FinalSolved") instanceof Long ? (Long) gs.get("p2FinalSolved") : 0;
        curSet = PHASE_R2.equals(syncPhase) || PHASE_R2_REVEAL.equals(syncPhase) ? 1 : 0;
    }

    // ───────────────────────────── UI ─────────────────────────────────────

    private void updateUI() {
        isMyTurn = syncActivePlayer == me;

        boolean r1     = PHASE_R1.equals(syncPhase);
        boolean r2     = PHASE_R2.equals(syncPhase);
        boolean reveal = PHASE_R1_REVEAL.equals(syncPhase) || PHASE_R2_REVEAL.equals(syncPhase);
        boolean done   = PHASE_DONE.equals(syncPhase);
        boolean playing = r1 || r2;

        int myTotal  = (me == 1 ? prevP1 : prevP2) + myPts;
        int oppTotal = (me == 1 ? prevP2 : prevP1) + oppPts;
        tvMyScore.setText(String.valueOf(myTotal));
        tvOppScore.setText(String.valueOf(oppTotal));

        // Faza se promenila — pokreni timer/reveal logiku
        if (!syncPhase.equals(lastPhase)) {
            lastPhase = syncPhase;
            onPhaseChanged();
            // onPhaseChanged resets pendingGuess, restore after phase init
            if (playing && isMyTurn) {
                btnSkip.setVisibility(View.VISIBLE);
            }
        }

        if (done) {
            tvTimer.setVisibility(View.GONE);
            tvInstr.setText("Kraj igre");
            btnSkip.setVisibility(View.GONE);
            return;
        }

        if (playing) {
            btnSkip.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
            if (pendingGuess) {
                tvInstr.setText(isMyTurn ? "Pogodi kolonu ili konačno rešenje" : "Protivnik pogađa...");
            } else if (isMyTurn) {
                tvInstr.setText("Tvoj red - otvori polje");
            } else {
                tvInstr.setText("Protivnik je na potezu");
            }
        } else if (reveal) {
            tvInstr.setText("Rešenje");
            btnSkip.setVisibility(View.GONE);
        }

        if (reveal || done) {
            tvTimer.setVisibility(View.GONE);
        } else {
            tvTimer.setVisibility(View.VISIBLE);
            int sec = timerRun ? timerSec : ROUND_TIME;
            tvTimer.setText(String.valueOf(sec));
            tvTimer.setTextColor(sec <= 10 ? 0xFFFF0000 : 0xFFFFFFFF);
        }

        String[][] groups   = ALL_GROUPS[curSet];
        String[]   groupSol = ALL_GROUP_SOL[curSet];

        for (int g = 0; g < GROUPS; g++) {
            for (int f = 0; f < FIELDS; f++) {
                Button btn    = wordBtns[g][f];
                boolean opened = syncOpened[g][f] || reveal || syncSolved[g] != 0;
                if (opened) {
                    btn.setText(groups[g][f]);
                    if      (syncSolved[g] == 1) btn.setBackgroundTintList(ColorStateList.valueOf(0xFF1565C0));
                    else if (syncSolved[g] == 2) btn.setBackgroundTintList(ColorStateList.valueOf(0xFFF44336));
                    else if (reveal)             btn.setBackgroundTintList(ColorStateList.valueOf(0xFFFF9800));
                    else                         btn.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_light));
                    btn.setEnabled(false);
                } else {
                    btn.setText((char)('A' + g) + "" + (f + 1));
                    btn.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_dark));
                    btn.setTextColor(0xFFFFFFFF);
                    btn.setEnabled(isMyTurn && !pendingGuess && syncSolved[g] == 0);
                }
            }

            Button cb = colBtns[g];
            if (syncSolved[g] == 1) {
                cb.setText(groupSol[g]);
                cb.setBackgroundTintList(ColorStateList.valueOf(0xFF1565C0));
                cb.setEnabled(false);
            } else if (syncSolved[g] == 2) {
                cb.setText(groupSol[g]);
                cb.setBackgroundTintList(ColorStateList.valueOf(0xFFF44336));
                cb.setEnabled(false);
            } else if (reveal) {
                cb.setText(groupSol[g]);
                cb.setBackgroundTintList(ColorStateList.valueOf(0xFFFF9800));
                cb.setEnabled(false);
            } else {
                cb.setText("" + (char)('A' + g));
                cb.setBackgroundTintList(ColorStateList.valueOf(0xFFFF9800));
                cb.setTextColor(0xFFFFFFFF);
                cb.setEnabled(pendingGuess);
            }
        }

        if (reveal) {
            btnFinal.setText(ALL_FINAL[curSet]);
            if      (syncFinalSolved == 1) btnFinal.setBackgroundTintList(ColorStateList.valueOf(0xFF1565C0));
            else if (syncFinalSolved == 2) btnFinal.setBackgroundTintList(ColorStateList.valueOf(0xFFF44336));
            else                           btnFinal.setBackgroundTintList(ColorStateList.valueOf(0xFFFF9800));
            btnFinal.setEnabled(false);
        } else {
            btnFinal.setText("KONAČNO");
            btnFinal.setBackgroundTintList(ColorStateList.valueOf(0xFFFF9800));
            btnFinal.setTextColor(0xFFFFFFFF);
            btnFinal.setEnabled(pendingGuess);
        }
    }

    // ───────────────────────────── PHASE / TIMER ──────────────────────────

    private void onPhaseChanged() {
        // Zaustavi sve što je teklo
        if (timer != null) { timer.cancel(); timer = null; }
        if (revealHandler != null) { revealHandler.removeCallbacksAndMessages(null); revealHandler = null; }
        timerRun    = false;
        pendingGuess = false;

        if (PHASE_R1.equals(syncPhase) || PHASE_R2.equals(syncPhase)) {
            // Timer pokreće SVAKO lokalno — istekne samo onaj čiji je red
            startTimer(ROUND_TIME);

        } else if (PHASE_R1_REVEAL.equals(syncPhase) || PHASE_R2_REVEAL.equals(syncPhase)) {
            // Reveal countdown pokreće samo onaj ko je poslao reveal fazu.
            // Ko je poslao reveal? U R1 → P1 (activePlayer bio 1 ili nije bitan),
            // u R2 → P2. Koristimo: ko god je bio activePlayer PRE reveal-a.
            // Jednostavnije: ko je owner runde šalje advanceAfterReveal.
            // R1 owner = P1 (me==1), R2 owner = P2 (me==2).
            boolean iAmRevealOwner = (PHASE_R1_REVEAL.equals(syncPhase) && me == 1)
                    || (PHASE_R2_REVEAL.equals(syncPhase) && me == 2);
            if (iAmRevealOwner) {
                scheduleAdvanceAfterReveal();
            }
        }
        // PHASE_DONE → ništa, endGame se poziva iz advanceAfterReveal
    }

    private void startTimer(int sec) {
        if (timer != null) timer.cancel();
        timerSec = sec;
        timerRun = true;
        timer = new CountDownTimer(sec * 1000L, 1000) {
            public void onTick(long m) {
                timerSec = (int)(m / 1000) + 1;
                tvTimer.setText(String.valueOf(timerSec));
                tvTimer.setTextColor(timerSec <= 10 ? 0xFFFF0000 : 0xFFFFFFFF);
            }
            public void onFinish() {
                timerRun = false;
                tvTimer.setText("0");
                tvTimer.setTextColor(0xFFFF0000);
                if (finished) return;
                // Samo aktivan igrač šalje reveal kad timer istekne
                if (!isMyTurn && syncActivePlayer != me) return;
                if (PHASE_R1.equals(syncPhase) || PHASE_R2.equals(syncPhase)) {
                    goToReveal();
                }
            }
        }.start();
    }

    /**
     * Zakazuje prelaz iz reveal → sledeća faza.
     * Poziva se samo kod "reveal owner" igrača (P1 za R1, P2 za R2).
     */
    private void scheduleAdvanceAfterReveal() {
        if (revealHandler != null) revealHandler.removeCallbacksAndMessages(null);
        revealHandler = new Handler(Looper.getMainLooper());
        revealHandler.postDelayed(() -> {
            if (finished) return;
            advanceAfterReveal();
        }, REVEAL_TIME * 1000L);
    }

    private void advanceAfterReveal() {
        if (finished) return;
        if (!PHASE_R1_REVEAL.equals(syncPhase) && !PHASE_R2_REVEAL.equals(syncPhase)) return;

        boolean toR2 = PHASE_R1_REVEAL.equals(syncPhase);
        String np          = toR2 ? PHASE_R2 : PHASE_DONE;
        long   nextActive  = toR2 ? 2L : -1L;

        Map<String, Object> u = new HashMap<>();
        u.put("phase",        np);
        u.put("activePlayer", nextActive);
        u.put("openedFields", zeroMatrix());
        u.put("solvedGroups", zeroArray());
        u.put("finalSolved",  0L);
        sm.updateGameState(u);

        if (!toR2) {
            // Završi igru
            new Handler(Looper.getMainLooper()).postDelayed(() -> endGame(), END_DELAY);
        }
    }

    /**
     * Prelaz na reveal fazu.
     * Može pozvati SAMO aktivan igrač (onaj ko igra rundu).
     */
    private void goToReveal() {
        if (finished) return;
        if (PHASE_R1_REVEAL.equals(syncPhase) || PHASE_R2_REVEAL.equals(syncPhase)) return;
        // Samo aktivan igrač šalje reveal
        if (syncActivePlayer != me) return;

        boolean isR1 = PHASE_R1.equals(syncPhase);
        String  rp   = isR1 ? PHASE_R1_REVEAL : PHASE_R2_REVEAL;

        boolean[][] allOpen = new boolean[GROUPS][FIELDS];
        for (int g = 0; g < GROUPS; g++)
            for (int f = 0; f < FIELDS; f++)
                allOpen[g][f] = true;

        Map<String, Object> u = new HashMap<>();
        u.put("phase",        rp);
        u.put("activePlayer", -1L);
        u.put("openedFields", toLongMatrix(allOpen));
        u.put("solvedGroups", toLongArray(syncSolved));
        u.put("finalSolved",  (long) syncFinalSolved);
        sm.updateGameState(u);
        // scheduleAdvanceAfterReveal će se pokrenuti u onPhaseChanged() kad state stigne nazad
    }

    // ───────────────────────────── KLIKOVI ────────────────────────────────

    private void onWordClick(int g, int f) {
        if (finished || !isMyTurn || pendingGuess) return;
        if (!PHASE_R1.equals(syncPhase) && !PHASE_R2.equals(syncPhase)) return;
        if (syncOpened[g][f] || syncSolved[g] != 0) return;

        syncOpened[g][f] = true;
        Map<String, Object> u = new HashMap<>();
        u.put("openedFields", toLongMatrix(syncOpened));
        sm.updateGameState(u);
        pendingGuess = true;
        updateUI();

        if (allFieldsOpened()) goToReveal();
    }

    private boolean allFieldsOpened() {
        for (int g = 0; g < GROUPS; g++)
            for (int f = 0; f < FIELDS; f++)
                if (!syncOpened[g][f] && syncSolved[g] == 0) return false;
        return true;
    }

    private void onColumnClick(int g) {
        if (finished || !pendingGuess || syncSolved[g] != 0) return;
        showInputDialog(g, false);
    }

    private void onFinalClick() {
        if (finished || !pendingGuess) return;
        showInputDialog(-1, true);
    }

    private void showInputDialog(int col, boolean isFinal) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        String title = isFinal ? "KONAČNO REŠENJE" : "Rešenje kolone " + (char)('A' + col);
        b.setTitle(title);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        b.setView(input);
        b.setPositiveButton("Potvrdi", (d, w) -> {
            String guess = input.getText().toString().trim().toUpperCase();
            processGuess(guess, col, isFinal);
        });
        b.setCancelable(false);
        b.show();
    }

    private void processGuess(String guess, int col, boolean isFinal) {
        if (isFinal) {
            if (guess.equals(ALL_FINAL[curSet])) {
                int pts = calcFinalScore();
                if (me == 1) syncP1 += pts; else syncP2 += pts;
                myPts       = (int)(me == 1 ? syncP1 : syncP2);
                syncFinalSolved = me;
                int remainingGroups = 0;
                for (int g = 0; g < GROUPS; g++)
                    if (syncSolved[g] == 0) { syncSolved[g] = me; remainingGroups++; }
                if (me == 1) { p1SolvedGroups += remainingGroups; p1FinalSolved++; }
                else         { p2SolvedGroups += remainingGroups; p2FinalSolved++; }
                Map<String, Object> u = new HashMap<>();
                u.put("p1Score",        syncP1);
                u.put("p2Score",        syncP2);
                u.put("finalSolved",    (long) syncFinalSolved);
                u.put("solvedGroups",   toLongArray(syncSolved));
                u.put("p1SolvedGroups", p1SolvedGroups);
                u.put("p2SolvedGroups", p2SolvedGroups);
                u.put("p1FinalSolved",  p1FinalSolved);
                u.put("p2FinalSolved",  p2FinalSolved);
                sm.updateGameState(u);
                Toast.makeText(this, "Konačno rešenje tačno! +" + pts, Toast.LENGTH_LONG).show();
                updateUI();
                goToReveal();
            } else {
                pendingGuess = false;
                switchTurn();
                Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
            }
        } else {
            String correct = ALL_GROUP_SOL[curSet][col];
            if (guess.equals(correct)) {
                int oc  = openedCount(col);
                int pts = 2 + (FIELDS - oc);
                if (me == 1) syncP1 += pts; else syncP2 += pts;
                myPts = (int)(me == 1 ? syncP1 : syncP2);
                if (me == 1) p1SolvedGroups++;
                else         p2SolvedGroups++;
                syncSolved[col] = me;
                for (int f = 0; f < FIELDS; f++) syncOpened[col][f] = true;
                Map<String, Object> u = new HashMap<>();
                u.put("solvedGroups",   toLongArray(syncSolved));
                u.put("openedFields",   toLongMatrix(syncOpened));
                u.put("p1Score",        syncP1);
                u.put("p2Score",        syncP2);
                u.put("p1SolvedGroups", p1SolvedGroups);
                u.put("p2SolvedGroups", p2SolvedGroups);
                sm.updateGameState(u);
                Toast.makeText(this, "Tačno! +" + pts, Toast.LENGTH_SHORT).show();
                pendingGuess = true;
                updateUI();
                if (allFieldsOpened()) goToReveal();
            } else {
                pendingGuess = false;
                switchTurn();
                Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void switchTurn() {
        // Prebaci turn na protivnika — timer se NE restartuje, samo activePlayer
        int next = (syncActivePlayer == 1) ? 2 : 1;
        Map<String, Object> u = new HashMap<>();
        u.put("activePlayer", (long) next);
        sm.updateGameState(u);
    }

    // ───────────────────────────── SCORE HELPERS ──────────────────────────

    private int openedCount(int g) {
        int c = 0;
        for (int f = 0; f < FIELDS; f++) if (syncOpened[g][f]) c++;
        return c;
    }

    private int calcFinalScore() {
        int score = 7;
        for (int g = 0; g < GROUPS; g++) {
            if (syncSolved[g] != 0) continue;
            int oc = openedCount(g);
            if (oc == 0) score += 6;
            else         score += 2 + (FIELDS - oc);
        }
        return score;
    }

    // ───────────────────────────── INIT / END ─────────────────────────────

    private void writeInit() {
        Map<String, Object> s = new HashMap<>();
        s.put("phase",          PHASE_INIT);
        s.put("activePlayer",   1L);
        s.put("sets",           "loaded");
        s.put("openedFields",   zeroMatrix());
        s.put("solvedGroups",   zeroArray());
        s.put("p1Score",        0L);
        s.put("p2Score",        0L);
        s.put("finalSolved",    0L);
        s.put("playerReady",    1L);
        s.put("p1SolvedGroups", 0L);
        s.put("p2SolvedGroups", 0L);
        s.put("p1FinalSolved",  0L);
        s.put("p2FinalSolved",  0L);
        sm.setGameState(s);
    }

    private void startR1() {
        Map<String, Object> s = new HashMap<>();
        s.put("phase",          PHASE_R1);
        s.put("activePlayer",   1L);
        s.put("openedFields",   zeroMatrix());
        s.put("solvedGroups",   zeroArray());
        s.put("p1Score",        0L);
        s.put("p2Score",        0L);
        s.put("finalSolved",    0L);
        s.put("playerReady",    3L);
        s.put("p1SolvedGroups", 0L);
        s.put("p2SolvedGroups", 0L);
        s.put("p1FinalSolved",  0L);
        s.put("p2FinalSolved",  0L);
        sm.setGameState(s);
    }

    private void endGame() {
        if (finished) return;
        finished = true;
        if (timer != null) { timer.cancel(); timer = null; }
        int tP1 = prevP1 + (int) syncP1;
        int tP2 = prevP2 + (int) syncP2;
        Map<String, Object> stats = new HashMap<>();
        stats.put("gameType", GameSessionManager.GAME_TYPE_ASOCIJACIJE);
        stats.put("p1SolvedGroups", p1SolvedGroups);
        stats.put("p2SolvedGroups", p2SolvedGroups);
        stats.put("p1FinalSolved", p1FinalSolved);
        stats.put("p2FinalSolved", p2FinalSolved);
        stats.put("totalGroups", (long) (GROUPS * 2));
        stats.put("player1Score", (long) syncP1);
        stats.put("player2Score", (long) syncP2);
        sm.finishCurrentGame(gameIdx, (int) syncP1, (int) syncP2, tP1, tP2, 6, stats);
        sm.cleanup();
        setResult(RESULT_OK);
        finish();
    }

    // ───────────────────────────── UTIL ───────────────────────────────────

    private void loadAvatar(ImageView iv, String url) {
        if (iv == null || isDestroyed()) return;
        Glide.with(this)
                .load(url != null && !url.isEmpty() ? url : R.drawable.default_profile)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(iv);
    }

    private String str(Object o) { return o instanceof String ? (String) o : PHASE_INIT; }

    private int[] readIntArray(Object o) {
        int[] r = new int[GROUPS];
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            for (int i = 0; i < Math.min(l.size(), GROUPS); i++)
                r[i] = l.get(i) instanceof Long ? ((Long) l.get(i)).intValue() : 0;
        }
        return r;
    }

    private boolean[][] readBoolMatrix(Object o) {
        boolean[][] r = new boolean[GROUPS][FIELDS];
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            for (int i = 0; i < Math.min(l.size(), GROUPS * FIELDS); i++)
                r[i / FIELDS][i % FIELDS] = l.get(i) instanceof Long && (Long) l.get(i) == 1L;
        }
        return r;
    }

    private List<Long> toLongArray(int[] arr) {
        List<Long> r = new ArrayList<>();
        for (int v : arr) r.add((long) v);
        return r;
    }

    private List<Long> toLongMatrix(boolean[][] mat) {
        List<Long> r = new ArrayList<>();
        for (int g = 0; g < GROUPS; g++)
            for (int f = 0; f < FIELDS; f++)
                r.add(mat[g][f] ? 1L : 0L);
        return r;
    }

    private List<Long> zeroArray() {
        List<Long> r = new ArrayList<>();
        for (int i = 0; i < GROUPS; i++) r.add(0L);
        return r;
    }

    private List<Long> zeroMatrix() {
        List<Long> r = new ArrayList<>();
        for (int i = 0; i < GROUPS * FIELDS; i++) r.add(0L);
        return r;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inactivityWatcher != null) inactivityWatcher.cancel();
        if (timer != null) timer.cancel();
        if (revealHandler != null) revealHandler.removeCallbacksAndMessages(null);
        if (sm != null) sm.cleanup();
    }
}