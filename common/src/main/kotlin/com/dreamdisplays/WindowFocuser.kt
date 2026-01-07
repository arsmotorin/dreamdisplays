package com.dreamdisplays

import com.dreamdisplays.ModInitializer.config
import com.dreamdisplays.screen.managers.ScreenManager.getScreens
import net.minecraft.client.Minecraft
import org.jspecify.annotations.NullMarked

/**
 * A background thread that mutes/unmutes screens based on window focus.
 */
@NullMarked
class WindowFocuser : Thread("window-focus-mute-thread") {

    init {
        isDaemon = true
        instance = this
    }

    private fun getMinecraftInstanceSafe(): Minecraft? {
        return try {
            val instanceField = Minecraft::class.java.getDeclaredField("instance")
            instanceField.isAccessible = true
            instanceField.get(null) as? Minecraft
        } catch (_: Exception) {
            null
        }
    }

    override fun run() {
        while (true) {
            val client = getMinecraftInstanceSafe()

            if (client == null) {
                try {
                    sleep(250)
                } catch (_: InterruptedException) {
                    currentThread().interrupt()
                    break
                }
                continue
            }

            val focused = client.isWindowActive

            if (config.muteOnAltTab) {
                for (screen in getScreens()) {
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
        var instance: WindowFocuser = WindowFocuser()
    }
}
