package com.durabilityplus.logic.degrade;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;

public class PerItemIntCurves {
    private final Map<Material, NavigableMap<Integer, Integer>> exact = new HashMap<>();
    private final Map<String, NavigableMap<Integer, Integer>> prefix = new HashMap<>();

    public PerItemIntCurves(ConfigurationSection sec) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection curveSec = sec.getConfigurationSection(key);
            if (curveSec == null) continue;
            NavigableMap<Integer, Integer> curve = CurveUtil.parseIntCurve(curveSec, 0);

            if (key.endsWith("*")) {
                String p = key.substring(0, key.length() - 1);
                prefix.put(p, curve);
            } else {
                try {
                    Material mat = Material.valueOf(key);
                    exact.put(mat, curve);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public NavigableMap<Integer, Integer> find(Material mat) {
        if (mat == null) return null;
        if (exact.containsKey(mat)) return exact.get(mat);
        String name = mat.name();
        for (Map.Entry<String, NavigableMap<Integer, Integer>> e : prefix.entrySet()) {
            if (name.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }
}
