package com.aethenheim.plugins.durabilitypluspaper.cmd;

import com.aethenheim.plugins.durabilitypluspaper.DurabilityPlusPlugin;
import com.aethenheim.plugins.durabilitypluspaper.logic.DurabilityService;
import com.aethenheim.plugins.durabilitypluspaper.logic.util.PdcKeys;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * DurabilityPlus main command executor.
 * Subcommands:
 * - setmultiplier <value>
 * - add <amount>
 * - take <amount>
 * - set <remaining>
 * - unbreakable <true|false>
 * - toggleunbreakable
 * - ping <on|off|toggle>
 * - repair
 * - repairall
 * - autoprotection <on|off|toggle>
 * - reload
 *
 * Permissions:
 * - durabilityplus.use (base command registration)
 * - durabilityplus.modify (modify item durability / item state)
 * - durabilityplus.edit (edit global config / server-wide toggles)
 */
public class DurabilityCommands implements CommandExecutor {

    private final DurabilityPlusPlugin plugin;
    private final DurabilityService service;

    public DurabilityCommands(DurabilityPlusPlugin plugin, DurabilityService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // Determine subcommand (empty string if none)
        final String sub = (args.length > 0) ? args[0].toLowerCase() : "";

        switch (sub) {

            // ------------------------------------------------------------
            // /dp setmultiplier <value>
            // ------------------------------------------------------------
            case "setmultiplier": {

                if (!sender.hasPermission("durabilityplus.edit")) {
                    sender.sendMessage("§cNo permission: durabilityplus.edit");
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " setmultiplier <value>");
                    return true;
                }

                double v;
                try {
                    v = Double.parseDouble(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cInvalid number: " + args[1]);
                    return true;
                }

                plugin.getConfig().set("globalMultiplier", v);
                plugin.saveConfig();
                plugin.reloadConfig();

                sender.sendMessage("§aGlobal multiplier set to §6" + v);
                return true;
            }

            // ------------------------------------------------------------
            // /dp add <amount>
            // ------------------------------------------------------------
            case "add": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.modify")) {
                    sender.sendMessage("§cNo permission: durabilityplus.modify");
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " add <amount>");
                    return true;
                }

                int amt;
                try {
                    amt = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cInvalid number: " + args[1]);
                    return true;
                }

                boolean ok = service.addDurability(p, amt);

                if (ok) {
                    sender.sendMessage("§aAdded §6" + amt + "§a durability to item in hand.");
                } else {
                    sender.sendMessage("§cUnable to modify this item.");
                }

