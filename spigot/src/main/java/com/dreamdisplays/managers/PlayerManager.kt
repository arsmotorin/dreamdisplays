package com.dreamdisplays.managers

import com.github.zafarkhaja.semver.Version
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Manages player-related data such as versions and update notifications.
 */
@NullMarked
object PlayerManager {
    private val versions: MutableMap<UUID?, Version?> = HashMap<UUID?, Version?>()
    private val modUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap<UUID?, Boolean?>()
    private val pluginUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap<UUID?, Boolean?>()

    // Set version
    @JvmStatic
    fun setVersion(player: Player, version: Version?) {
        versions[player.uniqueId] = version
    }

    // Get version
    fun removeVersion(player: Player) {
        versions.remove(player.uniqueId)
        modUpdateNotified.remove(player.uniqueId)
        pluginUpdateNotified.remove(player.uniqueId)
    }

    // Update notification status
    @JvmStatic
    fun hasBeenNotifiedAboutModUpdate(player: Player): Boolean {
        return modUpdateNotified[player.uniqueId] ?: false
    }

    // Set mod update notified
    @JvmStatic
    fun setModUpdateNotified(player: Player, notified: Boolean) {
        modUpdateNotified[player.uniqueId] = notified
    }

    // Check plugin update notified
    @JvmStatic
    fun hasBeenNotifiedAboutPluginUpdate(player: Player): Boolean {
        return pluginUpdateNotified[player.uniqueId] ?: false
    }

    // Set plugin update notified
    @JvmStatic
    fun setPluginUpdateNotified(player: Player, notified: Boolean) {
        pluginUpdateNotified[player.uniqueId] = notified
    }
}
