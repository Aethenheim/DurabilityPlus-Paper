package com.aethenheim.plugins.durabilitypluspaper.logic.degrade;

import com.aethenheim.plugins.durabilitypluspaper.DurabilityPlusPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

public class WeaponDamageListener implements Listener {

    private final DurabilityPlusPlugin plugin;
    private final DegradationConfig cfg;

    public WeaponDamageListener(DurabilityPlusPlugin plugin, DegradationConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!ItemUtil.isDamageable(hand) || ItemUtil.isUnbreakable(hand)) return;

        double pct = ItemUtil.remainingPercent(hand);
        double factor = cfg.weaponFactorFor(hand.getType(), pct);
        if (factor != 1.0) e.setDamage(e.getDamage() * factor);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack bow = e.getBow();
        if (!ItemUtil.isDamageable(bow) || ItemUtil.isUnbreakable(bow)) return;

        double pct = ItemUtil.remainingPercent(bow);
        double factor = cfg.weaponFactorFor(bow.getType(), pct);

        if (e.getProjectile() instanceof Projectile proj) {
            ProjectileUtil.setFactor(plugin, proj, factor);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Projectile proj)) return;
        Double factor = ProjectileUtil.getFactor(plugin, proj);
        if (factor == null || factor == 1.0) return;
        e.setDamage(e.getDamage() * factor);
    }
}
