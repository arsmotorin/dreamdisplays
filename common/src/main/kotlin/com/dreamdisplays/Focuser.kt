package com.dreamdisplays

import com.dreamdisplays.display.DisplayManager
import net.minecraft.client.Minecraft

/** Background thread that mutes / unmutes screens based on window focus. */
class Focuser : Thread() {

    init {
        isDaemon = true
        instance = this
        name = "window-focus-mute-thread"
    }

    override fun run() {
        while (true) {
            val focused = Minecraft.getInstance().isWindowActive
            if (Initializer.config.muteOnAltTab) {
                for (screen in DisplayManager.getScreens()) {
                    screen.mute(!focused)
                }
            }
            try {
                sleep(250)
            } catch (_: InterruptedException) {
                currentThread().interrupt()
                break
            }
        }
    }

    companion object {

        var instance: Focuser = Focuser()
    }
}
