package com.dreamdisplays.screen

import org.jspecify.annotations.NullMarked
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for screen actions and storage.
 */
@NullMarked
object Manager {
    val screens: ConcurrentHashMap<UUID, Screen> = ConcurrentHashMap<UUID, Screen>()

    // Get all registered screens
    @JvmStatic
    fun getScreens(): MutableCollection<Screen> {
        return screens.values
    }

    // Register screen
    fun registerScreen(screen: Screen) {
        if (screens.containsKey(screen.iD)) {
            // Unregister old screen with same ID
            val old: Screen = screens.get(screen.iD)!!
            old.unregister()
        }

        screens[screen.iD] = screen
    }

    // Unregister screen
    // TODO: rewrite this
    fun unregisterScreen(screen: Screen) {
        screens.remove(screen.iD)
        screen.unregister()
    }

    // Unload all screens
    // TODO: rewrite this
    fun unloadAll() {
        for (screen in screens.values) {
            screen.unregister()
        }

        screens.clear()
    }
}
