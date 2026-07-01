package com.example.slagalica;

public final class LeagueHelper {

    private static final String[] LEAGUE_NAMES = {
            "Mrav", "Je\u017E", "Lisica", "Vuk", "Medvjed", "Orao"
    };

    private static final int[] LEAGUE_ICONS = {
            R.drawable.ant,
            R.drawable.hedgehog,
            R.drawable.fox,
            R.drawable.wolf,
            R.drawable.bear,
            R.drawable.eagle
    };

    private static final long[] THRESHOLDS = {
            0, 100, 200, 400, 800, 1600
    };

    public static int getLeagueIndex(long stars) {
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (stars >= THRESHOLDS[i]) {
                return i;
            }
        }
        return 0;
    }

    public static String getLeagueName(long stars) {
        return LEAGUE_NAMES[getLeagueIndex(stars)];
    }

    public static String getLeagueNameByIndex(int index) {
        if (index < 0 || index >= LEAGUE_NAMES.length) return "Nepoznata";
        return LEAGUE_NAMES[index];
    }

    public static int getLeagueIcon(long stars) {
        return LEAGUE_ICONS[getLeagueIndex(stars)];
    }

    public static int getLeagueIconByIndex(int index) {
        if (index < 0 || index >= LEAGUE_ICONS.length) return R.drawable.ant;
        return LEAGUE_ICONS[index];
    }

    public static long getThresholdForLeague(int leagueIndex) {
        if (leagueIndex < 0 || leagueIndex >= THRESHOLDS.length) return THRESHOLDS[0];
        return THRESHOLDS[leagueIndex];
    }

    public static long getStarsForNextLeague(long stars) {
        int current = getLeagueIndex(stars);
        if (current >= LEAGUE_NAMES.length - 1) return -1;
        return THRESHOLDS[current + 1];
    }

    public static int getLeaguesCount() {
        return LEAGUE_NAMES.length;
    }
}
