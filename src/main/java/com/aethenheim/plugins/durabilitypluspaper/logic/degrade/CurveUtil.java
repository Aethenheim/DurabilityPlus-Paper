package com.aethenheim.plugins.durabilitypluspaper.logic.degrade;

import org.bukkit.configuration.ConfigurationSection;

import java.util.NavigableMap;
import java.util.TreeMap;

public final class CurveUtil {
    private CurveUtil() {}

    // Expect keys as percentage thresholds (strings), e.g., "75": 1.0
    // Semantics: for a given pct, return the factor for the first threshold >= pct.
    public static NavigableMap<Integer, Double> parseDoubleCurve(ConfigurationSection sec, double defaultVal) {
        TreeMap<Integer, Double> map = new TreeMap<>();
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    int pct = Integer.parseInt(k);
                    map.put(pct, sec.getDouble(k, defaultVal));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (map.isEmpty()) {
            map.put(100, defaultVal);
            map.put(0, defaultVal);
        }
        return map;
    }

    public static NavigableMap<Integer, Integer> parseIntCurve(ConfigurationSection sec, int defaultVal) {
        TreeMap<Integer, Integer> map = new TreeMap<>();
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    int pct = Integer.parseInt(k);
                    map.put(pct, sec.getInt(k, defaultVal));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (map.isEmpty()) {
            map.put(100, defaultVal);
            map.put(0, defaultVal);
        }
        return map;
    }

    public static double lookup(NavigableMap<Integer, Double> map, double pct, double def) {
        var entry = map.ceilingEntry((int) Math.round(pct));
        if (entry == null) entry = map.lastEntry();
        return entry != null ? entry.getValue() : def;
    }

    public static int lookup(NavigableMap<Integer, Integer> map, double pct, int def) {
        var entry = map.ceilingEntry((int) Math.round(pct));
        if (entry == null) entry = map.lastEntry();
        return entry != null ? entry.getValue() : def;
    }
}
