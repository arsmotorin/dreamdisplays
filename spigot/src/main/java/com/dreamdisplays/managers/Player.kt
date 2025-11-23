package com.dreamdisplays.managers

import com.github.zafarkhaja.semver.Version
import org.bukkit.entity.Player
import java.util.*

object Player {
    private val versions: MutableMap<UUID?, Version?> = HashMap<UUID?, Version?>()
    private val modUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap<UUID?, Boolean?>()
    private val pluginUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap<UUID?, Boolean?>()

    @JvmStatic
    fun setVersion(player: Player, version: Version?) {
        versions[player.uniqueId] = version
    }

    fun removeVersion(player: Player) {
        versions.remove(player.uniqueId)
        modUpdateNotified.remove(player.uniqueId)
        pluginUpdateNotified.remove(player.uniqueId)
    }

    @JvmStatic
    fun hasBeenNotifiedAboutModUpdate(player: Player): Boolean {
        return modUpdateNotified.getOrDefault(player.uniqueId, false)!!
    }

    @JvmStatic
    fun setModUpdateNotified(player: Player, notified: Boolean) {
        modUpdateNotified[player.uniqueId] = notified
    }

    @JvmStatic
    fun hasBeenNotifiedAboutPluginUpdate(player: Player): Boolean {
        return pluginUpdateNotified.getOrDefault(player.uniqueId, false)!!
    }

    @JvmStatic
    fun setPluginUpdateNotified(player: Player, notified: Boolean) {
        pluginUpdateNotified[player.uniqueId] = notified
    }
}
