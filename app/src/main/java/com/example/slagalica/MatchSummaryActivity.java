package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class MatchSummaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_match_summary);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        MatchUiHelper.bindPlayerHeader(this, intent);

        int playerOneScore = intent != null
                ? intent.getIntExtra(MatchConstants.EXTRA_PLAYER_ONE_SCORE, 0)
                : 0;
        int playerTwoScore = intent != null
                ? intent.getIntExtra(MatchConstants.EXTRA_PLAYER_TWO_SCORE, 0)
                : 0;

        TextView summaryText = findViewById(R.id.matchSummaryText);
        summaryText.setText("Igrač 1: " + playerOneScore + "\nIgrač 2: " + playerTwoScore);

        MaterialButton backButton = findViewById(R.id.buttonBackToMain);
        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(this, MainActivity.class);
            backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(backIntent);
            finish();
        });
    }
}
