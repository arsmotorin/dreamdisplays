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
    private val versions: MutableMap<UUID, Version?> = HashMap()
    private val modUpdateNotified: MutableMap<UUID, Boolean> = HashMap()
    private val pluginUpdateNotified: MutableMap<UUID, Boolean> = HashMap()
    private val modRequiredNotified: MutableMap<UUID, Boolean> = HashMap()
    private val displaysEnabled: MutableMap<UUID, Boolean> = HashMap()

    @JvmStatic
    fun setVersion(player: Player, version: Version?) {
        versions[player.uniqueId] = version
    }

    fun removeVersion(player: Player) {
        val id = player.uniqueId
        versions.remove(id)
        modUpdateNotified.remove(id)
        pluginUpdateNotified.remove(id)
        modRequiredNotified.remove(id)
        displaysEnabled.remove(id)
    }

    @JvmStatic
    fun getVersion(player: Player): Version? {
        return versions[player.uniqueId]
    }

    @JvmStatic
    fun hasBeenNotifiedAboutModUpdate(player: Player): Boolean {
        return modUpdateNotified[player.uniqueId] ?: false
    }

    @JvmStatic
    fun setModUpdateNotified(player: Player, notified: Boolean) {
        modUpdateNotified[player.uniqueId] = notified
    }

    @JvmStatic
    fun hasBeenNotifiedAboutPluginUpdate(player: Player): Boolean {
        return pluginUpdateNotified[player.uniqueId] ?: false
    }

    @JvmStatic
    fun setPluginUpdateNotified(player: Player, notified: Boolean) {
        pluginUpdateNotified[player.uniqueId] = notified
    }

    @JvmStatic
    fun hasBeenNotifiedAboutModRequired(player: Player): Boolean {
        return modRequiredNotified[player.uniqueId] ?: false
    }

    @JvmStatic
    fun setModRequiredNotified(player: Player, notified: Boolean) {
        modRequiredNotified[player.uniqueId] = notified
    }

    @JvmStatic
    fun setDisplaysEnabled(player: Player, enabled: Boolean) {
        displaysEnabled[player.uniqueId] = enabled
    }

    @JvmStatic
    fun isDisplaysEnabled(player: Player): Boolean {
        return displaysEnabled.getOrDefault(player.uniqueId, true)
    }

    @JvmStatic
    fun getVersions(): Map<UUID, Version?> {
        return HashMap(versions)
    }
}
