package com.dreamdisplays.utils

import com.dreamdisplays.Main
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.jspecify.annotations.NullMarked

@NullMarked
object Message {
    fun sendColoredMessage(player: CommandSender?, message: String?) {
        if (player == null || message == null) return
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
    }

    fun sendColoredMessages(player: CommandSender?, messages: List<String?>?) {
        if (player == null || messages == null) return
        messages.forEach { message ->
            if (message != null) {
                sendColoredMessage(player, message)
            }
        }
    }

    fun sendMessage(player: CommandSender?, messageKey: String) {
        val message = Main.config.messages[messageKey] as? String
        sendColoredMessage(player, message)
    }

    fun getMessages(messageKey: String): List<String>? {
        @Suppress("UNCHECKED_CAST")
        return Main.config.messages[messageKey] as? List<String>
    }
}
