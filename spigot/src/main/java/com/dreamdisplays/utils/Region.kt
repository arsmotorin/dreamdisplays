package com.dreamdisplays.utils

import org.bukkit.Location
import org.jspecify.annotations.NullMarked
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@NullMarked
object Region {

    // Data class to hold all region information
    data class RegionData(
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val maxX: Int,
        val maxY: Int,
        val maxZ: Int,
        val width: Int,
        val height: Int,
        val deltaX: Int,
        val deltaZ: Int
    ) {
        // Creates a Location for the minimum corner
        fun getMinLocation(world: org.bukkit.World?): Location {
            return Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble())
        }

        // Creates a Location for the maximum corner
        fun getMaxLocation(world: org.bukkit.World?): Location {
            return Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())
        }
    }

    // Calculate region data from two positions
    fun calculateRegion(pos1: Location, pos2: Location): RegionData {
        val deltaX = abs(pos1.blockX - pos2.blockX) + 1
        val deltaZ = abs(pos1.blockZ - pos2.blockZ) + 1

        val width = max(deltaX, deltaZ)
        val height = abs(pos1.blockY - pos2.blockY) + 1

        val minX = min(pos1.blockX, pos2.blockX)
        val minY = min(pos1.blockY, pos2.blockY)
        val minZ = min(pos1.blockZ, pos2.blockZ)

        val maxX = max(pos1.blockX, pos2.blockX)
        val maxY = max(pos1.blockY, pos2.blockY)
        val maxZ = max(pos1.blockZ, pos2.blockZ)

        return RegionData(
            minX, minY, minZ,
            maxX, maxY, maxZ,
            width, height,
            deltaX, deltaZ
        )
    }

    // Check if a location is within the boundaries defined by two positions
    fun isInBoundaries(pos1: Location, pos2: Location, location: Location): Boolean {
        if (location.world != pos1.world) return false

        val minX = min(pos1.blockX, pos2.blockX)
        val minY = min(pos1.blockY, pos2.blockY)
        val minZ = min(pos1.blockZ, pos2.blockZ)

        val maxX = max(pos1.blockX, pos2.blockX)
        val maxY = max(pos1.blockY, pos2.blockY)
        val maxZ = max(pos1.blockZ, pos2.blockZ)

        if (location.blockX !in minX..maxX) return false
        if (location.blockY !in minY..maxY) return false
        return location.blockZ in minZ..maxZ
    }

    // Calculate the shortest distance from a location to the region defined by two positions
    fun getDistance(location: Location, pos1: Location, pos2: Location): Double {
        val minX = min(pos1.blockX, pos2.blockX)
        val minY = min(pos1.blockY, pos2.blockY)
        val minZ = min(pos1.blockZ, pos2.blockZ)

        val maxX = max(pos1.blockX, pos2.blockX)
        val maxY = max(pos1.blockY, pos2.blockY)
        val maxZ = max(pos1.blockZ, pos2.blockZ)

        val clampedX = min(max(location.blockX, minX), maxX)
        val clampedY = min(max(location.blockY, minY), maxY)
        val clampedZ = min(max(location.blockZ, minZ), maxZ)

        val closestPoint = Location(location.world, clampedX.toDouble(), clampedY.toDouble(), clampedZ.toDouble())

        return closestPoint.distance(location)
    }
}
