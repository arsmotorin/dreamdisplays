package com.dreamdisplays.api.media.session

import com.dreamdisplays.api.display.model.DisplayId
import com.dreamdisplays.api.media.source.MediaMetadata
import kotlin.time.Duration

interface MediaSession : AutoCloseable {
    val sessionId: String
    val displayId: DisplayId
    val state: MediaSessionState
    val currentPosition: Duration
    val duration: Duration?
    val metadata: MediaMetadata

    fun play()
    fun pause()
    fun seek(position: Duration)
    fun setVolume(volume: Float)
    fun on(listener: (MediaSessionEvent) -> Unit): AutoCloseable
}
