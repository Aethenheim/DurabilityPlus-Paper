package com.aethenheim.plugins.durabilitypluspaper.logic;

import com.aethenheim.plugins.durabilitypluspaper.DurabilityPlusPlugin;
import com.aethenheim.plugins.durabilitypluspaper.logic.util.PdcKeys;
import com.aethenheim.plugins.durabilitypluspaper.logic.util.SalvageUtil;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.bukkit.Registry.*;

public class DurabilityService implements Listener {

    private final DurabilityPlusPlugin plugin;
    // left non-final on purpose in case you hot-swap via /dp reload (reflection)
    private MaterialMatcher matcher;
    private final Random rng = new Random();

    // Wrong-tool tracking (consumed on next durability event)
    private final Map<UUID, Boolean> lastWrongToolBlock = new HashMap<>();
    private final Map<UUID, Boolean> lastWrongToolCombat = new HashMap<>();

    // Cooldown to avoid spamming the low-durability ping sound
    private final Map<UUID, Long> pingCooldown = new HashMap<>();

    public DurabilityService(DurabilityPlusPlugin plugin, MaterialMatcher matcher) {
        this.plugin = plugin;
        this.matcher = matcher;
    }

    /* =========================================================
       CORE DURABILITY SCALING
       ========================================================= */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        FileConfiguration cfg = plugin.getConfig();

        // Armor include toggle
        if (!cfg.getBoolean("includeArmor", true) && isArmorItem(item.getType())) {
            return;
        }

        // Base factor (global → per-item/elytra → per-world → donor)
        double factor = effectiveMultiplier(p, item.getType());

        // Wrong-tool: blocks (applies once per break)
        if (cfg.getBoolean("wrongTool.blocks.enabled", true)) {
            Boolean bad = lastWrongToolBlock.remove(p.getUniqueId());
            if (Boolean.TRUE.equals(bad)) {
                factor *= cfg.getDouble("wrongTool.blocks.multiplier", 1.0);
            }
        }

        // Wrong-tool: combat (applies once per hit)
        if (cfg.getBoolean("wrongTool.combat.enabled", true)) {
            Boolean bad = lastWrongToolCombat.remove(p.getUniqueId());
            if (Boolean.TRUE.equals(bad)) {
                factor *= cfg.getDouble("wrongTool.combat.multiplier", 1.0);
            }
        }

        // 0 or below → never loses durability
        if (factor <= 0.0) {
            e.setCancelled(true);
            return;
        }

        // Expected damage scaling via fractional roll
        int baseDamage = e.getDamage(); // usually 1
        double expected = baseDamage * factor;
        int out = (int) Math.floor(expected);
        double frac = expected - out;
        if (rng.nextDouble() < frac) out++;

