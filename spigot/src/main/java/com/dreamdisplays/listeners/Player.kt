package com.dreamdisplays.listeners

import com.dreamdisplays.managers.PlayerManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.jspecify.annotations.NullMarked

/**
 * Listener for player quit events to manage player data.
 */
@NullMarked
class Player : Listener {
    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        PlayerManager.removeVersion(event.getPlayer())
    }
}
