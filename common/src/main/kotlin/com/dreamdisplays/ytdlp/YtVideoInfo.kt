package com.dreamdisplays.ytdlp

/**
 * Data class representing `YouTube` video information, as returned by `yt-dlp`. It includes methods to format the
 * duration, view count, and like count.
 */
class YtVideoInfo constructor(
    val id: String,
    val title: String,
    val uploader: String?,
    private val durationSec: Long?,
    private val viewCount: Long?,
    private val likeCount: Long? = null,
    val publishedText: String? = null,
    private val publishedDaysAgo: Int? = null,
) {

    fun isRecent(daysWindow: Int): Boolean =
        publishedDaysAgo != null && publishedDaysAgo >= 0 && publishedDaysAgo <= daysWindow

    fun getWatchUrl(): String = "https://www.youtube.com/watch?v=$id"

    fun getThumbnailUrl(): String = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

    fun formatDuration(): String {
        val s = durationSec ?: return ""
        if (s <= 0) return ""
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%d:%02d", m, sec)
    }

    fun formatViews(): String {
        val v = viewCount ?: return ""
        if (v <= 0) return ""
        return when {
            v >= 1_000_000_000L -> String.format("%.1fB views", v / 1_000_000_000.0)
            v >= 1_000_000L -> String.format("%.1fM views", v / 1_000_000.0)
            v >= 1_000L -> String.format("%.1fK views", v / 1_000.0)
            else -> "$v views"
        }
    }

    fun formatLikes(): String {
        val l = likeCount ?: return ""
        if (l <= 0) return ""
        return when {
            l >= 1_000_000_000L -> String.format("%.1fB", l / 1_000_000_000.0)
            l >= 1_000_000L -> String.format("%.1fM", l / 1_000_000.0)
            l >= 1_000L -> String.format("%.1fK", l / 1_000.0)
            else -> l.toString()
        }
    }
}
