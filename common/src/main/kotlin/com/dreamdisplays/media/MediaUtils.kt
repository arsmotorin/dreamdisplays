package com.dreamdisplays.media

import java.io.IOException
import java.io.InputStream

/** Stateless helpers used by the media pipeline. */
object MediaUtils {

    private val BORING_STDERR = arrayOf(
        "Broken pipe",
        "Error muxing a packet",
        "Error submitting a packet to the muxer",
        "Error writing trailer",
        "Error closing file",
        "Terminating thread with return code",
        "Task finished with error code",
        "Last message repeated",
    )

    private val TRANSIENT_MARKERS = arrayOf(
        "403", "Forbidden",
        "404", "Not Found",
        "429", "Too Many Requests",
        "503", "Service Unavailable",
        "502", "Bad Gateway",
        "Connection reset",
        "Connection refused",
        "Connection timed out",
        "Network is unreachable",
        "Operation timed out",
        "Server returned",
    )


    fun isInterestingStderr(line: String): Boolean = BORING_STDERR.none { it in line }


    fun truncate(s: String?): String = when {
        s == null -> "null"
        s.length <= 120 -> s
        else -> s.substring(0, 120) + "...(${s.length})"
    }


    @Throws(IOException::class)
    fun readFull(input: InputStream, buf: ByteArray, len: Int): Int {
        var total = 0
        while (total < len) {
            val n = input.read(buf, total, len - total)
            if (n < 0) return total
            total += n
        }
        return total
    }


    fun isTransientError(stderr: String): Boolean = TRANSIENT_MARKERS.any { it in stderr }
}
