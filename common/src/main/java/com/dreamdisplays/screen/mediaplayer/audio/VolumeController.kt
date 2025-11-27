package com.dreamdisplays.screen.mediaplayer.audio

import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * Controls the volume of a SourceDataLine.
 * Volume is set in a logarithmic scale to match human perception.
 */
class VolumeController(private val line: javax.sound.sampled.SourceDataLine) {
    fun setVolume(volume01: Double) {
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return
        val control = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        val db = (log10(kotlin.math.max(0.0001, volume01)) * 20.0).toFloat().coerceIn(control.minimum, control.maximum)
        control.value = db
    }
}
