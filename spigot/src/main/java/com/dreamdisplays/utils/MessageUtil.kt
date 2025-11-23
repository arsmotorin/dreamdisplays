package com.dreamdisplays.utils

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

object MessageUtil {
    fun sendColoredMessage(player: CommandSender?, message: String?) {
        if (player == null || message == null) return
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
    }

    fun sendColoredMessages(player: CommandSender?, messages: MutableList<String?>?) {
        if (player == null || messages == null) return
        messages.forEach { message ->
            if (message != null) {
                sendColoredMessage(player, message)
            }
        }
    }
}
