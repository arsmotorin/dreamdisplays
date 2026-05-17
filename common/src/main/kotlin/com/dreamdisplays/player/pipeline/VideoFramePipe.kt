package com.dreamdisplays.player.pipeline

import com.dreamdisplays.player.MediaPlayer
import com.dreamdisplays.player.util.MediaUtil
import com.dreamdisplays.player.util.daemon
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Producer-consumer frame buffer and FFmpeg video reader loop.
 *
 * The reader thread fills a "spare" buffer, then atomically swaps it into [readyBufferRef];
 * the render thread reads from [readyBufferRef] without blocking the reader.
 */
internal class VideoFramePipe(private val debugLabel: String) {
    companion object {
        /** Default frame rate when the source doesn't report one or reports an invalid one. */
        private const val DEFAULT_FPS = 30.0

        /** Threshold under which we use busy-wait (spin) instead of sleep for precise timing. */
        private const val SPIN_THRESHOLD_NS = 2L * 1_000_000L

        /** Drop a frame when it's more than 80 ms behind the audio clock. */
        private const val DROP_THRESHOLD_NS = 80_000_000L
    }

    /** Updated by the reader thread on every frame; used by the watchdog to detect stalls. */
    val lastFrameReceivedNanos = AtomicLong(0)

    @Volatile var expectedW = 0
        private set
    @Volatile var expectedH = 0
        private set

    private val readyBufferRef = AtomicReference<ByteBuffer?>(null)
    private val frameAvailable = AtomicBoolean(false)

    private var uploadTotalNs = 0L
    private var uploadCount = 0

    /**
     * Returns true when a frame is available for upload. The render thread should call [updateFrame] as soon as possible
     * after this returns true, to minimize the chance of the reader thread overwriting the ready buffer before upload.
     */
    fun textureFilled(): Boolean = readyBufferRef.get() != null

