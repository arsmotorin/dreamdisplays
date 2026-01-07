package com.dreamdisplays.managers

import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.datatypes.SelectionData
import com.dreamdisplays.utils.Message.sendColoredMessage
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player

/**
 * Validates display structures based on selection data.
 * Returns specific error codes for different validation failures.
 *
 */
object DisplayValidator {
    private const val VALID_DISPLAY = 6

    fun isValidDisplay(data: SelectionData): Int {
        val pos1 = data.pos1 ?: return 0
        val pos2 = data.pos2 ?: return 0
        if (pos1.world != pos2.world) return 1

        val (minX, maxX) = minOf(pos1.blockX, pos2.blockX) to maxOf(pos1.blockX, pos2.blockX)
        val (minY, maxY) = minOf(pos1.blockY, pos2.blockY) to maxOf(pos1.blockY, pos2.blockY)
        val (minZ, maxZ) = minOf(pos1.blockZ, pos2.blockZ) to maxOf(pos1.blockZ, pos2.blockZ)

        val deltaX = maxX - minX + 1
        val deltaY = maxY - minY + 1
        val deltaZ = maxZ - minZ + 1

        if (deltaX == 1 && deltaY == 1 && deltaZ == 1) return 3

        val originalFace = data.getFace()
        val (width, height, detectedFace) = when {
            deltaY == 1 -> {
                val face = if (originalFace == BlockFace.DOWN) BlockFace.DOWN else BlockFace.UP
                Triple(maxOf(deltaX, deltaZ), minOf(deltaX, deltaZ), face)
            }
            deltaZ == 1 -> {
                val face = if (originalFace == BlockFace.SOUTH) BlockFace.SOUTH else BlockFace.NORTH
                Triple(deltaX, deltaY, face)
            }
            deltaX == 1 -> {
                val face = if (originalFace == BlockFace.WEST) BlockFace.WEST else BlockFace.EAST
                Triple(deltaZ, deltaY, face)
            }
            else -> return 2
        }

        data.setFace(detectedFace)

        if (height < config.settings.minHeight || width < config.settings.minWidth) return 3
        if (height > config.settings.maxHeight || width > config.settings.maxWidth) return 4

        val required = config.settings.baseMaterial
        val world = pos1.world ?: return 1
        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ)
            if (world.getBlockAt(x, y, z).type != required) return 5

        return VALID_DISPLAY
    }

    fun sendErrorMessage(player: Player, code: Int) {
        val key = when (code) {
            0 -> "secondPointNotSelected"
            1 -> "displayOverlap"
            2 -> "structureWrongDepth"
            3 -> "structureTooSmall"
            4 -> "structureTooLarge"
            5 -> "wrongStructure"
            else -> return
        }
        sendColoredMessage(player, config.messages[key] as String?)
    }
}
