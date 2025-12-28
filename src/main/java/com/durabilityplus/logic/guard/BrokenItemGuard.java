package com.durabilityplus.logic.guard;

import com.durabilityplus.DurabilityPlusPlugin;
import com.durabilityplus.logic.degrade.ItemUtil;
import com.durabilityplus.logic.util.Notify;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BrokenItemGuard implements Listener {
    private final DurabilityPlusPlugin plugin;
    private final Map<UUID, Long> notifyCd = new ConcurrentHashMap<>();

    public BrokenItemGuard(DurabilityPlusPlugin plugin) { this.plugin = plugin; }

    private boolean shouldCancel(ItemStack item) {
        return item != null && ItemUtil.isBroken(item);
    }

    private void maybeNotify(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("autoProtect.notifyOnUse", true)) return;

        long now = System.currentTimeMillis();
        long cdMs = 1500; // small anti-spam cooldown
        Long last = notifyCd.get(p.getUniqueId());
        if (last != null && now - last < cdMs) return;
        notifyCd.put(p.getUniqueId(), now);

        String mode = cfg.getString("autoProtect.notifyMode", "chat").toLowerCase();
        String msg = "§eItem is broken — repair it to use";
        if ("actionbar".equals(mode)) {
            // strip color for actionbar for readability
            Notify.actionBar(p, "Item is broken — repair to use");
        } else {
            Notify.chat(p, msg);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (shouldCancel(e.getItem())) { e.setCancelled(true); maybeNotify(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (shouldCancel(e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true); maybeNotify(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (shouldCancel(p.getInventory().getItemInMainHand())) { e.setCancelled(true); maybeNotify(p); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (shouldCancel(e.getBow())) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player p) maybeNotify(p);
        }
    }
}