    /**
     * Uploads the ready frame to [texture] if one is available.
     * [actualW] / [actualH] must match the dimensions this pipe was started with.
     *
     * Warning: this is one of the most expensive operations in the pipeline. It's critical to call this as soon as
     * possible after [textureFilled] returns true, to minimize the chance of the reader thread overwriting the ready
     * buffer before upload.
     *
     * You should also never call this method more than once per frame, or call it when [textureFilled] is false.
     * It does not block or wait for a frame to be ready, and it does not guarantee that the same frame will still be
     * ready by the time it executes.
     */
    fun updateFrame(texture: GpuTexture, actualW: Int, actualH: Int) {
        if (!frameAvailable.compareAndSet(true, false)) return
        val buf = readyBufferRef.get() ?: return
        if (actualW != expectedW || actualH != expectedH) return
        if (Minecraft.getInstance().window.isMinimized) return
        buf.rewind()
        val start = System.nanoTime()
        if (!texture.isClosed) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                texture, buf, NativeImage.Format.RGB,
                0, 0, 0, 0, texture.getWidth(0), texture.getHeight(0),
            )
        }
        if (MediaPlayer.DEBUG) {
            uploadTotalNs += System.nanoTime() - start
            MediaPlayer.framesToGpu.incrementAndGet()
            if (++uploadCount >= 60) {
                val avgMs = uploadTotalNs / 60 / 1_000_000.0
                LoggingManager.info("[VideoFramePipe $debugLabel] Upload avg. ${String.format("%.3f", avgMs)} ms / frame")
                uploadTotalNs = 0L; uploadCount = 0
            }
        }
    }

    /** Discards the current ready frame. Call when stopping or seeking. */
    fun clear() {
        frameAvailable.set(false)
        readyBufferRef.set(null)
    }

    /**
     * Starts the video reader thread and returns it (already running).
     *
     * @param seekOffsetNanos initial playback position (must match the FFmpeg `-ss` offset)
     * @param sourceFps       frame rate reported by yt-dlp for the chosen stream
     * @param getAudioClock   returns current audio position in nanos, or -1 if unavailable
     * @param onFirstFrame    called once when the first frame arrives (starts the wall clock)
     * @param getBrightness   returns current brightness multiplier (read per frame)
     * @param onEos           called when the stream ends with stderr output and EOS flag
     * @param fitTexture      posted to the Minecraft render queue after each frame swap
     */
    fun start(proc: Process, w: Int, h: Int, seekOffsetNanos: Long, sourceFps: Double, stopFlag: AtomicBoolean,
        terminated: AtomicBoolean, getAudioClock: () -> Long, onFirstFrame: () -> Unit, getBrightness: () -> Double,
        onEos: (stderr: String, normalEos: Boolean) -> Unit, fitTexture: Runnable,
    ): Thread {
        expectedW = w
        expectedH = h
        val frameNs = (1_000_000_000.0 / (sourceFps.takeIf { it > 1.0 } ?: DEFAULT_FPS)).toLong()
        return daemon(
            { read(proc, w, h, frameNs, seekOffsetNanos, stopFlag, terminated, getAudioClock, onFirstFrame, getBrightness, onEos, fitTexture) },
            "MediaPlayer-video",
        ).also { it.start() }
    }

    /**
     * Main loop of the video reader thread. Reads raw RGBA frames from [proc], applies brightness, and fills the ready buffer.
     */
    private fun read(proc: Process, w: Int, h: Int, frameNs: Long, seekOffsetNanos: Long, stopFlag: AtomicBoolean,
        terminated: AtomicBoolean, getAudioClock: () -> Long, onFirstFrame: () -> Unit, getBrightness: () -> Double,
        onEos: (stderr: String, normalEos: Boolean) -> Unit, fitTexture: Runnable,
    ) {
        val frameSize = w * h * 3
        val bufA = ByteBuffer.allocateDirect(frameSize).order(ByteOrder.nativeOrder())
        val bufB = ByteBuffer.allocateDirect(frameSize).order(ByteOrder.nativeOrder())
        var spare: ByteBuffer = bufA

        var firstFrame = false
        var videoPts = seekOffsetNanos

        val stderrBuf = StringBuilder()
        val stderrThread = daemon({
            try {
                BufferedReader(InputStreamReader(proc.errorStream)).use { r ->
                    r.lineSequence().forEach { line ->
                        synchronized(stderrBuf) { stderrBuf.append(line).append('\n') }
                        if (MediaUtil.isInterestingStderr(line)) {
                            LoggingManager.warn("[FFmpeg[V] $debugLabel] $line")
                        }
                    }
                }
            } catch (_: IOException) {}
        }, "MediaPlayer-vstderr").also { it.start() }

        var normalEos = false
        val mc = Minecraft.getInstance()

        try {
            Channels.newChannel(proc.inputStream).use { channel ->
                while (!terminated.get() && !stopFlag.get()) {
                    spare.clear()
                    while (spare.hasRemaining()) {
                        val n = channel.read(spare)
                        if (n < 0) { normalEos = true
                            break
                        }
                    }
                    if (normalEos) break
                    spare.flip()

                    lastFrameReceivedNanos.set(System.nanoTime())
                    if (!firstFrame) {
                        firstFrame = true
                        onFirstFrame()
                        if (MediaPlayer.DEBUG) LoggingManager.info("[VideoFramePipe $debugLabel] First frame ${w}x${h}.")
                    }

                    val audioClock = getAudioClock()
                    val diff = videoPts - if (audioClock >= 0) audioClock else videoPts
                    if (diff > 0) {
                        val target = System.nanoTime() + diff
                        if (diff > SPIN_THRESHOLD_NS) {
                            Thread.sleep(diff / 1_000_000)
                        } else {
                            while (System.nanoTime() < target) {
                                Thread.onSpinWait()
                            }
                        }
                    }
                    if (diff < -DROP_THRESHOLD_NS) {
                        if (MediaPlayer.DEBUG) MediaPlayer.framesDropped.incrementAndGet()
                        videoPts += frameNs
                        continue
                    }

                    if (!MediaPlayer.captureSamples) { videoPts += frameNs; continue }

                    val prev = readyBufferRef.getAndSet(spare)
                    spare = when {
                        prev === bufA || prev === bufB -> prev
                        spare === bufA -> bufB
                        else -> bufA
                    }
                    frameAvailable.set(true)
                    if (MediaPlayer.DEBUG) MediaPlayer.samplesIn.incrementAndGet()
                    mc.execute(fitTexture)

                    videoPts += frameNs
                }
            }
        } catch (e: IOException) {
            if (MediaPlayer.DEBUG && !terminated.get() && !stopFlag.get()) {
                LoggingManager.warn("[VideoFramePipe $debugLabel] Read: ${e.message}")
            }
        }

        var exitCode = -1
        if (normalEos) {
            try {
                val done = proc.waitFor(500, TimeUnit.MILLISECONDS)
                exitCode = if (done) proc.exitValue() else -1
                if (!done) proc.destroyForcibly()
            } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }

        if (!terminated.get() && !stopFlag.get()) {
            try { stderrThread.join(500) } catch (_: InterruptedException) {}
            val stderr = synchronized(stderrBuf) { stderrBuf.toString() }
            onEos(stderr, exitCode == 0)
        }
    }
}
