package com.dreamdisplays.displays.mediaplayer.utils

import org.freedesktop.gstreamer.Sample
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Buffer {
    companion object {
        fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, bytes: Int) {
            val longs = bytes ushr 3
            val ints = (bytes and 7) ushr 2
            val remaining = bytes and 3

            // Copy 8 bytes at a time
            for (i in 0..<longs) {
                dst.putLong(src.getLong())
            }

            // Copy 4 bytes at a time
            for (i in 0..<ints) {
                dst.putInt(src.getInt())
            }

            // Copy remaining bytes
            for (i in 0..<remaining) {
                dst.put(src.get())
            }

            // Flip the destination buffer for reading
            dst.flip()
        }

        fun sampleToBuffer(sample: Sample): ByteBuffer {
            val buf = sample.buffer
            val bb = buf.map(false)

            if (bb.order() == ByteOrder.nativeOrder()) {
                val result = ByteBuffer.allocateDirect(bb.remaining()).order(ByteOrder.nativeOrder())
                result.put(bb)
                result.flip()
                buf.unmap()
                return result
            }

            val result = ByteBuffer.allocateDirect(bb.remaining()).order(ByteOrder.nativeOrder())
            bb.rewind()
            for (i in 0..<bb.remaining()) {
                result.put(bb.get())
            }
            result.flip()
            buf.unmap()
            return result
        }
    }
}