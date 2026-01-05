package com.dreamdisplays

import com.dreamdisplays.screen.Manager
import net.minecraft.client.Minecraft
import org.jspecify.annotations.NullMarked

/**
 * A background thread that mutes/unmutes screens based on window focus.
 */
@NullMarked
class Focuser : Thread("window-focus-mute-thread") {

    init {
        isDaemon = true
        instance = this
    }

    override fun run() {
        while (true) {
            val client = Minecraft.getInstance()

            val focused = client.isWindowActive

            if (Initializer.config.muteOnAltTab) {
                for (screen in Manager.getScreens()) {
                    screen.mute(!focused)
                }
            }

            // TODO: rewrite this logic
            // Potential issue: if user alt-tabs while a screen is loading and server
            // restarts, crash may occur due.
            try {
                sleep(250)
            } catch (_: InterruptedException) {
                currentThread().interrupt()
                break
            }
        }
    }

    companion object {
        @JvmField
        var instance: Focuser = Focuser()
    }
}
