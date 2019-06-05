package com.fh;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class NormalRange {

    private final HashMap<String, Range> map = new HashMap<>();

    public class Range {

        public Range(Double max, Double min) {
            this.max = max;
            this.min = min;
        }

        Double max;
        Double min;
    }

    public NormalRange() {
        String json = new Scanner(getClass().getClassLoader().getResourceAsStream("NormalRange.json"), "UTF-8").useDelimiter("\\A").next();
        Gson gson = new Gson();

        System.out.println(json);

        Map<String, Map<String, Double>> map = new HashMap<>();

        map = (Map<String, Map<String, Double>>) gson.fromJson(json, map.getClass());

        map.forEach((key, value) -> {
            this.map.put(key, new Range(value.get("max"), value.get("min")));
        });
    }

    public boolean isInRange(String unit, double value) {
        Range range = map.get(unit);
        if (range == null) return true;

        return range.min <= value && value <= range.max;
    }

}