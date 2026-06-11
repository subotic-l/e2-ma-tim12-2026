package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private NavigationHelper navHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new ProfileFragment())
                    .commit();
        }

        navHelper = new NavigationHelper(this, R.id.navigation_profile)
                .addTab(R.id.navigation_home, (fromId, toId) -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                })
                .addSoonTab(R.id.navigation_stats)
                .addSoonTab(R.id.navigation_friends)
                .addSoonTab(R.id.navigation_rankings);

        navHelper.setup(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (navHelper != null) {
            navHelper.onResume();
        }
    }
}
