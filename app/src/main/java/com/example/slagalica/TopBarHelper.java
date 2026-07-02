package com.example.slagalica;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public final class TopBarHelper {

    private static final String PREFS_NAME = "topbar_cache";
    private static final String KEY_TOKENS = "tokens";
    private static final String KEY_STARS = "stars";
    private static final String KEY_LEAGUE = "league";

    private static long cachedTokens;
    private static long cachedStars;
    private static long cachedLeague;

    public static ListenerRegistration loadAndUpdateTopBar(Activity activity) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;

        return FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;
                    long tokens = doc.getLong("tokens") != null ? doc.getLong("tokens") : 0;
                    long stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    long league = doc.getLong("league") != null ? doc.getLong("league") : 0;
                    saveToCache(activity, tokens, stars, league);
                    applyToActivity(activity, tokens, stars, league);
                });
    }

    public static void updateTopBarFromCache(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long tokens = prefs.getLong(KEY_TOKENS, 0);
        long stars = prefs.getLong(KEY_STARS, 0);
        long league = prefs.getLong(KEY_LEAGUE, 0);
        applyToActivity(activity, tokens, stars, league);
    }

    public static void saveToCache(Context context, long tokens, long stars, long league) {
        cachedTokens = tokens;
        cachedStars = stars;
        cachedLeague = league;
        persistToPrefs(context, tokens, stars, league);
    }

    public static void decrementTokenCache(Context context) {
        cachedTokens = Math.max(0, cachedTokens - 1);
        persistToPrefs(context, cachedTokens, cachedStars, cachedLeague);
    }

    private static void persistToPrefs(Context context, long tokens, long stars, long league) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_TOKENS, tokens)
                .putLong(KEY_STARS, stars)
                .putLong(KEY_LEAGUE, league)
                .apply();
    }

    private static void applyToActivity(Activity activity, long tokens, long stars, long league) {
        TextView tokenView = activity.findViewById(R.id.topBarTokenCount);
        TextView starView = activity.findViewById(R.id.topBarStarCount);
        TextView leagueView = activity.findViewById(R.id.topBarLeagueText);
        ImageView leagueIconView = activity.findViewById(R.id.topBarLeagueIcon);

        if (tokenView != null) tokenView.setText(String.valueOf(tokens));
        if (starView != null) starView.setText(String.valueOf(stars));
        int leagueIndex = (int) league;
        if (leagueView != null) {
            leagueView.setText(LeagueHelper.getLeagueNameByIndex(leagueIndex));
        }
        if (leagueIconView != null) {
            leagueIconView.setImageResource(LeagueHelper.getLeagueIconByIndex(leagueIndex));
        }
    }
}
