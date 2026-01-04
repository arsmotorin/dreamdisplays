package com.dreamdisplays.mixins;

import com.dreamdisplays.downloader.Error;
import com.dreamdisplays.downloader.Listener;
import com.dreamdisplays.downloader.Menu;
import com.dreamdisplays.util.Utils;
import me.inotsleep.utils.logging.LoggingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.freedesktop.gstreamer.Gst;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
@Mixin(Minecraft.class)
public abstract class GStreamer {

    @Unique
    private static final AtomicBoolean dreamdisplays$recursionDetector =
            new AtomicBoolean(false);

    @Unique
    private static boolean dreamdisplays$downloaded = false;

    @Shadow
    public abstract void setScreen(@Nullable Screen screen);

    // Redirect screen setting to GStreamer downloader if not downloaded yet
    @Inject(at = @At("HEAD"), method = "setScreen", cancellable = true)
    public void redirScreen(Screen screen, CallbackInfo ci) {
        if (!dreamdisplays$downloaded) {
            boolean recursionValue = dreamdisplays$recursionDetector.get();
            dreamdisplays$recursionDetector.set(true);

            if (!(screen instanceof Menu) && !(screen instanceof Error)) {
                if (
                        Listener.INSTANCE.isDone() && !Listener.INSTANCE.isFailed()
                ) {
                    dreamdisplays$downloaded = true;
                    try {
                        Gst.init("MediaPlayer");
                    } catch (Throwable e) {
                        LoggingManager.error(
                                "Failed to initialize GStreamer",
                                e
                        );
                        setScreen(
                                new Error(
                                        screen,
                                        "Dream Displays failed to initialize GStreamer."
                                )
                        );
                    }
                } else if (
                        !Listener.INSTANCE.isDone() && !Listener.INSTANCE.isFailed()
                ) {
                    LoggingManager.warn(
                            "GStreamer has not finished loading, displaying loading screen"
                    );
                    setScreen(new Menu(screen));
                    ci.cancel();
                } else if (Listener.INSTANCE.isFailed()) {
                    dreamdisplays$downloaded = true;
                    if (Utils.detectPlatform().equals("windows")) {
                        LoggingManager.error(
                                "GStreamer failed to initialize on Windows"
                        );
                        setScreen(
                                new Error(
                                        screen,
                                        "Dream Displays failed to download libraries"
                                )
                        );
                    } else {
                        try {
                            Gst.init("MediaPlayer");
                        } catch (Throwable e) {
                            LoggingManager.error(
                                    "Failed to initialize system GStreamer",
                                    e
                            );
                            setScreen(
                                    new Error(
                                            screen,
                                            "Dream Displays failed to initialize GStreamer. Please install GStreamer via your package manager."
                                    )
                            );
                        }
                    }
                }
            }
            dreamdisplays$recursionDetector.set(recursionValue);
        }
    }
}
