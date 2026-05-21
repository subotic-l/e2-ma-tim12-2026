package com.example.slagalica;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

public final class MatchUiHelper {

    private MatchUiHelper() {
    }

    public static void bindPlayerHeader(Activity activity, Intent intent) {
        if (activity == null) {
            return;
        }

        TextView playerOneName = activity.findViewById(R.id.playerOneName);
        TextView playerOneScore = activity.findViewById(R.id.playerOneScore);
        TextView playerTwoName = activity.findViewById(R.id.playerTwoName);
        TextView playerTwoScore = activity.findViewById(R.id.playerTwoScore);

        if (playerOneName == null || playerOneScore == null || playerTwoName == null || playerTwoScore == null) {
            return;
        }

        String playerOneText = MatchConstants.DEFAULT_PLAYER_ONE_NAME;
        String playerTwoText = MatchConstants.DEFAULT_PLAYER_TWO_NAME;
        int playerOnePoints = 0;
        int playerTwoPoints = 0;
        int activePlayer = MatchConstants.PLAYER_ONE;

        if (intent != null) {
            String name1 = intent.getStringExtra(MatchConstants.EXTRA_PLAYER_ONE_NAME);
            String name2 = intent.getStringExtra(MatchConstants.EXTRA_PLAYER_TWO_NAME);
            if (name1 != null) {
                playerOneText = name1;
            }
            if (name2 != null) {
                playerTwoText = name2;
            }
            playerOnePoints = intent.getIntExtra(MatchConstants.EXTRA_PLAYER_ONE_SCORE, 0);
            playerTwoPoints = intent.getIntExtra(MatchConstants.EXTRA_PLAYER_TWO_SCORE, 0);
            activePlayer = intent.getIntExtra(MatchConstants.EXTRA_ACTIVE_PLAYER, MatchConstants.PLAYER_ONE);
        }

        playerOneName.setText(playerOneText);
        playerTwoName.setText(playerTwoText);
        playerOneScore.setText(String.valueOf(playerOnePoints));
        playerTwoScore.setText(String.valueOf(playerTwoPoints));

        View playerOneContainer = activity.findViewById(R.id.playerOneContainer);
        View playerTwoContainer = activity.findViewById(R.id.playerTwoContainer);
        if (playerOneContainer != null && playerTwoContainer != null) {
            playerOneContainer.setAlpha(activePlayer == MatchConstants.PLAYER_ONE ? 1f : 0.6f);
            playerTwoContainer.setAlpha(activePlayer == MatchConstants.PLAYER_TWO ? 1f : 0.6f);
        }
    }
}
