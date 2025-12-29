package com.aethenheim.plugins.durabilitypluspaper.logic.degrade;

import com.aethenheim.plugins.durabilitypluspaper.DurabilityPlusPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public class ArmorProtectionListener implements Listener {

    private final DurabilityPlusPlugin plugin;
    private final DegradationConfig dcfg;

    public ArmorProtectionListener(DurabilityPlusPlugin plugin, DegradationConfig dcfg) {
        this.plugin = plugin;
        this.dcfg = dcfg;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityEquipment eq = p.getEquipment();
        if (eq == null) return;

        double sumFactor = 0.0;
        int count = 0;

        for (ItemStack piece : new ItemStack[]{eq.getHelmet(), eq.getChestplate(), eq.getLeggings(), eq.getBoots()}) {
            if (!ItemUtil.isDamageable(piece)) continue;
            if (ItemUtil.isUnbreakable(piece)) continue;

            int percent = Math.max(0, Math.min(100, (int) Math.round(ItemUtil.remainingPercent(piece))));
            double factor = dcfg.armorFactorFor(piece.getType(), percent);
            sumFactor += factor;
            count++;
        }
        if (count == 0) return;

        double finalFactor = sumFactor / count; // average of per-piece factors
        if (finalFactor != 1.0) e.setDamage(e.getDamage() * finalFactor);
    }
}
