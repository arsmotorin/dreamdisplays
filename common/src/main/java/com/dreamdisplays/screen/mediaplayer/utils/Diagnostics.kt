package com.dreamdisplays.screen.mediaplayer.utils

import me.inotsleep.utils.logging.LoggingManager
import java.util.concurrent.atomic.AtomicLong

object Diagnostics {
    private var lastLogTime = 0L

    private val videoFramesDecoded = AtomicLong(0)
    private val videoFramesDropped = AtomicLong(0)
    private val videoFramesRendered = AtomicLong(0)

    private val audioFramesDecoded = AtomicLong(0)
    private val audioFramesWritten = AtomicLong(0)

    private val lastVideoTimestamp = AtomicLong(0)
    private val lastAudioTimestamp = AtomicLong(0)

    fun videoFrameDecoded(timestampMicros: Long) {
        videoFramesDecoded.incrementAndGet()
        lastVideoTimestamp.set(timestampMicros)
    }

    fun videoFrameDropped() = videoFramesDropped.incrementAndGet()
    fun videoFrameRendered() = videoFramesRendered.incrementAndGet()

    fun audioFrameDecoded(timestampMicros: Long) {
        audioFramesDecoded.incrementAndGet()
        lastAudioTimestamp.set(timestampMicros)
    }

    fun audioFrameWritten() = audioFramesWritten.incrementAndGet()

    fun tick(currentTimeMicros: Long) {
        val now = System.currentTimeMillis()
        if (now - lastLogTime < 5000) return

        lastLogTime = now

        val vDec = videoFramesDecoded.get()
        val vRend = videoFramesRendered.get()
        val vDrop = videoFramesDropped.get()
        val aDec = audioFramesDecoded.get()
        val aWrt = audioFramesWritten.get()

        val vFps = (vDec * 1000.0 / 5000.0).toInt()
        val aFps = (aDec * 1000.0 / 5000.0).toInt()

        LoggingManager.info(
            """
            ╭───── MediaPlayer Diagnostics ─────╮
            │ Video:  decoded=${vDec.pad(6)} rendered=${vRend.pad(6)} dropped=${vDrop.pad(4)} ~${vFps}fps
            │ Audio:  decoded=${aDec.pad(6)} written=${aWrt.pad(6)} ~${aFps}fps
            │ Time:   video=${(lastVideoTimestamp.get() / 1000)}ms  audio=${(lastAudioTimestamp.get() / 1000)}ms  pos=${currentTimeMicros / 1000}ms
            │ Drift:  ${(lastAudioTimestamp.get() - lastVideoTimestamp.get()).let { if (it > 0) "+$it" else "$it" }}µs
            ╰────────────────────────────────────╯
            """.trimIndent()
        )

        videoFramesDecoded.set(0)
        videoFramesDropped.set(0)
        videoFramesRendered.set(0)
        audioFramesDecoded.set(0)
        audioFramesWritten.set(0)
    }

    private fun Long.pad(width: Int) = toString().padStart(width)
}
