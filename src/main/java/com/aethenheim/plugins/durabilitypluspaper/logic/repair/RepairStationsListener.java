package com.aethenheim.plugins.durabilitypluspaper.logic.repair;

import com.aethenheim.plugins.durabilitypluspaper.DurabilityPlusPlugin;
import com.aethenheim.plugins.durabilitypluspaper.logic.util.PdcKeys;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class RepairStationsListener implements Listener {

    private final DurabilityPlusPlugin plugin;

    public RepairStationsListener(DurabilityPlusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        if (!isAutoProtectEnabled()) return;

        AnvilInventory inv = e.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack result = e.getResult();

        ItemStack fixed = maybeClearBroken(left, result);
        if (fixed != result) e.setResult(fixed);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent e) {
        if (!isAutoProtectEnabled()) return;

        GrindstoneInventory inv = e.getInventory();
        ItemStack top = inv.getItem(0);
        ItemStack bottom = inv.getItem(1);
        ItemStack result = e.getResult();

        ItemStack fixed = maybeClearBroken(top, result);
        fixed = maybeClearBroken(bottom, fixed);

        if (fixed != result) e.setResult(fixed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTakeResult(InventoryClickEvent e) {
        if (!isAutoProtectEnabled()) return;

        Inventory inv = e.getInventory();

        // Result slot is 2 for both Anvil and Grindstone top inventories.
        if (e.getRawSlot() != 2) return;

        if (inv instanceof AnvilInventory anvil) {
            ItemStack left = anvil.getItem(0);
            ItemStack current = e.getCurrentItem();

            ItemStack fixed = maybeClearBroken(left, current);
            if (fixed != current) e.setCurrentItem(fixed);
            return;
        }

        if (inv instanceof GrindstoneInventory grind) {
            ItemStack top = grind.getItem(0);
            ItemStack bottom = grind.getItem(1);
            ItemStack current = e.getCurrentItem();

            ItemStack fixed = maybeClearBroken(top, current);
            fixed = maybeClearBroken(bottom, fixed);

            if (fixed != current) e.setCurrentItem(fixed);
        }
    }

    private boolean isAutoProtectEnabled() {
        return plugin.getConfig().getBoolean("autoProtect.enabled", true);
    }

    private ItemStack maybeClearBroken(ItemStack input, ItemStack output) {
        if (input == null || output == null) return output;
        if (input.getType().isAir() || output.getType().isAir()) return output;

        ItemMeta inMeta = input.getItemMeta();
        ItemMeta outMeta = output.getItemMeta();
        if (!(inMeta instanceof Damageable)) return output;
        if (!(outMeta instanceof Damageable outDmg)) return output;

        boolean inputBroken;
        try {
            inputBroken = inMeta.getPersistentDataContainer().has(PdcKeys.BROKEN, PersistentDataType.BYTE);
        } catch (Throwable ignored) {
            return output;
        }
        if (!inputBroken) return output;

        int max = output.getType().getMaxDurability();
        if (max <= 0) return output;

        // Plugin-defined "broken edge" is max - 1
        if (outDmg.getDamage() >= max - 1) return output;

        ItemStack out = output.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta == null) return output;

        meta.setUnbreakable(false);
        try { meta.getPersistentDataContainer().remove(PdcKeys.BROKEN); } catch (Throwable ignored) {}

        out.setItemMeta(meta);
        return out;
    }
}

