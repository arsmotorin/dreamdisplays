package com.dreamdisplays.datatypes

import com.dreamdisplays.managers.DisplayManager
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Manages the state of a display, including its paused status and timing information.
 */
@NullMarked
class State(private val id: UUID?) {
    private var paused = false
    private var lastReportedTime: Long = 0
    private var lastReportedTimeTimestamp: Long = 0
    private var limitTime: Long = 0
    var displayData: Display =
        DisplayManager.getDisplayData(id) ?: throw IllegalStateException("Display data not found for id: $id")

    // Updates the state based on the received Sync packet
    fun update(packet: Sync) {
        this.paused = packet.currentState
        this.lastReportedTime = packet.currentTime
        this.lastReportedTimeTimestamp = System.nanoTime()
        limitTime = packet.limitTime
    }

    // Creates a Sync packet representing the current state
    fun createPacket(): Sync {
        val nanos = System.nanoTime()
        var currentTime: Long

        if (paused) {
            currentTime = lastReportedTime
        } else {
            val elapsed = nanos - lastReportedTimeTimestamp
            currentTime = lastReportedTime + elapsed
        }

        if (limitTime == 0L) {
            displayData.duration?.let { limitTime = it }
        }

        if (limitTime > 0) {
            currentTime %= limitTime
        }

        return Sync(id, true, paused, currentTime, limitTime)
    }
}
