package com.dreamdisplays.listeners

import com.dreamdisplays.managers.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class Player : Listener {
    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        Player.removeVersion(event.getPlayer())
    }
}
