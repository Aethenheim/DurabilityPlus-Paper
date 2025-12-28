package com.durabilityplus.logic.degrade;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ProjectileUtil {
    private ProjectileUtil() {}

    public static void setFactor(Plugin plugin, Projectile proj, double factor) {
        NamespacedKey key = new NamespacedKey(plugin, "dpFactor");
        PersistentDataContainer pdc = proj.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.DOUBLE, factor);
    }

    public static Double getFactor(Plugin plugin, Projectile proj) {
        NamespacedKey key = new NamespacedKey(plugin, "dpFactor");
        PersistentDataContainer pdc = proj.getPersistentDataContainer();
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            return pdc.get(key, PersistentDataType.DOUBLE);
        }
        return null;
    }
}

