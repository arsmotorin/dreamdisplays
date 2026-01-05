package com.dreamdisplays.screen

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.inotsleep.utils.logging.LoggingManager
import org.jspecify.annotations.NullMarked
import java.io.*
import java.util.*

/**
 * Manages loading and saving of display settings and data.
 */
@NullMarked
object Settings {
    // Store settings per server
    private val SETTINGS_DIR = File("./config/dreamdisplays")
    private val GSON = GsonBuilder()
        .setPrettyPrinting()
        .create()

    // displayUuid -> DisplaySettings
    private val displaySettings: MutableMap<UUID, DisplaySettings> = HashMap()

    // serverId -> (displayUuid -> FullDisplayData)
    private val serverDisplays: MutableMap<String, MutableMap<UUID, FullDisplayData>> = HashMap()
    private var currentServerId: String? = null

    // Load settings from disk
    @JvmStatic
    fun load() {
        // Ensure directory exists
        if (!SETTINGS_DIR.exists() && !SETTINGS_DIR.mkdirs()) {
            LoggingManager.error("Failed to create settings directory.")
            return
        }

        // Load client display settings (volume, quality, muted)
        val clientSettingsFile = File(SETTINGS_DIR, "client-display-settings.json")
        try {
            FileReader(clientSettingsFile).use { reader ->
                val type = object : TypeToken<Map<String, DisplaySettings>>() {}.type
                val loadedSettings: Map<String, DisplaySettings>? = GSON.fromJson(reader, type)

                if (loadedSettings != null) {
                    displaySettings.clear()
                    for ((key, value) in loadedSettings) {
                        try {
                            val uuid = UUID.fromString(key)
                            displaySettings[uuid] = value
                        } catch (_: IllegalArgumentException) {
                            LoggingManager.error("Invalid UUID in client display settings: $key")
                        }
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            // File doesn't exist yet, that's fine
        } catch (e: IOException) {
            LoggingManager.error("Failed to load client display settings", e)
        }
    }

    // Load server-specific display data
    @JvmStatic
    fun loadServerDisplays(serverId: String) {
        currentServerId = serverId
        val serverFile = File(SETTINGS_DIR, "server-$serverId-displays.json")

        try {
            FileReader(serverFile).use { reader ->
                val type = object : TypeToken<Map<String, FullDisplayData>>() {}.type
                val loadedDisplays: Map<String, FullDisplayData>? = GSON.fromJson(reader, type)

                val displays: MutableMap<UUID, FullDisplayData> = HashMap()
                if (loadedDisplays != null) {
                    for ((key, value) in loadedDisplays) {
                        try {
                            val uuid = UUID.fromString(key)
                            displays[uuid] = value
                        } catch (_: IllegalArgumentException) {
                            LoggingManager.error("Invalid UUID in server displays: $key")
                        }
                    }
                }
                serverDisplays[serverId] = displays
                LoggingManager.info("Loaded ${displays.size} displays for server: $serverId")
            }
        } catch (_: FileNotFoundException) {
            serverDisplays[serverId] = HashMap()
        } catch (e: IOException) {
            LoggingManager.error("Failed to load server displays for $serverId", e)
            serverDisplays[serverId] = HashMap()
        }
    }

    // Save all client settings to disk
    @JvmStatic
    fun save() {
        try {
            if (!SETTINGS_DIR.exists() && !SETTINGS_DIR.mkdirs()) {
                LoggingManager.error("Failed to create settings directory.")
                return
            }

            val toSave: MutableMap<String, DisplaySettings> = HashMap()
            for ((key, value) in displaySettings) {
                toSave[key.toString()] = value
            }

            val clientSettingsFile = File(SETTINGS_DIR, "client-display-settings.json")
            FileWriter(clientSettingsFile).use { writer ->
                GSON.toJson(toSave, writer)
            }
        } catch (e: IOException) {
            LoggingManager.error("Failed to save client display settings", e)
        }
    }

    // Save server-specific displays to disk
    @JvmStatic
    fun saveServerDisplays(serverId: String) {
        try {
            if (!SETTINGS_DIR.exists() && !SETTINGS_DIR.mkdirs()) {
                LoggingManager.error("Failed to create settings directory.")
                return
            }

            val displays = serverDisplays.getOrDefault(serverId, HashMap())
            val toSave: MutableMap<String, FullDisplayData> = HashMap()
            for ((key, value) in displays) {
                toSave[key.toString()] = value
            }

            val serverFile = File(SETTINGS_DIR, "server-$serverId-displays.json")
            FileWriter(serverFile).use { writer ->
                GSON.toJson(toSave, writer)
            }
        } catch (e: IOException) {
            LoggingManager.error("Failed to save server displays for $serverId", e)
        }
    }

    // Get client display settings
    @JvmStatic
    fun getSettings(displayUuid: UUID): DisplaySettings {
        return displaySettings.computeIfAbsent(displayUuid) { DisplaySettings() }
    }

    // Update client display settings
    @JvmStatic
    fun updateSettings(
        displayUuid: UUID,
        volume: Float,
        quality: String,
        brightness: Float,
        muted: Boolean,
        paused: Boolean,
    ) {
        val settings = getSettings(displayUuid)
        settings.volume = volume
        settings.quality = quality
        settings.brightness = brightness
        settings.muted = muted
        settings.paused = paused
        save()
    }

    // Get full display data for a server
    @JvmStatic
    fun getDisplayData(displayUuid: UUID): FullDisplayData? {
        if (currentServerId == null) return null
        return serverDisplays.getOrDefault(currentServerId, HashMap())[displayUuid]
    }

    // Save full display data
    @JvmStatic
    fun saveDisplayData(displayUuid: UUID, data: FullDisplayData) {
        if (currentServerId == null) return

        serverDisplays.computeIfAbsent(currentServerId!!) { HashMap() }[displayUuid] = data
        saveServerDisplays(currentServerId!!)
    }

    // Remove display from all servers and client settings
    @JvmStatic
    fun removeDisplay(displayUuid: UUID) {
        // Remove from server-specific display data
        for (displays in serverDisplays.values) {
            if (displays.remove(displayUuid) != null) {
                var serverId: String? = null
                for ((key, value) in serverDisplays) {
                    if (value === displays) {
                        serverId = key
                        break
                    }
                }
                if (serverId != null) {
                    saveServerDisplays(serverId)
                }
            }
        }

        // Also remove from client display settings (volume, quality, muted)
        displaySettings.remove(displayUuid)
        save()

        LoggingManager.info("Removed display from all saved data: $displayUuid")
    }

    // Client settings for a display (volume, quality, muted, brightness, paused)
    class DisplaySettings {
        @JvmField
        var volume: Float = 0.5f
        @JvmField
        var quality: String = "720"
        @JvmField
        var brightness: Float = 1.0f
        @JvmField
        var muted: Boolean = false
        @JvmField
        var paused: Boolean = true
    }

    // Full display data (stored per server)
    class FullDisplayData(
        @JvmField var uuid: UUID,
        @JvmField var x: Int,
        @JvmField var y: Int,
        @JvmField var z: Int,
        @JvmField var facing: String,
        @JvmField var width: Int,
        @JvmField var height: Int,
        @JvmField var videoUrl: String,
        @JvmField var lang: String?,
        @JvmField var volume: Float,
        @JvmField var quality: String,
        @JvmField var muted: Boolean,
        @JvmField var isSync: Boolean,
        @JvmField var ownerUuid: UUID,
        @JvmField var renderDistance: Int = 64,
        @JvmField var currentTimeNanos: Long = 0,
    )
}