        // Auto-protect (prevent final break)
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dMeta)) {
            // Non-damageable (shouldn't happen for this event)
            return;
        }
        int max = item.getType().getMaxDurability();
        int currentDamage = dMeta.getDamage();

        // If autoProtect is enabled and this hit would break the item:
        if (cfg.getBoolean("autoProtect.enabled", true) && max > 0 && currentDamage + out >= max) {
            // Stop this damage from applying; clamp to max-1 and mark BROKEN
            e.setCancelled(true);
            dMeta.setDamage(Math.max(0, max - 1));

            // Mark broken on the item PDC (so other parts of the plugin can respect it)
            try {
                var pdc = meta.getPersistentDataContainer();
                pdc.set(PdcKeys.BROKEN, PersistentDataType.BYTE, (byte) 1);
            } catch (Throwable ignored) {}
            item.setItemMeta(meta);

            // Notify player (longer action-bar)
            notifyBroken(p);

            // Update lore
            plugin.getLoreUtil().updateLore(item);

            // Optional: also drop salvage if configured to do so
            if (cfg.getBoolean("salvage.enabled", false)
                    && cfg.getBoolean("salvage.alsoOnAutoProtect", false)) {
                SalvageUtil.tryDropSalvage(cfg, p, item);
            }
            return;
        }
        // If not auto-protect, but this hit will break the item, drop salvage first
        else if (max > 0 && currentDamage + out >= max) {
            if (cfg.getBoolean("salvage.enabled", true)) {
                SalvageUtil.tryDropSalvage(cfg, p, item);
            }
            // fall through to setDamage(out) below so the item actually breaks
        }

        // Apply damage
        if (out <= 0) {
            // Negate this tick's damage entirely
            e.setCancelled(true);
        } else {
            e.setDamage(out);
        }

        // After the event runs, update lore & possibly ping
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLoreUtil().updateLore(item);
            tryLowDurabilityPing(p, item);
        });
    }
    public void setMatcher(MaterialMatcher matcher) {
        this.matcher = matcher;
    }
    /* =========================================================
       LONGER ACTION-BAR BROKEN NOTICE
       ========================================================= */
    private void notifyBroken(Player p) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("autoProtect.notifyOnUse", true)) return;

        String mode = cfg.getString("autoProtect.notifyMode", "actionbar").toLowerCase();
        String msg = "Your item is broken and needs repair.";

        if ("chat".equals(mode)) {
            p.sendMessage("§c" + msg);
            return;
        }

        // ACTION BAR — re-send for ~3s so players can read it
        try {
            var tc = new net.md_5.bungee.api.chat.TextComponent("§c" + msg);
            final int period = 20; // 1s
            final int total = 60; // 3s
            final int[] ran = {0};

            var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!p.isOnline() || ran[0] >= total) return;
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, tc);
                ran[0] += period;
            }, 0L, period);

            Bukkit.getScheduler().runTaskLater(plugin, task::cancel, total);
        } catch (Throwable ignored) {
            p.sendMessage("§c" + msg);
        }
    }

    /* =========================================================
       LOW-DURABILITY PING
       ========================================================= */
    private void tryLowDurabilityPing(Player p, ItemStack item) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("lowDurabilityPing.enabled", true)) return;

        int max = item.getType().getMaxDurability();
        if (max <= 0) return;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dMeta)) return;

        int remaining = Math.max(0, max - dMeta.getDamage());
        int percent = Math.round(remaining * 100f / max);

        int threshold = cfg.getInt("lowDurabilityPing.thresholdPercent", 5);
        if (percent > threshold) return;

        // per-player toggle via PDC
        Byte disabled = p.getPersistentDataContainer().get(
                PdcKeys.PING_DISABLED, PersistentDataType.BYTE);
        if (disabled != null && disabled == (byte) 1) return;

        int cooldownSec = Math.max(0, cfg.getInt("lowDurabilityPing.cooldownSeconds", 10));
        long now = System.currentTimeMillis();
        Long last = pingCooldown.get(p.getUniqueId());
        if (last != null && (now - last) < cooldownSec * 1000L) return;
        pingCooldown.put(p.getUniqueId(), now);

        String soundName = cfg.getString("lowDurabilityPing.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float vol = (float) cfg.getDouble("lowDurabilityPing.volume", 1.0);
        float pit = (float) cfg.getDouble("lowDurabilityPing.pitch", 1.0);

        try {
            NamespacedKey key = NamespacedKey.fromString(soundName.toUpperCase());
            if (key == null) {
                key = NamespacedKey.minecraft(soundName.toLowerCase());
            }
            org.bukkit.Sound s = Registry.SOUNDS.get(key);
            p.playSound(p.getLocation(), s, vol, pit);
        } catch (IllegalArgumentException ex) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, vol, pit);
        }
    }

    private boolean isArmorItem(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS")
                || n.equals("ELYTRA");
    }

    /* =========================================================
       PREVENT/ALTER REPAIR PATHS
       ========================================================= */
    // Elytra: optionally block anvil repair
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        var cfg = plugin.getConfig();
        if (cfg.getBoolean("elytra.repairable", true)) return;
        var inv = e.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        if (left == null || right == null) return;
        if (left.getType() == Material.ELYTRA || right.getType() == Material.ELYTRA) {
            e.setResult(null);
        }
    }

    // Mending rebalance (and optionally block on Elytra via repairable=false)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent e) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("elytra.repairable", true) && e.getItem().getType() == Material.ELYTRA) {
            e.setCancelled(true);
            return;
        }
        double factor = cfg.getDouble("mending.factor", 1.0);
        if (factor <= 0.0) {
            e.setCancelled(true);
            return;
        }
        int amt = e.getRepairAmount();
        int scaled = (int) Math.floor(amt * factor);
        if (scaled <= 0) {
            e.setCancelled(true);
        } else if (scaled != amt) {
            e.setRepairAmount(scaled);
        }
    }

    /* =========================================================
       MULTIPLIER RESOLUTION
       ========================================================= */
    private double effectiveMultiplier(Player p, Material mat) {
        FileConfiguration cfg = plugin.getConfig();

        // 1) base = global
        double base = cfg.getDouble("globalMultiplier", 1.0);

        // 2) per-item (includes wildcard) with Elytra override
        if (mat == Material.ELYTRA) {
            base = matcher.resolve(mat, cfg.getDouble("elytra.multiplier", base));
        } else {
            base = matcher.resolve(mat, base);
        }

        // 3) per-world (MULTIPLICATIVE)
        var worldMap = cfg.getConfigurationSection("perWorldMultipliers");
        if (worldMap != null) {
            String w = p.getWorld().getName();
            if (worldMap.isDouble(w)) {
                base *= worldMap.getDouble(w);
            }
        }

        // 4) donor permission bonuses (first match wins; MULTIPLICATIVE)
        var donor = cfg.getConfigurationSection("donorBonuses");
        if (donor != null) {
            for (String key : donor.getKeys(false)) {
                String perm = "durabilityplus.bonus." + key;
                if (p.hasPermission(perm)) {
                    base *= donor.getDouble(key, 1.0);
                    break;
                }
            }
        }
        return base;
    }

    /* =========================================================
       WRONG-TOOL DETECTION MARKERS
       ========================================================= */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("wrongTool.blocks.enabled", true)) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        boolean effective = isEffectiveToolForBlock(item.getType(), e.getBlock().getType());
        lastWrongToolBlock.put(p.getUniqueId(), !effective);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("wrongTool.combat.enabled", true)) return;
        if (!(e.getDamager() instanceof Player p)) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        boolean treatAxeAsWeapon = plugin.getConfig().getBoolean("wrongTool.combat.treatAxeAsWeapon", true);
        boolean isWeapon = isWeaponItem(item.getType(), treatAxeAsWeapon);
        lastWrongToolCombat.put(p.getUniqueId(), !isWeapon);
    }

    private boolean isWeaponItem(Material m, boolean treatAxeAsWeapon) {
        String n = m.name();
        if (n.endsWith("_SWORD")) return true;
        if (treatAxeAsWeapon && n.endsWith("_AXE")) return true;
        return n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT");
    }

    private boolean isEffectiveToolForBlock(Material tool, Material block) {
        // Heuristic using Bukkit tags; fast and accurate for vanilla behavior
        boolean mineablePick = Tag.MINEABLE_PICKAXE.isTagged(block);
        boolean mineableShovel = Tag.MINEABLE_SHOVEL.isTagged(block);
        boolean mineableAxe = Tag.MINEABLE_AXE.isTagged(block);
        boolean mineableHoe = Tag.MINEABLE_HOE.isTagged(block);

        String t = tool.name();
        if (mineablePick) return t.endsWith("_PICKAXE");
        if (mineableShovel) return t.endsWith("_SHOVEL");
        if (mineableAxe) return t.endsWith("_AXE");
        if (mineableHoe) return t.endsWith("_HOE");

        // If block has no clear mineable tag, assume OK to avoid false positives
        return true;
    }

    /* =========================================================
       COMMAND HELPERS (EDITED: clear BROKEN + remove Unbreakable)
       ========================================================= */
    public boolean addDurability(Player p, int amount) {
        ItemStack item = p.getInventory().getItem(EquipmentSlot.HAND);
        if (item == null) return false;
        var meta = item.getItemMeta();
        if (!(meta instanceof Damageable d)) return false;
        int max = item.getType().getMaxDurability();
        if (max <= 0) return false;

        int damage = Math.max(0, d.getDamage() - amount);
        d.setDamage(Math.min(max, damage));


        meta.setUnbreakable(false);
        try { meta.getPersistentDataContainer().remove(PdcKeys.BROKEN); } catch (Throwable ignored) {}

        item.setItemMeta(meta);
        plugin.getLoreUtil().updateLore(item);
        return true;
    }

    public boolean takeDurability(Player p, int amount) {
        ItemStack item = p.getInventory().getItem(EquipmentSlot.HAND);
        if (item == null) return false;
        var meta = item.getItemMeta();
        if (!(meta instanceof Damageable d)) return false;
        int max = item.getType().getMaxDurability();
        if (max <= 0) return false;

        int damage = Math.min(max, d.getDamage() + amount);
        d.setDamage(damage);
        if (damage >= max - 1 && plugin.getConfig().getBoolean("autoProtect.enabled", true)) {
            // mark BROKEN if we reached the protected edge
            try { meta.getPersistentDataContainer().set(PdcKeys.BROKEN, PersistentDataType.BYTE, (byte)1); } catch (Throwable ignored) {}
        }
        item.setItemMeta(meta);
        plugin.getLoreUtil().updateLore(item);
        return true;
    }

    public boolean setRemaining(Player p, int remaining) {
        ItemStack item = p.getInventory().getItem(EquipmentSlot.HAND);
        if (item == null) return false;
        var meta = item.getItemMeta();
        if (!(meta instanceof Damageable d)) return false;
        int max = item.getType().getMaxDurability();
        if (max <= 0) return false;

        int clampedRemaining = Math.max(0, Math.min(max, remaining));
        int damage = max - clampedRemaining;
        d.setDamage(damage);

        // make usable again
        meta.setUnbreakable(false);
        try { meta.getPersistentDataContainer().remove(PdcKeys.BROKEN); } catch (Throwable ignored) {}

        item.setItemMeta(meta);
        plugin.getLoreUtil().updateLore(item);
        return true;
    }

    public boolean repairCurrent(Player p) {
        ItemStack item = p.getInventory().getItem(EquipmentSlot.HAND);
        if (item == null) return false;
        var meta = item.getItemMeta();
        if (!(meta instanceof Damageable d)) return false;
        int max = item.getType().getMaxDurability();
        if (max <= 0) return false;

        d.setDamage(0);

        // make usable again
        meta.setUnbreakable(false);
        try { meta.getPersistentDataContainer().remove(PdcKeys.BROKEN); } catch (Throwable ignored) {}

        item.setItemMeta(meta);
        plugin.getLoreUtil().updateLore(item);
        return true;
    }

    public MaterialMatcher getMatcher(){
        return matcher;
    }

    public int repairAll(Player p) {
        int repaired = 0;
        PlayerInventory inv = p.getInventory();

        repaired += repairItem(inv.getItem(EquipmentSlot.HAND));
        repaired += repairItem(inv.getItem(EquipmentSlot.OFF_HAND));

        for (ItemStack armor : inv.getArmorContents()) {
            repaired += repairItem(armor);
        }
        for (ItemStack stack : inv.getContents()) {
            repaired += repairItem(stack);
        }
        return repaired;
    }

    private int repairItem(ItemStack item) {
        if (item == null) return 0;
        var meta = item.getItemMeta();
        if (!(meta instanceof Damageable d)) return 0;
        int max = item.getType().getMaxDurability();
        if (max <= 0 || d.getDamage() == 0) return 0;

        d.setDamage(0);

        // make usable again
        meta.setUnbreakable(false);
        try { meta.getPersistentDataContainer().remove(PdcKeys.BROKEN); } catch (Throwable ignored) {}

        item.setItemMeta(meta);
        plugin.getLoreUtil().updateLore(item);
        return 1;
    }
}