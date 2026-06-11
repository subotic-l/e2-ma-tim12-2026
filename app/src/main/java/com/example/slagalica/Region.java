package com.example.slagalica;

public class Region {

    private final String code;
    private final String name;

    private final double lat;
    private final double lon;

    public Region(String code, String name, double lat, double lon) {
        this.code = code;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    @Override
    public String toString() {
        return name;
    }
}
