package com.dreamdisplays.media

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/**
 * Per-sample / pixel adjustments applied to raw RGBA video frames and S16LE
 * audio before they reach the GPU / audio line.
 */
object MediaBufferEffects {


    fun applyBrightness(buffer: ByteBuffer, brightness: Double) {
        if (abs(brightness - 1.0) < 1e-5) return
        buffer.rewind()
        while (buffer.remaining() >= 4) {
            val r = min(255.0, (buffer.get().toInt() and 0xFF) * brightness).toInt()
            val g = min(255.0, (buffer.get().toInt() and 0xFF) * brightness).toInt()
            val b = min(255.0, (buffer.get().toInt() and 0xFF) * brightness).toInt()
            val a = buffer.get()
            buffer.position(buffer.position() - 4)
            buffer.put(r.toByte()).put(g.toByte()).put(b.toByte()).put(a)
        }
        buffer.flip()
    }


    fun applyVolumeS16LE(buf: ByteArray, len: Int, volume: Double) {
        if (abs(volume - 1.0) < 1e-5) return
        var i = 0
        while (i + 1 < len) {
            val lo = buf[i].toInt() and 0xFF
            val hi = buf[i + 1].toInt()
            val s = (hi shl 8) or lo
            val scaled = (s * volume).toInt().coerceIn(-32768, 32767)
            buf[i] = (scaled and 0xFF).toByte()
            buf[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += 2
        }
    }
}
