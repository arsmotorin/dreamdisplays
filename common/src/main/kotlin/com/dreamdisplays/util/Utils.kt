package com.dreamdisplays.util

import org.jspecify.annotations.NullMarked
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.regex.Pattern

/**
 * Helpers.
 */
@NullMarked
object Utils {

    // Detects the current operating system platform
    @JvmStatic
    fun detectPlatform(): String {
        val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        if (os.contains("win")) {
            return "windows"
        } else if (os.contains("mac")) {
            return "macos"
        } else if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            return "linux"
        }
        throw UnsupportedOperationException("Unsupported OS: $os")
    }

    // Extracts video ID from various YouTube URL formats
    @JvmStatic
    fun extractVideoId(youtubeUrl: String): String? {
        if (youtubeUrl.isEmpty()) {
            return null
        }

        try {
            val uri = URI(youtubeUrl)
            val host = uri.host

            // Handle youtu.be shortened URLs
            if (host != null && host.contains("youtu.be")) {
                val path = uri.path
                if (path != null && path.length > 1) {
                    var videoId = path.substring(1)
                    // Remove any query parameters from the video ID (including #, ?, &)
                    videoId = videoId.split("[?&#]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                    return videoId.ifEmpty { null }
                }
            }

            // Handle youtube.com URLs
            if (host != null && host.contains("youtube.com")) {
                val query = uri.query

                // Extract video ID from v parameter, ignoring start_radio, t, and other parameters
                if (query != null) {
                    for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                        val pair = param.split("=".toRegex(), limit = 2).toTypedArray()
                        if (pair.size == 2 && pair[0] == "v") {
                            // Clean the video ID from any fragments or additional characters
                            var videoId = pair[1]
                            videoId = videoId.split("[&#]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                            return videoId.ifEmpty { null }
                        }
                    }
                }

                // Handle YouTube Shorts URLs
                val path = uri.path
                if (path != null && path.contains("shorts")) {
                    val pathSegments = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (segment in pathSegments) {
                        if (!segment.isEmpty() && segment != "shorts") {
                            // Remove any query parameters and fragments
                            val videoId =
                                segment.split("[?&#]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                            return videoId.ifEmpty { null }
                        }
                    }
                }
            }
        } catch (_: URISyntaxException) {
        }

        // Additional pattern for direct video IDs (11 character alphanumeric)
        val directIdRegex = "[a-zA-Z0-9_-]{11}"
        val matcher = Pattern.compile(directIdRegex).matcher(youtubeUrl)
        if (matcher.find()) {
            return matcher.group()
        }

        return null
    }

    // Extracts timecode (t parameter) from YouTube URL in seconds
    @JvmStatic
    fun extractTimecode(youtubeUrl: String): Int {
        if (youtubeUrl.isEmpty()) {
            return 0
        }

        try {
            val uri = URI(youtubeUrl)
            val query = uri.query

            if (query != null) {
                for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val pair = param.split("=".toRegex(), limit = 2).toTypedArray()
                    if (pair.size == 2 && pair[0] == "t") {
                        return try {
                            pair[1].toInt()
                        } catch (_: NumberFormatException) {
                            0
                        }
                    }
                }
            }
        } catch (_: URISyntaxException) {
        }

        return 0
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readResource(resourcePath: String): String {
        Utils::class.java.getResourceAsStream(resourcePath).use { `in` ->
            if (`in` == null) {
                throw IOException(
                    "Can't find the resource: $resourcePath"
                )
            }
            val reader = BufferedReader(
                InputStreamReader(`in`)
            )
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            return sb.toString()
        }
    }

    // Reads the mod version from the appropriate metadata file
    @JvmStatic
    fun getModVersion(): String {
        // Fabric
        try {
            val fabricJson = readResource("/fabric.mod.json")
            val pattern = Pattern.compile(
                "\"version\"\\s*:\\s*\"([^\"]+)\""
            )
            val matcher = pattern.matcher(fabricJson)
            if (matcher.find()) {
                return matcher.group(1).trim { it <= ' ' }
            }
        } catch (_: IOException) {
        }

        // NeoForge/Forge
        try {
            val neoforgeToml = readResource("/META-INF/neoforge.mods.toml")
            val pattern = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"")
            val matcher = pattern.matcher(neoforgeToml)
            if (matcher.find()) {
                return matcher.group(1).trim { it <= ' ' }
            }
        } catch (_: IOException) {
        }

        return "unknown"
    }
}
