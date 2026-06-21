package com.dreamdisplays.media.api

import com.dreamdisplays.api.DisplayId

/** Hands out [MediaSession] views onto playing displays. */
interface MediaSessionManager {
    fun open(displayId: DisplayId): MediaSession?
    fun activeSessions(): List<MediaSession>
}
