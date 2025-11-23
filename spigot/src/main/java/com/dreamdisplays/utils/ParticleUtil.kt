package com.dreamdisplays.utils

import me.inotsleep.utils.particle.Util
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import kotlin.math.max
import kotlin.math.min

object ParticleUtil {
    fun drawLine(player: Player, from: Location, to: Location, particlesPerBlock: Int, color: Color) {
        val distance = from.distance(to)
        val particles = (distance * particlesPerBlock).toInt()
        val world = from.getWorld()

        for (i in 0..particles) {
            val t = i / particles.toDouble()
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            val z = from.z + (to.z - from.z) * t

            val particleLocation = Location(world, x, y, z)
            Util.drawParticle(
                player,
                particleLocation.x,
                particleLocation.y,
                particleLocation.z,
                0.0,
                0.0,
                0.0,
                Particle.DUST,
                null,
                color.red,
                color.green,
                color.blue
            )
        }
    }

    fun drawRectangleOnFace(
        player: Player,
        corner1: Location,
        corner2: Location,
        face: BlockFace,
        particlesPerBlock: Int,
        color: Color
    ) {
        val minX = min(corner1.blockX, corner2.blockX)
        val minY = min(corner1.blockY, corner2.blockY)
        val minZ = min(corner1.blockZ, corner2.blockZ)
        val maxX = max(corner1.blockX, corner2.blockX) + 1
        val maxY = max(corner1.blockY, corner2.blockY) + 1
        val maxZ = max(corner1.blockZ, corner2.blockZ) + 1
        val world = corner1.getWorld()

        when (face) {
            BlockFace.UP -> {
                // Up face: fixed Y = maxY, XZ plane
                drawLine(
                    player,
                    Location(world, minX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, minX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
            }

            BlockFace.DOWN -> {
                // Down face: fixed Y = minY, XZ plane
                drawLine(
                    player,
                    Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, minX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
            }

            BlockFace.NORTH -> {
                // North face: fixed Z = minZ, XY plane
                drawLine(
                    player,
                    Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    Location(world, minX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, minX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
            }

            BlockFace.SOUTH -> {
                // South face: fixed Z = maxZ, XY plane
                drawLine(
                    player,
                    Location(world, minX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    Location(world, maxX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, minX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
            }

            BlockFace.EAST -> {
                // East face: fixed X = maxX, YZ plane
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    Location(world, maxX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, maxX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    Location(world, maxX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
            }

            BlockFace.WEST -> {
                // West face: fixed X = minX, YZ plane
                drawLine(
                    player,
                    Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    Location(world, minX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, minX.toDouble(), maxY.toDouble(), minZ.toDouble()),
                    Location(world, minX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, minX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    particlesPerBlock,
                    color
                )
                drawLine(
                    player,
                    Location(world, minX.toDouble(), minY.toDouble(), maxZ.toDouble()),
                    Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
                    particlesPerBlock,
                    color
                )
            }

            else -> throw IllegalArgumentException("Unsupported BlockFace for a flat rectangle: $face")
        }
    }
}
