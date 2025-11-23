package com.dreamdisplays.listeners;

import com.dreamdisplays.managers.PlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        PlayerManager.removeVersion(event.getPlayer());
    }
}
