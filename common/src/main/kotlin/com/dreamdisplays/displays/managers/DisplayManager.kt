package com.dreamdisplays.displays.managers

import com.dreamdisplays.displays.DisplayScreen
import me.inotsleep.utils.logging.LoggingManager
import org.jspecify.annotations.NullMarked
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for all displays.
 */
@NullMarked
object DisplayManager {
    val displays: ConcurrentHashMap<UUID, DisplayScreen> = ConcurrentHashMap()

    // Cache of unloaded displays
    val unloadedDisplays: ConcurrentHashMap<UUID, SettingsManager.FullDisplayData> = ConcurrentHashMap()
    fun getDisplays(): Collection<DisplayScreen> {
        return displays.values
    }

    fun registerDisplay(display: DisplayScreen) {
        if (displays.containsKey(display.uuid)) {
            val old = displays[display.uuid]
            old?.unregister()
        }

        val clientSettings = SettingsManager.getSettings(display.uuid)
        display.volume = clientSettings.volume.toDouble()
        display.quality = clientSettings.quality
        display.muted = clientSettings.muted

        val savedData = SettingsManager.getDisplayData(display.uuid)
        if (savedData != null) {
            display.renderDistance = savedData.renderDistance
            display.setSavedTimeNanos(savedData.currentTimeNanos)
        }

        displays[display.uuid] = display

        // Save the display data for persistence
        saveDisplayData(display)
    }

    fun unregisterDisplay(display: DisplayScreen) {
        // Cache the display data before unregistering
        val videoUrl = display.videoUrl
        val lang = display.lang
        val ownerUuid = display.ownerUuid

        val data = SettingsManager.FullDisplayData(
            display.uuid,
            display.getPos().x,
            display.getPos().y,
            display.getPos().z,
            display.getFacing(),
            display.getWidth().toInt(),
            display.getHeight().toInt(),
            videoUrl ?: "",
            lang ?: "",
            display.volume.toFloat(),
            display.quality,
            display.muted,
            display.isSync,
            ownerUuid,
            display.renderDistance,
            display.getCurrentTimeNanos()
        )
        unloadedDisplays[display.uuid] = data

        displays.remove(display.uuid)
        display.unregister()
    }

    fun unloadAllDisplays() {
        for (display in displays.values) {
            display.unregister()
        }

        displays.clear()
        unloadedDisplays.clear() // Clear cache when changing servers
    }

    // Save display data to persistent storage
    fun saveDisplayData(display: DisplayScreen) {
        val data = SettingsManager.FullDisplayData(
            display.uuid,
            display.getPos().x,
            display.getPos().y,
            display.getPos().z,
            display.getFacing(),
            display.getWidth().toInt(),
            display.getHeight().toInt(),
            display.videoUrl ?: "",
            display.lang.toString(),
            display.volume.toFloat(),
            display.quality,
            display.muted,
            display.isSync,
            display.ownerUuid,
            display.renderDistance,
            display.getCurrentTimeNanos()
        )

        SettingsManager.saveDisplayData(display.uuid, data)
    }

    // Load displays from persistent storage for a server
    // Actual display data comes from the server via Info packets.
    // Local cache is used only for client preferences (volume, quality, muted).
    fun loadDisplaysForServer(serverId: String) {
        SettingsManager.loadServerDisplays(serverId)
        LoggingManager.info("Initialized display settings storage for server: $serverId")
        // Displays will be received from server via Info packets
    }

    // Save all displays to persistent storage for current server
    fun saveAllDisplays() {
        for (display in displays.values) {
            saveDisplayData(display)
        }
    }
}
