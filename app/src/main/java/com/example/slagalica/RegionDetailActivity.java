package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.RegionRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegionDetailActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_region_detail);

        db = FirebaseFirestore.getInstance();

        String regionCode = getIntent().getStringExtra("region_code");
        String regionName = getIntent().getStringExtra("region_name");
        if (regionCode == null) {
            finish();
            return;
        }

        Region region = RegionRepository.get(regionCode);
        if (region == null) {
            finish();
            return;
        }

        initRegionIcons();

        regionIcon = findViewById(R.id.regionDetailIcon);
        textRegionName = findViewById(R.id.textRegionName);
        textActivePlayers = findViewById(R.id.textActivePlayers);
        textTotalPlayers = findViewById(R.id.textTotalPlayers);
        textMonthlyStars = findViewById(R.id.textMonthlyStars);
        textFirstPlaces = findViewById(R.id.textFirstPlaces);
        textSecondPlaces = findViewById(R.id.textSecondPlaces);
        textThirdPlaces = findViewById(R.id.textThirdPlaces);

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

    private void loadRegionStats(String regionName) {
        if (regionName == null) return;
        db.collection("users")
                .whereEqualTo("region", regionName)
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
        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        db.collection("region_rankings")
                .whereEqualTo("regionCode", regionCode)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DocumentSnapshot> docs = new ArrayList<DocumentSnapshot>(queryDocumentSnapshots.getDocuments());
                    Collections.sort(docs, (a, b) -> {
                        String ma = a.getString("month");
                        String mb = b.getString("month");
                        if (ma == null && mb == null) return 0;
                        if (ma == null) return 1;
                        if (mb == null) return -1;
                        return mb.compareTo(ma);
                    });
                    long firsts = 0, seconds = 0, thirds = 0;
                    int count = 0;
                    for (DocumentSnapshot doc : docs) {
                        if (count >= 12) break;
                        String month = doc.getString("month");
                        if (currentMonth.equals(month)) continue;
                        Long rank = doc.getLong("rank");
                        if (rank != null) {
                            if (rank == 1) firsts++;
                            else if (rank == 2) seconds++;
                            else if (rank == 3) thirds++;
                        }
                        count++;
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