package com.dreamdisplays.screen.mediaplayer.player

/**
 * Configuration for the media player.
 */
data class MediaPlayerConfig(
    val youtubeUrl: String,
    val language: String = "en",
    val initialVolume: Double = 0.8,
    val initialQuality: VideoQuality = VideoQuality.P720,
    val maxSoundDistance: Int = 32
)
