package com.dreamdisplays.screen.mediaplayer.decoder

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * Audio-only decoder using FFmpegFrameGrabber.
 */
class AudioDecoder(url: String) : FrameProvider {

    // FFmpegFrameGrabber setup for audio stream
    internal val grabber = FFmpegFrameGrabber(url).apply {
        setOption("user_agent", "ANDROID_VR")
        setOption("referer", "https://www.youtube.com")
        setOption("headers", "Origin: https://www.youtube.com\r\n")
        setOption("timeout", "10000000")
        start()
    }

    // Return null for video frames because this is an audio-only decoder
    override fun nextVideoFrame(): Frame? = null

    // Grab and return the next audio frame
    override fun nextAudioFrame(): Frame? = try {
        grabber.grabSamples()
    } catch (_: Exception) {
        null
    }

    // Controls
    override fun seek(micros: Long) = grabber.setTimestamp(micros)
    override fun release() = grabber.release()
    override val timestamp: Long get() = grabber.timestamp
}
