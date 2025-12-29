package com.aethenheim.plugins.durabilitypluspaper.logic.mending;

import com.aethenheim.plugins.durabilitypluspaper.DurabilityPlusPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemMendEvent;

public class MendingRebalanceListener implements Listener {
    private final DurabilityPlusPlugin plugin;
    public MendingRebalanceListener(DurabilityPlusPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent e) {
        var cfg = plugin.getConfig();

        if (!cfg.getBoolean("elytra.repairable", true) && e.getItem().getType() == Material.ELYTRA) {
            e.setCancelled(true);
            return;
        }

        double factor = Math.max(0.0, cfg.getDouble("mending.factor", 1.0));
        int amount = (int) Math.floor(e.getRepairAmount() * factor);
        e.setRepairAmount(amount);
    }
}
