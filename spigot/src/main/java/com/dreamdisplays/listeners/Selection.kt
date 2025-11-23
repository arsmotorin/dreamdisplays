package com.dreamdisplays.listeners

import com.dreamdisplays.Main
import com.dreamdisplays.datatypes.Selection
import com.dreamdisplays.managers.Display
import com.dreamdisplays.utils.Message
import com.dreamdisplays.utils.Region
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Selection(plugin: Main) : Listener {

    init {
        // Particles for Spigot/Paper servers
        // Folia is not supported yet due to differences in scheduling
        if (!Main.getIsFolia() && Main.config.settings.particlesEnabled) {
            object : BukkitRunnable() {
                override fun run() {
                    selectionPoints.values.forEach { it.drawBox() }

                    // Update display outlines for all players with active selections
                    selectionPoints.forEach { (playerId, selection) ->
                        val player = org.bukkit.Bukkit.getPlayer(playerId) ?: return@forEach
                        val pos1 = selection.pos1
                        val pos2 = selection.pos2
                        if (selection.isReady && pos1 != null && pos2 != null) {
                            com.dreamdisplays.utils.Outliner.showOutline(player, pos1, pos2)
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, Main.config.settings.particleRenderDelay.toLong())
        }
    }

    // Player interaction for selection points
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val heldItem = player.inventory.itemInMainHand.type

        // Clear selection if sneaking and right-clicking
        if (player.isSneaking && event.action.isRightClick) {
            if (selectionPoints.remove(player.uniqueId) != null) {
                com.dreamdisplays.utils.Outliner.hideOutline(player)
                Message.sendMessage(player, "selectionClear")
            }
            return
        }

        // Only proceed if holding the selection material
        if (heldItem != Main.config.settings.selectionMaterial) return
        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != Main.config.settings.baseMaterial) return

        event.isCancelled = true

        val location = clickedBlock.location
        var blockFace = event.blockFace

        // Adjust for vertical faces
        if (blockFace == BlockFace.UP || blockFace == BlockFace.DOWN) {
            blockFace = player.facing.oppositeFace
        }

        if (event.action == Action.LEFT_CLICK_BLOCK) {
            // Get or create selection
            val selection = selectionPoints.getOrPut(player.uniqueId) { Selection(player) }

            // Reset if world changed
            if (selection.pos1?.world != location.world || selection.pos2?.world != location.world) {
                selection.pos1 = null
                selection.pos2 = null
            }

            selection.pos1 = location.clone()
            selection.setFace(blockFace)
            selection.isReady = false

            Message.sendMessage(player, "firstPointSelected")

            // Validate if pos2 already exists
            if (selection.pos2 != null) {
                val code = isValidDisplay(selection)
                if (code == VALID_DISPLAY) {
                    selection.isReady = true
                    Message.sendMessage(player, "createDisplayCommand")
                } else {
                    sendErrorMessage(player, code)
                }
            }
        }
        else if (event.action == Action.RIGHT_CLICK_BLOCK) {
            // Check if pos1 exists BEFORE getting/creating selection
            val existingSelection = selectionPoints[player.uniqueId]
            if (existingSelection == null || existingSelection.pos1 == null) {
                Message.sendMessage(player, "noDisplayTerritories")
                return
            }

            // Now we can safely update pos2
            val selection = existingSelection

            // Reset if world changed
            if (selection.pos1?.world != location.world) {
                selection.pos1 = null
                selection.pos2 = null
                Message.sendMessage(player, "noDisplayTerritories")
                return
            }

            selection.pos2 = location.clone()
            selection.isReady = false

            Message.sendMessage(player, "secondPointSelected")

            // Validate the full selection
            val code = isValidDisplay(selection)
            if (code == VALID_DISPLAY) {
                selection.isReady = true
                Message.sendMessage(player, "createDisplayCommand")
            } else {
                sendErrorMessage(player, code)
            }
        }
    }

    // Block break protection for base material
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.type == Main.config.settings.baseMaterial) {
            cancelIfProtected(event.block.location, event)
        }
    }

    // Explosion protection for base material
    @EventHandler
    fun onExplosion(event: EntityExplodeEvent) {
        val baseMaterial = Main.config.settings.baseMaterial
        val toRemove = event.blockList().filter { block ->
            block.type == baseMaterial && isLocationProtected(block.location)
        }
        event.blockList().removeAll(toRemove.toSet())
    }

    // Piston movement protection for base material
    @EventHandler
    fun onPistonExtend(event: BlockPistonExtendEvent) = handlePiston(event.blocks, event)

    @EventHandler
    fun onPistonRetract(event: BlockPistonRetractEvent) = handlePiston(event.blocks, event)

    private fun handlePiston(blocks: List<Block>, event: Cancellable) {
        if (event.isCancelled) return

        val baseMaterial = Main.config.settings.baseMaterial
        for (block in blocks) {
            if (block.type == baseMaterial && isLocationProtected(block.location)) {
                event.isCancelled = true
                break
            }
        }
    }


    // Check if a location is protected by existing displays or selections
    private fun isLocationProtected(loc: Location): Boolean {
        if (Display.isContains(loc) != null) return true

        return selectionPoints.values
            .filter { it.isReady }
            .any { selection ->
                val pos1 = selection.pos1
                val pos2 = selection.pos2
                pos1 != null && pos2 != null && Region.isInBoundaries(pos1, pos2, loc)
            }
    }

    // Cancel event if location is protected
    private fun cancelIfProtected(loc: Location, event: Cancellable) {
        if (isLocationProtected(loc)) event.isCancelled = true
    }


    private val Action.isRightClick get() = this == Action.RIGHT_CLICK_AIR || this == Action.RIGHT_CLICK_BLOCK

    // Object for managing selections
    companion object {
        val selectionPoints = mutableMapOf<UUID, Selection>()

        private const val VALID_DISPLAY = 6

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
            Message.sendColoredMessage(player, Main.config.messages[key] as String?)
        }

        // Validate the selection for display creation
        fun isValidDisplay(data: Selection): Int {
            val pos1 = data.pos1 ?: return 0
            val pos2 = data.pos2 ?: return 0
            if (pos1.world != pos2.world) return 1

            val (minX, maxX) = min(pos1.blockX, pos2.blockX) to max(pos1.blockX, pos2.blockX)
            val (minY, maxY) = min(pos1.blockY, pos2.blockY) to max(pos1.blockY, pos2.blockY)
            val (minZ, maxZ) = min(pos1.blockZ, pos2.blockZ) to max(pos1.blockZ, pos2.blockZ)

            val deltaX = maxX - minX + 1
            val deltaZ = maxZ - minZ + 1
            val face = data.getFace()

            // Check if the structure depth matches the facing direction
            if (deltaX != abs(face.modX) && deltaZ != abs(face.modZ)) return 2

            val width = maxOf(deltaX, deltaZ)
            val height = maxY - minY + 1

            if (height < Main.config.settings.minHeight || width < Main.config.settings.minWidth) return 3
            if (height > Main.config.settings.maxHeight || width > Main.config.settings.maxWidth) return 4

            val required = Main.config.settings.baseMaterial
            val world = pos1.world ?: return 1

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        if (world.getBlockAt(x, y, z).type != required) return 5
                    }
                }
            }
            return VALID_DISPLAY
        }
    }
}
