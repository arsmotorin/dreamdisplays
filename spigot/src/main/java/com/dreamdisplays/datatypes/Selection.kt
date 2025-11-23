package com.dreamdisplays.datatypes

import com.dreamdisplays.Main
import com.dreamdisplays.utils.Particle
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Selection(player: Player) {
    @JvmField
    var pos1: Location? = null
    @JvmField
    var pos2: Location? = null
    private var face: BlockFace? = null
    private val playerId: UUID
    @JvmField
    var isReady: Boolean = false

    init {
        this.playerId = player.getUniqueId()
    }

    fun setFace(blockFace: BlockFace) {
        this.face = blockFace
    }

    fun getFace(): BlockFace {
        return this.face!!
    }

    fun drawBox() {
        val p1 = pos1 ?: return
        val p2 = pos2 ?: return
        val f = face ?: return

        val player = Bukkit.getPlayer(playerId) ?: return

        Particle.drawRectangleOnFace(
            player,
            p1,
            p2,
            f,
            Main.config.settings.particlesPerBlock,
            Color.fromRGB(Main.config.settings.particlesColor)
        )
    }

    fun generateDisplayData(): Display {
        val p1 = pos1!!
        val p2 = pos2!!
        val f = face!!

        val deltaX = abs(p1.blockX - p2.blockX) + 1
        val deltaZ = abs(p1.blockZ - p2.blockZ) + 1

        val width = max(deltaX, deltaZ)
        val height = abs(p1.blockY - p2.blockY) + 1

        val minX = min(p1.blockX, p2.blockX)
        val minY = min(p1.blockY, p2.blockY)
        val minZ = min(p1.blockZ, p2.blockZ)

        val maxX = max(p1.blockX, p2.blockX)
        val maxY = max(p1.blockY, p2.blockY)
        val maxZ = max(p1.blockZ, p2.blockZ)

        val dPos1 = Location(p1.world, minX.toDouble(), minY.toDouble(), minZ.toDouble())
        val dPos2 = Location(p1.world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())

        return Display(UUID.randomUUID(), playerId, dPos1, dPos2, width, height, f)
    }
}
