package com.durabilityplus.logic.degrade;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;


import java.util.*;

public final class DegradationConfig {

    private final FileConfiguration cfg;

    // toggles
    private final boolean enabled;
    private final boolean weaponEnabled;
    private final boolean armorEnabled;
    private final boolean miningEnabled;

    // mining mode + visuals
    private final boolean modeEffect; // true => "effect", false => "delay"
    private final String effectStyle; // "burst" (default) or "smooth"
    private final int effectDurationTicks; // burst duration
    private final boolean syncAnimation;
    private final int maxTrackSeconds;

    // smooth (new) – only used when effectStyle == "smooth"
    private final int smoothMinDurationTicks;
    private final int smoothMaxDurationTicks;
    private final int smoothMinLevel;
    private final int smoothMaxLevel;

    // global curves
    private final NavigableMap<Integer, Double> wCurveGlobal;
    private final NavigableMap<Integer, Double> aCurveGlobal;
    private final NavigableMap<Integer, Double> mDelayCurveGlobal; // delay multiplier
    private final NavigableMap<Integer, Integer> mFatigueLevelsGlobal;

    // per-item curves
    private final PerItemDoubleCurves weaponPerItem;
    private final PerItemDoubleCurves armorPerItem;
    private final PerItemDoubleCurves miningDelayPerItem;
    private final PerItemIntCurves miningEffectPerItem;

    public DegradationConfig(org.bukkit.plugin.Plugin plugin) {
        this.cfg = plugin.getConfig();

        // root sections
        ConfigurationSection root = cfg.getConfigurationSection("degradation");
        ConfigurationSection w = (root != null) ? root.getConfigurationSection("weaponDamage") : null;
        ConfigurationSection a = (root != null) ? root.getConfigurationSection("armorProtection") : null;
        ConfigurationSection m = (root != null) ? root.getConfigurationSection("miningSpeed") : null;
        ConfigurationSection v = (root != null) ? root.getConfigurationSection("visuals") : null;
        ConfigurationSection eff = (m != null ? m.getConfigurationSection("effect") : null);
        ConfigurationSection burst = (eff != null ? eff.getConfigurationSection("burst") : null);
        ConfigurationSection smooth= (eff != null ? eff.getConfigurationSection("smooth") : null);
        ConfigurationSection delay = (m != null ? m.getConfigurationSection("delay") : null);


        this.enabled = (root == null) ? true : root.getBoolean("enabled", true);
        this.weaponEnabled = w != null && w.getBoolean("enabled", true);
        this.armorEnabled = a != null && a.getBoolean("enabled", true);
        this.miningEnabled = m != null && m.getBoolean("enabled", true);

        // mode: "effect" vs "delay"
        this.modeEffect = "effect".equalsIgnoreCase(m != null ? m.getString("mode", "effect") : "effect");
        this.effectStyle = (eff != null ? eff.getString("style", "burst") : "burst");
        this.effectDurationTicks = Math.max(1, burst != null ? burst.getInt("durationTicks", 40) : 40);


        // Smooth config (new getters already exist in your class)
        this.smoothMinLevel = smooth != null ? Math.max(0, smooth.getInt("minLevel", 1)) : 1;
        this.smoothMaxLevel = smooth != null ? Math.max(smoothMinLevel, smooth.getInt("maxLevel", 2)) : 2;
        this.smoothMinDurationTicks = Math.max(1, smooth != null ? smooth.getInt("minDurationTicks", 40) : 40);
        this.smoothMaxDurationTicks = Math.max(smoothMinDurationTicks,
                smooth != null ? smooth.getInt("maxDurationTicks", 200) : 200);


        // visuals / animation helpers
        this.syncAnimation = v != null && v.getBoolean("syncAnimation", true);
        this.maxTrackSeconds = Math.max(1, v != null ? v.getInt("maxTrackSeconds", 8) : 8);

        // === global curves ===
        this.wCurveGlobal = CurveUtil.parseDoubleCurve(w != null ? w.getConfigurationSection("curve") : null, 1.0);
        this.aCurveGlobal = CurveUtil.parseDoubleCurve(a != null ? a.getConfigurationSection("curve") : null, 1.0);

        // Delay base multiplier curve (prefer new location, fall back to old)
        this.mDelayCurveGlobal = CurveUtil.parseDoubleCurve(
                delay != null ? delay.getConfigurationSection("baseMultiplierCurve")
                        : (m != null ? m.getConfigurationSection("baseMultiplierCurve") : null),
                1.0);
        this.mFatigueLevelsGlobal = CurveUtil.parseIntCurve(
                burst != null ? burst.getConfigurationSection("fatigueLevels") : null, 0);


        // === per-item curves ===
        this.weaponPerItem = new PerItemDoubleCurves(w != null ? w.getConfigurationSection("perItem") : null);
        this.armorPerItem = new PerItemDoubleCurves(a != null ? a.getConfigurationSection("perItem") : null);
        this.miningDelayPerItem = new PerItemDoubleCurves(
                delay != null ? delay.getConfigurationSection("perItemDelayCurves") : null);

        this.miningEffectPerItem = new PerItemIntCurves(null);

    }
    // ------------------------ public API used by listeners ------------------------

