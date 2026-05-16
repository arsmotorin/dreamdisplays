package com.dreamdisplays.player

/** Playback state of a [MediaPlayer] instance. */
enum class PlaybackState {
    IDLE,
    INITIALIZING,
    PLAYING,
    PAUSED,
    RESTARTING,
    STOPPED,
    ERROR,
}
