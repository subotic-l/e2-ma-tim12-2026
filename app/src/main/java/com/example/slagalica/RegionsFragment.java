package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.slagalica.data.RegionRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class RegionsFragment extends Fragment {

    private static final double SERBIA_CENTER_LAT = 44.0;
    private static final double SERBIA_CENTER_LON = 20.8;

    private MapView mapView;
    private LinearLayout leaderboardList;
    private View mapContainer;
    private View listContainer;
    private TextView mapTab;
    private TextView listTab;
    private TextView textDateRange;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentUserRegionName;

    private final Map<String, Integer> regionColors = new HashMap<>();
    private final Map<String, Integer> regionIcons = new HashMap<>();
    private final List<RegionEntry> regionEntries = new ArrayList<>();
    private boolean mapInitialized = false;

    private static final Map<String, Integer> REGION_BORDER_RESOURCES = new HashMap<>();

    static {
        REGION_BORDER_RESOURCES.put(RegionRepository.VOJVODINA, R.raw.vojvodina_border);
        REGION_BORDER_RESOURCES.put(RegionRepository.BEOGRAD, R.raw.beograd_border);
        REGION_BORDER_RESOURCES.put(RegionRepository.SUMADIJA_ZAPAD, R.raw.sumadija_i_zapad_border);
        REGION_BORDER_RESOURCES.put(RegionRepository.JUZNA_ISTOCNA, R.raw.juzna_istocna_border);
        REGION_BORDER_RESOURCES.put(RegionRepository.KOSOVO, R.raw.kosovo_border);
    }

    private static class RegionEntry {
        final String code;
        final String name;
        final long monthlyStars;
        final long totalPlayers;
        final int iconResId;

        RegionEntry(String code, String name, long monthlyStars, long totalPlayers, int iconResId) {
            this.code = code;
            this.name = name;
            this.monthlyStars = monthlyStars;
            this.totalPlayers = totalPlayers;
            this.iconResId = iconResId;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_regions, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        initRegionData();

        mapView = view.findViewById(R.id.mapView);
        mapContainer = view.findViewById(R.id.mapContainer);
        listContainer = view.findViewById(R.id.listContainer);
        leaderboardList = view.findViewById(R.id.leaderboardList);
        mapTab = view.findViewById(R.id.mapTab);
        listTab = view.findViewById(R.id.listTab);
        textDateRange = view.findViewById(R.id.textRegionDateRange);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date monthStart = cal.getTime();
        Calendar calEnd = Calendar.getInstance();
        calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        Date monthEnd = calEnd.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        textDateRange.setText("Ciklus: " + sdf.format(monthStart) + " - " + sdf.format(monthEnd));

        mapTab.setOnClickListener(v -> {
            mapContainer.setVisibility(View.VISIBLE);
            listContainer.setVisibility(View.GONE);
            mapTab.setBackgroundResource(R.drawable.rounded_white_small);
            mapTab.setTextColor(0xFF333333);
            listTab.setBackgroundResource(0);
            listTab.setTextColor(0xFFFFFFFF);
        });

        listTab.setOnClickListener(v -> {
            mapContainer.setVisibility(View.GONE);
            listContainer.setVisibility(View.VISIBLE);
            listTab.setBackgroundResource(R.drawable.rounded_white_small);
            listTab.setTextColor(0xFF333333);
            mapTab.setBackgroundResource(0);
            mapTab.setTextColor(0xFFFFFFFF);
        });

        loadRegionData();
    }

    private void initRegionData() {
        regionColors.put(RegionRepository.VOJVODINA, 0xFF4CAF50);
        regionColors.put(RegionRepository.BEOGRAD, 0xFFF44336);
        regionColors.put(RegionRepository.SUMADIJA_ZAPAD, 0xFFFF9800);
        regionColors.put(RegionRepository.JUZNA_ISTOCNA, 0xFF9C27B0);
        regionColors.put(RegionRepository.KOSOVO, 0xFF607D8B);

        regionIcons.put(RegionRepository.VOJVODINA, R.drawable.ic_region_vojvodina);
        regionIcons.put(RegionRepository.BEOGRAD, R.drawable.ic_region_beograd);
        regionIcons.put(RegionRepository.SUMADIJA_ZAPAD, R.drawable.ic_region_sumadija);
        regionIcons.put(RegionRepository.JUZNA_ISTOCNA, R.drawable.ic_region_juzna);
        regionIcons.put(RegionRepository.KOSOVO, R.drawable.ic_region_kosovo);
    }

    private List<List<GeoPoint>> loadRegionPolygons(String regionCode) {
        List<List<GeoPoint>> result = new ArrayList<>();
        Integer resId = REGION_BORDER_RESOURCES.get(regionCode);
        if (resId == null) return result;

        try {
            InputStream is = getResources().openRawResource(resId);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");

            JSONObject root = new JSONObject(json);
            extractPolygonsFromGeometry(root, result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private void extractPolygonsFromGeometry(JSONObject root, List<List<GeoPoint>> result) throws Exception {
        JSONObject geometry;
        if (root.has("features")) {
            JSONArray features = root.getJSONArray("features");
            for (int i = 0; i < features.length(); i++) {
                extractPolygonsFromGeometry(features.getJSONObject(i), result);
            }
            return;
        }
        if (root.has("geometry")) {
            geometry = root.getJSONObject("geometry");
        } else if (root.has("type") && ("Polygon".equals(root.getString("type"))
                || "MultiPolygon".equals(root.getString("type"))
                || "GeometryCollection".equals(root.getString("type")))) {
            geometry = root;
        } else {
            return;
        }
        String type = geometry.getString("type");

        if ("Polygon".equals(type)) {
            List<GeoPoint> polygon = parseCoordinates(geometry.getJSONArray("coordinates").getJSONArray(0));
            if (!polygon.isEmpty()) result.add(polygon);
        } else if ("MultiPolygon".equals(type)) {
            JSONArray polygons = geometry.getJSONArray("coordinates");
            for (int i = 0; i < polygons.length(); i++) {
                JSONArray ring = polygons.getJSONArray(i).getJSONArray(0);
                List<GeoPoint> polygon = parseCoordinates(ring);
                if (!polygon.isEmpty()) result.add(polygon);
            }
        } else if ("GeometryCollection".equals(type)) {
            JSONArray geometries = geometry.getJSONArray("geometries");
            for (int i = 0; i < geometries.length(); i++) {
                JSONObject sub = geometries.getJSONObject(i);
                String subType = sub.getString("type");
                if ("Polygon".equals(subType)) {
                    List<GeoPoint> polygon = parseCoordinates(sub.getJSONArray("coordinates").getJSONArray(0));
                    if (!polygon.isEmpty()) result.add(polygon);
                } else if ("MultiPolygon".equals(subType)) {
                    JSONArray polygons = sub.getJSONArray("coordinates");
                    for (int j = 0; j < polygons.length(); j++) {
                        JSONArray ring = polygons.getJSONArray(j).getJSONArray(0);
                        List<GeoPoint> polygon = parseCoordinates(ring);
                        if (!polygon.isEmpty()) result.add(polygon);
                    }
                }
            }
        }
    }

    private List<GeoPoint> parseCoordinates(JSONArray coords) throws Exception {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < coords.length(); i++) {
            JSONArray coord = coords.getJSONArray(i);
            double lon = coord.getDouble(0);
            double lat = coord.getDouble(1);
            points.add(new GeoPoint(lat, lon));
        }
        return points;
    }

    private void loadRegionData() {
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        String storedRegion = doc.getString("region");
                        if (storedRegion != null) {
                            currentUserRegionName = storedRegion;
                        }
                        loadAllUsers();
                    })
                    .addOnFailureListener(e -> loadAllUsers());
        } else {
            loadAllUsers();
        }
    }

    private void loadAllUsers() {
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, RegionAggregate> aggregates = new HashMap<>();
                    for (String code : RegionRepository.getMap().keySet()) {
                        aggregates.put(code, new RegionAggregate());
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String storedRegionName = doc.getString("region");
                        if (storedRegionName == null) continue;

                        String code = RegionRepository.getNameToCode(storedRegionName);
                        if (code != null && aggregates.containsKey(code)) {
                            RegionAggregate agg = aggregates.get(code);
                            agg.totalPlayers++;
                            Long monthlyStars = doc.getLong("monthlyStars");
                            if (monthlyStars != null) {
                                agg.monthlyStars += monthlyStars;
                            }
                        }
                    }

                    regionEntries.clear();
                    for (Map.Entry<String, RegionAggregate> entry : aggregates.entrySet()) {
                        String code = entry.getKey();
                        RegionAggregate agg = entry.getValue();
                        com.example.slagalica.Region region = RegionRepository.get(code);
                        if (region != null) {
                            Integer iconRes = regionIcons.get(code);
                            regionEntries.add(new RegionEntry(
                                    code,
                                    region.getName(),
                                    agg.monthlyStars,
                                    agg.totalPlayers,
                                    iconRes != null ? iconRes : R.drawable.ic_region_vojvodina
                            ));
                        }
                    }

                    Collections.sort(regionEntries, (a, b) -> Long.compare(b.monthlyStars, a.monthlyStars));

                    buildLeaderboard();
                    setupMap();
                    mapInitialized = true;

                    saveMonthlyRankingsIfNeeded(regionEntries);
                });
    }

    private static class RegionAggregate {
        long monthlyStars = 0;
        long totalPlayers = 0;
    }

    private void buildLeaderboard() {
        leaderboardList.removeAllViews();

        if (regionEntries.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("Nema podataka o regionima");
            empty.setTextColor(android.graphics.Color.WHITE);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 32, 0, 32);
            leaderboardList.addView(empty);
            return;
        }

        String userRegionName = getUserRegionName();
        int rank = 1;
        for (RegionEntry entry : regionEntries) {
            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_region_leaderboard, leaderboardList, false);

            TextView textRank = itemView.findViewById(R.id.textRank);
            TextView textRegionName = itemView.findViewById(R.id.textRegionName);
            TextView textStars = itemView.findViewById(R.id.textRegionStars);
            TextView textPlayers = itemView.findViewById(R.id.textRegionPlayers);
            View iconView = itemView.findViewById(R.id.regionIcon);

            iconView.setBackgroundResource(entry.iconResId);
            textRank.setText(String.valueOf(rank));
            textRegionName.setText(entry.name);
            textStars.setText(entry.monthlyStars + " zvezdi");
            textPlayers.setText(entry.totalPlayers + " igra\u010Da");

            if (userRegionName != null && userRegionName.equals(entry.name)) {
                itemView.setBackgroundColor(0x33FFFFFF);
            }

            final String regionCode = entry.code;
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireActivity(), RegionDetailActivity.class);
                intent.putExtra("region_code", regionCode);
                intent.putExtra("region_name", entry.name);
                startActivity(intent);
            });

            leaderboardList.addView(itemView);

            if (rank < regionEntries.size()) {
                View divider = new View(requireContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0x22FFFFFF);
                leaderboardList.addView(divider);
            }

            rank++;
        }
    }

    private String getUserRegionName() {
        return currentUserRegionName;
    }

    private void setupMap() {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);

        GeoPoint serbiaCenter = new GeoPoint(SERBIA_CENTER_LAT, SERBIA_CENTER_LON);
        mapView.getController().setZoom(7.3);
        mapView.getController().setCenter(serbiaCenter);

        for (RegionEntry entry : regionEntries) {
            Integer color = regionColors.get(entry.code);
            if (color == null) color = 0xFF888888;

            List<List<GeoPoint>> polygons = loadRegionPolygons(entry.code);
            if (polygons.isEmpty()) continue;

            String regionName = entry.name;
            for (List<GeoPoint> polygonPoints : polygons) {
                Polygon polygon = new Polygon();
                polygon.setPoints(polygonPoints);
                int fillColor = (color & 0x00FFFFFF) | 0x33000000;
                polygon.setFillColor(fillColor);
                polygon.setStrokeColor(color);
                polygon.setStrokeWidth(3);
                polygon.setTitle(entry.name);
                polygon.setOnClickListener((poly, mapView, eventPos) -> {
                    Intent intent = new Intent(requireActivity(), RegionDetailActivity.class);
                    intent.putExtra("region_code", entry.code);
                    intent.putExtra("region_name", regionName);
                    startActivity(intent);
                    return true;
                });
                mapView.getOverlays().add(polygon);
            }
        }

        db.collection("users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                String storedRegionName = doc.getString("region");
                String docId = doc.getId();
                if (storedRegionName == null) continue;
                String code = RegionRepository.getNameToCode(storedRegionName);
                if (code == null) continue;

                double[] coords = getPlayerMapCoords(docId, code);
                GeoPoint playerPoint = new GeoPoint(coords[0], coords[1]);

                Marker playerMarker = new Marker(mapView);
                playerMarker.setPosition(playerPoint);
                playerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

                Integer color = regionColors.get(code);
                if (color == null) color = 0xFF888888;
                int dotSize = dpToPx(14);
                android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
                dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dot.setBounds(0, 0, dotSize, dotSize);
                dot.setColor(color);
                dot.setStroke(dpToPx(2), android.graphics.Color.WHITE);
                playerMarker.setIcon(dot);
                playerMarker.setTitle(storedRegionName);
                mapView.getOverlays().add(playerMarker);
            }
            mapView.invalidate();
        });

        mapView.invalidate();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private double[] getPlayerMapCoords(String uid, String regionCode) {
        Random rng = new Random(uid.hashCode());
        com.example.slagalica.Region region = RegionRepository.get(regionCode);
        if (region == null) {
            return new double[]{44.0, 20.8};
        }

        double latRange, lonRange;
        switch (regionCode) {
            case RegionRepository.VOJVODINA:
                latRange = 1.2; lonRange = 2.3;
                break;
            case RegionRepository.BEOGRAD:
                latRange = 0.25; lonRange = 0.4;
                break;
            case RegionRepository.SUMADIJA_ZAPAD:
                latRange = 1.2; lonRange = 2.0;
                break;
            case RegionRepository.JUZNA_ISTOCNA:
                latRange = 1.8; lonRange = 1.5;
                break;
            case RegionRepository.KOSOVO:
                latRange = 0.6; lonRange = 1.0;
                break;
            default:
                latRange = 0.5; lonRange = 0.5;
        }

        double lat = region.getLat() + (rng.nextDouble() - 0.5) * latRange;
        double lon = region.getLon() + (rng.nextDouble() - 0.5) * lonRange;
        return new double[]{lat, lon};
    }

    private void saveMonthlyRankingsIfNeeded(List<RegionEntry> entries) {
        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());

        db.collection("region_rankings")
                .document(currentMonth)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) return;

                    WriteBatch batch = db.batch();

                    for (int i = 0; i < entries.size(); i++) {
                        RegionEntry entry = entries.get(i);
                        long rank = i + 1;
                        Map<String, Object> rankData = new HashMap<>();
                        rankData.put("regionCode", entry.code);
                        rankData.put("regionName", entry.name);
                        rankData.put("rank", rank);
                        rankData.put("monthlyStars", entry.monthlyStars);
                        rankData.put("month", currentMonth);
                        batch.set(db.collection("region_rankings")
                                .document(currentMonth + "_" + entry.code), rankData);
                    }

                    batch.commit().addOnSuccessListener(aVoid -> {
                        updateUserRegionRanks(entries);
                    });
                });
    }

    private void updateUserRegionRanks(List<RegionEntry> entries) {
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch();
                    Map<String, Long> regionRanks = new HashMap<>();
                    for (int i = 0; i < entries.size(); i++) {
                        regionRanks.put(entries.get(i).name, (long) (i + 1));
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String storedRegionName = doc.getString("region");
                        String code = RegionRepository.getNameToCode(storedRegionName);
                        if (code != null) {
                            String name = RegionRepository.getCodeToName(code);
                            if (name != null && regionRanks.containsKey(name)) {
                                batch.update(doc.getReference(), "lastMonthRegionRank", regionRanks.get(name));
                            } else {
                                batch.update(doc.getReference(), "lastMonthRegionRank", 0L);
                            }
                        } else {
                            batch.update(doc.getReference(), "lastMonthRegionRank", 0L);
                        }
                    }

                    batch.commit();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (!mapInitialized && mapView != null) {
            setupMap();
            mapInitialized = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) {
            mapView.onDetach();
        }
    }
}