    public boolean isEnabled() { return enabled; }
    public boolean weaponDamageEnabled() { return enabled && weaponEnabled; }
    public boolean armorProtectionEnabled() { return enabled && armorEnabled; }
    public boolean miningEnabled() { return enabled && miningEnabled; }

    public boolean miningModeEffect() { return modeEffect; } // true => effect mode
    public boolean effectStyleSmooth() { return "smooth".equalsIgnoreCase(effectStyle); }
    public int miningFatigueDurationTicks(){ return effectDurationTicks; } // burst duration

    public boolean syncAnimation() { return syncAnimation; }
    public int maxTrackSeconds() { return maxTrackSeconds; }

    // Smooth getters (used only when effectStyleSmooth() is true)
    public int smoothMinLevel() { return smoothMinLevel; }
    public int smoothMaxLevel() { return smoothMaxLevel; }
    public int smoothMinDurationTicks() { return smoothMinDurationTicks; }
    public int smoothMaxDurationTicks() { return smoothMaxDurationTicks; }

    // Curves — weapon / armor / mining delay
    public double weaponFactorFor(Material mat, double pct) {
        int p = clampPercent(pct);
        NavigableMap<Integer, Double> per = weaponPerItem.find(mat);
        if (per != null) return lookupDouble(per, p, 1.0);
        return lookupDouble(wCurveGlobal, p, 1.0);
    }

    public double armorFactorFor(Material mat, double pct) {
        int p = clampPercent(pct);
        NavigableMap<Integer, Double> per = armorPerItem.find(mat);
        if (per != null) return lookupDouble(per, p, 1.0);
        return lookupDouble(aCurveGlobal, p, 1.0);
    }

    public double miningDelayFactorFor(Material toolMat, int percent) {
        int p = Math.max(0, Math.min(100, percent));

        // per-item override first
        if (miningDelayPerItem != null && toolMat != null) {
            java.util.NavigableMap<Integer, Double> per = miningDelayPerItem.find(toolMat);
            if (per != null) return lookupDouble(per, p, 1.0);
        }

        // global base curve
        if (mDelayCurveGlobal != null && !mDelayCurveGlobal.isEmpty()) {
            return lookupDouble(mDelayCurveGlobal, p, 1.0);
        }

        return 1.0; // default = no extra delay
    }

    // Mining Fatigue level (effect mode)
    public int miningFatigueLevelFor(Material mat, int percent) {
        int p = clampPercent(percent);
        NavigableMap<Integer, Integer> per = miningEffectPerItem.find(mat);
        if (per != null) return lookupInt(per, p, 0);
        return lookupInt(mFatigueLevelsGlobal, p, 0);
    }

    // ------------------------ helpers ------------------------

    private static int clampPercent(double pct) {
        // accepts either 0..1.0 or 0..100; normalizes to 0..100 int
        if (pct <= 1.0) pct *= 100.0;
        if (pct < 0.0) pct = 0.0;
        if (pct > 100.0) pct = 100.0;
        return (int)Math.round(pct);
    }

    private static double lookupDouble(NavigableMap<Integer, Double> curve, int percent, double def) {
        if (curve == null || curve.isEmpty()) return def;
        Map.Entry<Integer, Double> e = curve.ceilingEntry(percent); // first threshold >= percent
        return (e != null) ? e.getValue() : def;
    }

    private static int lookupInt(NavigableMap<Integer, Integer> curve, int percent, int def) {
        if (curve == null || curve.isEmpty()) return def;
        Map.Entry<Integer, Integer> e = curve.ceilingEntry(percent); // first threshold >= percent
        return (e != null) ? e.getValue() : def;
    }
}