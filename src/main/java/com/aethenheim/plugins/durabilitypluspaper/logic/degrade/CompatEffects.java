package com.aethenheim.plugins.durabilitypluspaper.logic.degrade;

import org.bukkit.potion.PotionEffectType;

public final class CompatEffects {
    private CompatEffects() {}

    /**
     * Return the Mining Fatigue effect across API variants.
     * Never references enum constants directly so it compiles on all versions.
     * May return null if neither alias exists (very unlikely on modern Spigot).
     */
    public static PotionEffectType miningFatigue() {
        PotionEffectType t = PotionEffectType.getByName("MINING_FATIGUE"); // modern name
        if (t != null) return t;
        return PotionEffectType.getByName("SLOW_DIGGING"); // legacy alias
    }
}