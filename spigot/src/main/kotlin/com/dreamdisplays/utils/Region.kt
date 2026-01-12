package com.dreamdisplays.utils

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.jspecify.annotations.NullMarked
import kotlin.math.max
import kotlin.math.min

/**
 * Utils for 3D region calculations and boundary checks.
 */
@NullMarked
object Region {
    fun calculateRegion(pos1: Location, pos2: Location, facing: BlockFace? = null): RegionData {
        val minX = min(pos1.blockX, pos2.blockX)
        val minY = min(pos1.blockY, pos2.blockY)
        val minZ = min(pos1.blockZ, pos2.blockZ)

        val maxX = max(pos1.blockX, pos2.blockX)
        val maxY = max(pos1.blockY, pos2.blockY)
        val maxZ = max(pos1.blockZ, pos2.blockZ)

        val deltaX = maxX - minX + 1
        val deltaY = maxY - minY + 1
        val deltaZ = maxZ - minZ + 1

        val (width, height) = when (facing) {
            BlockFace.UP, BlockFace.DOWN -> {
                deltaX to deltaZ
            }

            else -> {
                max(deltaX, deltaZ) to deltaY
            }
        }

        return RegionData(
            minX, minY, minZ,
            maxX, maxY, maxZ,
            width, height,
            deltaX, deltaY, deltaZ
        )
    }

    fun isInBoundaries(pos1: Location, pos2: Location, location: Location): Boolean {
        if (location.world != pos1.world) return false

        return location.blockX in getRange(pos1.blockX, pos2.blockX) &&
                location.blockY in getRange(pos1.blockY, pos2.blockY) &&
                location.blockZ in getRange(pos1.blockZ, pos2.blockZ)
    }

    private fun getRange(a: Int, b: Int): IntRange {
        return min(a, b)..max(a, b)
    }

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
        val deltaY: Int,
        val deltaZ: Int,
    ) {

        fun getMinLocation(world: World?): Location {
            return Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble())
        }

        fun getMaxLocation(world: World?): Location {
            return Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())
        }
    }
}