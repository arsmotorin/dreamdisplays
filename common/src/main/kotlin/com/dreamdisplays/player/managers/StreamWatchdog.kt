package com.dreamdisplays.player.managers

import com.dreamdisplays.player.util.daemon
import me.inotsleep.utils.logging.LoggingManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Periodically checks whether the video reader thread is still producing frames.
 * If no frame arrives within [timeoutNs] nanoseconds, [onStall] is invoked so the caller
 * can restart the stream. Call [pulse] from the reader thread on every decoded frame.
 */
internal class StreamWatchdog(
    private val debugLabel: String,
    private val isActive: () -> Boolean,
    private val timeoutNs: Long = 30_000_000_000L,
    private val checkIntervalMs: Long = 5_000L,
    private val onStall: () -> Unit,
) {
    /** Timestamp of the last decoded frame; updated by [pulse]. */
    val lastFrameNanos = AtomicLong(0)

    @Volatile private var executor: ScheduledExecutorService? = null

    /** Resets [lastFrameNanos] to now and starts the periodic stall check. */
    fun start() {
        stop()
        lastFrameNanos.set(System.nanoTime())
        executor = Executors.newSingleThreadScheduledExecutor { daemon(it, "MediaPlayer-watchdog") }.also { exec ->
            exec.scheduleAtFixedRate(::check, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS)
        }
    }

    /** Cancels the periodic check. */
    fun stop() { executor?.shutdownNow(); executor = null }

    /** Updates [lastFrameNanos] to now. Call from the video reader thread on every frame. */
    fun pulse() { lastFrameNanos.set(System.nanoTime()) }

    private fun check() {
        runCatching {
            if (!isActive()) return
            val elapsed = System.nanoTime() - lastFrameNanos.get()
            if (elapsed > timeoutNs) {
                LoggingManager.warn("[Watchdog $debugLabel] No frames for ${elapsed / 1_000_000L}ms. Restarting...")
                lastFrameNanos.set(System.nanoTime())
                onStall()
            }
        }
    }
}
