package com.dreamdisplays.listeners

import com.dreamdisplays.Main
import com.dreamdisplays.datatypes.Selection
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.utils.Message
import com.dreamdisplays.utils.Region
import org.bukkit.Bukkit
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
import org.jspecify.annotations.NullMarked
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Listener for handling player selections of display areas
 * and protecting those areas from modification.
 */
@NullMarked
class Selection(plugin: Main) : Listener {

    init {
        // Particles for Spigot/Paper servers (Folia unsupported)
        val settings = Main.config.settings
        if (!Main.getIsFolia() && settings.particles) {
            object : BukkitRunnable() {
                override fun run() {
                    // Single pass: draw base outlines for selections and additionally
                    // show the ready-outline for selections marked as ready.
                    selectionPoints.forEach { (playerId, selection) ->
                        val player = Bukkit.getPlayer(playerId) ?: return@forEach

                        // Draw selection box
                        selection.drawBox()

                        // If the selection is ready (complete and validated), show the
                        // full 3D outline to the player (keeps previous behavior)
                        val pos1 = selection.pos1
                        val pos2 = selection.pos2
                        if (selection.isReady && pos1 != null && pos2 != null) {
                            com.dreamdisplays.utils.Outliner.showOutline(player, pos1, pos2)
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, Main.config.settings.particles_color.toLong())
        }
    }

    // Player interaction for selection points
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val settings = Main.config.settings
        val heldItem = player.inventory.itemInMainHand.type

        // Sneak + right-click clears selection
        if (player.isSneaking && event.action.isRightClick) {
            if (selectionPoints.remove(player.uniqueId) != null) {
                com.dreamdisplays.utils.Outliner.hideOutline(player)
                Message.sendMessage(player, "selectionClear")
            }
            return
        }

        // Only act when holding the selection tool and clicking base material
        if (heldItem != settings.selectionMaterial) return
        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != settings.baseMaterial) return

        // Prevent normal interaction (place/break)
        event.isCancelled = true

        val location = clickedBlock.location
        var blockFace = event.blockFace

        // Adjust vertical faces to player's facing
        if (blockFace == BlockFace.UP || blockFace == BlockFace.DOWN) {
            blockFace = player.facing.oppositeFace
        }

        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> handleLeftClick(player, location, blockFace)
            Action.RIGHT_CLICK_BLOCK -> handleRightClick(player, location)
            else -> return
        }
    }

    private fun handleLeftClick(player: Player, location: Location, face: BlockFace) {
        // Get or create selection for player
        val selection = selectionPoints.getOrPut(player.uniqueId) { Selection(player) }
        // Reset if world changed
        if (selection.pos1?.world != location.world || selection.pos2?.world != location.world) {
            selection.pos1 = null
            selection.pos2 = null
        }

        selection.pos1 = location.clone()
        selection.setFace(face)
        selection.isReady = false

        Message.sendMessage(player, "firstPointSelected")

        // If second point already present, validate spot
        val pos2 = selection.pos2
        if (pos2 != null) {
            val code = isValidDisplay(selection)
            if (code == VALID_DISPLAY) {
                selection.isReady = true
                Message.sendMessage(player, "createDisplayCommand")
            } else sendErrorMessage(player, code)
        }
    }

    private fun handleRightClick(player: Player, location: Location) {
        // Ensure player has started selection
        val existingSelection = selectionPoints[player.uniqueId]
        if (existingSelection == null || existingSelection.pos1 == null) {
            Message.sendMessage(player, "noDisplayTerritories")
            return
        }

        // Reset if world changed
        if (existingSelection.pos1?.world != location.world) {
            existingSelection.pos1 = null
            existingSelection.pos2 = null
            Message.sendMessage(player, "noDisplayTerritories")
            return
        }

        existingSelection.pos2 = location.clone()
        existingSelection.isReady = false

        Message.sendMessage(player, "secondPointSelected")

        val code = isValidDisplay(existingSelection)
        if (code == VALID_DISPLAY) {
            existingSelection.isReady = true
            Message.sendMessage(player, "createDisplayCommand")
        } else sendErrorMessage(player, code)
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
        event.blockList().removeIf { block -> block.type == baseMaterial && isLocationProtected(block.location) }
    }

    // Piston movement protection for base material
    @EventHandler
    fun onPistonExtend(event: BlockPistonExtendEvent) = handlePiston(event.blocks, event)

    @EventHandler
    fun onPistonRetract(event: BlockPistonRetractEvent) = handlePiston(event.blocks, event)

    private fun handlePiston(blocks: List<Block>, event: Cancellable) {
        if (event.isCancelled) return

        val baseMaterial = Main.config.settings.baseMaterial
        if (blocks.any { it.type == baseMaterial && isLocationProtected(it.location) }) {
            event.isCancelled = true
        }
    }

    // Check if a location is protected by existing displays or selections
    private fun isLocationProtected(loc: Location): Boolean {
        if (DisplayManager.isContains(loc) != null) return true

        return selectionPoints.values.any { sel ->
            if (!sel.isReady) return@any false
            val p1 = sel.pos1 ?: return@any false
            val p2 = sel.pos2 ?: return@any false
            Region.isInBoundaries(p1, p2, loc)
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

            if (height < Main.config.settings.display.min_height || width < Main.config.settings.display.min_width) return 3
            if (height > Main.config.settings.display.max_height || width > Main.config.settings.display.max_width) return 4

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
