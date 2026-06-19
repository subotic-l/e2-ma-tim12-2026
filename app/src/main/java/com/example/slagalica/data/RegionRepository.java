package com.example.slagalica.data;

import com.example.slagalica.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionRepository {

    public static final String VOJVODINA = "VOJVODINA";
    public static final String BEOGRAD = "BEOGRAD";
    public static final String SUMADIJA_ZAPAD = "SUMADIJA_ZAPAD";
    public static final String JUZNA_ISTOCNA = "JUZNA_ISTOCNA";
    public static final String KOSOVO = "KOSOVO";

    private static final Map<String, Region> REGIONS = new HashMap<>();

    static {
        REGIONS.put(VOJVODINA,
                new Region(
                        VOJVODINA,
                        "Vojvodina",
                        45.2671,   // Novi Sad
                        19.8335
                ));

        REGIONS.put(BEOGRAD,
                new Region(
                        BEOGRAD,
                        "Beograd",
                        44.7983,   // Vračar
                        20.4781
                ));

        REGIONS.put(SUMADIJA_ZAPAD,
                new Region(
                        SUMADIJA_ZAPAD,
                        "Šumadija i Zapadna Srbija",
                        44.2758,   // Valjevo
                        19.8982
                ));

        REGIONS.put(JUZNA_ISTOCNA,
                new Region(
                        JUZNA_ISTOCNA,
                        "Južna i Istočna Srbija",
                        43.3209,   // Niš
                        21.8958
                ));

        REGIONS.put(KOSOVO,
                new Region(
                        KOSOVO,
                        "Kosovo",
                        42.6629,   // Priština
                        21.1655
                ));
    }

    public static Region get(String code) {
        return REGIONS.get(code);
    }

    public static List<Region> getAll() {
        return new ArrayList<>(REGIONS.values());
    }

    public static List<String> getAllNames() {
        List<String> names = new ArrayList<>();
        for (Region r : REGIONS.values()) {
            names.add(r.getName());
        }
        return names;
    }

    public static Map<String, Region> getMap() {
        return Collections.unmodifiableMap(REGIONS);
    }

    public static String getNameToCode(String name) {
        for (Region r : REGIONS.values()) {
            if (r.getName().equals(name)) return r.getCode();
        }
        return null;
    }

    public static String getCodeToName(String code) {
        Region r = REGIONS.get(code);
        return r != null ? r.getName() : null;
    }
}