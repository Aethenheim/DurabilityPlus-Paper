package com.durabilityplus.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DurabilityTabCompleter implements TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "setmultiplier", "add", "take", "set",
            "unbreakable", "toggleunbreakable",
            "ping", "repair", "repairall",
            "autoprotection", "reload"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String start = args[0].toLowerCase();
            return SUBS.stream().filter(s -> s.startsWith(start)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "setmultiplier": return List.of("0.5", "1.0", "2.0");
                case "add":
                case "take":
                case "set": return List.of("1", "10", "100");
                case "unbreakable": return List.of("true", "false");
                case "ping": return List.of("on", "off", "toggle");
                case "autoprotection": return List.of("on", "off", "toggle");
            }
        }
        return List.of();
    }
}
