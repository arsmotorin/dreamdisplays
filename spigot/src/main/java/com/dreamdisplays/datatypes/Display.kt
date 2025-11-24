package com.dreamdisplays.datatypes

import com.dreamdisplays.Main
import com.dreamdisplays.utils.Region
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.jspecify.annotations.NullMarked
import java.util.*
import kotlin.math.max
import kotlin.math.min
import com.dreamdisplays.utils.net.Utils as Net

@NullMarked
class Display(
    val id: UUID,
    val ownerId: UUID,
    val pos1: Location,
    val pos2: Location,
    val width: Int,
    val height: Int,
    val facing: BlockFace? = BlockFace.NORTH
) {
    var url: String = ""
    var duration: Long? = null
    var isSync: Boolean = false
    var lang: String = ""

    val box: BoundingBox = BoundingBox(
        min(pos1.blockX, pos2.blockX).toDouble(),
        min(pos1.blockY, pos2.blockY).toDouble(),
        min(pos1.blockZ, pos2.blockZ).toDouble(),
        (max(pos1.blockX, pos2.blockX) + 1).toDouble(),
        (max(pos1.blockY, pos2.blockY) + 1).toDouble(),
        (max(pos1.blockZ, pos2.blockZ) + 1).toDouble()
    )

    fun isInRange(loc: Location): Boolean {
        val maxRender = Main.config.settings.maxRenderDistance
        val clampedX = loc.blockX.coerceIn(box.minX.toInt(), box.maxX.toInt())
        val clampedY = loc.blockY.coerceIn(box.minY.toInt(), box.maxY.toInt())
        val clampedZ = loc.blockZ.coerceIn(box.minZ.toInt(), box.maxZ.toInt())

        val dx = loc.blockX - clampedX
        val dy = loc.blockY - clampedY
        val dz = loc.blockZ - clampedZ

        return dx * dx + dy * dy + dz * dz <= maxRender * maxRender
    }

    fun sendUpdatePacket(players: List<Player>) {
        @Suppress("UNCHECKED_CAST")
        Net.sendDisplayInfoPacket(
            players as MutableList<Player?>, id, ownerId, box.min, width, height,
            url, lang, facing ?: BlockFace.NORTH, isSync
        )
    }

    val receivers: List<Player>
        get() = pos1.world?.players
            ?.filter { Region.getDistance(it.location, pos1, pos2) < Main.config.settings.maxRenderDistance }
            ?: emptyList()
}
