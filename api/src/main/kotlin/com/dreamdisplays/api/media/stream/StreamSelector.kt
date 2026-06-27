package com.dreamdisplays.api.media.stream

import com.dreamdisplays.api.DreamDisplaysUnstableApi

@DreamDisplaysUnstableApi
interface StreamSelector {
    fun select(streams: List<MediaStream>, preferences: StreamPreferences): StreamSet
}
