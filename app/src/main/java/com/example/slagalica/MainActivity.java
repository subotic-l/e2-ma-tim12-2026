package com.example.slagalica;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

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

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
        }

        BottomNavigationHelper.setup(this, R.id.navigation_home, this::switchFragment);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationHelper.onResume(this);
    }

    private void switchFragment(int fromItemId, int toItemId) {
        Fragment fragment;
        if (toItemId == R.id.navigation_home) {
            fragment = new HomeFragment();
        } else if (toItemId == R.id.navigation_profile) {
            fragment = new ProfileFragment();
        } else {
            return;
        }
        navigateToFragment(fragment, fromItemId, toItemId);
    }

    private void navigateToFragment(Fragment fragment, int fromItemId, int toItemId) {
        int fromIndex = BottomNavigationHelper.tabIndex(fromItemId);
        int toIndex = BottomNavigationHelper.tabIndex(toItemId);

        if (toIndex > fromIndex) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
        } else {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
        }
    }
}
