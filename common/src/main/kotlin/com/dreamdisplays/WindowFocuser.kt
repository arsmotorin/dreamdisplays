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
            null // Minecraft instance isn't initialized yet
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

            try {
                val focused = client.isWindowActive

                if (config.muteOnAltTab) {
                    val screensList = try {
                        getScreens().toList()
                    } catch (_: Exception) {
                        emptyList()
                    }

                    for (screen in screensList) {
                        try {
                            screen.mute(!focused)
                        } catch (_: Exception) {
                            // Server is probably restarting, ignore
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore exceptions to prevent thread from dying
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
        @JvmField
        var instance: WindowFocuser = WindowFocuser()
    }
}
