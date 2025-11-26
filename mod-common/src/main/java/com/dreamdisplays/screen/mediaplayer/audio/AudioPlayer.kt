package com.dreamdisplays.screen.mediaplayer.audio

import com.dreamdisplays.screen.mediaplayer.decoder.FrameProvider
import com.dreamdisplays.screen.mediaplayer.utils.Diagnostics
import java.nio.ShortBuffer
import javax.sound.sampled.SourceDataLine

/**
 * Audio player that reads audio frames from a decoder and plays them through a SourceDataLine.
 */
class AudioPlayer(
    private val decoder: FrameProvider,
    private val line: SourceDataLine
) {
    private val volumeCtrl = VolumeController(line)
    private val buffer = ByteArray(8192)

    private val lock = Object()

    // Audio playback thread
    fun start(isPlaying: () -> Boolean, isPaused: () -> Boolean) =
        Thread({
            try {
                line.start()

                while (isPlaying()) {
                    synchronized(lock) {
                        while (isPaused() && isPlaying()) {
                            lock.wait(100)
                        }
                    }

                    if (!isPlaying()) break

                    val frame = decoder.nextAudioFrame()
                    if (frame == null) {
                        Thread.sleep(10)
                        continue
                    }

                    Diagnostics.audioFrameDecoded(frame.timestamp)
                    @Suppress("UNCHECKED_CAST")
                    val samples = frame.samples as? Array<ShortBuffer>
                    if (samples == null) {
                        continue
                    }

                    writeSamples(samples)
                    Diagnostics.audioFrameWritten()
                }

                line.drain()
                line.stop()
            } catch (e: Exception) {
                me.inotsleep.utils.logging.LoggingManager.error("AudioPlayer error", e)
            }
        }, "AudioPlayer").start()

    // Resume audio playback
    fun resume() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    // Write interleaved samples to the audio line from separate channel buffers
    private fun writeSamples(samples: Array<ShortBuffer>) {
        if (samples.isEmpty() || samples[0].limit() == 0) return

        val channels = samples.size
        val frameSize = samples[0].limit()
        val bytesNeeded = frameSize * channels * 2

        // Allocate larger buffer if needed
        val writeBuffer = if (bytesNeeded > buffer.size) {
            ByteArray(bytesNeeded)
        } else {
            buffer
        }

        var out = 0
        for (i in 0 until frameSize) {
            for (c in 0 until channels) {
                val s = samples[c][i].toInt()
                writeBuffer[out++] = s.toByte()
                writeBuffer[out++] = (s shr 8).toByte()
            }
        }

        line.write(writeBuffer, 0, out)
    }

    // Controls
    fun setVolume(volume: Double) = volumeCtrl.setVolume(volume)
    fun stop() {
        line.drain(); line.stop(); line.close(); decoder.release()
    }
}
