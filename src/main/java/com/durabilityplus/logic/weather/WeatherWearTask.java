package com.durabilityplus.logic.weather;

import com.durabilityplus.DurabilityPlusPlugin;
import com.durabilityplus.logic.LoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class WeatherWearTask implements Runnable {
    private final DurabilityPlusPlugin plugin;
    private final LoreUtil lore;
    private BukkitTask task;

    public WeatherWearTask(DurabilityPlusPlugin plugin, LoreUtil lore) {
        this.plugin = plugin;
        this.lore = lore;
    }

    public void start() {
        stop();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("weatherWear.enabled", true)) return;

        int periodSec = Math.max(1, cfg.getInt("weatherWear.periodSeconds", 5));
        long ticks = periodSec * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override public void run() {
        final FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("weatherWear.enabled", true)) return;

        boolean anyStorm = Bukkit.getWorlds().stream().anyMatch(this::isStorming);
        if (!anyStorm) return;

        final boolean affectTools = cfg.getBoolean("weatherWear.affectTools", true);
        final boolean affectArmor = cfg.getBoolean("weatherWear.affectArmor", true);

        final List<String> toolPrefixes = cfg.getStringList("weatherWear.materials.tools"); // e.g. ["WOODEN","STONE","IRON","GOLDEN"]
        final List<String> armorPrefixes = cfg.getStringList("weatherWear.materials.armor"); // e.g. ["LEATHER","CHAINMAIL","IRON","GOLDEN"]
        final List<String> exemptTiers = cfg.getStringList("weatherWear.exemptTiers"); // e.g. ["DIAMOND","NETHERITE"]

        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (!isStorming(w)) continue;

            // Tools
            if (affectTools) {
                tickIfAffected(p.getInventory().getItem(EquipmentSlot.HAND), toolPrefixes, exemptTiers);
                tickIfAffected(p.getInventory().getItem(EquipmentSlot.OFF_HAND), toolPrefixes, exemptTiers);
            }
             //Armor
            if (affectArmor) {
                for (ItemStack armor : p.getInventory().getArmorContents()) {
                    tickIfAffected(armor, armorPrefixes, exemptTiers);
                }
            }
        }
    }

    private boolean isStorming(World w) {
        return w.hasStorm() || w.isThundering();
    }

    /** Apply 1 point of damage if the item matches the allowed prefixes and is NOT in the exempt list. */
    private void tickIfAffected(ItemStack stack, List<String> allowedPrefixes, List<String> exemptPrefixes) {
        if (stack == null || stack.getType() == Material.AIR) return;

        final Material mat = stack.getType();
        final String name = mat.name();

        if (!matchesAnyPrefix(name, allowedPrefixes)) return;
        if (matchesAnyPrefix(name, exemptPrefixes)) return;

        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable d)) return;
        int max = mat.getMaxDurability();
        if (max <= 0) return;

        int newDamage = Math.min(max, d.getDamage() + 1);
        d.setDamage(newDamage);
        stack.setItemMeta(meta);

        lore.updateLore(stack);
    }

    private boolean matchesAnyPrefix(String materialName, List<String> prefixes) {
        if (prefixes == null) return false;
        for (String pref : prefixes) {
            if (pref == null || pref.isEmpty()) continue;
            String p = pref.trim().toUpperCase();
            if (materialName.startsWith(p + "_")) return true;
        }
        return false;
    }
}