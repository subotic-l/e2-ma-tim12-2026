package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class NetworkMatchSummaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_network_match_summary);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        Intent intent = getIntent();
        String player1Name = intent.getStringExtra("player1Name");
        String player2Name = intent.getStringExtra("player2Name");
        int player1Score = intent.getIntExtra("player1Score", 0);
        int player2Score = intent.getIntExtra("player2Score", 0);
        String winner = intent.getStringExtra("winner");

        if (player1Name == null) player1Name = "Igrač 1";
        if (player2Name == null) player2Name = "Igrač 2";

        TextView summaryTitleText = findViewById(R.id.summaryTitleText);
        TextView winnerText = findViewById(R.id.winnerText);
        TextView player1SummaryText = findViewById(R.id.player1SummaryText);
        TextView player2SummaryText = findViewById(R.id.player2SummaryText);
        MaterialButton backButton = findViewById(R.id.buttonBackToHome);

        summaryTitleText.setText("Konačni rezultat");
        winnerText.setText("Pobednik: " + winner);
        player1SummaryText.setText(player1Name + ": " + player1Score + " poena");
        player2SummaryText.setText(player2Name + ": " + player2Score + " poena");

        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(this, MainActivity.class);
            backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(backIntent);
            finish();
        });
    }
}
