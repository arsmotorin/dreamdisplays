package com.dreamdisplays.utils

import com.dreamdisplays.datatypes.Display
import org.bukkit.Location
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

object Utils {
    fun isInBoundaries(pos1: Location, pos2: Location, location: Location): Boolean {
        if (location.world != pos1.world) return false

        val minX = min(pos1.getBlockX(), pos2.getBlockX())
        val minY = min(pos1.getBlockY(), pos2.getBlockY())
        val minZ = min(pos1.getBlockZ(), pos2.getBlockZ())

        val maxX = max(pos1.getBlockX(), pos2.getBlockX())
        val maxY = max(pos1.getBlockY(), pos2.getBlockY())
        val maxZ = max(pos1.getBlockZ(), pos2.getBlockZ())

        if (minX > location.getBlockX() || location.getBlockX() > maxX) return false
        if (minY > location.getBlockY() || location.getBlockY() > maxY) return false
        return minZ <= location.getBlockZ() && location.getBlockZ() <= maxZ
    }

    fun getDistanceToScreen(location: Location, displayData: Display): Double {
        val minX = min(displayData.pos1.getBlockX(), displayData.pos2!!.getBlockX())
        val minY = min(displayData.pos1.getBlockY(), displayData.pos2.getBlockY())
        val minZ = min(displayData.pos1.getBlockZ(), displayData.pos2.getBlockZ())

        val maxX = max(displayData.pos1.getBlockX(), displayData.pos2.getBlockX())
        val maxY = max(displayData.pos1.getBlockY(), displayData.pos2.getBlockY())
        val maxZ = max(displayData.pos1.getBlockZ(), displayData.pos2.getBlockZ())

        val clampedX = min(max(location.getBlockX(), minX), maxX)
        val clampedY = min(max(location.getBlockY(), minY), maxY)
        val clampedZ = min(max(location.getBlockZ(), minZ), maxZ)

        val closestPoint = Location(location.getWorld(), clampedX.toDouble(), clampedY.toDouble(), clampedZ.toDouble())

        return closestPoint.distance(location)
    }

    fun extractVideoId(youtubeUrl: String): String? {
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
