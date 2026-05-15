package com.dreamdisplays.media

import com.dreamdisplays.ytdlp.YtStream
import kotlin.math.abs

/**
 * Pure helpers for parsing stream quality labels and picking video / audio
 * tracks from the candidate lists returned by `yt-dlp`.
 */
object MediaStreamSelector {


    fun parseQuality(stream: YtStream): Int = parseQualityValue(stream.resolution, Int.MAX_VALUE)


    fun parseQualityValue(raw: String?, fallback: Int): Int {
        if (raw == null) return fallback
        var i = 0
        val n = raw.length
        while (i < n && !raw[i].isDigit()) i++
        val start = i
        while (i < n && raw[i].isDigit()) i++
        if (start == i) return fallback
        return raw.substring(start, i).toIntOrNull() ?: fallback
    }


    fun qualityToDims(quality: Int): IntArray = when {
        quality <= 240 -> intArrayOf(426, 240)
        quality <= 360 -> intArrayOf(640, 360)
        quality <= 480 -> intArrayOf(854, 480)
        quality <= 720 -> intArrayOf(1280, 720)
        quality <= 1080 -> intArrayOf(1920, 1080)
        quality <= 1440 -> intArrayOf(2560, 1440)
        else -> intArrayOf(3840, 2160)
    }


    fun pickVideo(streams: List<YtStream>?, target: Int): YtStream? {
        if (streams.isNullOrEmpty()) return null
        return streams.asSequence()
            .filter { it.resolution != null }
            .minWithOrNull(
                compareBy<YtStream> { abs(parseQuality(it) - target) }
                    .thenBy { if (it.isMuxed) 0 else 1 }
                    .thenBy { if (it.hasAudio()) 0 else 1 }
            )
    }


    fun pickAudio(audioStreams: List<YtStream>, lang: String, chosenVideo: YtStream?): YtStream? {
        val audioOnly = audioStreams.filter { !it.hasVideo() }

        audioOnly.lastOrNull { matchesLanguage(it, lang) }?.let { return it }
        audioOnly.lastOrNull { it.audioTrackId == null || it.audioTrackId == "und" }?.let { return it }
        audioOnly.lastOrNull()?.let { return it }
        if (chosenVideo != null && chosenVideo.hasAudio()) return chosenVideo
        audioStreams.lastOrNull { matchesLanguage(it, lang) }?.let { return it }
        return audioStreams.firstOrNull()
    }


    fun matchesLanguage(stream: YtStream, lang: String): Boolean =
        (stream.audioTrackId?.contains(lang) == true) ||
                (stream.audioTrackName?.contains(lang) == true)
}
