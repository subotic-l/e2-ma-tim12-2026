package com.example.slagalica;

import android.app.Activity;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class BottomNavigationHelper {

    public interface NavigationCallback {
        void onNavigate(int fromItemId, int toItemId);
    }

    private static BottomNavigationView navView;
    private static int currentItemId;

    public static void setup(Activity activity, int itemId, NavigationCallback callback) {
        currentItemId = itemId;
        navView = activity.findViewById(R.id.bottomNavigation);
        if (navView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        navView.setSelectedItemId(itemId);

        navView.setOnItemSelectedListener(item -> {
            int previousId = currentItemId;
            int targetId = item.getItemId();
            if (targetId == currentItemId) return true;

            currentItemId = targetId;

            if (targetId == R.id.navigation_home || targetId == R.id.navigation_profile) {
                callback.onNavigate(previousId, targetId);
                return true;
            }

            Toast.makeText(activity, "Uskoro", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    public static void onResume(Activity activity) {
        navView = activity.findViewById(R.id.bottomNavigation);
        if (navView != null) {
            navView.setSelectedItemId(currentItemId);
        }
    }

//    public static int currentTabIndex() {
//        return tabIndex(currentItemId);
//    }

    public static int tabIndex(int menuItemId) {
        if (menuItemId == R.id.navigation_profile) return 0;
        if (menuItemId == R.id.navigation_stats) return 1;
        if (menuItemId == R.id.navigation_home) return 2;
        if (menuItemId == R.id.navigation_friends) return 3;
        if (menuItemId == R.id.navigation_rankings) return 4;
        return -1;
    }
}
