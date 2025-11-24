package com.dreamdisplays.datatypes

import com.dreamdisplays.utils.Outliner
import com.dreamdisplays.utils.Region
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.util.*

@NullMarked
class Selection(player: Player) {
    @JvmField
    var pos1: Location? = null

    @JvmField
    var pos2: Location? = null
    private var face: BlockFace? = null
    private val playerId: UUID = player.uniqueId

    @JvmField
    var isReady: Boolean = false

    fun setFace(blockFace: BlockFace) {
        this.face = blockFace
    }

    fun getFace(): BlockFace {
        return face ?: throw IllegalStateException("Face is not set")
    }

    fun drawBox() {
        val p1 = pos1 ?: return
        val p2 = pos2 ?: return

        val player = Bukkit.getPlayer(playerId) ?: return

        // Show full 3D outline of the display
        Outliner.showOutline(player, p1, p2)
    }

    fun generateDisplayData(): Display {
        val p1 = pos1 ?: throw IllegalStateException("pos1 is not set")
        val p2 = pos2 ?: throw IllegalStateException("pos2 is not set")
        val f = face ?: throw IllegalStateException("face is not set")

        val region = Region.calculateRegion(p1, p2)
        val dPos1 = region.getMinLocation(p1.world)
        val dPos2 = region.getMaxLocation(p1.world)

        return Display(UUID.randomUUID(), playerId, dPos1, dPos2, region.width, region.height, f)
    }
}
