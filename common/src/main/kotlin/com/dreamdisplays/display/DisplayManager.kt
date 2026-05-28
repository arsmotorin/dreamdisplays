package com.dreamdisplays.display

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing a display screen in the world. Contains all necessary information for rendering and playback.
 *
 * @see DisplaySettings.FullDisplayData
 */
private fun DisplayScreen.toFullDisplayData() = DisplaySettings.FullDisplayData(
    uuid, pos.x, pos.y, pos.z, facing, width, height,
    videoUrl ?: "", lang ?: "", volume, quality, brightness,
    muted, isSync, ownerUuid, renderDistance, currentTimeNanos,
)

/** Manager for all screen displays. */
object DisplayManager {
    val screens = ConcurrentHashMap<UUID, DisplayScreen>()
    val unloadedScreens = ConcurrentHashMap<UUID, DisplaySettings.FullDisplayData>()

    /** Returns a snapshot of all currently registered screens. */
    fun getScreens(): Collection<DisplayScreen> = screens.values

    /** Registers a new display screen. */
    fun registerScreen(displayScreen: DisplayScreen) {
        screens[displayScreen.uuid]?.unregister()

        val clientSettings = DisplaySettings.getSettings(displayScreen.uuid)
        displayScreen.volume = clientSettings.volume
        displayScreen.quality = clientSettings.quality
        displayScreen.muted = clientSettings.muted

        DisplaySettings.getDisplayData(displayScreen.uuid)?.let { saved ->
            displayScreen.renderDistance = saved.renderDistance
            displayScreen.savedTimeNanos = saved.currentTimeNanos
        }

        screens[displayScreen.uuid] = displayScreen
    }

    /** Unregisters a display screen, saving its data for later re-registration. */
    fun unregisterScreen(displayScreen: DisplayScreen) {
        unloadedScreens[displayScreen.uuid] = displayScreen.toFullDisplayData()
        screens.remove(displayScreen.uuid)
        displayScreen.unregister()
    }

    /** Unregisters all display screens. */
    fun unloadAll() {
        screens.values.forEach { it.unregister() }
        screens.clear()
        unloadedScreens.clear()
    }

    /** Saves the display screen data to disk. */
    fun saveScreenData(displayScreen: DisplayScreen) {
        DisplaySettings.saveDisplayData(displayScreen.uuid, displayScreen.toFullDisplayData())
    }

    /** Loads all display screens for a given server from disk. */
    fun loadScreensForServer(serverId: String) {
        DisplaySettings.loadServerDisplays(serverId)
    }

    /** Saves all display screens to disk. */
    fun saveAllScreens() {
        screens.values.forEach { saveScreenData(it) }
    }
}
