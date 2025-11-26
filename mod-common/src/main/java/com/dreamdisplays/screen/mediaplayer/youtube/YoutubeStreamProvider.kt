package com.dreamdisplays.screen.mediaplayer.youtube

import com.dreamdisplays.screen.mediaplayer.player.VideoQuality
import com.github.felipeucelli.javatube.Stream
import com.github.felipeucelli.javatube.Youtube
import me.inotsleep.utils.logging.LoggingManager

/**
 * Provides video and audio streams from a YouTube URL.
 */
class YoutubeStreamProvider(url: String) {
    private val yt: Youtube
    private val allStreams by lazy { yt.streams().all }

    // Initialize the YouTube instance
    init {
        try {
            yt = Youtube(url, "ANDROID_VR")
        } catch (e: Throwable) {
            LoggingManager.error("YoutubeStreamProvider: Failed to create Youtube instance", e)
            throw e
        }
    }

    // Get video streams
    fun getVideoStreams(): List<Stream> = allStreams.filter { it.mimeType.startsWith("video/") }

    // Get audio streams
    fun getAudioStreams(): List<Stream> = allStreams.filter { it.mimeType.startsWith("audio/") }

    // Select the best video stream within the specified quality
    fun selectBestVideo(maxQuality: VideoQuality): Stream? {
        return getVideoStreams()
            .mapNotNull {
                val h = VideoQuality.fromString(it.resolution ?: "")?.height ?: 0
                if (h <= maxQuality.height) it to h else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    // Select audio stream based on preferred language
    fun selectAudio(preferredLang: String): Stream? {
        return getAudioStreams().firstOrNull {
            val id = it.audioTrackId ?: ""
            val name = it.audioTrackName ?: ""
            id.contains(preferredLang, ignoreCase = true) || name.contains(preferredLang, ignoreCase = true)
        } ?: getAudioStreams().firstOrNull()
    }

    // Get list of available video qualities
    fun getAvailableQualities(): List<Int> {
        return getVideoStreams()
            .mapNotNull { stream ->
                stream.resolution?.let { resolution ->
                    VideoQuality.fromString(resolution)?.height
                }
            }
            .distinct()
            .sorted()
    }
}
