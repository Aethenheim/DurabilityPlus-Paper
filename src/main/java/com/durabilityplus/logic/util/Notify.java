package com.durabilityplus.logic.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public final class Notify {
    private Notify() {}

    public static void actionBar(Player p, String msg) {
        if (p == null || msg == null || msg.isEmpty()) return;
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    public static void chat(Player p, String msg) {
        if (p == null || msg == null || msg.isEmpty()) return;
        p.sendMessage(msg);
    }
}
