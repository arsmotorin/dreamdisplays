package com.dreamdisplays.ytdlp

/**
 * `YouTube` stream information, as returned by `yt-dlp`. It includes methods to check if the stream has video / audio and
 * if it's muxed (because it can be a stream or première or etc.)
 */
class YtStream(
    val url: String,
    private val mimeType: String,
    val container: String?,
    val protocol: String?,
    val resolution: String?,
    val audioTrackId: String?,
    val audioTrackName: String?,
    private val vcodec: String?,
    private val acodec: String?,
    private val fps: Double?,
    private val tbrKbps: Double?,
    private val hasVideo: Boolean,
    private val hasAudio: Boolean,
    val isLive: Boolean,
    val isSeekable: Boolean,
    val durationNanos: Long,
) {

    fun hasVideo(): Boolean = hasVideo
    fun hasAudio(): Boolean = hasAudio
    val isMuxed: Boolean get() = hasVideo && hasAudio

    override fun toString(): String = buildString {
        append("YtStream{").append(mimeType)
        container?.let { append(" container=").append(it) }
        protocol?.let { append(" proto=").append(it) }
        resolution?.let { append(' ').append(it) }
        vcodec?.takeIf { it != "none" }?.let { append(" v=").append(it) }
        acodec?.takeIf { it != "none" }?.let { append(" a=").append(it) }
        fps?.let { append(' ').append(it).append("fps") }
        tbrKbps?.let { append(' ').append(it).append("kbps") }
        audioTrackId?.let { append(" lang=").append(it) }
        if (isLive) append(" live")
        if (!isSeekable) append(" nonseekable")
        append('}')
    }
}
