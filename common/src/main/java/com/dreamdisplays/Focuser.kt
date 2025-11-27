package com.dreamdisplays

import com.dreamdisplays.screen.Manager
import net.minecraft.client.Minecraft
import org.jspecify.annotations.NullMarked

/**
 * Thread for muting/unmuting audio when the Minecraft window loses/gains focus.
 */
@NullMarked
class Focuser : Thread() {
    init {
        setDaemon(true)
        instance = this
        setName("window-focus-mute-thread")
    }

    override fun run() {
        while (true) {
            val client = Minecraft.getInstance()

            val focused = client.isWindowActive

            if (Initializer.config.muteOnAltTab) for (screen in Manager.getScreens()) {
                screen.mute(!focused)
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
