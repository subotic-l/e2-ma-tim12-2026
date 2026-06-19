package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.service.UserService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class NetworkMatchSummaryActivity extends AppCompatActivity {

    private UserService userService;

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

        userService = new UserService();

        Intent intent = getIntent();
        String player1Name = intent.getStringExtra("player1Name");
        String player2Name = intent.getStringExtra("player2Name");
        int player1Score = intent.getIntExtra("player1Score", 0);
        int player2Score = intent.getIntExtra("player2Score", 0);
        String winner = intent.getStringExtra("winner");
        int myPlayerNumber = intent.getIntExtra("myPlayerNumber", 1);

        if (player1Name == null) player1Name = "Igrač 1";
        if (player2Name == null) player2Name = "Igrač 2";

        TextView summaryTitleText = findViewById(R.id.summaryTitleText);
        TextView winnerText = findViewById(R.id.winnerText);
        TextView player1SummaryText = findViewById(R.id.player1SummaryText);
        TextView player2SummaryText = findViewById(R.id.player2SummaryText);
        MaterialButton backButton = findViewById(R.id.buttonBackToHome);
        MaterialCardView rewardsCard = findViewById(R.id.rewardsCard);
        TextView starsChangeText = findViewById(R.id.starsChangeText);
        TextView tokenBonusText = findViewById(R.id.tokenBonusText);

        summaryTitleText.setText("Kona\u010Dni rezultat");
        winnerText.setText("Pobednik: " + winner);
        player1SummaryText.setText(player1Name + ": " + player1Score + " poena");
        player2SummaryText.setText(player2Name + ": " + player2Score + " poena");

        // Process rewards for authenticated user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            int myScore = myPlayerNumber == 1 ? player1Score : player2Score;
            int opponentScore = myPlayerNumber == 1 ? player2Score : player1Score;
            boolean iWon = myScore > opponentScore;

            int starsDelta = UserService.calculateStarsDelta(myScore, iWon);

            userService.updateLastSeen();
            userService.processMatchRewards(myScore, iWon)
                    .addOnSuccessListener(aVoid -> {
                        // Read updated profile to show current balance
                        userService.loadProfile().addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                Long stars = doc.getLong("stars");
                                Long tokens = doc.getLong("tokens");
                                rewardsCard.setVisibility(View.VISIBLE);

                                String starPrefix = starsDelta >= 0 ? "+" : "";
                                starsChangeText.setText(
                                        "Zvezde: " + starPrefix + starsDelta +
                                        " (ukupno: " + (stars != null ? stars : 0) + ")");

                                tokenBonusText.setText(
                                        "Tokeni: " + (tokens != null ? tokens : 0));
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        rewardsCard.setVisibility(View.VISIBLE);
                        starsChangeText.setText("Greška pri obradi nagrada");
                        tokenBonusText.setText("");
                    });
        }

        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(this, MainActivity.class);
            backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(backIntent);
            finish();
        });
    }
}
