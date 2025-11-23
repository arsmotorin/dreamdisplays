package com.dreamdisplays.listeners

import com.dreamdisplays.managers.PlayerManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class PlayerListener : Listener {
    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        PlayerManager.removeVersion(event.getPlayer())
    }
}
