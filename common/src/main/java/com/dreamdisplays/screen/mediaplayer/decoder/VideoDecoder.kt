package com.dreamdisplays.screen.mediaplayer.decoder

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * Audio-only decoder using FFmpegFrameGrabber.
 */
class VideoDecoder(url: String) : FrameProvider {

    // FFmpegFrameGrabber setup for video stream
    private val grabber = FFmpegFrameGrabber(url).apply {
        setOption("user_agent", "ANDROID_VR")
        setOption("referer", "https://www.youtube.com")
        setOption("headers", "Origin: https://www.youtube.com\r\n")
        setOption("timeout", "10000000")
        start()
    }

    // Return null for audio frames because this is a video-only decoder
    override fun nextAudioFrame(): Frame? = null

    // Grab and return the next video frame
    override fun nextVideoFrame(): Frame? = try {
        grabber.grabImage()
    } catch (_: Exception) {
        null
    }

    // Controls
    override fun seek(micros: Long) = grabber.setTimestamp(micros)
    override fun release() = grabber.release()
    override val timestamp: Long get() = grabber.timestamp
}
