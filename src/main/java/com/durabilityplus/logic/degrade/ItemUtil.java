package com.durabilityplus.logic.degrade;

import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import com.durabilityplus.logic.util.PdcKeys;

public final class ItemUtil {
    private ItemUtil() {}

    public static boolean isDamageable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType().getMaxDurability() <= 0) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable;
    }

    public static boolean isUnbreakable(ItemStack item) {
        ItemMeta meta = item != null ? item.getItemMeta() : null;
        return meta instanceof Damageable d && d.isUnbreakable();
    }

    public static double remainingPercent(ItemStack item) {
        if (!isDamageable(item)) return 100.0;
        ItemMeta meta = item.getItemMeta();
        int max = item.getType().getMaxDurability();
        int damage = ((Damageable) meta).getDamage();
        if (max <= 0) return 100.0;
        int rem = Math.max(0, max - damage);
        return (rem * 100.0) / max;
    }
    
    public static boolean isBroken(ItemStack item) {
  if (!(item != null && item.hasItemMeta())) return false;
  return item.getItemMeta().getPersistentDataContainer().has(PdcKeys.BROKEN, PersistentDataType.BYTE);
}

}
