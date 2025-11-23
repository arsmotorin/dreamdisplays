package com.dreamdisplays.managers

import com.github.zafarkhaja.semver.Version
import org.bukkit.entity.Player
import java.util.*

object Player {
    private val versions: MutableMap<UUID?, Version?> = HashMap<UUID?, Version?>()
    private val modUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap<UUID?, Boolean?>()
    private val pluginUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap<UUID?, Boolean?>()

    fun getVersion(player: Player): Version? {
        return versions.get(player.getUniqueId())
    }

    @JvmStatic
    fun setVersion(player: Player, version: Version?) {
        versions.put(player.getUniqueId(), version)
    }

    fun removeVersion(player: Player) {
        versions.remove(player.getUniqueId())
        modUpdateNotified.remove(player.getUniqueId())
        pluginUpdateNotified.remove(player.getUniqueId())
    }

    fun getVersions(): MutableCollection<Version?> {
        return versions.values
    }

    @JvmStatic
    fun hasBeenNotifiedAboutModUpdate(player: Player): Boolean {
        return modUpdateNotified.getOrDefault(player.getUniqueId(), false)!!
    }

    @JvmStatic
    fun setModUpdateNotified(player: Player, notified: Boolean) {
        modUpdateNotified.put(player.getUniqueId(), notified)
    }

    @JvmStatic
    fun hasBeenNotifiedAboutPluginUpdate(player: Player): Boolean {
        return pluginUpdateNotified.getOrDefault(player.getUniqueId(), false)!!
    }

    @JvmStatic
    fun setPluginUpdateNotified(player: Player, notified: Boolean) {
        pluginUpdateNotified.put(player.getUniqueId(), notified)
    }
}
