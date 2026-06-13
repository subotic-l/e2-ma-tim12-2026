package com.example.slagalica;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.LinkedHashMap;
import java.util.Map;

public class NavigationHelper {

    private final AppCompatActivity activity;
    private final int defaultItemId;
    private final Map<Integer, TabEntry> tabs = new LinkedHashMap<>();
    private int currentItemId;
    private BottomNavigationView navView;

    public interface TabHandler {
        void onSelected(int fromItemId, int toItemId);
    }

    private static class TabEntry {
        final Class<? extends Fragment> fragmentClass;
        final TabHandler handler;
        final boolean isSoon;

        TabEntry(Class<? extends Fragment> fragmentClass) {
            this.fragmentClass = fragmentClass;
            this.handler = null;
            this.isSoon = false;
        }

        TabEntry(TabHandler handler) {
            this.fragmentClass = null;
            this.handler = handler;
            this.isSoon = false;
        }

        TabEntry(boolean isSoon) {
            this.fragmentClass = null;
            this.handler = null;
            this.isSoon = true;
        }
    }

    public NavigationHelper(AppCompatActivity activity, int defaultItemId) {
        this.activity = activity;
        this.defaultItemId = defaultItemId;
        this.currentItemId = defaultItemId;
    }

    public NavigationHelper addFragmentTab(int menuItemId, Class<? extends Fragment> fragmentClass) {
        tabs.put(menuItemId, new TabEntry(fragmentClass));
        return this;
    }

    public NavigationHelper addTab(int menuItemId, TabHandler handler) {
        tabs.put(menuItemId, new TabEntry(handler));
        return this;
    }

    public NavigationHelper addSoonTab(int menuItemId) {
        tabs.put(menuItemId, new TabEntry(true));
        return this;
    }

    public void setup(Bundle savedInstanceState) {
        navView = activity.findViewById(R.id.bottomNavigation);
        if (navView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        navView.setSelectedItemId(currentItemId);

        if (savedInstanceState == null) {
            TabEntry defaultEntry = tabs.get(defaultItemId);
            if (defaultEntry != null && defaultEntry.fragmentClass != null) {
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, Fragment.instantiate(
                                activity, defaultEntry.fragmentClass.getName()))
                        .commit();
            }
        }

        navView.setOnItemSelectedListener(item -> {
            int targetId = item.getItemId();
            if (targetId == currentItemId) return true;

            TabEntry entry = tabs.get(targetId);
            if (entry == null || entry.isSoon) {
                navView.setSelectedItemId(currentItemId);
                Toast.makeText(activity, "Uskoro", Toast.LENGTH_SHORT).show();
                return true;
            }

            int previousId = currentItemId;
            currentItemId = targetId;

            if (entry.handler != null) {
                entry.handler.onSelected(previousId, targetId);
            } else if (entry.fragmentClass != null) {
                navigateToFragment(entry.fragmentClass, previousId, targetId);
            }

            return true;
        });
    }

    public void onResume() {
        navView = activity.findViewById(R.id.bottomNavigation);
        if (navView != null) {
            navView.setSelectedItemId(currentItemId);
        }
    }

    private void navigateToFragment(Class<? extends Fragment> fragmentClass, int fromItemId, int toItemId) {
        int fromIndex = tabIndex(fromItemId);
        int toIndex = tabIndex(toItemId);

        Fragment fragment = Fragment.instantiate(activity, fragmentClass.getName());

        if (toIndex > fromIndex) {
            activity.getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
        } else {
            activity.getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
        }
    }

    private int tabIndex(int menuItemId) {
        if (menuItemId == R.id.navigation_profile) return 0;
        if (menuItemId == R.id.navigation_stats) return 1;
        if (menuItemId == R.id.navigation_home) return 2;
        if (menuItemId == R.id.navigation_friends) return 3;
        if (menuItemId == R.id.navigation_rankings) return 4;
        return -1;
    }
}
