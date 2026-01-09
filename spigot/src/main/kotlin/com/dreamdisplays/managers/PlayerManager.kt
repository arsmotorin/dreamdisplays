package com.dreamdisplays.managers

import com.github.zafarkhaja.semver.Version
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Manages player-specific data such as versions and notification states.
 */
@NullMarked
object PlayerManager {
    private val versions: MutableMap<UUID?, Version?> = HashMap()
    private val modUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap()
    private val pluginUpdateNotified: MutableMap<UUID?, Boolean?> = HashMap()
    private val modRequiredNotified: MutableMap<UUID?, Boolean?> = HashMap()
    private val displaysEnabled: MutableMap<UUID, Boolean> = HashMap()

    fun setVersion(player: Player, version: Version?) {
        versions[player.uniqueId] = version
    }

    fun removeVersion(player: Player) {
        versions.remove(player.uniqueId)
        modUpdateNotified.remove(player.uniqueId)
        pluginUpdateNotified.remove(player.uniqueId)
        modRequiredNotified.remove(player.uniqueId)
        displaysEnabled.remove(player.uniqueId)
    }

    fun getVersion(player: Player): Version? {
        return versions[player.uniqueId]
    }

    fun hasBeenNotifiedAboutModUpdate(player: Player): Boolean {
        return modUpdateNotified[player.uniqueId] ?: false
    }

    fun setModUpdateNotified(player: Player, notified: Boolean) {
        modUpdateNotified[player.uniqueId] = notified
    }

    fun hasBeenNotifiedAboutPluginUpdate(player: Player): Boolean {
        return pluginUpdateNotified[player.uniqueId] ?: false
    }

    fun setPluginUpdateNotified(player: Player, notified: Boolean) {
        pluginUpdateNotified[player.uniqueId] = notified
    }

    fun hasBeenNotifiedAboutModRequired(player: Player): Boolean {
        return modRequiredNotified[player.uniqueId] ?: false
    }

    fun setModRequiredNotified(player: Player, notified: Boolean) {
        modRequiredNotified[player.uniqueId] = notified
    }

    fun setDisplaysEnabled(player: Player, enabled: Boolean) {
        displaysEnabled[player.uniqueId] = enabled
    }

    fun isDisplaysEnabled(player: Player): Boolean {
        return displaysEnabled.getOrDefault(player.uniqueId, true)
    }

    fun getVersions(): Map<UUID?, Version?> {
        return versions
    }
}
