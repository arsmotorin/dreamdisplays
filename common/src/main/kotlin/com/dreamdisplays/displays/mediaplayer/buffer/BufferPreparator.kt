package com.dreamdisplays.displays.mediaplayer.buffer

import com.dreamdisplays.displays.mediaplayer.managers.BrightManager
import com.dreamdisplays.displays.mediaplayer.utils.Buffer
import com.dreamdisplays.displays.mediaplayer.MediaPlayer
import com.dreamdisplays.displays.mediaplayer.controls.MediaPlayerControls
import com.dreamdisplays.displays.mediaplayer.utils.Scaler
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import org.freedesktop.gstreamer.FlowReturn
import org.freedesktop.gstreamer.elements.AppSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.RejectedExecutionException
import kotlin.math.abs

class BufferPreparator(val mp: MediaPlayer) {
    val bufferLock = Any()
    internal var convertBuffer: ByteBuffer? = null
    internal var convertBufferSize = 0
    internal var scaleBuffer: ByteBuffer? = null
    internal var scaleBufferSize = 0
    internal var lastFrameTime: Long = 0
    internal var preparedBuffer: ByteBuffer? = null

    internal var currentFrameBuffer: ByteBuffer? = null
    internal var currentFrameWidth = 0
    internal var currentFrameHeight = 0
    internal var preparedW = 0
    internal var preparedH = 0
    internal var frameReady = false
    internal var hasInitialFrame = false

    fun prepareBufferAsync() {
        synchronized(bufferLock) { if (currentFrameBuffer == null) return }

        val now = System.nanoTime()
        if (now - lastFrameTime < MediaPlayerControls.MIN_FRAME_INTERVAL_NS) {
            LoggingManager.info("[MediaPlayer] Frame skipped (rate limit)")
            return
        }
        lastFrameTime = now

        try {
            mp.frameExecutor.submit { this.prepareBuffer() }
        } catch (_: RejectedExecutionException) {
            LoggingManager.warn("[MediaPlayer] Frame task rejected")
        }
    }

    private fun prepareBuffer() {
        synchronized(bufferLock) {
            // long startNs = System.nanoTime();

            val targetW = mp.display.textureWidth
            val targetH = mp.display.textureHeight
            if (targetW == 0 || targetH == 0 || currentFrameBuffer == null) return

            val needsScaling = currentFrameWidth != targetW || currentFrameHeight != targetH
            val bufferSize = targetW * targetH * 4

            if (needsScaling) {
                if (scaleBuffer == null || scaleBufferSize < bufferSize) {
                    scaleBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                    scaleBufferSize = bufferSize
                }

                Scaler.scaleRGBA(
                    currentFrameBuffer!!, currentFrameWidth, currentFrameHeight,
                    scaleBuffer!!, targetW, targetH, MediaPlayerControls.brightness
                )

                preparedBuffer = scaleBuffer
            } else {
                if (convertBuffer == null || convertBufferSize < bufferSize) {
                    convertBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                    convertBufferSize = bufferSize
                }

                currentFrameBuffer!!.rewind()
                convertBuffer!!.clear()

                if (abs(MediaPlayerControls.brightness - 1.0) < 1e-5) {
                    Buffer.copyBuffer(currentFrameBuffer!!, convertBuffer!!, bufferSize)
                } else {
                    BrightManager.applyBrightnessToBuffer(
                        currentFrameBuffer!!, convertBuffer!!, bufferSize,
                        MediaPlayerControls.brightness
                    )
                }

                preparedBuffer = convertBuffer
            }

            preparedW = targetW
            preparedH = targetH
            frameReady = true
            hasInitialFrame = true

            // long elapsedNs = System.nanoTime() - startNs;
            // LoggingManager.info("[MediaPlayer] prepareBuffer: " + elapsedNs + "ns");
            Minecraft.getInstance().execute { mp.display.fitTexture() }
        }
    }

    fun configureVideoSink(sink: AppSink) {
        LoggingManager.info("[MediaPlayer] Configuring AppSink")
        sink.set("emit-signals", true)
        sink.set("sync", true)
        sink.set("max-buffers", 1)
        sink.set("drop", true)
        sink.connect(AppSink.NEW_SAMPLE { elem: AppSink? ->
            val s = elem!!.pullSample()
            if (s == null || !MediaPlayerControls.captureSamples) {
                LoggingManager.warn("[MediaPlayer] pullSample returned null or capture disabled")
                return@NEW_SAMPLE FlowReturn.OK
            }
            try {
                val st = s.caps.getStructure(0)
                val w = st.getInteger("width")
                val h = st.getInteger("height")
                val newBuffer = Buffer.sampleToBuffer(s)

                synchronized(bufferLock) {
                    currentFrameWidth = w
                    currentFrameHeight = h
                    currentFrameBuffer = newBuffer
                }
                prepareBufferAsync()
            } catch (e: Exception) {
                LoggingManager.error("[MediaPlayer] Error in NEW_SAMPLE handler", e)
            } finally {
                s.dispose()
            }
            FlowReturn.OK
        })
    }
}
