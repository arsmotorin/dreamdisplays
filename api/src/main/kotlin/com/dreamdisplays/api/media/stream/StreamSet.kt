package com.dreamdisplays.api.media.stream

import com.dreamdisplays.api.DreamDisplaysUnstableApi

@DreamDisplaysUnstableApi
data class StreamSet(
    val videoStream: MediaStream?,
    val audioStream: MediaStream?,
    val allStreams: List<MediaStream>,
) {
    val isMuxed: Boolean get() = videoStream?.type == MediaStreamType.VIDEO_AUDIO
    val hasVideo: Boolean get() = videoStream != null
    val hasAudio: Boolean get() = audioStream != null
}
