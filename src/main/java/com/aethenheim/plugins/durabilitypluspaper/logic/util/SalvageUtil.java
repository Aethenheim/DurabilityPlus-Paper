package com.aethenheim.plugins.durabilitypluspaper.logic.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;

import java.util.*;

public final class SalvageUtil {
    private SalvageUtil() {}

    public static void tryDropSalvage(FileConfiguration cfg, Player p, ItemStack item) {
        if (cfg == null || p == null || item == null || item.getType().isAir()) return;
        if (!cfg.getBoolean("salvage.enabled", true)) return;

        int min = Math.max(0, cfg.getInt("salvage.dropCountMin", 1));
        int max = Math.max(min, cfg.getInt("salvage.dropCountMax", 2));
        int count = min + rng().nextInt(max - min + 1);

        List<Material> pool = resolvePool(cfg, item);
        if (pool.isEmpty() || count <= 0) return;

        Location at = p.getLocation();
        Random r = rng();
        for (int i = 0; i < count; i++) {
            Material m = pool.get(r.nextInt(pool.size()));
            if (m != Material.AIR) {
                p.getWorld().dropItemNaturally(at, new ItemStack(m, 1));
            }
        }
    }

    /** Resolution priority: matchers > perItem > perTier */
    private static List<Material> resolvePool(FileConfiguration cfg, ItemStack stack) {
        // 0) matchers (metadata rules)
        List<Material> m = resolveByMatchers(cfg, stack);
        if (!m.isEmpty()) return m;

        // 1) perItem by Material name
        var perItem = cfg.getConfigurationSection("salvage.perItem");
        if (perItem != null) {
            var arr = perItem.getStringList(stack.getType().name());
            if (arr != null && !arr.isEmpty()) return normalize(arr);
        }

        // 2) perTier by prefix before first underscore
        String name = stack.getType().name();
        int underscore = name.indexOf('_');
        String tier = underscore > 0 ? name.substring(0, underscore) : name;
        var perTier = cfg.getConfigurationSection("salvage.perTier");
        if (perTier != null) {
            var arr = perTier.getStringList(tier);
            if (arr != null && !arr.isEmpty()) return normalize(arr);
        }

        return Collections.emptyList();
    }

    private static List<Material> resolveByMatchers(FileConfiguration cfg, ItemStack stack) {
        ConfigurationSection root = cfg.getConfigurationSection("salvage");
        if (root == null) return Collections.emptyList();
        var entries = root.getList("matchers");
        if (entries == null || entries.isEmpty()) return Collections.emptyList();

        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        String display = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
        List<String> lore = (meta != null && meta.hasLore()) ? meta.getLore() : Collections.emptyList();
        Integer cmd = null;
        if (meta != null) {
            try {
                var hasCmd = (Boolean) meta.getClass().getMethod("hasCustomModelData").invoke(meta);
                if (Boolean.TRUE.equals(hasCmd)) {
                    cmd = (Integer) meta.getClass().getMethod("getCustomModelData").invoke(meta);
                }
            } catch (Throwable ignored) {}
        }

        for (Object o : entries) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) o;

            Object whenObj = entry.get("when");
            Object dropsObj = entry.get("drops");
            if (!(whenObj instanceof Map) || !(dropsObj instanceof List)) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> when = (Map<String, Object>) whenObj;
            @SuppressWarnings("unchecked")
            List<String> drops = (List<String>) dropsObj;
            if (drops.isEmpty()) continue;

            String wantMat  = asString(when.get("material"));
            Integer wantCmd = asInt(when.get("customModelData"));
            String wantName = asString(when.get("nameContains"));
            String wantLore = asString(when.get("loreContains"));

            if (wantMat != null) {
                try {
                    if (stack.getType() != Material.valueOf(wantMat.toUpperCase(Locale.ROOT))) continue;
                } catch (IllegalArgumentException ex) { continue; }
            }
            if (wantCmd != null) {
                if (cmd == null || !cmd.equals(wantCmd)) continue;
            }
            if (wantName != null) {
                if (display == null || !display.toLowerCase(Locale.ROOT).contains(wantName.toLowerCase(Locale.ROOT))) continue;
            }
            if (wantLore != null) {
                boolean hit = false;
                if (lore != null) {
                    String needle = wantLore.toLowerCase(Locale.ROOT);
                    for (String line : lore) {
                        if (line != null && line.toLowerCase(Locale.ROOT).contains(needle)) { hit = true; break; }
                    }
                }
                if (!hit) continue;
            }

            List<Material> pool = normalize(drops);
            if (!pool.isEmpty()) return pool;
        }
        return Collections.emptyList();
    }

    private static String asString(Object o) { return (o instanceof String s && !s.isBlank()) ? s : null; }
    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }

    private static List<Material> normalize(List<String> names) {
        List<Material> out = new ArrayList<>();
        for (String s : names) {
            try { out.add(Material.valueOf(s.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    private static Random RNG;
    private static Random rng() {
        if (RNG == null) RNG = new Random();
        return RNG;
    }
}

