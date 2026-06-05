package com.dreamdisplays.server.managers

import com.dreamdisplays.server.platform.PlatformPlayer
import com.dreamdisplays.server.platform.platformUuid
import org.jspecify.annotations.NullMarked
import org.semver4j.Semver
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks per-player state: reported mod version, notification flags, and whether
 * displays are enabled for each connected player.
 */
@NullMarked object PlayerManager {
    private val versions: MutableMap<UUID, Semver?> = ConcurrentHashMap()
    private val modUpdateNotified: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val pluginUpdateNotified: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val modRequiredNotified: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val displaysDisabled: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /** Records the mod [version] reported by [uuid] for compatibility checks. */
    fun setVersion(uuid: UUID, version: Semver?) {
        versions[uuid] = version
    }

    /** Records the mod [version] reported by [player] for compatibility checks. */
    @JvmStatic fun setVersion(player: PlatformPlayer, version: Semver?) = setVersion(player.platformUuid, version)

    /** Drops all cached per-player state on disconnect. */
    fun removePlayer(uuid: UUID) {
        versions.remove(uuid)
        modUpdateNotified.remove(uuid)
        pluginUpdateNotified.remove(uuid)
        modRequiredNotified.remove(uuid)
        displaysDisabled.remove(uuid)
    }

    /** Drops all cached per-player state on disconnect. */
    fun removeVersion(player: PlatformPlayer) = removePlayer(player.platformUuid)

    /** Returns a defensive copy of the per-player version map. */
    @JvmStatic fun getVersions(): Map<UUID, Semver?> = HashMap(versions)

    /** Returns the mod version reported by [uuid], or null if none was reported. */
    fun getVersion(uuid: UUID): Semver? = versions[uuid]

    /** Returns the mod version reported by [player], or null if none was reported. */
    @JvmStatic fun getVersion(player: PlatformPlayer): Semver? = getVersion(player.platformUuid)

    /** Returns true if [uuid] has already been informed about a mod update. */
    fun hasBeenNotifiedAboutModUpdate(uuid: UUID): Boolean = uuid in modUpdateNotified

    /** Returns true if [player] has already been informed about a mod update. */
    @JvmStatic fun hasBeenNotifiedAboutModUpdate(player: PlatformPlayer): Boolean =
        hasBeenNotifiedAboutModUpdate(player.platformUuid)

    /** Marks whether [uuid] has been notified about a mod update. */
    fun setModUpdateNotified(uuid: UUID, notified: Boolean) {
        if (notified) modUpdateNotified.add(uuid) else modUpdateNotified.remove(uuid)
    }

    /** Marks whether [player] has been notified about a mod update. */
    @JvmStatic fun setModUpdateNotified(player: PlatformPlayer, notified: Boolean) =
        setModUpdateNotified(player.platformUuid, notified)

    /** Returns true if [uuid] has already been informed about a plugin update. */
    fun hasBeenNotifiedAboutPluginUpdate(uuid: UUID): Boolean = uuid in pluginUpdateNotified

    /** Returns true if [player] has already been informed about a plugin update. */
    @JvmStatic fun hasBeenNotifiedAboutPluginUpdate(player: PlatformPlayer): Boolean =
        hasBeenNotifiedAboutPluginUpdate(player.platformUuid)

    /** Marks whether [uuid] has been notified about a plugin update. */
    fun setPluginUpdateNotified(uuid: UUID, notified: Boolean) {
        if (notified) pluginUpdateNotified.add(uuid) else pluginUpdateNotified.remove(uuid)
    }

    /** Marks whether [player] has been notified about a plugin update. */
    @JvmStatic fun setPluginUpdateNotified(player: PlatformPlayer, notified: Boolean) =
        setPluginUpdateNotified(player.platformUuid, notified)

    /** Returns true if [uuid] has already been informed that the mod is required. */
    fun hasBeenNotifiedAboutModRequired(uuid: UUID): Boolean = uuid in modRequiredNotified

    /** Returns true if [player] has already been informed that the mod is required. */
    @JvmStatic fun hasBeenNotifiedAboutModRequired(player: PlatformPlayer): Boolean =
        hasBeenNotifiedAboutModRequired(player.platformUuid)

    /** Marks whether [uuid] has been notified that the mod is required. */
    fun setModRequiredNotified(uuid: UUID, notified: Boolean) {
        if (notified) modRequiredNotified.add(uuid) else modRequiredNotified.remove(uuid)
    }

    /** Marks whether [player] has been notified that the mod is required. */
    @JvmStatic fun setModRequiredNotified(player: PlatformPlayer, notified: Boolean) =
        setModRequiredNotified(player.platformUuid, notified)

    /** Sets whether displays should be rendered for [uuid]. */
    fun setDisplaysEnabled(uuid: UUID, enabled: Boolean) {
        if (enabled) displaysDisabled.remove(uuid) else displaysDisabled.add(uuid)
    }

    /** Sets whether displays should be rendered for [player]. */
    @JvmStatic fun setDisplaysEnabled(player: PlatformPlayer, enabled: Boolean) =
        setDisplaysEnabled(player.platformUuid, enabled)

    /** Returns whether displays are enabled for [uuid] (defaults to true). */
    fun isDisplaysEnabled(uuid: UUID): Boolean = uuid !in displaysDisabled

    /** Returns whether displays are enabled for [player] (defaults to true). */
    @JvmStatic fun isDisplaysEnabled(player: PlatformPlayer): Boolean =
        isDisplaysEnabled(player.platformUuid)
}
