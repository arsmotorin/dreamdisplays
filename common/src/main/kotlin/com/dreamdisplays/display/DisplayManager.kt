package com.dreamdisplays.display

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Manager for all screen displays. */
object DisplayManager {


    val screens = ConcurrentHashMap<UUID, DisplayScreen>()


    val unloadedScreens = ConcurrentHashMap<UUID, DisplaySettings.FullDisplayData>()


    fun getScreens(): Collection<DisplayScreen> = screens.values


    fun registerScreen(displayScreen: DisplayScreen) {
        screens[displayScreen.uuid]?.unregister()

        val clientSettings = DisplaySettings.getSettings(displayScreen.uuid)
        displayScreen.volume = clientSettings.volume
        displayScreen.quality = clientSettings.quality
        displayScreen.muted = clientSettings.muted

        DisplaySettings.getDisplayData(displayScreen.uuid)?.let { saved ->
            displayScreen.setRenderDistance(saved.renderDistance)
            displayScreen.setSavedTimeNanos(saved.currentTimeNanos)
        }

        screens[displayScreen.uuid] = displayScreen
        saveScreenData(displayScreen)
    }


    fun unregisterScreen(displayScreen: DisplayScreen) {
        val videoUrl = displayScreen.videoUrl
        val lang = displayScreen.lang
        val ownerUuid = displayScreen.ownerUuid

        val data = DisplaySettings.FullDisplayData(
            displayScreen.uuid,
            displayScreen.pos.x,
            displayScreen.pos.y,
            displayScreen.pos.z,
            displayScreen.facing,
            displayScreen.width,
            displayScreen.height,
            videoUrl ?: "",
            lang ?: "",
            displayScreen.volume,
            displayScreen.quality,
            displayScreen.brightness,
            displayScreen.muted,
            displayScreen.isSync,
            ownerUuid,
            displayScreen.renderDistance,
            displayScreen.currentTimeNanos,
        )
        unloadedScreens[displayScreen.uuid] = data

        screens.remove(displayScreen.uuid)
        displayScreen.unregister()
    }


    fun unloadAll() {
        for (s in screens.values) s.unregister()
        screens.clear()
        unloadedScreens.clear()
    }


    fun saveScreenData(displayScreen: DisplayScreen) {
        val data = DisplaySettings.FullDisplayData(
            displayScreen.uuid,
            displayScreen.pos.x,
            displayScreen.pos.y,
            displayScreen.pos.z,
            displayScreen.facing,
            displayScreen.width,
            displayScreen.height,
            displayScreen.videoUrl ?: "",
            displayScreen.lang ?: "",
            displayScreen.volume,
            displayScreen.quality,
            displayScreen.brightness,
            displayScreen.muted,
            displayScreen.isSync,
            displayScreen.ownerUuid,
            displayScreen.renderDistance,
            displayScreen.currentTimeNanos,
        )
        DisplaySettings.saveDisplayData(displayScreen.uuid, data)
    }


    fun loadScreensForServer(serverId: String) {
        DisplaySettings.loadServerDisplays(serverId)
    }


    fun saveAllScreens() {
        for (s in screens.values) saveScreenData(s)
    }
}
