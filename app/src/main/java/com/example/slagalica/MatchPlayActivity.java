package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;
import java.util.List;

public class MatchPlayActivity extends AppCompatActivity {

    private final List<Class<?>> gameOrder = Arrays.asList(
            WhoKnowsKnows.class,
            MatchingGameActivity.class,
            AsocijacijeGameActivity.class,
            SkockoGameActivity.class,
            StepByStepActivity.class,
            NumbersGameActivity.class
    );

    private int currentGameIndex = 0;
    private int currentPlayer = MatchConstants.PLAYER_ONE;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private String playerOneName = MatchConstants.DEFAULT_PLAYER_ONE_NAME;
    private String playerTwoName = MatchConstants.DEFAULT_PLAYER_TWO_NAME;

    private ActivityResultLauncher<Intent> gameLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_match_play);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        if (intent != null) {
            String name1 = intent.getStringExtra(MatchConstants.EXTRA_PLAYER_ONE_NAME);
            String name2 = intent.getStringExtra(MatchConstants.EXTRA_PLAYER_TWO_NAME);
            if (name1 != null) {
                playerOneName = name1;
            }
            if (name2 != null) {
                playerTwoName = name2;
            }
        }

        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    int score = 0;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        score = result.getData().getIntExtra(MatchConstants.EXTRA_GAME_SCORE, 0);
                    }
                    Class<?> currentGame = gameOrder.get(currentGameIndex);
                    if (currentGame == WhoKnowsKnows.class) {
                        playerOneScore += score;
                        currentPlayer = MatchConstants.PLAYER_ONE;
                        currentGameIndex++;
                    } else if (currentGame == AsocijacijeGameActivity.class) {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            int playerOneGameScore = result.getData()
                                    .getIntExtra(MatchConstants.EXTRA_GAME_SCORE_PLAYER_ONE, 0);
                            int playerTwoGameScore = result.getData()
                                    .getIntExtra(MatchConstants.EXTRA_GAME_SCORE_PLAYER_TWO, 0);
                            playerOneScore += playerOneGameScore;
                            playerTwoScore += playerTwoGameScore;
                        }
                        currentPlayer = MatchConstants.PLAYER_ONE;
                        currentGameIndex++;
                    } else if (currentPlayer == MatchConstants.PLAYER_ONE) {
                        playerOneScore += score;
                        currentPlayer = MatchConstants.PLAYER_TWO;
                    } else {
                        playerTwoScore += score;
                        currentPlayer = MatchConstants.PLAYER_ONE;
                        currentGameIndex++;
                    }
                    startNextGame();
                }
        );

        startNextGame();
    }

    private void startNextGame() {
        if (currentGameIndex >= gameOrder.size()) {
            showSummary();
            return;
        }

        Intent intent = new Intent(this, gameOrder.get(currentGameIndex));
        intent.putExtra(MatchConstants.EXTRA_PLAYER_ONE_NAME, playerOneName);
        intent.putExtra(MatchConstants.EXTRA_PLAYER_TWO_NAME, playerTwoName);
        intent.putExtra(MatchConstants.EXTRA_PLAYER_ONE_SCORE, playerOneScore);
        intent.putExtra(MatchConstants.EXTRA_PLAYER_TWO_SCORE, playerTwoScore);
        intent.putExtra(MatchConstants.EXTRA_ACTIVE_PLAYER, currentPlayer);
        gameLauncher.launch(intent);
    }

    private void showSummary() {
        Intent intent = new Intent(this, MatchSummaryActivity.class);
        intent.putExtra(MatchConstants.EXTRA_PLAYER_ONE_NAME, playerOneName);
        intent.putExtra(MatchConstants.EXTRA_PLAYER_TWO_NAME, playerTwoName);
        intent.putExtra(MatchConstants.EXTRA_PLAYER_ONE_SCORE, playerOneScore);
        intent.putExtra(MatchConstants.EXTRA_PLAYER_TWO_SCORE, playerTwoScore);
        startActivity(intent);
        finish();
    }
}
