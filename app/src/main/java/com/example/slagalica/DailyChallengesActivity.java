package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.data.DailyMissionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DailyChallengesActivity extends AppCompatActivity {

    private DailyMissionManager missionManager;
    private TextView missionProgressText, missionDateText, bonusInfoText;
    private TextView[] missionStatusTexts = new TextView[4];
    private View[] missionRows = new View[4];
    private ProgressBar loadingSpinner;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_daily_challenges);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            androidx.core.graphics.Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        missionManager = new DailyMissionManager();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        missionProgressText = findViewById(R.id.missionProgressText);
        missionDateText = findViewById(R.id.missionDateText);
        bonusInfoText = findViewById(R.id.bonusInfoText);
        loadingSpinner = findViewById(R.id.loadingSpinner);

        missionStatusTexts[0] = findViewById(R.id.missionWinGameStatus);
        missionStatusTexts[1] = findViewById(R.id.missionSendChatStatus);
        missionStatusTexts[2] = findViewById(R.id.missionPlayFriendStatus);
        missionStatusTexts[3] = findViewById(R.id.missionWinTournamentStatus);

        missionRows[0] = findViewById(R.id.missionWinGame);
        missionRows[1] = findViewById(R.id.missionSendChat);
        missionRows[2] = findViewById(R.id.missionPlayFriend);
        missionRows[3] = findViewById(R.id.missionWinTournament);

        missionDateText.setText("Danas: " + DailyMissionManager.getTodayDate());

        // Click on chat mission to manually mark it
        missionRows[1].setOnClickListener(v -> markMission(DailyMissionManager.Mission.SEND_CHAT));

        if (currentUser != null) {
            loadMissions();
        }
    }

    private void loadMissions() {
        loadingSpinner.setVisibility(View.VISIBLE);
        missionManager.loadMissions(currentUser.getUid())
                .addOnSuccessListener(data -> {
                    loadingSpinner.setVisibility(View.GONE);
                    if (data != null) {
                        updateUI(data);
                    } else {
                        // No missions yet today - show all as incomplete
                        updateUI(null);
                    }
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(this, "Greška pri učitavanju misija", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUI(DailyMissionManager.DailyMissionsData data) {
        boolean[] done = new boolean[4];
        boolean allBonusClaimed = false;
        int count = 0;

        if (data != null) {
            done[0] = data.isWinGameDone();
            done[1] = data.isSendChatDone();
            done[2] = data.isPlayFriendDone();
            done[3] = data.isWinTournamentDone();
            allBonusClaimed = data.isAllCompletedBonusClaimed();
            for (int i = 0; i < 4; i++) {
                if (done[i]) count++;
            }
        }

        for (int i = 0; i < 4; i++) {
            missionStatusTexts[i].setText(done[i] ? "✓" : "✗");
            missionStatusTexts[i].setTextColor(done[i] ? 0xFF4CAF50 : 0xFFFF5252);
        }

        missionProgressText.setText(count + " / 4 misije rešene");

        boolean allFour = count >= 4;
        bonusInfoText.setText(allFour
                ? (allBonusClaimed ? "Bonus osvojen! (+2 tokena + 3 zvezde)" : "Bonus spreman! Osvoji +2 tokena + 3 zvezde!")
                : "Bonus za sve 4 misije: 2 tokena + 3 zvezde");
        bonusInfoText.setTextColor(allFour ? 0xFF4CAF50 : 0xFFFFD700);
    }

    private void markMission(DailyMissionManager.Mission mission) {
        if (currentUser == null) return;
        loadingSpinner.setVisibility(View.VISIBLE);
        missionManager.markMissionDone(currentUser.getUid(), mission)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Misija završena! +3 zvezde", Toast.LENGTH_SHORT).show();
                    loadMissions();
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
