package com.dreamdisplays.listeners

import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.Main.Companion.getInstance
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.managers.DisplayManager.getDisplays
import com.dreamdisplays.managers.PlayerManager
import com.dreamdisplays.managers.PlayerManager.hasBeenNotifiedAboutModRequired
import com.dreamdisplays.managers.PlayerManager.setModRequiredNotified
import com.dreamdisplays.utils.Message.sendColoredMessage
import com.dreamdisplays.utils.net.PacketUtils
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.jspecify.annotations.NullMarked

/**
 * Listener for player join and quit events to manage mod detection notifications.
 *
 * When a player joins, if mod detection is enabled and the player does not have the mod,
 * they will be notified after a delay. When a player leaves, their version information is removed
 * from the `PlayerManager`.
 *
 */
@NullMarked
class PlayerListener : Listener {

    private var hasValidatedWorld = false

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Validate displays
        if (!hasValidatedWorld && getDisplays().isNotEmpty()) {
            hasValidatedWorld = true
            getInstance().server.scheduler.runTaskLater(getInstance(), Runnable {
                val removedDisplayUuids = DisplayManager.validateDisplaysAndCleanup()
                if (removedDisplayUuids.isNotEmpty()) {
                    val onlinePlayers = Bukkit.getOnlinePlayers().toMutableList()
                    @Suppress("UNCHECKED_CAST")
                    PacketUtils.sendClearCache(onlinePlayers, removedDisplayUuids)
                }
            }, 40L)
        }

        if (!config.settings.modDetectionEnabled) return
        if (getDisplays().isEmpty()) return

        if (Bukkit.getServer().name.contains("Folia", ignoreCase = true)) {
            // Temporarily disable mod detection on Folia due to scheduler issues
            // TODO: implement a Folia-compatible solution
            return
        }

        getInstance().server.scheduler.runTaskLater(getInstance(), Runnable {
            if (PlayerManager.getVersion(player) == null && !hasBeenNotifiedAboutModRequired(player)) {
                val message = config.messages["modRequired"]
                sendColoredMessage(player, message)
                setModRequiredNotified(player, true)
            }
        }, 600L)
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        PlayerManager.removeVersion(event.getPlayer())
    }
}
