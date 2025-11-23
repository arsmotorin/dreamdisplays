package com.dreamdisplays.mixins;

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
import com.dreamdisplays.downloader.GStreamerDownloadListener;
import com.dreamdisplays.downloader.GStreamerDownloaderMenu;
import com.dreamdisplays.downloader.GStreamerErrorScreen;
import com.dreamdisplays.util.Utils;

import java.util.concurrent.atomic.AtomicBoolean;

@NullMarked
@Mixin(Minecraft.class)
public abstract class GStreamInitMixin {
    @Shadow
    public abstract void setScreen(@Nullable Screen screen);

    @Unique
    private static final AtomicBoolean dreamdisplays$recursionDetector = new AtomicBoolean(false);

    @Unique
    private static boolean dreamdisplays$downloaded = false;

    // Redirect screen setting to GStreamer downloader if not downloaded yet
    @Inject(at = @At("HEAD"), method = "setScreen", cancellable = true)
    public void redirScreen(Screen screen, CallbackInfo ci) {
        if (!dreamdisplays$downloaded) {
            boolean recursionValue = dreamdisplays$recursionDetector.get();
            dreamdisplays$recursionDetector.set(true);

            if (!(screen instanceof GStreamerDownloaderMenu) && !(screen instanceof GStreamerErrorScreen)) {
                if (GStreamerDownloadListener.INSTANCE.isDone() && !GStreamerDownloadListener.INSTANCE.isFailed()) {
                    dreamdisplays$downloaded = true;
                    Gst.init("MediaPlayer");
                }
                else if (!GStreamerDownloadListener.INSTANCE.isDone() && !GStreamerDownloadListener.INSTANCE.isFailed()) {
                    LoggingManager.warn("GStreamer has not finished loading, displaying loading screen");
                    setScreen(new GStreamerDownloaderMenu(screen));
                    ci.cancel();
                }
                else if (GStreamerDownloadListener.INSTANCE.isFailed()) {
                    dreamdisplays$downloaded = true;
                    LoggingManager.error("GStreamer failed to initialize");
                    setScreen(new GStreamerErrorScreen(screen, Utils.detectPlatform().equals("windows") ? "Dream Displays failed to download libraries": "Dream Displays failed to initialize GStreamer. You need to download GStreamer libraries manually and place them in the ./libs/gstreamer directory"));
                }
            }
            dreamdisplays$recursionDetector.set(recursionValue);
        }
    }
}