                return true;
            }

            // ------------------------------------------------------------
            // /dp take <amount>
            // ------------------------------------------------------------
            case "take": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.modify")) {
                    sender.sendMessage("§cNo permission: durabilityplus.modify");
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " take <amount>");
                    return true;
                }

                int amt;
                try {
                    amt = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cInvalid number: " + args[1]);
                    return true;
                }

                boolean ok = service.takeDurability(p, amt);

                if (ok) {
                    sender.sendMessage("§aTook §6" + amt + "§a durability from item in hand.");
                } else {
                    sender.sendMessage("§cUnable to modify this item.");
                }

                return true;
            }

            // ------------------------------------------------------------
            // /dp set <remaining>
            // ------------------------------------------------------------
            case "set": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.modify")) {
                    sender.sendMessage("§cNo permission: durabilityplus.modify");
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " set <remaining>");
                    return true;
                }

                int rem;
                try {
                    rem = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cInvalid number: " + args[1]);
                    return true;
                }

                boolean ok = service.setRemaining(p, rem);

                if (ok) {
                    sender.sendMessage("§aSet item remaining durability to §6" + rem + "§a.");
                } else {
                    sender.sendMessage("§cUnable to modify this item.");
                }

                return true;
            }

            // ------------------------------------------------------------
            // /dp unbreakable <true|false>
            // ------------------------------------------------------------
            case "unbreakable": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.modify")) {
                    sender.sendMessage("§cNo permission: durabilityplus.modify");
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " unbreakable <true|false>");
                    return true;
                }

                boolean makeUnbreakable = Boolean.parseBoolean(args[1]);

                ItemStack item = p.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    sender.sendMessage("§cHold a damageable item.");
                    return true;
                }

                ItemMeta meta = item.getItemMeta();
                if (!(meta instanceof Damageable)) {
                    sender.sendMessage("§cThat item cannot be unbreakable.");
                    return true;
                }

                meta.setUnbreakable(makeUnbreakable);
                item.setItemMeta(meta);

                plugin.getLoreUtil().updateLore(item);

                sender.sendMessage("§aUnbreakable set to §6" + makeUnbreakable + "§a for the held item.");
                return true;
            }

            // ------------------------------------------------------------
            // /dp toggleunbreakable
            // ------------------------------------------------------------
            case "toggleunbreakable": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.modify")) {
                    sender.sendMessage("§cNo permission: durabilityplus.modify");
                    return true;
                }

                ItemStack item = p.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    sender.sendMessage("§cHold a damageable item.");
                    return true;
                }

                ItemMeta meta = item.getItemMeta();
                if (!(meta instanceof Damageable)) {
                    sender.sendMessage("§cThat item cannot be unbreakable.");
                    return true;
                }

                meta.setUnbreakable(!meta.isUnbreakable());
                item.setItemMeta(meta);

                plugin.getLoreUtil().updateLore(item);

                sender.sendMessage("§aToggled Unbreakable on the item in your hand.");
                return true;
            }

            // ------------------------------------------------------------
            // /dp ping <on|off|toggle>
            // (per-player low-durability sound toggle)
            // ------------------------------------------------------------
            case "ping": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.ping")) {
                    sender.sendMessage("§cNo permission: durabilityplus.ping");
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " ping <on|off|toggle>");
                    return true;
                }

                Byte cur = p.getPersistentDataContainer().get(PdcKeys.PING_DISABLED, PersistentDataType.BYTE);
                boolean disabled = (cur != null && cur == (byte) 1);

                String mode = args[1].toLowerCase();

                if (mode.equals("on")) {
                    p.getPersistentDataContainer().remove(PdcKeys.PING_DISABLED);
                    p.sendMessage("§aLow-durability sound ping §2enabled§a.");
                    return true;
                }

                if (mode.equals("off")) {
                    p.getPersistentDataContainer().set(PdcKeys.PING_DISABLED, PersistentDataType.BYTE, (byte) 1);
                    p.sendMessage("§aLow-durability sound ping §cdisabled§a.");
                    return true;
                }

                if (mode.equals("toggle")) {
                    if (disabled) {
                        p.getPersistentDataContainer().remove(PdcKeys.PING_DISABLED);
                        p.sendMessage("§aLow-durability sound ping §2enabled§a.");
                    } else {
                        p.getPersistentDataContainer().set(PdcKeys.PING_DISABLED, PersistentDataType.BYTE, (byte) 1);
                        p.sendMessage("§aLow-durability sound ping §cdisabled§a.");
                    }
                    return true;
                }

                sender.sendMessage("§eUsage: /" + label + " ping <on|off|toggle>");
                return true;
            }

            // ------------------------------------------------------------
            // /dp repair (repair held item to 100%)
            // ------------------------------------------------------------
            case "repair": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.modify")) {
                    sender.sendMessage("§cNo permission: durabilityplus.modify");
                    return true;
                }

                boolean ok = service.repairCurrent(p);

                if (ok) {
                    sender.sendMessage("§aRepaired the item in your hand to §6full§a.");
                } else {
                    sender.sendMessage("§cHold a damageable item.");
                }

                return true;
            }

            // ------------------------------------------------------------
            // /dp repairall (repair all items in inventory/armor/offhand)
            // ------------------------------------------------------------
            case "repairall": {

                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }

                Player p = (Player) sender;

                if (!sender.hasPermission("durabilityplus.modify")) {
                    sender.sendMessage("§cNo permission: durabilityplus.modify");
                    return true;
                }

                int count = service.repairAll(p);
                sender.sendMessage("§aRepaired §6" + count + "§a items.");
                return true;
            }

            // ------------------------------------------------------------
            // /dp autoprotection <on|off|toggle>
            // (server-wide toggle: keep items from breaking)
            // ------------------------------------------------------------
            case "autoprotection": {

                if (!sender.hasPermission("durabilityplus.edit")) {
                    sender.sendMessage("§cNo permission: durabilityplus.edit");
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " autoprotection <on|off|toggle>");
                    return true;
                }

                boolean current = plugin.getConfig().getBoolean("autoProtect.enabled", true);
                String mode = args[1].toLowerCase();

                boolean next;
                if (mode.equals("on")) {
                    next = true;
                } else if (mode.equals("off")) {
                    next = false;
                } else if (mode.equals("toggle")) {
                    next = !current;
                } else {
                    sender.sendMessage("§eUsage: /" + label + " autoprotection <on|off|toggle>");
                    return true;
                }

                plugin.getConfig().set("autoProtect.enabled", next);
                plugin.saveConfig();

                sender.sendMessage("§aAuto-Protect is now §6" + (next ? "ON" : "OFF") + "§a.");
                return true;
            }

            // ------------------------------------------------------------
            // /dp reload
            // ------------------------------------------------------------
            case "reload": {
                if (!(sender.hasPermission("durabilityplus.edit"))) {
                    sender.sendMessage("§cNo permission: durabilityplus.edit");
                    return true;
                }

                plugin.reloadAll(); // <-- use the centralized reload now
                sender.sendMessage("§aDurabilityPlus config reloaded.");
                return true;
            }

            default: {
                sender.sendMessage("§6DurabilityPlus Commands:");
                sender.sendMessage("§e/" + label + " setmultiplier <value> §7— set global multiplier");
                sender.sendMessage("§e/" + label + " add <amount> §7— add durability to item in hand");
                sender.sendMessage("§e/" + label + " take <amount> §7— take durability from item in hand");
                sender.sendMessage("§e/" + label + " set <remaining> §7— set exact durability on item in hand");
                sender.sendMessage("§e/" + label + " unbreakable <true|false> §7— set item unbreakable");
                sender.sendMessage("§e/" + label + " toggleunbreakable §7— flip item unbreakable flag");
                sender.sendMessage("§e/" + label + " ping <on|off|toggle> §7— per-player low-durability sound");
                sender.sendMessage("§e/" + label + " repair §7— repair held item to 100%");
                sender.sendMessage("§e/" + label + " repairall §7— repair all items (inv + armor + offhand)");
            }
        } // end switch

        return true; // always return a boolean from onCommand
    }
}