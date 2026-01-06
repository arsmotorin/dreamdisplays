package com.dreamdisplays.datatypes

import com.dreamdisplays.managers.DisplayManager.getDisplayData
import org.jspecify.annotations.NullMarked
import java.lang.System.nanoTime
import java.util.*

/**
 * Class to manage the state data of a display.
 *
 * @property id The unique identifier of the display.
 * @property displayData The display data.
 * @property paused Boolean indicating if the display is paused.
 * @property lastReportedTime The last reported time of the display.
 * @property lastReportedTimestamp The timestamp of the last report.
 * @property limitTime The limit time for the display.
 *
 * @param id The unique identifier of the display.
 *
 * @throws IllegalStateException if the display data is not found for the given ID.
 *
 */
@NullMarked
class StateData(private val id: UUID?) {
    // TODO: handle null id gracefully in the future
    // check(id != null) { "ID cannot be null" }
    // check(getDisplayData(id) != null) { "Display data not found for id: $id" }
    var displayData: DisplayData = getDisplayData(id)!!

    private var paused = false
    private var lastReportedTime: Long = 0
    private var lastReportedTimestamp: Long = 0
    private var limitTime: Long = 0

    fun update(packet: SyncData) {
        paused = packet.currentState
        lastReportedTime = packet.currentTime
        lastReportedTimestamp = nanoTime()
        limitTime = packet.limitTime
    }

    fun createPacket(): SyncData {
        val nanos = nanoTime()
        val currentTime = if (paused) {
            lastReportedTime
        } else {
            lastReportedTime + (nanos - lastReportedTimestamp)
        }

        if (limitTime == 0L) displayData.duration?.let { limitTime = it }

        val time = if (limitTime > 0) currentTime % limitTime else currentTime
        return SyncData(id, true, paused, time, limitTime)
    }
}
