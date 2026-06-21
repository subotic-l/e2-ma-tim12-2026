package com.example.slagalica;

import android.app.Activity;
import android.widget.TextView;

public final class MatchUiHelper {

    private MatchUiHelper() {
    }

    public static void bindHeader(Activity activity, String gameName, int score) {
        if (activity == null) return;

        TextView gameNameText = activity.findViewById(R.id.gameNameText);
        TextView scoreText = activity.findViewById(R.id.scoreText);

        if (gameNameText != null) {
            gameNameText.setText(gameName);
        }
        if (scoreText != null) {
            scoreText.setText(String.valueOf(score));
        }
    }

    public static void updateScore(Activity activity, int score) {
        TextView scoreText = activity.findViewById(R.id.scoreText);
        if (scoreText != null) {
            scoreText.setText(String.valueOf(score));
        }
    }
}
