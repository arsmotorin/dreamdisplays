package com.dreamdisplays.datatypes

import com.dreamdisplays.managers.Display
import java.util.*

class State(private val id: UUID?) {
    private var paused = false
    private var lastReportedTime: Long = 0
    private var lastReportedTimeTimestamp: Long = 0
    private var limitTime: Long = 0
    var displayData: com.dreamdisplays.datatypes.Display = Display.getDisplayData(id)!!

    fun update(packet: Sync) {
        this.paused = packet.currentState
        this.lastReportedTime = packet.currentTime
        this.lastReportedTimeTimestamp = System.nanoTime()
        limitTime = packet.limitTime
    }

    fun createPacket(): Sync {
        val nanos = System.nanoTime()
        var currentTime: Long

        if (paused) {
            currentTime = lastReportedTime
        } else {
            val elapsed = nanos - lastReportedTimeTimestamp
            currentTime = lastReportedTime + elapsed
        }

        if (limitTime == 0L && displayData.duration != null) {
            limitTime = displayData.duration!!
        }

        if (limitTime > 0) {
            currentTime %= limitTime
        }

        return Sync(id, true, paused, currentTime, limitTime)
    }
}
