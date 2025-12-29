package com.aethenheim.plugins.durabilitypluspaper.logic;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MaterialMatcher {
    private final Map<String, Double> exact = new HashMap<>();
    private final Map<String, Double> prefix = new HashMap<>(); // "DIAMOND_*" -> "DIAMOND_"
    private final Map<Material, Double> cache = new ConcurrentHashMap<>();
    private final boolean useCache;

    public MaterialMatcher(FileConfiguration cfg) {
        useCache = cfg.getBoolean("performance.cacheMaterialResolution", true);
        var section = cfg.getConfigurationSection("perItemMultipliers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                double val = section.getDouble(key, 1.0);
                if (key.endsWith("*")) {
                    String p = key.substring(0, key.length() - 1);
                    prefix.put(p, val);
                } else {
                    exact.put(key, val);
                }
            }
        }
    }

    public double resolve(Material mat, double fallback) {
        if (useCache) {
            return cache.computeIfAbsent(mat, m -> resolve0(m, fallback));
        }
        return resolve0(mat, fallback);
    }

    private double resolve0(Material mat, double fallback) {
        String name = mat.name();
        if (exact.containsKey(name)) return exact.get(name);
        for (Map.Entry<String, Double> e : prefix.entrySet()) {
            if (name.startsWith(e.getKey())) return e.getValue();
        }
        return fallback;
    }

    public void clearCache() { cache.clear(); }
}
