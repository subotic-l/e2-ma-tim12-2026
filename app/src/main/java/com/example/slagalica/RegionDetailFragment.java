package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalica.data.RegionRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class RegionDetailFragment extends Fragment {

    private FirebaseFirestore db;

    private View regionIcon;
    private TextView textRegionName;
    private TextView textActivePlayers;
    private TextView textTotalPlayers;
    private TextView textMonthlyStars;
    private TextView textFirstPlaces;
    private TextView textSecondPlaces;
    private TextView textThirdPlaces;

    private final Map<String, Integer> regionIcons = new HashMap<>();
    private String regionCode;
    private String regionName;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_region_detail, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        Bundle args = getArguments();
        if (args != null) {
            regionCode = args.getString("region_code");
            regionName = args.getString("region_name");
        }

        if (regionCode == null) return;

        Region region = RegionRepository.get(regionCode);
        if (region == null) return;

        initRegionIcons();

        regionIcon = view.findViewById(R.id.regionDetailIcon);
        textRegionName = view.findViewById(R.id.textRegionName);
        textActivePlayers = view.findViewById(R.id.textActivePlayers);
        textTotalPlayers = view.findViewById(R.id.textTotalPlayers);
        textMonthlyStars = view.findViewById(R.id.textMonthlyStars);
        textFirstPlaces = view.findViewById(R.id.textFirstPlaces);
        textSecondPlaces = view.findViewById(R.id.textSecondPlaces);
        textThirdPlaces = view.findViewById(R.id.textThirdPlaces);

        Integer iconRes = regionIcons.get(regionCode);
        if (iconRes != null) {
            regionIcon.setBackgroundResource(iconRes);
        }

        textRegionName.setText(region.getName());

        String queryRegionName = regionName != null ? regionName : region.getName();
        loadRegionStats(queryRegionName);
        loadRegionHistory(regionCode);
    }

    private void initRegionIcons() {
        regionIcons.put(RegionRepository.VOJVODINA, R.drawable.ic_region_vojvodina);
        regionIcons.put(RegionRepository.BEOGRAD, R.drawable.ic_region_beograd);
        regionIcons.put(RegionRepository.SUMADIJA_ZAPAD, R.drawable.ic_region_sumadija);
        regionIcons.put(RegionRepository.JUZNA_ISTOCNA, R.drawable.ic_region_juzna);
        regionIcons.put(RegionRepository.KOSOVO, R.drawable.ic_region_kosovo);
    }

    private void loadRegionStats(String queryRegionName) {
        if (queryRegionName == null) return;
        db.collection("users")
                .whereEqualTo("region", queryRegionName)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long total = 0;
                    long active = 0;
                    long monthlyStars = 0;

                    long fiveMinutesMs = 5 * 60 * 1000L;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        total++;
                        Long stars = doc.getLong("monthlyStars");
                        if (stars != null) {
                            monthlyStars += stars;
                        }
                        Timestamp lastSeen = doc.getTimestamp("lastSeen");
                        if (lastSeen != null) {
                            long diff = System.currentTimeMillis() - lastSeen.toDate().getTime();
                            if (diff < fiveMinutesMs) {
                                active++;
                            }
                        }
                    }

                    textTotalPlayers.setText(String.valueOf(total));
                    textActivePlayers.setText(String.valueOf(active));
                    textMonthlyStars.setText(String.valueOf(monthlyStars));
                })
                .addOnFailureListener(e -> {
                    textTotalPlayers.setText("0");
                    textActivePlayers.setText("0");
                    textMonthlyStars.setText("0");
                });
    }

    private void loadRegionHistory(String regionCode) {
        if (regionCode == null) return;
        db.collection("region_rankings")
                .whereEqualTo("regionCode", regionCode)
                .orderBy("month", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(12)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long firsts = 0, seconds = 0, thirds = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Long rank = doc.getLong("rank");
                        if (rank != null) {
                            if (rank == 1) firsts++;
                            else if (rank == 2) seconds++;
                            else if (rank == 3) thirds++;
                        }
                    }
                    textFirstPlaces.setText(String.valueOf(firsts));
                    textSecondPlaces.setText(String.valueOf(seconds));
                    textThirdPlaces.setText(String.valueOf(thirds));
                })
                .addOnFailureListener(e -> {
                    textFirstPlaces.setText("0");
                    textSecondPlaces.setText("0");
                    textThirdPlaces.setText("0");
                });
    }
}
