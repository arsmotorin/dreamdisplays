package com.dreamdisplays.screen.mediaplayer.player

/**
 * Represents various video quality options.
 * Each quality is defined by its vertical resolution in pixels.
 */
sealed class VideoQuality(val height: Int) {
    data object P144 : VideoQuality(144)
    data object P240 : VideoQuality(240)
    data object P360 : VideoQuality(360)
    data object P480 : VideoQuality(480)
    data object P720 : VideoQuality(720)
    data object P1080 : VideoQuality(1080)
    data object P1440 : VideoQuality(1440)
    data object P2160 : VideoQuality(2160)

    companion object {
        fun fromString(str: String): VideoQuality? {
            val height = str.replace(Regex("\\D"), "").toIntOrNull() ?: return null
            return when (height) {
                144 -> P144; 240 -> P240; 360 -> P360; 480 -> P480
                720 -> P720; 1080 -> P1080; 1440 -> P1440; 2160 -> P2160
                else -> null
            }
        }
    }

    // Returns a string representation of the video quality
    override fun toString() = "${height}p"
}
