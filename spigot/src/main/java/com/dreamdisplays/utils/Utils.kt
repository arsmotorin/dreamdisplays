package com.dreamdisplays.utils

import org.jspecify.annotations.NullMarked
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

@NullMarked
object Utils {
    fun extractVideo(youtubeUrl: String): String? {
        try {
            val uri = URI(youtubeUrl)
            val query = uri.query // Takes part after "?"
            if (query != null) {
                for (param in query.split("&")) {
                    val pair = param.split("=", limit = 2)
                    if (pair.size == 2 && pair[0] == "v") {
                        return pair[1]
                    }
                }
            }
            // If youtu.be/ID
            val host = uri.host
            if (host != null && host.contains("youtu.be")) {
                val path = uri.path
                if (path != null && path.length > 1) {
                    return path.substring(1)
                }
            } else if (host != null && host.contains("youtube.com")) {
                val path = uri.path
                if (path != null && path.contains("shorts")) {
                    return path.split("/").lastOrNull { it.isNotEmpty() }
                }
            }
        } catch (_: URISyntaxException) {
            // Invalid URL, fall back to regex parsing
        }

        val regex = "(?<=([?&]v=))[^#&?]*"
        val m = Pattern.compile(regex).matcher(youtubeUrl)
        return if (m.find()) m.group() else null
    }

    fun sanitize(raw: String?): String? {
        if (raw == null) {
            return null
        }
        return raw.trim { it <= ' ' }.replace("[^0-9A-Za-z+.-]".toRegex(), "")
    }
}
