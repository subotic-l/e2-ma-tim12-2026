package com.example.slagalica;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private NavigationHelper navHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        navHelper = new NavigationHelper(this, R.id.navigation_home)
                .addFragmentTab(R.id.navigation_home, HomeFragment.class)
                .addFragmentTab(R.id.navigation_profile, ProfileFragment.class)
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
