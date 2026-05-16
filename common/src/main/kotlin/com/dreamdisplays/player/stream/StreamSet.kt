package com.dreamdisplays.player.stream

import com.dreamdisplays.ytdlp.YtStream

/**
 * All currently-selected and available streams for one playback session.
 * Replaced atomically on quality change or re-initialization; never partially updated.
 */
internal data class StreamSet(
    val availableVideo: List<YtStream>,
    val availableAudio: List<YtStream>,
    val currentVideo: YtStream,
    val currentAudio: YtStream,
)
