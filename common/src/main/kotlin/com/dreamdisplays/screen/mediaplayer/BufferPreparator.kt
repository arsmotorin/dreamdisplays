package com.dreamdisplays.screen.mediaplayer

import com.dreamdisplays.screen.mediaplayer.BufferUtils.Companion.copyBuffer
import com.dreamdisplays.screen.mediaplayer.MediaPlayer.Companion.MIN_FRAME_INTERVAL_NS
import com.dreamdisplays.screen.mediaplayer.MediaPlayer.Companion.captureSamples
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import org.freedesktop.gstreamer.FlowReturn
import org.freedesktop.gstreamer.elements.AppSink
import java.nio.ByteBuffer.allocateDirect
import java.nio.ByteOrder
import java.util.concurrent.RejectedExecutionException
import kotlin.math.abs

class BufferPreparator(private val mp: MediaPlayer) {
    fun prepareBufferAsync() {
        if (mp.currentFrameBuffer == null) return

        val now = System.nanoTime()
        if (now - mp.lastFrameTime < MIN_FRAME_INTERVAL_NS) {
            LoggingManager.info("[MediaPlayer] Frame skipped (rate limit)")
            return
        }
        mp.lastFrameTime = now

        try {
            mp.frameExecutor.submit { this.prepareBuffer() }
        } catch (_: RejectedExecutionException) {
            LoggingManager.warn("[MediaPlayer] Frame task rejected")
        }
    }

    private fun prepareBuffer() {
        // long startNs = System.nanoTime();

        val targetW = mp.screen.textureWidth
        val targetH = mp.screen.textureHeight
        if (targetW == 0 || targetH == 0 || mp.currentFrameBuffer == null) return

        val needsScaling = mp.currentFrameWidth != targetW || mp.currentFrameHeight != targetH
        val bufferSize = targetW * targetH * 4

        if (needsScaling) {
            if (mp.scaleBuffer == null || mp.scaleBufferSize < bufferSize) {
                mp.scaleBuffer = allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                mp.scaleBufferSize = bufferSize
            }

            Scaler.scaleRGBA(
                mp.currentFrameBuffer!!, mp.currentFrameWidth, mp.currentFrameHeight,
                mp.scaleBuffer!!, targetW, targetH, mp.brightness
            )

            mp.preparedBuffer = mp.scaleBuffer
        } else {
            if (mp.convertBuffer == null || mp.convertBufferSize < bufferSize) {
                mp.convertBuffer = allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                mp.convertBufferSize = bufferSize
            }

            mp.currentFrameBuffer!!.rewind()
            mp.convertBuffer!!.clear()

            if (abs(mp.brightness - 1.0) < 1e-5) {
                copyBuffer(mp.currentFrameBuffer!!, mp.convertBuffer!!, bufferSize)
            } else {
                BrightnessAdjuster.applyBrightnessToBuffer(mp.currentFrameBuffer!!, mp.convertBuffer!!, bufferSize, mp.brightness)
            }

            mp.preparedBuffer = mp.convertBuffer
        }

        mp.preparedW = targetW
        mp.preparedH = targetH
        mp.frameReady = true

        // long elapsedNs = System.nanoTime() - startNs;
        // LoggingManager.info("[MediaPlayer] prepareBuffer: " + elapsedNs + "ns");
        Minecraft.getInstance().execute { mp.screen.fitTexture() }
    }

    fun configureVideoSink(mp: MediaPlayer, sink: AppSink) {
        LoggingManager.info("[MediaPlayer] Configuring AppSink")
        sink.set("emit-signals", true)
        sink.set("sync", true)
        sink.set("max-buffers", 1)
        sink.set("drop", true)
        sink.connect(AppSink.NEW_SAMPLE { elem: AppSink? ->
            val s = elem!!.pullSample()
            if (s == null || !captureSamples) {
                LoggingManager.warn("[MediaPlayer] pullSample returned null or capture disabled")
                return@NEW_SAMPLE FlowReturn.OK
            }
            try {
                val st = s.caps.getStructure(0)
                val w = st.getInteger("width")
                val h = st.getInteger("height")
                mp.currentFrameWidth = w
                mp.currentFrameHeight = h
                mp.currentFrameBuffer = BufferUtils.sampleToBuffer(s)
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
