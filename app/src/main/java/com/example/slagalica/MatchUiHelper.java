package com.example.slagalica;

import android.app.Activity;
import android.widget.TextView;

public final class MatchUiHelper {

    private MatchUiHelper() {
    }

    public static void bindHeader(Activity activity, String playerName, int score) {
        if (activity == null) return;

        TextView gameNameText = activity.findViewById(R.id.gameNameText);
        if (gameNameText == null) {
            gameNameText = activity.findViewById(R.id.playerOneName);
        }

        TextView scoreText = activity.findViewById(R.id.scoreText);
        if (scoreText == null) {
            scoreText = activity.findViewById(R.id.playerOneScore);
        }

        if (gameNameText != null) {
            gameNameText.setText(playerName);
        }
        if (scoreText != null) {
            scoreText.setText(String.valueOf(score));
        }
    }

    public static void updateScore(Activity activity, int score) {
        TextView scoreText = activity.findViewById(R.id.scoreText);
        if (scoreText == null) {
            scoreText = activity.findViewById(R.id.playerOneScore);
        }
        if (scoreText != null) {
            scoreText.setText(String.valueOf(score));
        }
    }
}
