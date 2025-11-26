package com.dreamdisplays.screen.mediaplayer.sync

import java.util.concurrent.atomic.AtomicLong

/**
 * Synchronization between audio (pseudo) and video.
 * Audio management is in SourceDataLine.
 */
class AVSynchronizer {
    private val startTime = AtomicLong(0L)
    private var baseTimestampMicros = 0L // timestamp where play()

    // Start playback from current position
    fun start() {
        startTime.set(System.nanoTime())
    }

    // Pause playback
    fun resume() {
        val now = System.nanoTime()
        val elapsed = now - startTime.get()
        startTime.addAndGet(-elapsed)
    }

    // Seek to specific timestamp in microseconds
    fun seek(timestampMicros: Long) {
        baseTimestampMicros = timestampMicros
        startTime.set(System.nanoTime())
    }

    // Return sleep time for the given video timestamp in microseconds.
    // If negative, we are late and should drop the frame immediately,
    // and return how much we are late
    fun calculateSleepTime(videoTimestampMicros: Long): Long {
        val videoTime = (videoTimestampMicros - baseTimestampMicros) * 1000L
        val target = startTime.get() + videoTime
        val now = System.nanoTime()
        return (target - now) / 1_000_000L
    }

    // Current playback position in microseconds
    fun currentPositionMicros(): Long {
        val elapsedNs = System.nanoTime() - startTime.get()
        return baseTimestampMicros + elapsedNs / 1000L
    }

    // TODO: isDrifting
    // fun isDrifting(...): Boolean {
    // ...
}
