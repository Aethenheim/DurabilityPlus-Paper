package com.aethenheim.plugins.durabilitypluspaper.logic.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class PdcKeys {
    private PdcKeys() {}

    public static NamespacedKey BROKEN;
    public static NamespacedKey PING_DISABLED; // per-player toggle

    public static void init(JavaPlugin plugin) {
        BROKEN = new NamespacedKey(plugin, "broken");
        PING_DISABLED = new NamespacedKey(plugin, "ping_disabled");
    }
}
