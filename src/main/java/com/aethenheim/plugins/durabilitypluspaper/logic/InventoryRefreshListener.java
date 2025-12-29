package com.aethenheim.plugins.durabilitypluspaper.logic;

import com.aethenheim.plugins.durabilitypluspaper.DurabilityPlusPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public final class InventoryRefreshListener implements Listener {
    private final DurabilityPlusPlugin plugin;
    private final LoreUtil lore;

    public InventoryRefreshListener(DurabilityPlusPlugin plugin, LoreUtil lore) {
        this.plugin = plugin;
        this.lore = lore;
    }

    /* ------------ helpers ------------ */

    private void refreshItem(ItemStack s) {
        if (s == null || s.getType().isAir()) return;
        lore.updateLore(s);
    }

    private void refreshAll(Player p) {
        var inv = p.getInventory();
        for (ItemStack s : inv.getContents()) refreshItem(s);
        for (ItemStack s : inv.getArmorContents()) refreshItem(s);
        refreshItem(inv.getItemInOffHand());
        refreshItem(inv.getItemInMainHand());
    }

    /* ------------ events ------------ */

    // After join/respawn, inventory is populated on next tick
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshAll(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshAll(e.getPlayer()));
    }

    // When player switches hotbar slot
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var p = e.getPlayer();
            refreshItem(p.getInventory().getItemInMainHand());
        });
    }

    // Craft/move/equip via inventory clicks (covers shift-click armor equip too)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshAll(p));
    }

    // After a pickup lands in the inventory
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshAll(p));
    }
}