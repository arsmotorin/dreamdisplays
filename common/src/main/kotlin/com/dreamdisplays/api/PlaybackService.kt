package com.dreamdisplays.api

import kotlin.time.Duration

interface PlaybackService {
    fun play(displayId: DisplayId)
    fun pause(displayId: DisplayId)
    fun stop(displayId: DisplayId)
    fun seek(displayId: DisplayId, position: Duration)
    fun setVolume(displayId: DisplayId, volume: Float)
    fun mute(displayId: DisplayId, muted: Boolean)
    fun getState(displayId: DisplayId): DisplayRuntimeState
    fun restart(displayId: DisplayId)
}
