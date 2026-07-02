package com.example.slagalica.network;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.slagalica.R;
import com.example.slagalica.data.AvatarHelper;
import com.example.slagalica.data.GameSessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class NetworkSkockoGame extends AppCompatActivity {

    private boolean opponentLeft = false;
    private static final int TOTAL_COLS = 4;
    private static final int TOTAL_ROWS = 6;
    private static final int ROUND_TIME = 60;
    private static final int STEAL_TIME = 10;
    private static final int END_DELAY = 2000;
    private static final int SOLUTION_DISPLAY_MS = 5000;

    private static final String PHASE_INIT = "init";
    private static final String PHASE_R1_PLAY = "r1_play";
    private static final String PHASE_R1_STEAL = "r1_steal";
    private static final String PHASE_R2_PLAY = "r2_play";
    private static final String PHASE_R2_STEAL = "r2_steal";
    private static final String PHASE_DONE = "done";

    private static final String[] SYMBOLS = {"S", "T", "K", "P", "H", "Z"};
    private static final int[] DRAWABLES = {
            R.drawable.ic_skocko, R.drawable.ic_tref, R.drawable.ic_karo,
            R.drawable.ic_pik, R.drawable.ic_herc, R.drawable.ic_zvezda
    };

    private GameSessionManager sm;
    private int me, opp, gameIdx, prevP1, prevP2, totalGames;
    private boolean finished;

    private String syncPhase = PHASE_INIT;
    private int syncActivePlayer = 1;
    private List<String> syncSolR1, syncSolR2;
    private List<List<String>> syncR1G = new ArrayList<>();
    private List<List<Long>> syncR1F = new ArrayList<>();
    private int syncR1Row;
    private boolean syncR1Won, syncR1Done;
    private List<String> syncR1SG = new ArrayList<>();
    private List<Long> syncR1SF = new ArrayList<>();
    private boolean syncR1SDone, syncR1SWon;
    private List<List<String>> syncR2G = new ArrayList<>();
    private List<List<Long>> syncR2F = new ArrayList<>();
    private int syncR2Row;
    private boolean syncR2Won, syncR2Done;
    private List<String> syncR2SG = new ArrayList<>();
    private List<Long> syncR2SF = new ArrayList<>();
    private boolean syncR2SDone, syncR2SWon;
    private long syncP1, syncP2;
    private long syncR1Attempt = -1, syncR2Attempt = -1;

    private boolean loaded;
    private boolean iAmFinisher;
    private String lastPhase = "";
    private boolean isMyTurn;
    private int localCol;
    private int stealCol;
    private int myPts, oppPts;

    private CountDownTimer timer;
    private boolean timerRun;
    private int timerSec;

    private TextView tvTimer, tvInstr, tvMyName, tvOppName, tvMyScore, tvOppScore;
    private ImageView ivMyAvatar, ivOppAvatar, ivSol0, ivSol1, ivSol2, ivSol3;

    private ImageView[][] cells;
    private ImageView[][] dots;
    private Button[] subBtns;
    private LinearLayout[] dotConts;

    private LinearLayout stealRow;
    private ImageView[] stealCells;
    private Button stealBtn;
    private ImageView[] stealDots;
    private LinearLayout stealDotCont;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_skocko);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        try {
            Intent i = getIntent();
            me = i.getIntExtra("myPlayerNumber", 1);
            opp = me == 1 ? 2 : 1;
            gameIdx = i.getIntExtra("gameIndex", 0);
            prevP1 = i.getIntExtra("previousPlayer1Score", 0);
            prevP2 = i.getIntExtra("previousPlayer2Score", 0);
            totalGames = i.getIntExtra("totalGames", 6);
            boolean spectator = i.getBooleanExtra("isSpectator", false);

            tvTimer = findViewById(R.id.timerText);
            tvInstr = findViewById(R.id.instructionsTextView);
            tvMyName = findViewById(R.id.playerOneName);
            tvOppName = findViewById(R.id.playerTwoName);
            tvMyScore = findViewById(R.id.playerOneScore);
            tvOppScore = findViewById(R.id.playerTwoScore);
            ivMyAvatar = findViewById(R.id.playerOneAvatar);
            ivOppAvatar = findViewById(R.id.playerTwoAvatar);
            ivSol0 = findViewById(R.id.sol0);
            ivSol1 = findViewById(R.id.sol1);
            ivSol2 = findViewById(R.id.sol2);
            ivSol3 = findViewById(R.id.sol3);

            buildBoard();
            setupSymbolBtns();

            tvTimer.setVisibility(View.GONE);
            tvInstr.setText("Priprema...");

            String myName = i.getStringExtra("myPlayerName");
            if (myName == null || myName.isEmpty()) myName = me == 1 ? "Igrač 1" : "Igrač 2";
            String myAvatar = i.getStringExtra("myAvatarUrl");

            if (me == 1) {
                syncSolR1 = genSol();
                syncSolR2 = genSol();
            }

            sm = new GameSessionManager();
            sm.attachToMatch(i.getStringExtra("matchId"), me);
            sm.listenToMatch(createListener());

            if (spectator) {
                int[] symIds = {R.id.btnSkocko, R.id.btnTref, R.id.btnKaro, R.id.btnPik, R.id.btnHerc, R.id.btnZvezda};
                for (int id : symIds) { View v = findViewById(id); if (v != null) v.setEnabled(false); }
                for (Button b : subBtns) if (b != null) b.setEnabled(false);
                if (stealBtn != null) stealBtn.setEnabled(false);
            }

            if (me == 1) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!finished) writeInit();
                }, 300);
            }
            setupQuitButton();
        } catch (Exception e) {
            Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupQuitButton() {
        ImageButton quitBtn = findViewById(R.id.quitGameButton);
        if (quitBtn != null) {
            quitBtn.setVisibility(View.VISIBLE);
            quitBtn.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle("Napusti igru")
                    .setMessage("Napuštanjem igre igrač gubi partiju i ne dobija zvezde. Protivnik nastavlja partiju.")
                    .setPositiveButton("Napusti", (d, w) -> {
                        if (sm != null) { sm.forfeitMatch(); sm.cleanup(); }
                        finished = true;
                        if (timer != null) timer.cancel();
                        finish();
                    })
                    .setNegativeButton("Nastavi", null)
                    .show());
        }
    }

    // ===== SOLUTION DISPLAY HELPER =====

    /**
     * Prikazuje rešenje u gornjem redu 5 sekundi, zaustavlja tajmer,
     * a zatim poziva after() da nastavi sa sledećom fazom.
     * Poziva je samo aktivni igrač (onaj ko šalje state update).
     */
    private void showSolutionThenProceed(List<String> sol, Runnable after) {
        if (timer != null) timer.cancel();
        timerRun = false;
        tvTimer.setVisibility(View.GONE);
        tvInstr.setText("Rešenje:");
        setSolution(sol);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!finished) after.run();
        }, SOLUTION_DISPLAY_MS);
    }

    // ===== LISTENER =====

    private GameSessionManager.StateListener createListener() {
        return new GameSessionManager.StateListener() {
            public void onStateChanged(Map<String, Object> full) {
                if (finished || isFinishing()) return;
                try {
                    Map<String, Object> gs = (Map<String, Object>) full.get("gameState");
                    if (gs == null || gs.isEmpty()) return;

                    setNames(full);

                    if (!loaded) {
                        if (gs.containsKey("solutionR1") && gs.get("solutionR1") != null) {
                            syncSolR1 = readStrList(gs.get("solutionR1"));
                            syncSolR2 = readStrList(gs.get("solutionR2"));
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
                        boolean prevR1W = syncR1Won;
                        boolean prevR2W = syncR2Won;
                        boolean prevR1SD = syncR1SDone;
                        boolean prevR2SD = syncR2SDone;
                        syncState(gs);
                        updateUI();
                        if (syncR1Won && !prevR1W)
                            Toast.makeText(NetworkSkockoGame.this,
                                    "Kombinacija pogodjena u rundi 1!",
                                    Toast.LENGTH_LONG).show();
                        if (syncR2Won && !prevR2W)
                            Toast.makeText(NetworkSkockoGame.this,
                                    "Kombinacija pogodjena u rundi 2!",
                                    Toast.LENGTH_LONG).show();
                        if (syncR1SDone && !prevR1SD)
                            Toast.makeText(NetworkSkockoGame.this,
                                    syncR1SWon ? "Krađa uspela! (+10)" : "Krađa nije uspela!",
                                    Toast.LENGTH_LONG).show();
                        if (syncR2SDone && !prevR2SD)
                            Toast.makeText(NetworkSkockoGame.this,
                                    syncR2SWon ? "Krađa uspela! (+10)" : "Krađa nije uspela!",
                                    Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    // ignore
                }
            }

            public void onMatchEnded(Map<String, Object> f) {
                if (opponentLeft) return;
                opponentLeft = true;
                new Handler(Looper.getMainLooper()).post(() -> endGame());
            }

            public void onError(String e) {}
        };
    }

    private void setNames(Map<String, Object> full) {
        String p1n = (String) full.get("player1Name");
        String p2n = (String) full.get("player2Name");
        String p1a = (String) full.get("player1Avatar");
        String p2a = (String) full.get("player2Avatar");
        String p1id = (String) full.get("player1Id");
        String p2id = (String) full.get("player2Id");
        if (me == 1) {
            tvMyName.setText(p1n != null ? p1n : "Igrač 1");
            tvOppName.setText(p2n != null ? p2n : "Protivnik");
            tvMyName.setTextColor(0xFF1565C0);
            tvOppName.setTextColor(0xFFE65100);
            if (p1a != null) loadAvatar(ivMyAvatar, p1id, p1a);
            if (p2a != null) loadAvatar(ivOppAvatar, p2id, p2a);
        } else {
            tvMyName.setText(p2n != null ? p2n : "Igrač 2");
            tvOppName.setText(p1n != null ? p1n : "Protivnik");
            tvMyName.setTextColor(0xFFE65100);
            tvOppName.setTextColor(0xFF1565C0);
            if (p1a != null) loadAvatar(ivOppAvatar, p1id, p1a);
            if (p2a != null) loadAvatar(ivMyAvatar, p2id, p2a);
        }
    }

    // ===== STATE SYNC =====

    private void syncState(Map<String, Object> gs) {
        syncPhase = str(gs.get("phase"));
        syncActivePlayer = gs.get("activePlayer") instanceof Long ? ((Long) gs.get("activePlayer")).intValue() : 1;
        syncSolR1 = readStrList(gs.get("solutionR1"));
        syncSolR2 = readStrList(gs.get("solutionR2"));
        syncR1G = readStrListList(gs.get("r1Guesses"));
        syncR1F = readLongListList(gs.get("r1Feedback"));
        syncR1Row = gs.get("r1CurrentRow") instanceof Long ? ((Long) gs.get("r1CurrentRow")).intValue() : 0;
        syncR1Won = bool(gs.get("r1Won"));
        syncR1Done = bool(gs.get("r1Done"));
        syncR1SG = readStrList(gs.get("r1StealGuess"));
        syncR1SF = readLongList(gs.get("r1StealFeedback"));
        syncR1SDone = bool(gs.get("r1StealDone"));
        syncR1SWon = bool(gs.get("r1StealWon"));
        syncR2G = readStrListList(gs.get("r2Guesses"));
        syncR2F = readLongListList(gs.get("r2Feedback"));
        syncR2Row = gs.get("r2CurrentRow") instanceof Long ? ((Long) gs.get("r2CurrentRow")).intValue() : 0;
        syncR2Won = bool(gs.get("r2Won"));
        syncR2Done = bool(gs.get("r2Done"));
        syncR2SG = readStrList(gs.get("r2StealGuess"));
        syncR2SF = readLongList(gs.get("r2StealFeedback"));
        syncR2SDone = bool(gs.get("r2StealDone"));
        syncR2SWon = bool(gs.get("r2StealWon"));
        syncP1 = gs.get("p1Score") instanceof Long ? (Long) gs.get("p1Score") : 0;
        syncP2 = gs.get("p2Score") instanceof Long ? (Long) gs.get("p2Score") : 0;
        syncR1Attempt = gs.get("r1Attempt") instanceof Long ? (Long) gs.get("r1Attempt") : -1L;
        syncR2Attempt = gs.get("r2Attempt") instanceof Long ? (Long) gs.get("r2Attempt") : -1L;
        syncR1SWon = bool(gs.get("r1StealWon"));
        syncR2SWon = bool(gs.get("r2StealWon"));
        myPts = (int) (me == 1 ? syncP1 : syncP2);
        oppPts = (int) (me == 1 ? syncP2 : syncP1);

        if (PHASE_R1_PLAY.equals(syncPhase) && syncR1Row < syncR1G.size())
            localCol = countFilled(syncR1G.get(syncR1Row));
        else if (PHASE_R2_PLAY.equals(syncPhase) && syncR2Row < syncR2G.size())
            localCol = countFilled(syncR2G.get(syncR2Row));
        else localCol = 0;

        if (PHASE_R1_STEAL.equals(syncPhase)) stealCol = syncR1SG.size();
        else if (PHASE_R2_STEAL.equals(syncPhase)) stealCol = syncR2SG.size();
        else stealCol = 0;
    }

    // ===== UI =====

    private void updateUI() {
        isMyTurn = syncActivePlayer == me;

        boolean r1p = PHASE_R1_PLAY.equals(syncPhase);
        boolean r1s = PHASE_R1_STEAL.equals(syncPhase);
        boolean r2p = PHASE_R2_PLAY.equals(syncPhase);
        boolean r2s = PHASE_R2_STEAL.equals(syncPhase);
        boolean done = PHASE_DONE.equals(syncPhase);

        int myTotal = (me == 1 ? prevP1 : prevP2) + myPts;
        int oppTotal = (me == 1 ? prevP2 : prevP1) + oppPts;
        tvMyScore.setText(String.valueOf(myTotal));
        tvOppScore.setText(String.valueOf(oppTotal));

        if (!syncPhase.equals(lastPhase)) {
            lastPhase = syncPhase;
            onPhaseChanged();
        }

        if (done) {
            tvInstr.setText("Kraj igre");
            tvTimer.setVisibility(View.GONE);
            if (iAmFinisher) endGame();
            return;
        }

        tvTimer.setVisibility(View.VISIBLE);
        int tDef = (r1s || r2s) ? STEAL_TIME : ROUND_TIME;
        int sec = timerRun ? timerSec : tDef;
        tvTimer.setText(String.valueOf(sec));
        tvTimer.setTextColor(sec <= 5 ? 0xFFFF0000 : 0xFFFFFFFF);

        if (r1p || r2p) {
            int r = r1p ? 1 : 2;
            tvInstr.setText("Runda " + r + " - " + (isMyTurn ? "Tvoj potez" : "Čekanje..."));
        } else if (r1s || r2s) {
            int r = r1s ? 1 : 2;
            tvInstr.setText("Runda " + r + " - " + (isMyTurn ? "Krađa!" : "Protivnik pokušava..."));
        }

        renderBoard(r1p || r1s, r2p || r2s, r1s || r2s);
        renderStealRow(r1s || r2s);

        if (r1p || r1s) {
            boolean show = syncR1Won || syncR1SDone;
            setSolution(show ? syncSolR1 : null);
        } else if (r2p || r2s || done) {
            boolean show = syncR2Won || syncR2SDone || done;
            setSolution(show ? syncSolR2 : null);
        } else {
            setSolution(null);
        }
    }

    private void renderBoard(boolean showR1, boolean showR2, boolean isSteal) {
        for (int r = 0; r < TOTAL_ROWS; r++) {
            for (int c = 0; c < TOTAL_COLS; c++) {
                if (cells[r][c] != null) cells[r][c].setImageDrawable(null);
            }
            if (dotConts[r] != null) dotConts[r].setVisibility(View.GONE);
            if (subBtns[r] != null) subBtns[r].setVisibility(View.GONE);
        }

        List<List<String>> guesses = showR1 ? syncR1G : syncR2G;
        List<List<Long>> fb = showR1 ? syncR1F : syncR2F;
        int curRow = showR1 ? syncR1Row : syncR2Row;
        boolean won = showR1 ? syncR1Won : syncR2Won;
        boolean done = showR1 ? syncR1Done : syncR2Done;

        for (int r = 0; r < TOTAL_ROWS; r++) {
            boolean cur = r == curRow && !isSteal && !done;

            if (r < guesses.size()) {
                List<String> g = guesses.get(r);
                for (int c = 0; c < TOTAL_COLS && c < g.size(); c++) {
                    int idx = symIdx(g.get(c));
                    if (idx >= 0 && cells[r][c] != null)
                        cells[r][c].setImageDrawable(ContextCompat.getDrawable(this, DRAWABLES[idx]));
                }
            }

            if (r < fb.size() && dotConts[r] != null) {
                dotConts[r].setVisibility(View.VISIBLE);
                List<Long> fr = fb.get(r);
                for (int c = 0; c < 4 && c < fr.size(); c++) {
                    long v = fr.get(c);
                    if (dots[r][c] != null) {
                        if (v == 1L) dots[r][c].setBackgroundResource(R.drawable.feedback_circle_red);
                        else if (v == 2L) dots[r][c].setBackgroundResource(R.drawable.feedback_circle_yellow);
                        else dots[r][c].setBackgroundResource(R.drawable.feedback_circle);
                    }
                }
            } else if (dotConts[r] != null) {
                dotConts[r].setVisibility(View.GONE);
            }

            if (subBtns[r] != null) {
                if (isSteal || !cur) {
                    subBtns[r].setVisibility(View.GONE);
                } else if (isMyTurn) {
                    subBtns[r].setVisibility(View.VISIBLE);
                    subBtns[r].setEnabled(guesses.size() > r && guesses.get(r) != null
                            && guesses.get(r).size() >= TOTAL_COLS && !won && !done);
                } else {
                    subBtns[r].setVisibility(View.GONE);
                }
            }
        }
    }

    private void renderStealRow(boolean isSteal) {
        if (stealRow == null) return;
        boolean show = isSteal && isMyTurn;
        stealRow.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        boolean isR1 = PHASE_R1_STEAL.equals(syncPhase);
        List<String> sg = isR1 ? syncR1SG : syncR2SG;
        boolean sd = isR1 ? syncR1SDone : syncR2SDone;
        List<Long> sfb = isR1 ? syncR1SF : syncR2SF;

        for (int c = 0; c < TOTAL_COLS; c++) {
            if (stealCells[c] != null) {
                if (c < sg.size()) {
                    int idx = symIdx(sg.get(c));
                    if (idx >= 0) stealCells[c].setImageDrawable(ContextCompat.getDrawable(this, DRAWABLES[idx]));
                    else stealCells[c].setImageDrawable(null);
                } else {
                    stealCells[c].setImageDrawable(null);
                }
            }
        }
        if (stealBtn != null) {
            stealBtn.setEnabled(sg.size() >= TOTAL_COLS && !sd);
            stealBtn.setVisibility(sd ? View.GONE : View.VISIBLE);
        }
        if (stealDotCont != null) {
            stealDotCont.setVisibility(sd ? View.VISIBLE : View.GONE);
            if (sd) {
                for (int c = 0; c < 4 && c < sfb.size(); c++) {
                    if (stealDots[c] != null) {
                        long v = sfb.get(c);
                        if (v == 1L) stealDots[c].setBackgroundResource(R.drawable.feedback_circle_red);
                        else if (v == 2L) stealDots[c].setBackgroundResource(R.drawable.feedback_circle_yellow);
                        else stealDots[c].setBackgroundResource(R.drawable.feedback_circle);
                    }
                }
            }
        }
    }

    private void setSolution(List<String> sol) {
        ImageView[] sv = {ivSol0, ivSol1, ivSol2, ivSol3};
        for (int i = 0; i < TOTAL_COLS; i++) {
            if (sv[i] == null) continue;
            if (sol != null && i < sol.size()) {
                int idx = symIdx(sol.get(i));
                sv[i].setImageDrawable(idx >= 0 ? ContextCompat.getDrawable(this, DRAWABLES[idx]) : null);
            } else {
                sv[i].setImageDrawable(null);
            }
        }
    }

    // ===== PHASE / TIMER =====

    private void onPhaseChanged() {
        if (timer != null) timer.cancel();
        timerRun = false;
        localCol = 0;
        stealCol = 0;
        boolean isP = PHASE_R1_PLAY.equals(syncPhase) || PHASE_R2_PLAY.equals(syncPhase);
        boolean isS = PHASE_R1_STEAL.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase);
        if (isP) startTimer(ROUND_TIME);
        else if (isS) startTimer(STEAL_TIME);
    }

    private void startTimer(int sec) {
        if (timer != null) timer.cancel();
        timerSec = sec;
        timerRun = true;
        timer = new CountDownTimer(sec * 1000L, 1000) {
            public void onTick(long m) {
                timerSec = (int) (m / 1000) + 1;
                tvTimer.setText(String.valueOf(timerSec));
                tvTimer.setTextColor(timerSec <= 5 ? 0xFFFF0000 : 0xFFFFFFFF);
            }
            public void onFinish() {
                timerRun = false;
                tvTimer.setText("0");
                tvTimer.setTextColor(0xFFFF0000);
                if (!isMyTurn || finished) return;
                onTimerEnd();
            }
        }.start();
    }

    private void onTimerEnd() {
        boolean isP = PHASE_R1_PLAY.equals(syncPhase) || PHASE_R2_PLAY.equals(syncPhase);
        boolean isS = PHASE_R1_STEAL.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase);
        if (isP) {
            boolean isR1 = PHASE_R1_PLAY.equals(syncPhase);
            boolean won = isR1 ? syncR1Won : syncR2Won;
            boolean d = isR1 ? syncR1Done : syncR2Done;
            if (won || d) return;
            String np = isR1 ? PHASE_R1_STEAL : PHASE_R2_STEAL;
            // Prvo sačuvaj r1Done/r2Done bez phase promene
            Map<String, Object> u = new HashMap<>();
            u.put(isR1 ? "r1Done" : "r2Done", true);
            sm.updateGameState(u);
            // Prikaži rešenje 5s pa onda pošalji novu fazu
            final boolean isR1f = isR1;
            showSolutionThenProceed(isR1f ? syncSolR1 : syncSolR2, () -> {
                Map<String, Object> u2 = new HashMap<>();
                u2.put("phase", np);
                u2.put("activePlayer", (long) opp);
                sm.updateGameState(u2);
            });
        } else if (isS) {
            boolean isR1 = PHASE_R1_STEAL.equals(syncPhase);
            boolean sd = isR1 ? syncR1SDone : syncR2SDone;
            if (sd) return;
            String np = isR1 ? PHASE_R2_PLAY : PHASE_DONE;
            // Sačuvaj steal rezultat bez phase promene
            Map<String, Object> u = new HashMap<>();
            u.put(isR1 ? "r1StealDone" : "r2StealDone", true);
            u.put(isR1 ? "r1StealWon" : "r2StealWon", false);
            if (isR1) {
                u.put("r1Guesses", new ArrayList<>());
                u.put("r1Feedback", new ArrayList<>());
                u.put("r1CurrentRow", 0L);
                u.put("r1StealGuess", new ArrayList<>());
                u.put("r1StealFeedback", new ArrayList<>());
            }
            sm.updateGameState(u);
            // Prikaži rešenje 5s pa onda pošalji novu fazu
            final boolean isR1f = isR1;
            showSolutionThenProceed(isR1f ? syncSolR1 : syncSolR2, () -> {
                Map<String, Object> u2 = new HashMap<>();
                u2.put("phase", np);
                u2.put("activePlayer", isR1f ? 2L : -1L);
                sm.updateGameState(u2);
            });
        }
    }

    // ===== SYMBOL CLICK =====

    private void onSymbolClicked(String sym) {
        if (finished || !isMyTurn) return;

        if (PHASE_R1_STEAL.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase)) {
            if (stealCol >= TOTAL_COLS) return;
            boolean isR1 = PHASE_R1_STEAL.equals(syncPhase);
            boolean sd = isR1 ? syncR1SDone : syncR2SDone;
            if (sd) return;
            List<String> sg = isR1 ? syncR1SG : syncR2SG;
            if (sg.size() <= stealCol) sg.add(sym);
            else sg.set(stealCol, sym);
            stealCol++;
            sm.updateField("gameState." + (isR1 ? "r1StealGuess" : "r2StealGuess"), new ArrayList<>(sg));
            updateUI();
        } else if (PHASE_R1_PLAY.equals(syncPhase) || PHASE_R2_PLAY.equals(syncPhase)) {
            if (localCol >= TOTAL_COLS) return;
            boolean isR1 = PHASE_R1_PLAY.equals(syncPhase);
            List<List<String>> gs = isR1 ? syncR1G : syncR2G;
            int cr = isR1 ? syncR1Row : syncR2Row;
            boolean won = isR1 ? syncR1Won : syncR2Won;
            boolean dn = isR1 ? syncR1Done : syncR2Done;
            if (cr >= TOTAL_ROWS || won || dn) return;
            while (gs.size() <= cr) gs.add(new ArrayList<String>());
            List<String> row = gs.get(cr);
            while (row.size() <= localCol) row.add("");
            row.set(localCol, sym);
            localCol++;
            sm.updateField("gameState." + (isR1 ? "r1Guesses" : "r2Guesses"), serializeStrListList(gs));
            updateUI();
        }
    }

    private void onSubmitRow(int row) {
        if (finished || !isMyTurn) return;
        boolean isR1 = PHASE_R1_PLAY.equals(syncPhase);
        List<List<String>> gs = isR1 ? syncR1G : syncR2G;
        int cr = isR1 ? syncR1Row : syncR2Row;
        if (row != cr) return;
        if (row >= gs.size() || gs.get(row).size() < TOTAL_COLS) return;
        if ((isR1 ? syncR1Won : syncR2Won) || (isR1 ? syncR1Done : syncR2Done)) return;

        List<String> sol = isR1 ? syncSolR1 : syncSolR2;
        List<String> guess = gs.get(row);

        int black = 0, white = 0;
        boolean[] usedSol = new boolean[TOTAL_COLS];
        boolean[] usedG = new boolean[TOTAL_COLS];
        for (int i = 0; i < TOTAL_COLS; i++) {
            if (i < guess.size() && i < sol.size() && guess.get(i).equals(sol.get(i))) {
                black++; usedSol[i] = true; usedG[i] = true;
            }
        }
        for (int i = 0; i < TOTAL_COLS; i++) {
            if (usedG[i] || i >= guess.size()) continue;
            for (int j = 0; j < TOTAL_COLS; j++) {
                if (!usedSol[j] && j < sol.size() && guess.get(i).equals(sol.get(j))) {
                    white++; usedSol[j] = true; break;
                }
            }
        }

        List<Long> fbRow = new ArrayList<>();
        for (int i = 0; i < black; i++) fbRow.add(1L);
        for (int i = 0; i < white; i++) fbRow.add(2L);
        while (fbRow.size() < TOTAL_COLS) fbRow.add(0L);

        List<List<Long>> fb = isR1 ? syncR1F : syncR2F;
        while (fb.size() <= row) fb.add(new ArrayList<Long>());
        if (row < fb.size()) fb.set(row, fbRow);

        boolean solved = black == TOTAL_COLS;
        int nextRow = row + 1;
        boolean out = nextRow >= TOTAL_ROWS;

        Map<String, Object> u = new HashMap<>();
        u.put(isR1 ? "r1Feedback" : "r2Feedback", serializeLongListList(fb));
        u.put(isR1 ? "r1CurrentRow" : "r2CurrentRow", (long) nextRow);

        if (solved) {
            int pts = nextRow <= 2 ? 20 : (nextRow <= 4 ? 15 : 10);
            if (isR1) syncP1 += pts; else syncP2 += pts;
            u.put(isR1 ? "r1Won" : "r2Won", true);
            u.put(isR1 ? "r1Attempt" : "r2Attempt", (long) nextRow);
            u.put(isR1 ? "r1Done" : "r2Done", true);
            u.put("p1Score", syncP1);
            u.put("p2Score", syncP2);
            // Sačuvaj bez phase — phase šaljemo posle prikaza rešenja
            sm.updateGameState(u);
            String np = isR1 ? PHASE_R2_PLAY : PHASE_DONE;
            final boolean isR1f = isR1;
            showSolutionThenProceed(isR1f ? syncSolR1 : syncSolR2, () -> {
                Map<String, Object> u2 = new HashMap<>();
                u2.put("phase", np);
                u2.put("activePlayer", isR1f ? 2L : -1L);
                sm.updateGameState(u2);
                if (PHASE_DONE.equals(np)) {
                    iAmFinisher = true;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> endGame(), END_DELAY);
                }
            });
        } else if (out) {
            u.put(isR1 ? "r1Done" : "r2Done", true);
            // Sačuvaj bez phase — phase šaljemo posle prikaza rešenja
            sm.updateGameState(u);
            String np = isR1 ? PHASE_R1_STEAL : PHASE_R2_STEAL;
            final boolean isR1f = isR1;
            showSolutionThenProceed(isR1f ? syncSolR1 : syncSolR2, () -> {
                Map<String, Object> u2 = new HashMap<>();
                u2.put("phase", np);
                u2.put("activePlayer", (long) opp);
                sm.updateGameState(u2);
            });
        } else {
            sm.updateGameState(u);
        }
    }

    private void onSubmitSteal() {
        if (finished || !isMyTurn) return;
        boolean isR1 = PHASE_R1_STEAL.equals(syncPhase);
        List<String> sg = isR1 ? syncR1SG : syncR2SG;
        if (sg.size() < TOTAL_COLS) return;
        if (isR1 ? syncR1SDone : syncR2SDone) return;

        List<String> sol = isR1 ? syncSolR1 : syncSolR2;
        boolean won = true;
        for (int i = 0; i < TOTAL_COLS; i++) {
            if (!(i < sg.size() && i < sol.size() && sg.get(i).equals(sol.get(i)))) {
                won = false;
                break;
            }
        }

        if (won) {
            if (isR1) syncP2 += 10; else syncP1 += 10;
        }

        String np = isR1 ? PHASE_R2_PLAY : PHASE_DONE;
        Map<String, Object> u = new HashMap<>();
        u.put(isR1 ? "r1StealDone" : "r2StealDone", true);
        u.put(isR1 ? "r1StealWon" : "r2StealWon", won);
        u.put("p1Score", syncP1);
        u.put("p2Score", syncP2);
        List<Long> sfb = new ArrayList<>();
        for (int i = 0; i < TOTAL_COLS; i++) {
            sfb.add(i < sg.size() && i < sol.size() && sg.get(i).equals(sol.get(i)) ? 1L : 0L);
        }
        u.put(isR1 ? "r1StealFeedback" : "r2StealFeedback", sfb);
        u.put(isR1 ? "r1StealGuess" : "r2StealGuess", new ArrayList<>());
        if (isR1) {
            u.put("r1Guesses", new ArrayList<>());
            u.put("r1Feedback", new ArrayList<>());
            u.put("r1CurrentRow", 0L);
        }
        // Sačuvaj bez phase — phase šaljemo posle prikaza rešenja
        sm.updateGameState(u);
        final boolean isR1f = isR1;
        showSolutionThenProceed(isR1f ? syncSolR1 : syncSolR2, () -> {
            Map<String, Object> u2 = new HashMap<>();
            u2.put("phase", np);
            u2.put("activePlayer", isR1f ? 2L : -1L);
            sm.updateGameState(u2);
            if (PHASE_DONE.equals(np)) {
                iAmFinisher = true;
                new Handler(Looper.getMainLooper()).postDelayed(() -> endGame(), END_DELAY);
            }
        });
    }

    // ===== INIT FLOW =====

    private void writeInit() {
        Map<String, Object> s = new HashMap<>();
        s.put("phase", PHASE_INIT);
        s.put("activePlayer", 1L);
        s.put("solutionR1", syncSolR1);
        s.put("solutionR2", syncSolR2);
        s.put("r1Guesses", new ArrayList<>());
        s.put("r1Feedback", new ArrayList<>());
        s.put("r1CurrentRow", 0L);
        s.put("r1Won", false);
        s.put("r1Attempt", 0L);
        s.put("r1Done", false);
        s.put("r1StealGuess", new ArrayList<>());
        s.put("r1StealFeedback", new ArrayList<>());
        s.put("r1StealDone", false);
        s.put("r1StealWon", false);
        s.put("r2Guesses", new ArrayList<>());
        s.put("r2Feedback", new ArrayList<>());
        s.put("r2CurrentRow", 0L);
        s.put("r2Won", false);
        s.put("r2Attempt", 0L);
        s.put("r2Done", false);
        s.put("r2StealGuess", new ArrayList<>());
        s.put("r2StealFeedback", new ArrayList<>());
        s.put("r2StealDone", false);
        s.put("r2StealWon", false);
        s.put("p1Score", 0L);
        s.put("p2Score", 0L);
        s.put("playerReady", 1L);
        sm.setGameState(s);
    }

    private void startR1() {
        iAmFinisher = true;
        Map<String, Object> s = new HashMap<>();
        s.put("phase", PHASE_R1_PLAY);
        s.put("activePlayer", 1L);
        s.put("solutionR1", syncSolR1);
        s.put("solutionR2", syncSolR2);
        s.put("r1Guesses", new ArrayList<>());
        s.put("r1Feedback", new ArrayList<>());
        s.put("r1CurrentRow", 0L);
        s.put("r1Won", false);
        s.put("r1Attempt", 0L);
        s.put("r1Done", false);
        s.put("r1StealGuess", new ArrayList<>());
        s.put("r1StealFeedback", new ArrayList<>());
        s.put("r1StealDone", false);
        s.put("r1StealWon", false);
        s.put("r2Guesses", new ArrayList<>());
        s.put("r2Feedback", new ArrayList<>());
        s.put("r2CurrentRow", 0L);
        s.put("r2Won", false);
        s.put("r2Attempt", 0L);
        s.put("r2Done", false);
        s.put("r2StealGuess", new ArrayList<>());
        s.put("r2StealFeedback", new ArrayList<>());
        s.put("r2StealDone", false);
        s.put("r2StealWon", false);
        s.put("p1Score", 0L);
        s.put("p2Score", 0L);
        s.put("playerReady", 3L);
        sm.setGameState(s);
    }

    private void endGame() {
        if (finished) return;
        finished = true;
        if (timer != null) { timer.cancel(); timer = null; }
        int tP1 = prevP1 + (int) syncP1;
        int tP2 = prevP2 + (int) syncP2;
        Map<String, Object> stats = new HashMap<>();
        stats.put("gameType", GameSessionManager.GAME_TYPE_SKOCKO);
        stats.put("p1Attempt", syncR1Attempt);
        stats.put("p2Attempt", syncR2Attempt);
        stats.put("p1StealWon", syncR1SWon);
        stats.put("p2StealWon", syncR2SWon);
        stats.put("player1Score", (long) syncP1);
        stats.put("player2Score", (long) syncP2);
        sm.finishCurrentGame(gameIdx, (int) syncP1, (int) syncP2, tP1, tP2, 6, stats);
        sm.cleanup();
        setResult(RESULT_OK);
        finish();
    }

    // ===== BOARD BUILD =====

    private void buildBoard() {
        LinearLayout cont = findViewById(R.id.gameContainer);
        if (cont == null) return;
        cont.removeAllViews();

        cells = new ImageView[TOTAL_ROWS][TOTAL_COLS];
        dots = new ImageView[TOTAL_ROWS][4];
        subBtns = new Button[TOTAL_ROWS];
        dotConts = new LinearLayout[TOTAL_ROWS];

        int cellPad = (int) (4 * getResources().getDisplayMetrics().density + 0.5f);

        for (int r = 0; r < TOTAL_ROWS; r++) {
            LinearLayout rowL = new LinearLayout(this);
            rowL.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            rowL.setOrientation(LinearLayout.HORIZONTAL);
            rowL.setGravity(Gravity.CENTER_VERTICAL);
            rowL.setPadding(4, 2, 4, 2);

            LinearLayout flds = new LinearLayout(this);
            flds.setOrientation(LinearLayout.HORIZONTAL);
            flds.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            for (int c = 0; c < TOTAL_COLS; c++) {
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(180, 150);
                p.setMargins(2, 2, 2, 2);
                iv.setLayoutParams(p);
                iv.setPadding(cellPad, cellPad, cellPad, cellPad);
                iv.setBackgroundResource(R.drawable.cell_background);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                final int fr = r, fc = c;
                iv.setOnClickListener(v -> {
                    if (!finished && isMyTurn && !(PHASE_R1_STEAL.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase))) {
                        boolean isR1 = PHASE_R1_PLAY.equals(syncPhase);
                        int cr = isR1 ? syncR1Row : syncR2Row;
                        if (fr == cr) {
                            List<List<String>> gs = isR1 ? syncR1G : syncR2G;
                            if (fr < gs.size()) {
                                List<String> rw = gs.get(fr);
                                if (fc < rw.size() && fc < localCol) {
                                    rw.remove(fc);
                                    while (rw.size() < TOTAL_COLS) rw.add("");
                                    rw.remove(rw.size() - 1);
                                    localCol--;
                                    sm.updateField("gameState." + (isR1 ? "r1Guesses" : "r2Guesses"), serializeStrListList(gs));
                                    updateUI();
                                }
                            }
                        }
                    }
                });
                cells[r][c] = iv;
                flds.addView(iv);
            }

            Button sb = new Button(this);
            sb.setText("OK");
            sb.setTextSize(16);
            sb.setTextColor(Color.BLACK);
            sb.setGravity(Gravity.CENTER);
            sb.setPadding(0, 0, 0, 0);
            sb.setEnabled(false);
            sb.setVisibility(View.GONE);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(100, 100);
            bp.setMargins(4, 0, 2, 0);
            sb.setLayoutParams(bp);
            final int fr2 = r;
            sb.setOnClickListener(v -> onSubmitRow(fr2));
            subBtns[r] = sb;

            LinearLayout fbl = new LinearLayout(this);
            fbl.setOrientation(LinearLayout.VERTICAL);
            fbl.setPadding(8, 2, 0, 2);
            LinearLayout ft = new LinearLayout(this);
            ft.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout fb2 = new LinearLayout(this);
            fb2.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 4; i++) {
                ImageView fv = new ImageView(this);
                LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(32, 32);
                fp.setMargins(1, 1, 1, 1);
                fv.setLayoutParams(fp);
                fv.setBackgroundResource(R.drawable.feedback_circle);
                if (i < 2) ft.addView(fv); else fb2.addView(fv);
                dots[r][i] = fv;
            }
            fbl.addView(ft);
            fbl.addView(fb2);
            dotConts[r] = fbl;
            fbl.setVisibility(View.GONE);

            rowL.addView(flds);
            rowL.addView(sb);
            rowL.addView(fbl);
            cont.addView(rowL);
        }

        // Steal row
        stealRow = new LinearLayout(this);
        stealRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        stealRow.setOrientation(LinearLayout.HORIZONTAL);
        stealRow.setGravity(Gravity.CENTER_VERTICAL);
        stealRow.setPadding(4, 4, 4, 4);
        stealRow.setBackgroundColor(0x33FFFFFF);
        stealRow.setVisibility(View.GONE);

        TextView lbl = new TextView(this);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        lbl.setText("Krađa:");
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(14);
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        stealRow.addView(lbl);

        stealCells = new ImageView[TOTAL_COLS];
        LinearLayout sf = new LinearLayout(this);
        sf.setOrientation(LinearLayout.HORIZONTAL);
        sf.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        for (int c = 0; c < TOTAL_COLS; c++) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(100, 100);
            p.setMargins(1, 1, 1, 1);
            iv.setLayoutParams(p);
            iv.setPadding(cellPad, cellPad, cellPad, cellPad);
            iv.setBackgroundResource(R.drawable.cell_background);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            final int fc = c;
            iv.setOnClickListener(v -> {
                if (!finished && isMyTurn && (PHASE_R1_STEAL.equals(syncPhase) || PHASE_R2_STEAL.equals(syncPhase))) {
                    boolean isR1 = PHASE_R1_STEAL.equals(syncPhase);
                    List<String> sg = isR1 ? syncR1SG : syncR2SG;
                    if (fc < stealCol && fc < sg.size()) {
                        sg.remove(fc);
                        stealCol--;
                        sm.updateField("gameState." + (isR1 ? "r1StealGuess" : "r2StealGuess"), new ArrayList<>(sg));
                        updateUI();
                    }
                }
            });
            stealCells[c] = iv;
            sf.addView(iv);
        }

        stealBtn = new Button(this);
        stealBtn.setText("OK");
        stealBtn.setTextSize(14);
        stealBtn.setTextColor(Color.BLACK);
        stealBtn.setGravity(Gravity.CENTER);
        stealBtn.setPadding(0, 0, 0, 0);
        stealBtn.setEnabled(false);
        LinearLayout.LayoutParams sbp2 = new LinearLayout.LayoutParams(85, 85);
        sbp2.setMargins(2, 0, 2, 0);
        stealBtn.setLayoutParams(sbp2);
        stealBtn.setOnClickListener(v -> onSubmitSteal());

        stealDotCont = new LinearLayout(this);
        stealDotCont.setOrientation(LinearLayout.VERTICAL);
        stealDotCont.setPadding(6, 2, 0, 2);
        LinearLayout sft = new LinearLayout(this);
        sft.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout sfb2 = new LinearLayout(this);
        sfb2.setOrientation(LinearLayout.HORIZONTAL);
        stealDots = new ImageView[4];
        for (int i = 0; i < 4; i++) {
            ImageView fv = new ImageView(this);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(30, 30);
            fp.setMargins(1, 1, 1, 1);
            fv.setLayoutParams(fp);
            fv.setBackgroundResource(R.drawable.feedback_circle);
            if (i < 2) sft.addView(fv); else sfb2.addView(fv);
            stealDots[i] = fv;
        }
        stealDotCont.addView(sft);
        stealDotCont.addView(sfb2);
        stealDotCont.setVisibility(View.GONE);

        stealRow.addView(sf);
        stealRow.addView(stealBtn);
        stealRow.addView(stealDotCont);
        cont.addView(stealRow);
    }

    private void setupSymbolBtns() {
        View b;
        b = findViewById(R.id.btnSkocko); if (b != null) b.setOnClickListener(v -> onSymbolClicked("S"));
        b = findViewById(R.id.btnTref); if (b != null) b.setOnClickListener(v -> onSymbolClicked("T"));
        b = findViewById(R.id.btnKaro); if (b != null) b.setOnClickListener(v -> onSymbolClicked("K"));
        b = findViewById(R.id.btnPik); if (b != null) b.setOnClickListener(v -> onSymbolClicked("P"));
        b = findViewById(R.id.btnHerc); if (b != null) b.setOnClickListener(v -> onSymbolClicked("H"));
        b = findViewById(R.id.btnZvezda); if (b != null) b.setOnClickListener(v -> onSymbolClicked("Z"));
    }

    // ===== HELPERS =====

    private int countFilled(List<String> row) {
        int c = 0;
        for (String s : row) {
            if (s == null || s.isEmpty()) break;
            c++;
        }
        return c;
    }

    private int symIdx(String s) {
        if (s == null) return -1;
        for (int i = 0; i < SYMBOLS.length; i++) if (SYMBOLS[i].equals(s)) return i;
        return -1;
    }

    private List<String> genSol() {
        Random r = new Random();
        List<String> s = new ArrayList<>();
        for (int i = 0; i < TOTAL_COLS; i++) s.add(SYMBOLS[r.nextInt(SYMBOLS.length)]);
        return s;
    }

    private boolean bool(Object o) {
        return Boolean.TRUE.equals(o);
    }

    private String str(Object o) {
        return o instanceof String ? (String) o : PHASE_INIT;
    }

    private void loadAvatar(ImageView iv, String uid, String url) {
        AvatarHelper.loadAvatar(iv, uid, url);
    }

    // ===== SERIALIZATION =====

    private List<String> readStrList(Object o) {
        List<String> r = new ArrayList<>();
        if (o instanceof List) for (Object v : (List<?>) o) r.add(v != null ? v.toString() : "");
        return r;
    }

    private List<Long> readLongList(Object o) {
        List<Long> r = new ArrayList<>();
        if (o instanceof List) for (Object v : (List<?>) o) r.add(v instanceof Long ? (Long) v : 0L);
        return r;
    }

    private List<List<String>> readStrListList(Object o) {
        List<List<String>> r = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<?>) o) {
                if (item instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    List<String> row = new ArrayList<>();
                    for (int i = 0; i < TOTAL_COLS; i++) {
                        Object v = m.get("c" + i);
                        row.add(v != null ? v.toString() : "");
                    }
                    r.add(row);
                }
            }
        }
        return r;
    }

    private List<List<Long>> readLongListList(Object o) {
        List<List<Long>> r = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<?>) o) {
                if (item instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    List<Long> row = new ArrayList<>();
                    for (int i = 0; i < TOTAL_COLS; i++) {
                        Object v = m.get("c" + i);
                        row.add(v instanceof Long ? (Long) v : 0L);
                    }
                    r.add(row);
                }
            }
        }
        return r;
    }

    private List<Map<String, Object>> serializeStrListList(List<List<String>> lists) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (List<String> inner : lists) {
            Map<String, Object> m = new HashMap<>();
            for (int i = 0; i < inner.size(); i++) m.put("c" + i, inner.get(i));
            r.add(m);
        }
        return r;
    }

    private List<Map<String, Object>> serializeLongListList(List<List<Long>> lists) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (List<Long> inner : lists) {
            Map<String, Object> m = new HashMap<>();
            for (int i = 0; i < inner.size(); i++) m.put("c" + i, inner.get(i));
            r.add(m);
        }
        return r;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (sm != null) sm.cleanup();
    }
}