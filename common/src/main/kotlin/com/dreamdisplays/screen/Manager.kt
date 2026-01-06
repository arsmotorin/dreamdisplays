package com.dreamdisplays.screen

import com.dreamdisplays.screen.Settings.FullDisplayData
import com.dreamdisplays.screen.Settings.getDisplayData
import com.dreamdisplays.screen.Settings.getSettings
import me.inotsleep.utils.logging.LoggingManager
import org.jspecify.annotations.NullMarked
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for all screen displays.
 */
@NullMarked
object Manager {

    @JvmField
    val screens: ConcurrentHashMap<UUID, Screen> = ConcurrentHashMap()

    // Cache of unloaded displays
    @JvmField
    val unloadedScreens: ConcurrentHashMap<UUID, FullDisplayData> = ConcurrentHashMap()

    @JvmStatic
    fun getScreens(): Collection<Screen> {
        return screens.values
    }

    @JvmStatic
    fun registerScreen(screen: Screen) {
        if (screens.containsKey(screen.uuid)) {
            val old = screens[screen.uuid]
            old?.unregister()
        }

        val clientSettings = getSettings(screen.uuid)
        screen.volume = clientSettings.volume.toDouble()
        screen.quality = clientSettings.quality
        screen.muted = clientSettings.muted

        val savedData = getDisplayData(screen.uuid)
        if (savedData != null) {
            screen.renderDistance = savedData.renderDistance
            screen.setSavedTimeNanos(savedData.currentTimeNanos)
        }

        screens[screen.uuid] = screen

        // Save the display data for persistence
        saveScreenData(screen)
    }

    @JvmStatic
    fun unregisterScreen(screen: Screen) {
        // Cache the display data before unregistering
        val videoUrl = screen.videoUrl
        val lang = screen.lang
        val ownerUuid = screen.ownerUuid

        val data = FullDisplayData(
            screen.uuid,
            screen.getPos().x,
            screen.getPos().y,
            screen.getPos().z,
            screen.getFacing(),
            screen.getWidth().toInt(),
            screen.getHeight().toInt(),
            videoUrl ?: "",
            lang ?: "",
            screen.volume.toFloat(),
            screen.quality,
            screen.muted,
            screen.isSync,
            ownerUuid,
            screen.renderDistance,
            screen.getCurrentTimeNanos()
        )
        unloadedScreens[screen.uuid] = data

        screens.remove(screen.uuid)
        screen.unregister()
    }

    @JvmStatic
    fun unloadAll() {
        for (screen in screens.values) {
            screen.unregister()
        }

        screens.clear()
        unloadedScreens.clear() // Clear cache when changing servers
    }

    // Save screen data to persistent storage
    @JvmStatic
    fun saveScreenData(screen: Screen) {
        val data = FullDisplayData(
            screen.uuid,
            screen.getPos().x,
            screen.getPos().y,
            screen.getPos().z,
            screen.getFacing(),
            screen.getWidth().toInt(),
            screen.getHeight().toInt(),
            screen.videoUrl ?: "",
            screen.lang,
            screen.volume.toFloat(),
            screen.quality,
            screen.muted,
            screen.isSync,
            screen.ownerUuid,
            screen.renderDistance,
            screen.getCurrentTimeNanos()
        )

        Settings.saveDisplayData(screen.uuid, data)
    }

    // Load displays from persistent storage for a server
    // Actual display data comes from the server via Info packets.
    // Local cache is used only for client preferences (volume, quality, muted).
    @JvmStatic
    fun loadScreensForServer(serverId: String) {
        Settings.loadServerDisplays(serverId)
        LoggingManager.info("Initialized display settings storage for server: $serverId")
        // Displays will be received from server via Info packets
    }

    // Save all screens to persistent storage for current server
    @JvmStatic
    fun saveAllScreens() {
        for (screen in screens.values) {
            saveScreenData(screen)
        }
    }
}

