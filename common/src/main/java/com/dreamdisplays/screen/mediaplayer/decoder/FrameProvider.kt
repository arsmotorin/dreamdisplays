package com.dreamdisplays.screen.mediaplayer.decoder

import org.bytedeco.javacv.Frame

/**
 * Provides video and audio frames from a media source.
 */
interface FrameProvider {
    fun nextVideoFrame(): Frame?
    fun nextAudioFrame(): Frame?
    fun seek(micros: Long)
    fun release()
    val timestamp: Long
}
