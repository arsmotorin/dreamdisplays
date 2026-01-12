package com.dreamdisplays.registrars

import com.dreamdisplays.Main
import com.dreamdisplays.listeners.PlayerListener
import com.dreamdisplays.listeners.ProtectionListener
import com.dreamdisplays.listeners.SelectionListener
import org.bukkit.Bukkit

/**
 * Registers event listeners.
 */
object ListenerRegistrar {
    fun registerListeners(plugin: Main) {
        val pm = Bukkit.getPluginManager()
        pm.registerEvents(SelectionListener(plugin), plugin)
        pm.registerEvents(ProtectionListener(), plugin)
        pm.registerEvents(PlayerListener(), plugin)
    }
}
