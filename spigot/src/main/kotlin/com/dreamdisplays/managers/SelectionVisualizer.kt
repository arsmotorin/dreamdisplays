package com.dreamdisplays.managers

import com.dreamdisplays.Main
import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.managers.SelectionManager.selectionPoints
import com.dreamdisplays.utils.Outliner.showOutline
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable

/**
 * Manages the visualization of selection areas using particles. Currently not available for Folia.
 */
object SelectionVisualizer {
    fun startParticleTask(plugin: Main) {
        if (!config.settings.particlesEnabled) return
        if (Bukkit.getServer().name.contains("Folia", ignoreCase = true)) return

        object : BukkitRunnable() {
            override fun run() {
                selectionPoints.values.forEach { it.drawBox() }
                selectionPoints.forEach { (playerId, sel) ->
                    Bukkit.getPlayer(playerId)?.let { player ->
                        if (sel.isReady && sel.pos1 != null && sel.pos2 != null)
                            showOutline(player, sel.pos1!!, sel.pos2!!)
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, config.settings.particleRenderDelay.toLong())
    }
}
