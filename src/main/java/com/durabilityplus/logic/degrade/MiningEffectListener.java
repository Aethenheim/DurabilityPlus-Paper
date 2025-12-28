package com.durabilityplus.logic.degrade;

import com.durabilityplus.DurabilityPlusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Effect mode: briefly applies Mining Fatigue based on tool %,
 * and proactively clears it when the tool is healthy/switches.
 */
public class MiningEffectListener implements Listener {
    private final DurabilityPlusPlugin plugin;
    private final DegradationConfig dcfg;

    public MiningEffectListener(DurabilityPlusPlugin plugin, DegradationConfig dcfg) {
        this.plugin = plugin;
        this.dcfg = dcfg;
    }

    private int toolPercent(ItemStack item) {
        if (item == null || item.getType().isAir()) return 100;
        if (!(item.getItemMeta() instanceof Damageable d)) return 100;
        int max = item.getType().getMaxDurability();
        if (max <= 0) return 100;
        int remaining = Math.max(0, max - d.getDamage());
        return Math.round(remaining * 100f / max);
    }

    private int smoothDurationTicks(int percent) {
        // linearly map 100% -> min, 0% -> max
        int min = dcfg.smoothMinDurationTicks();
        int max = dcfg.smoothMaxDurationTicks();
        return min + ((100 - percent) * (max - min) / 100);
    }

    private int cappedAmplifierForSmooth(int baseLevel) {
        // clamp level to [minLevel..maxLevel] then convert to 0-based amplifier
        int minL = dcfg.smoothMinLevel();
        int maxL = dcfg.smoothMaxLevel();
        int level = Math.max(minL, Math.min(maxL, baseLevel));
        return Math.max(0, level - 1);
    }

    /** Reads fatigueLevels from config and returns the level for a given percent. */
    private int fatigueLevelForPercent(int percent) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("degradation.miningSpeed.fatigueLevels");
        if (sec == null) return 0;
        // Sort thresholds numerically ascending; first threshold >= percent wins
        List<Integer> thresholds = new ArrayList<>();
        for (String k : sec.getKeys(false)) {
            try { thresholds.add(Integer.parseInt(k)); } catch (NumberFormatException ignored) {}
        }
        thresholds.sort(Integer::compareTo); // 10,25,50,...
        for (int t : thresholds) {
            if (percent <= t) return sec.getInt(String.valueOf(t), 0);
        }
        return 0;
    }

    private void clearFatigue(Player p) {
        p.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        var dcfg = plugin.getDegradationConfig();
        if (dcfg == null || !dcfg.isEnabled() || !dcfg.miningEnabled() || !dcfg.miningModeEffect()) return;

        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) {
            clearFatigue(p);
            return;
        }

        int percent = toolPercent(tool);

        int baseLevel = dcfg.miningFatigueLevelFor(tool.getType(), percent);
        if (baseLevel <= 0) {
            clearFatigue(p);
            return;
        }

        if (dcfg.effectStyleSmooth()) {
            int dur = smoothDurationTicks(percent);
            int amp = cappedAmplifierForSmooth(baseLevel);
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.MINING_FATIGUE,
                    Math.max(1, dur),
                    amp,
                    true, false, false
            ));
        } else {
            int dur = Math.max(20, dcfg.miningFatigueDurationTicks());
            int amp = Math.max(0, baseLevel - 1);
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.MINING_FATIGUE,
                    dur,
                    amp,
                    true, false, false
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        var dcfg = plugin.getDegradationConfig();
        if (dcfg == null || !dcfg.isEnabled() || !dcfg.miningEnabled() || !dcfg.miningModeEffect()) return;

        if (e.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            clearFatigue(e.getPlayer());
            return;
        }

        var p = e.getPlayer();
        var tool = p.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) { clearFatigue(p); return; }

        int percent = toolPercent(tool);
        int baseLevel = dcfg.miningFatigueLevelFor(tool.getType(), percent);
        if (baseLevel <= 0) { clearFatigue(p); return; }

        if (dcfg.effectStyleSmooth()) {
            int dur = smoothDurationTicks(percent);
            int amp = Math.max(0, cappedAmplifierForSmooth(baseLevel) - 1); // Bukkit amp is 0-based
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.MINING_FATIGUE, // use SLOW_DIGGING if MINING_FATIGUE doesn't exist
                    Math.max(1, dur),
                    amp,
                    true, // ambient
                    false // particles
            ));
        } else {
            int dur = Math.max(20, dcfg.miningFatigueDurationTicks());
            int amp = Math.max(0, baseLevel - 1);
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.MINING_FATIGUE,
                    Math.max(1, dur),
                    amp,
                    true,
                    false
            ));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        if (!dcfg.isEnabled() || !dcfg.miningEnabled() || !dcfg.miningModeEffect()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            if (fatigueLevelForPercent(toolPercent(p.getInventory().getItemInMainHand())) <= 0) clearFatigue(p);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!dcfg.isEnabled() || !dcfg.miningEnabled() || !dcfg.miningModeEffect()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (fatigueLevelForPercent(toolPercent(e.getPlayer().getInventory().getItemInMainHand())) <= 0)
                clearFatigue(e.getPlayer());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInv(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!dcfg.isEnabled() || !dcfg.miningEnabled() || !dcfg.miningModeEffect()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (fatigueLevelForPercent(toolPercent(p.getInventory().getItemInMainHand())) <= 0) clearFatigue(p);
        });
    }
}