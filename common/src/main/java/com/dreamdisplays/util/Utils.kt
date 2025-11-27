package com.dreamdisplays.util

import org.jspecify.annotations.NullMarked
import java.io.*
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * General utility functions for the mod.
 */
@NullMarked
object Utils {

    // Extracts video ID from various YouTube URL formats
    @JvmStatic
    fun extractVideoId(youtubeUrl: String): String? {
        try {
            val uri = URI(youtubeUrl)
            val query = uri.getQuery()
            if (query != null) {
                for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val pair = param.split("=".toRegex(), limit = 2).toTypedArray()
                    if (pair.size == 2 && pair[0] == "v") {
                        return pair[1]
                    }
                }
            }

            // If the URL is a shortened version or a YouTube Shorts link
            val host = uri.host
            if (host != null && host.contains("youtu.be")) {
                val path = uri.getPath()
                if (path != null && path.length > 1) {
                    return path.substring(1)
                }
            } else if (host != null && host.contains("youtube.com")) {
                val path = uri.getPath()
                if (path != null && path.contains("shorts")) {
                    return path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.last()
                }
            }
        } catch (_: URISyntaxException) {
        }

        val regex = "(?<=([?&]v=))[^#&?]*"
        val m = Pattern.compile(regex).matcher(youtubeUrl)
        return if (m.find()) m.group() else null
    }

    @Throws(IOException::class)
    fun readResource(resourcePath: String): String {
        Utils::class.java.getResourceAsStream(resourcePath).use { `in` ->
            if (`in` == null) {
                throw IOException("Can't find the resource: $resourcePath")
            }
            val reader = BufferedReader(InputStreamReader(`in`))
            val sb = StringBuilder()
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                sb.append(line).append("\n")
            }
            return sb.toString()
        }
    }

    val modVersion: String
        // Reads the mod version from the appropriate metadata file
        get() {
            // Fabric
            try {
                val fabricJson = readResource("/fabric.mod.json")
                val pattern =
                    Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                val matcher = pattern.matcher(fabricJson)
                if (matcher.find()) {
                    return matcher.group(1).trim { it <= ' ' }
                }
            } catch (_: IOException) {
            }

            // NeoForge/Forge
            try {
                val neoforgeToml =
                    readResource("/META-INF/neoforge.mods.toml")
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
