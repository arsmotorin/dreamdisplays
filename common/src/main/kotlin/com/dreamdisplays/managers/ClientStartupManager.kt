package com.dreamdisplays.managers

import com.dreamdisplays.Config
import com.dreamdisplays.Focuser
import com.dreamdisplays.Initializer
import com.dreamdisplays.client.core.ClientApplication
import com.dreamdisplays.client.core.DefaultClientApplication
import com.dreamdisplays.client.core.DefaultClientContext
import com.dreamdisplays.client.core.DreamServices
import com.dreamdisplays.client.core.getOrNull
import com.dreamdisplays.client.core.register
import com.dreamdisplays.displays.DisplayRegistry
import com.dreamdisplays.platform.api.Platform
import com.dreamdisplays.displays.DisplayScreen
import com.dreamdisplays.displays.store.DisplayStorage
import com.dreamdisplays.player.nativebridge.NativeMedia
import com.dreamdisplays.player.process.FFmpegBinary
import com.dreamdisplays.ytdlp.FormatDiskCache
import com.dreamdisplays.ytdlp.YtDlp
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Handles client bootstrapping and background maintenance threads.
 */
object ClientStartupManager {
    private val logger = LoggerFactory.getLogger("DreamDisplays/ClientStartupManager")
    val config: Config = Config(File("./config/${Initializer.MOD_ID}"))

    /** Daemon so a hung refresh / sweep can never block JVM shutdown; [stop] still cancels it cleanly on a normal exit. */
    private val qualityRefreshExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "dreamdisplays-quality-refresh").apply { isDaemon = true }
    }
    private val cacheSweepExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "dreamdisplays-cache-sweep").apply { isDaemon = true }
    }

    fun start() {
        config.reload()
        DisplayStorage.load()

        // Wire the contract-typed service graph (media resolver chain, ...) before any
        // background prewarm touches it.
        DreamServices.bootstrap()

        // If the loader entrypoint registered a Platform, host the module system on top of it
        DreamServices.registry.getOrNull<Platform>()?.let { platform ->
            val application = DefaultClientApplication(DefaultClientContext(platform))
            DreamServices.registry.register<ClientApplication>(application)
            application.start()
        }

        YtDlp.prewarmAsync()
        FFmpegBinary.prewarmAsync()
        NativeMedia.prewarmAsync()

        cacheSweepExecutor.execute {
            runCatching { FormatDiskCache.sweepExpired() }
                .onFailure { e -> logger.warn("Cache sweep failed.", e) }
        }
        Focuser().start()
        qualityRefreshExecutor.scheduleWithFixedDelay({
            runCatching { DisplayRegistry.getScreens().forEach(DisplayScreen::reloadQuality) }
                .onFailure { e -> logger.warn("Quality refresh failed.", e) }
        }, 2500, 2500, TimeUnit.MILLISECONDS)
    }

    /** Cancels the background refresh / sweep tasks. Safe to call multiple times. */
    fun stop() {
        qualityRefreshExecutor.shutdownNow()
        cacheSweepExecutor.shutdownNow()
    }
}
