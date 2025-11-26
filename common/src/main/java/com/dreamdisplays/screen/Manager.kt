package com.dreamdisplays.screen

import org.jspecify.annotations.NullMarked
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@NullMarked
object Manager {
    val screens: ConcurrentHashMap<UUID, Screen> = ConcurrentHashMap<UUID, Screen>()

    @JvmStatic
    fun getScreens(): MutableCollection<Screen> {
        return screens.values
    }

    fun registerScreen(screen: Screen) {
        if (screens.containsKey(screen.iD)) {
            val old: Screen = screens.get(screen.iD)!!
            old.unregister()
        }

        screens[screen.iD] = screen
    }

    fun unregisterScreen(screen: Screen) {
        screens.remove(screen.iD)
        screen.unregister()
    }

    fun unloadAll() {
        for (screen in screens.values) {
            screen.unregister()
        }

        screens.clear()
    }
}
