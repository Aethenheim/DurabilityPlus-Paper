package com.durabilityplus.logic.degrade;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;

public class PerItemDoubleCurves {
    private final Map<Material, NavigableMap<Integer, Double>> exact = new HashMap<>();
    private final Map<String, NavigableMap<Integer, Double>> prefix = new HashMap<>(); // "DIAMOND_*" -> "DIAMOND_"

    public PerItemDoubleCurves(ConfigurationSection sec) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection curveSec = sec.getConfigurationSection(key);
            if (curveSec == null) continue;
            NavigableMap<Integer, Double> curve = CurveUtil.parseDoubleCurve(curveSec, 1.0);

            if (key.endsWith("*")) {
                String p = key.substring(0, key.length() - 1);
                prefix.put(p, curve);
            } else {
                try {
                    Material mat = Material.valueOf(key);
                    exact.put(mat, curve);
                } catch (IllegalArgumentException ignored) { /* invalid material name */ }
            }
        }
    }

    public NavigableMap<Integer, Double> find(Material mat) {
        if (mat == null) return null;
        if (exact.containsKey(mat)) return exact.get(mat);
        String name = mat.name();
        for (Map.Entry<String, NavigableMap<Integer, Double>> e : prefix.entrySet()) {
            if (name.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }
}
