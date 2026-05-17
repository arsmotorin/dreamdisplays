package com.dreamdisplays.player.preparation

import com.dreamdisplays.player.stream.MediaStreamSelector
import com.dreamdisplays.player.stream.StreamSet
import com.dreamdisplays.utils.GeneralUtil
import com.dreamdisplays.ytdlp.YtDlp
import com.dreamdisplays.ytdlp.YtStream

/**
 * Fetches stream metadata via `yt-dlp` and selects the best video and audio tracks.
 * Runs on a background thread; throws on failure so the caller can handle retries.
 */
internal object MediaPreparationService {
    /**
     * Resolves the video ID, fetches streams, analyses metadata, and picks the best tracks.
     *
     * @param url     raw YouTube URL
     * @param lang    preferred audio language (empty = default)
     * @param quality preferred video quality string, e.g. "720p"
     * @throws IllegalArgumentException if the video ID cannot be extracted from [url]
     * @throws IllegalStateException    if no usable streams are found
     */
    fun prepare(url: String, lang: String, quality: String): PreparedMedia {
        val videoId = GeneralUtil.extractVideoId(url)?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Could not extract video ID from URL: $url.")

        val cleanUrl = "https://www.youtube.com/watch?v=$videoId"
        val all = YtDlp.fetch(cleanUrl).takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("No streams available for $cleanUrl.")

        val isLive = all.any(YtStream::isLive)
        val isSeekable = !isLive && all.any(YtStream::isSeekable)
        val durationNanos = all.maxOfOrNull(YtStream::durationNanos) ?: 0L

        val videoStreams = all.filter(YtStream::hasVideo)
        val audioStreams = all.filter(YtStream::hasAudio)

        val requestedQuality = MediaStreamSelector.parseQualityValue(quality, 720)
        val pickedVideo = MediaStreamSelector.pickVideo(videoStreams, requestedQuality)
            ?: videoStreams.firstOrNull()
            ?: throw IllegalStateException("No usable video stream for $cleanUrl.")
        val pickedAudio = MediaStreamSelector.pickAudio(audioStreams, lang, pickedVideo)
            ?: throw IllegalStateException("No usable audio stream for $cleanUrl.")

        return PreparedMedia(
            streamSet = StreamSet(videoStreams, audioStreams, pickedVideo, pickedAudio),
            isLive = isLive,
            isSeekable = isSeekable,
            durationNanos = durationNanos,
        )
    }
}
