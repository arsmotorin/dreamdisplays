package com.dreamdisplays.media.api

enum class MediaStreamType {
    VIDEO,
    AUDIO,
    VIDEO_AUDIO;

    val hasVideo: Boolean get() = this == VIDEO || this == VIDEO_AUDIO
    val hasAudio: Boolean get() = this == AUDIO || this == VIDEO_AUDIO
}
