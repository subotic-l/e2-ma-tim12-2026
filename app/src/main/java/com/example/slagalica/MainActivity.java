package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Apply top/side insets to root; bottom inset is handled by BottomNavigationView
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        Button startWhoKnowsButton = findViewById(R.id.buttonStartWhoKnows);
        startWhoKnowsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WhoKnowsKnows.class);
            startActivity(intent);
        });

        Button startMatchingGameButton = findViewById(R.id.buttonStartMatchingGame);
        startMatchingGameButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MatchingGameActivity.class);
            startActivity(intent);
        });

        Button startStepByStepButton = findViewById(R.id.buttonStartStepByStep);
        startStepByStepButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StepByStepActivity.class);
            startActivity(intent);
        });

        Button startNumbersGameButton = findViewById(R.id.buttonStartNumbersGame);
        startNumbersGameButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NumbersGameActivity.class);
            startActivity(intent);
        });

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);

        // Apply bottom system bar inset specifically to the nav view so it sits flush at bottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_profile) {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                startActivity(intent);
                return true;
            }
            if (item.getItemId() == R.id.navigation_notifications) {
                Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });

        Button startSkockoButton = findViewById(R.id.buttonStartSkocko);
        startSkockoButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SkockoGameActivity.class);
            startActivity(intent);
        });

        Button startAsocijacijeButton = findViewById(R.id.buttonStartAsocijacije);
        startAsocijacijeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AsocijacijeGameActivity.class);
            startActivity(intent);
        });
    }
}