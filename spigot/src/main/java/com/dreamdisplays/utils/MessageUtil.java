package com.dreamdisplays.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class MessageUtil {
    public static void sendColoredMessage(CommandSender player, String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void sendColoredMessages(CommandSender player, List<String> messages) {
        messages.forEach(s -> sendColoredMessage(player, s));
    }
}
