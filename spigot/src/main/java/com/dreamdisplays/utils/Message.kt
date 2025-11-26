package com.dreamdisplays.utils

import com.dreamdisplays.Main
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.jspecify.annotations.NullMarked

/**
 * Sends messages to command senders with color codes translated.
 */
@NullMarked
object Message {
    // Sends a single colored message to the player
    fun sendColoredMessage(player: CommandSender?, message: String?) {
        if (player == null || message == null) return
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
    }

    // Sends multiple colored messages to the player
    fun sendColoredMessages(player: CommandSender?, messages: List<String?>?) {
        if (player == null || messages == null) return
        messages.forEach { message ->
            if (message != null) {
                sendColoredMessage(player, message)
            }
        }
    }

    // Sends a message from the config to the player
    fun sendMessage(player: CommandSender?, messageKey: String) {
        val message = Main.config.messages[messageKey] as? String
        sendColoredMessage(player, message)
    }

    // Sends multiple messages from the config to the player
    fun getMessages(messageKey: String): List<String>? {
        @Suppress("UNCHECKED_CAST")
        return Main.config.messages[messageKey] as? List<String>
    }
}
