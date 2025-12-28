package com.durabilityplus;

import com.durabilityplus.cmd.DurabilityCommands;
import com.durabilityplus.cmd.DurabilityTabCompleter;
import com.durabilityplus.logic.DurabilityService;
import com.durabilityplus.logic.LoreUtil;
import com.durabilityplus.logic.MaterialMatcher;
import com.durabilityplus.logic.degrade.*;
import com.durabilityplus.logic.mending.MendingRebalanceListener;
import com.durabilityplus.logic.guard.BrokenItemGuard;
import com.durabilityplus.logic.repair.RepairStationsListener;
import com.durabilityplus.logic.weather.WeatherWearTask;
import com.durabilityplus.logic.util.PdcKeys;
import com.durabilityplus.logic.InventoryRefreshListener;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class DurabilityPlusPlugin extends JavaPlugin {

    private DurabilityService service;
    private LoreUtil loreUtil;
    private DegradationConfig dcfg;
    private WeatherWearTask weatherTask;

    private MiningEffectListener miningEffectL;
    private MiningDelayListener miningDelayL;
    private MiningAnimationHelper miningAnim;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PdcKeys.init(this);

        printStartupBanner();

        MaterialMatcher matcher = new MaterialMatcher(getConfig());
        service = new DurabilityService(this, matcher);
        loreUtil = new LoreUtil(this);
        dcfg = new DegradationConfig(this);

        Bukkit.getPluginManager().registerEvents(service, this);

        if (dcfg.isEnabled()) {
            if (dcfg.weaponDamageEnabled()) {
                Bukkit.getPluginManager().registerEvents(new WeaponDamageListener(this, dcfg), this);
            }
            if (dcfg.armorProtectionEnabled()){
                Bukkit.getPluginManager().registerEvents(new ArmorProtectionListener(this, dcfg), this);
            }
            applyMiningModeListener();
        }

        Bukkit.getPluginManager().registerEvents(new BrokenItemGuard(this), this);
        Bukkit.getPluginManager().registerEvents(new RepairStationsListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MendingRebalanceListener(this), this);

        Bukkit.getPluginManager().registerEvents(new InventoryRefreshListener(this, loreUtil), this);

        startWeatherTask();

        if (getCommand("durabilityplus") != null) {
            getCommand("durabilityplus").setExecutor(new DurabilityCommands(this, service));
            getCommand("durabilityplus").setTabCompleter(new DurabilityTabCompleter());
        }

        Bukkit.getOnlinePlayers().forEach(p -> loreUtil.refreshHandItemLore(p));
    }

    @Override
    public void onDisable() {
        stopWeatherTask();
        if (miningEffectL != null) HandlerList.unregisterAll(miningEffectL);
        if (miningDelayL != null) HandlerList.unregisterAll(miningDelayL);
        miningEffectL = null;
        miningDelayL = null;
        miningAnim = null;

        if (miningDelayL != null) {
            miningDelayL.shutdown();
        }

        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_AQUA + "[DurabilityPlus] " + ChatColor.GRAY + "disabled.");
    }

    public DurabilityService getService() { return service; }
    public LoreUtil getLoreUtil() { return loreUtil; }
    public DegradationConfig getDegradationConfig() { return dcfg; }


    public void reloadAll() {
        reloadConfig();

        if (service == null) {
            service = new DurabilityService(this, new MaterialMatcher(getConfig()));
        } else {
            service.setMatcher(new MaterialMatcher(getConfig()));
        }

        service.getMatcher().clearCache();

        dcfg = new DegradationConfig(this);

        stopWeatherTask();
        startWeatherTask();

        applyMiningModeListener();
        normalizeBrokenFlagsAfterReload();
    }

    private boolean isProtocolLibPresent() {
        Plugin pl = getServer().getPluginManager().getPlugin("ProtocolLib");
        return pl != null && pl.isEnabled();
    }

    private void startWeatherTask() {
        if (!getConfig().getBoolean("weatherWear.enabled", false)) return;
        weatherTask = new WeatherWearTask(this, loreUtil);
        weatherTask.start();
    }

    private void stopWeatherTask() {
        if (weatherTask != null) {
            weatherTask.stop();
            weatherTask = null;
        }
    }

    private void printStartupBanner() {
        var cfg = getConfig();
        String v = getDescription().getVersion();
        String title = "DurabilityPlus v" + v + " — loaded";

        boolean deg = cfg.getBoolean("degradation.enabled", false);
        boolean ww = cfg.getBoolean("weatherWear.enabled", false);
        boolean ping = cfg.getBoolean("lowDurabilityPing.enabled", true);
        boolean salv = cfg.getBoolean("salvage.enabled", false);

        String features = String.join(" • ",
                        "Multipliers",
                        (deg ? "Degradation" : null),
                        (salv ? "Salvage" : null),
                        (ww ? "WeatherWear" : null),
                        (ping ? "Ping" : null)
                ).replaceAll("(?:^\\s*•\\s*|\\s*•\\s*$)", "")
                .replaceAll("\\s*•\\s*•\\s*", " • ");

        String[] lines = new String[]{"", title, features, ""};
        int width = 0;
        for (String s : lines) width = Math.max(width, s.length());
        int inner = width + 2;
        String top = "┌" + "─".repeat(inner) + "┐";
        String bottom = "└" + "─".repeat(inner) + "┘";
        getLogger().info(top);
        for (String s : lines) {
            String pad = " ".repeat(width - s.length());
            getLogger().info("│ " + s + pad + " │");
        }
        getLogger().info(bottom);
    }

    private void normalizeBrokenFlagsAfterReload() {
        boolean autoProtect = getConfig().getBoolean("autoProtect.enabled", true);
        if (autoProtect) return;
        Bukkit.getOnlinePlayers().forEach(p -> {
            var inv = p.getInventory();
            normalizeItem(inv.getItemInMainHand());
            normalizeItem(inv.getItemInOffHand());
            for (var it : inv.getArmorContents()) normalizeItem(it);
            for (var it : inv.getContents()) normalizeItem(it);
        });
    }

    private void normalizeItem(org.bukkit.inventory.ItemStack item) {
        if (item == null) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        try {
            var pdc = meta.getPersistentDataContainer();
            if (pdc.has(PdcKeys.BROKEN, org.bukkit.persistence.PersistentDataType.BYTE)) {
                pdc.remove(PdcKeys.BROKEN);
                item.setItemMeta(meta);
                getLoreUtil().updateLore(item);
            }
        } catch (Throwable ignored) {}
    }
    
    private void applyMiningModeListener() {
        // remove any previous mining listeners
        if (miningEffectL != null) { HandlerList.unregisterAll(miningEffectL); miningEffectL = null; }
        if (miningDelayL != null) { HandlerList.unregisterAll(miningDelayL); miningDelayL = null; }
        miningAnim = null;

        if (!dcfg.isEnabled() || !dcfg.miningEnabled()) return;

        if (dcfg.miningModeEffect()) {
            miningEffectL = new MiningEffectListener(this, dcfg);
            Bukkit.getPluginManager().registerEvents(miningEffectL, this);
        } else {
            if (dcfg.syncAnimation() && isProtocolLibPresent()) {
                miningAnim = new MiningAnimationHelper(this);
                getLogger().info("[DurabilityPlus] ProtocolLib detected — mining crack animation enabled.");
            } else if (dcfg.syncAnimation()) {
                getLogger().info("[DurabilityPlus] syncAnimation=true but ProtocolLib not found; continuing without animation.");
            }
            miningDelayL = new MiningDelayListener(this, dcfg, miningAnim);
            Bukkit.getPluginManager().registerEvents(miningDelayL, this);
        }
    }
}