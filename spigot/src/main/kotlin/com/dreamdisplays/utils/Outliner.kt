package com.dreamdisplays.utils

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Utility object for outlining areas with particles.
 */
@NullMarked
object Outliner {

    private val activeOutlines: MutableMap<UUID, OutlineData> = mutableMapOf()

    data class OutlineData(
        val box: BoundingBox,
        val world: org.bukkit.World,
    )

    fun showOutline(player: Player, pos1: Location, pos2: Location) {
        val world = pos1.world ?: return

        val minX = minOf(pos1.blockX, pos2.blockX).toDouble()
        val minY = minOf(pos1.blockY, pos2.blockY).toDouble()
        val minZ = minOf(pos1.blockZ, pos2.blockZ).toDouble()
        val maxX = maxOf(pos1.blockX, pos2.blockX) + 1.0
        val maxY = maxOf(pos1.blockY, pos2.blockY) + 1.0
        val maxZ = maxOf(pos1.blockZ, pos2.blockZ) + 1.0

        val box = BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
        activeOutlines[player.uniqueId] = OutlineData(box, world)

        drawOutlineBox(player, box, world)
    }

    private fun drawOutlineBox(player: Player, box: BoundingBox, world: org.bukkit.World) {
        val color = org.bukkit.Color.fromRGB(0, 255, 255)
        val dustOptions = org.bukkit.Particle.DustOptions(color, 0.5f)

        // 8 angles of the bounding box
        val corners = listOf(
            Location(world, box.minX, box.minY, box.minZ),
            Location(world, box.maxX, box.minY, box.minZ),
            Location(world, box.maxX, box.minY, box.maxZ),
            Location(world, box.minX, box.minY, box.maxZ),
            Location(world, box.minX, box.maxY, box.minZ),
            Location(world, box.maxX, box.maxY, box.minZ),
            Location(world, box.maxX, box.maxY, box.maxZ),
            Location(world, box.minX, box.maxY, box.maxZ)
        )

        // Edges between the corners
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,    // Bottom
            4 to 5, 5 to 6, 6 to 7, 7 to 4,    // Top
            0 to 4, 1 to 5, 2 to 6, 3 to 7     // Sides
        )

        for ((fromIdx, toIdx) in edges) {
            drawLine(player, corners[fromIdx], corners[toIdx], dustOptions)
        }
    }

    private fun drawLine(player: Player, from: Location, to: Location, dustOptions: org.bukkit.Particle.DustOptions) {
        val distance = from.distance(to)
        val steps = (distance * 2).toInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i / steps.toDouble()
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            val z = from.z + (to.z - from.z) * t

            player.spawnParticle(
                org.bukkit.Particle.DUST,
                x, y, z,
                1, 0.0, 0.0, 0.0, 0.0,
                dustOptions
            )
        }
    }
}
