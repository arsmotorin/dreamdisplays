package com.dreamdisplays.mixins

import com.dreamdisplays.downloader.Error as DownloadError
import com.dreamdisplays.downloader.GStreamerState
import com.dreamdisplays.downloader.Listener
import com.dreamdisplays.downloader.Menu
import com.dreamdisplays.util.Utils
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.freedesktop.gstreamer.Gst
import org.jspecify.annotations.NullMarked
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
@Mixin(Minecraft::class)
abstract class GStreamer {

    @Shadow
    abstract fun setScreen(screen: Screen?)

    // Redirect screen setting to GStreamer downloader if not downloaded yet
    @Inject(at = [At("HEAD")], method = ["setScreen"], cancellable = true)
    fun redirScreen(screen: Screen?, ci: CallbackInfo) {
        if (!GStreamerState.downloaded) {
            val recursionValue = GStreamerState.recursionDetector.get()
            GStreamerState.recursionDetector.set(true)

            if (screen !is Menu && screen !is DownloadError) {
                if (Listener.isDone && !Listener.isFailed) {
                    GStreamerState.downloaded = true
                    try {
                        Gst.init("MediaPlayer")
                    } catch (e: Throwable) {
                        LoggingManager.error(
                            "Failed to initialize GStreamer",
                            e
                        )
                        setScreen(
                            DownloadError(
                                screen!!,
                                "Dream Displays failed to initialize GStreamer."
                            )
                        )
                    }
                } else if (!Listener.isDone && !Listener.isFailed) {
                    LoggingManager.warn(
                        "GStreamer has not finished loading, displaying loading screen"
                    )
                    setScreen(Menu(screen!!))
                    ci.cancel()
                } else {
                    GStreamerState.downloaded = true
                    if (Utils.detectPlatform() == "windows") {
                        LoggingManager.error(
                            "GStreamer failed to initialize on Windows"
                        )
                        setScreen(
                            DownloadError(
                                screen!!,
                                "Dream Displays failed to download libraries"
                            )
                        )
                    } else {
                        try {
                            Gst.init("MediaPlayer")
                        } catch (e: Throwable) {
                            LoggingManager.error(
                                "Failed to initialize system GStreamer",
                                e
                            )
                            setScreen(
                                DownloadError(
                                    screen!!,
                                    "Dream Displays failed to initialize GStreamer. Please install GStreamer via your package manager."
                                )
                            )
                        }
                    }
                }
            }
            GStreamerState.recursionDetector.set(recursionValue)
        }
    }
}

