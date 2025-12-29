package com.aethenheim.plugins.durabilitypluspaper.logic;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import com.aethenheim.plugins.durabilitypluspaper.logic.util.*;

import java.util.ArrayList;
import java.util.List;

public class LoreUtil {
    private final Plugin plugin;

    public LoreUtil(Plugin plugin) {
        this.plugin = plugin;
    }

    public void refreshHandItemLore(Player p) {
        ItemStack item = p.getInventory().getItem(EquipmentSlot.HAND);
        if (item == null) return;
        updateLore(item);
    }

    public void updateLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("lore.enabled", true)) return;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmgMeta)) return;

        boolean hideVanillaUnbreakable = cfg.getBoolean("lore.hideVanillaUnbreakable", true);
        boolean showPluginUnbreakable = cfg.getBoolean("lore.showPluginUnbreakableLine", true);

        // Manage vanilla Unbreakable tooltip visibility
        if (hideVanillaUnbreakable) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        } else {
            meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }

        String prefix = cfg.getString("lore.prefix", "[D+]");
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        String line = null;

        if (dmgMeta.isUnbreakable()) {
            if (showPluginUnbreakable) {
                line = prefix + " Unbreakable";
            } else {
                // Remove our line if present to avoid duplication
                int idx = indexOfPrefixed(lore, prefix);
                if (idx >= 0) {
                    lore.remove(idx);
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                return;
            }
        } else {
            int max = item.getType().getMaxDurability();
            if (max <= 0) return;

            int damage = dmgMeta.getDamage();
            int remaining = Math.max(0, max - damage);
            int percent = Math.round(remaining * 100f / max);

            String mode = cfg.getString("lore.mode", "NUMBER").toUpperCase();
            if ("PERCENT".equals(mode)) {
                String fmt = cfg.getString("lore.percentFormat", "{percent}% ({current}/{max})");
                line = prefix + " " + fmt.replace("{percent}", String.valueOf(percent))
                        .replace("{current}", String.valueOf(remaining))
                        .replace("{max}", String.valueOf(max));
            } else {
                String fmt = cfg.getString("lore.numberFormat", "{current}/{max}");
                line = prefix + " " + fmt.replace("{current}", String.valueOf(remaining))
                        .replace("{max}", String.valueOf(max));
            }
        }

        boolean append = cfg.getBoolean("lore.append", true);
        boolean skipRedundant = cfg.getBoolean("performance.skipRedundantLoreUpdates", true);

        int idx = indexOfPrefixed(lore, prefix);
        if (idx >= 0) {
            if (skipRedundant && lore.get(idx).equals(line)) {
                // only update flags; line is identical
                item.setItemMeta(meta);
                return;
            }
            lore.set(idx, line);
        } else {
            if (append) lore.add(line);
            else lore.add(0, line);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private int indexOfPrefixed(List<String> lore, String prefix) {
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i) != null && lore.get(i).startsWith(prefix)) return i;
        }
        return -1;
    }
}
