package com.dreamdisplays.display

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.inotsleep.utils.logging.LoggingManager
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.UUID

/** Manages loading and saving of display settings and data. */
object DisplaySettings {

    private val SETTINGS_DIR = File("./config/dreamdisplays")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val displaySettings = HashMap<UUID, ClientDisplaySettings>()
    private val serverDisplays = HashMap<String, MutableMap<UUID, FullDisplayData>>()
    private var currentServerId: String? = null


    fun load() {
        if (!SETTINGS_DIR.exists() && !SETTINGS_DIR.mkdirs()) {
            LoggingManager.error("Failed to create settings directory.")
            return
        }
        val clientSettingsFile = File(SETTINGS_DIR, "client-display-settings.json")
        try {
            FileReader(clientSettingsFile).use { reader ->
                val type = object : TypeToken<Map<String, ClientDisplaySettings>>() {}.type
                val loaded: Map<String, ClientDisplaySettings>? = GSON.fromJson(reader, type)
                if (loaded != null) {
                    displaySettings.clear()
                    for ((key, value) in loaded) {
                        try {
                            displaySettings[UUID.fromString(key)] = value
                        } catch (_: IllegalArgumentException) {
                            LoggingManager.error("Invalid UUID in client display settings: $key")
                        }
                    }
                }
            }
        } catch (_: FileNotFoundException) {
        } catch (e: IOException) {
            LoggingManager.error("Failed to load client display settings", e)
        }
    }


    fun loadServerDisplays(serverId: String) {
        currentServerId = serverId
        val serverFile = File(SETTINGS_DIR, "server-$serverId-displays.json")
        try {
            FileReader(serverFile).use { reader ->
                val type = object : TypeToken<Map<String, FullDisplayData>>() {}.type
                val loaded: Map<String, FullDisplayData>? = GSON.fromJson(reader, type)
                val displays = HashMap<UUID, FullDisplayData>()
                if (loaded != null) {
                    for ((key, value) in loaded) {
                        try {
                            displays[UUID.fromString(key)] = value
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


    fun save() {
        try {
            if (!SETTINGS_DIR.exists() && !SETTINGS_DIR.mkdirs()) {
                LoggingManager.error("Failed to create settings directory.")
                return
            }
            val toSave = displaySettings.entries.associate { (k, v) -> k.toString() to v }
            FileWriter(File(SETTINGS_DIR, "client-display-settings.json")).use { writer ->
                GSON.toJson(toSave, writer)
            }
        } catch (e: IOException) {
            LoggingManager.error("Failed to save client display settings", e)
        }
    }


    fun saveServerDisplays(serverId: String) {
        try {
            if (!SETTINGS_DIR.exists() && !SETTINGS_DIR.mkdirs()) {
                LoggingManager.error("Failed to create settings directory.")
                return
            }
            val displays = serverDisplays[serverId] ?: HashMap()
            val toSave = displays.entries.associate { (k, v) -> k.toString() to v }
            FileWriter(File(SETTINGS_DIR, "server-$serverId-displays.json")).use { writer ->
                GSON.toJson(toSave, writer)
            }
        } catch (e: IOException) {
            LoggingManager.error("Failed to save server displays for $serverId", e)
        }
    }


    fun getSettings(displayUuid: UUID): ClientDisplaySettings =
        displaySettings.getOrPut(displayUuid) { ClientDisplaySettings() }


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


    fun setUrlOverride(displayUuid: UUID, url: String?, lang: String?) {
        val settings = getSettings(displayUuid)
        settings.urlOverride = url
        settings.langOverride = lang
        save()
    }


    fun getDisplayData(displayUuid: UUID): FullDisplayData? {
        val server = currentServerId ?: return null
        return serverDisplays[server]?.get(displayUuid)
    }


    fun saveDisplayData(displayUuid: UUID, data: FullDisplayData) {
        val server = currentServerId ?: return
        serverDisplays.getOrPut(server) { HashMap() }[displayUuid] = data
        saveServerDisplays(server)
    }


    fun removeDisplay(displayUuid: UUID) {
        for ((serverId, displays) in serverDisplays) {
            if (displays.remove(displayUuid) != null) {
                saveServerDisplays(serverId)
            }
        }
        displaySettings.remove(displayUuid)
        save()
    }

    class ClientDisplaySettings {
        var volume: Float = 0.5f
        var quality: String = "720"
        var brightness: Float = 1.0f
        var muted: Boolean = false
        var paused: Boolean = true
        var urlOverride: String? = null
        var langOverride: String? = null
    }

    class FullDisplayData constructor(
        var uuid: UUID,
        var x: Int,
        var y: Int,
        var z: Int,
        var facing: String,
        var width: Int,
        var height: Int,
        var videoUrl: String,
        var lang: String,
        var volume: Float,
        var quality: String,
        var brightness: Float,
        var muted: Boolean,
        var isSync: Boolean,
        var ownerUuid: UUID,
        var renderDistance: Int = 64,
        var currentTimeNanos: Long = 0,
    )
}
