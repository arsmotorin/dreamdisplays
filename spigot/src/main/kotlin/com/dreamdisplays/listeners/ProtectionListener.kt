package com.dreamdisplays.listeners

import com.dreamdisplays.managers.DisplayManager.isContains
import com.dreamdisplays.managers.SelectionManager.isLocationSelected
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.Material

/**
 * Listener for protecting display areas from modifications.
 * Handles block breaking, explosions, and piston movements.
 */
class ProtectionListener : Listener {
    // Problem: player tries to break a block in a protected area
    // Solution: cancel event entirely
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) = cancelIfProtected(event.block.location, event)

    // Problem: explosion tries to destroy blocks in a protected area
    // Solution: remove protected blocks from the list of blocks to be destroyed
    @EventHandler
    fun onExplosion(event: EntityExplodeEvent) {
        event.blockList().removeIf { isLocationProtected(it.location) }
    }

    // Problem: piston tries to move blocks in a protected area
    // Solution: cancel event entirely
    @EventHandler
    fun onPistonExtend(event: BlockPistonExtendEvent) = handlePiston(event.blocks, event)

    // Problem: piston tries to move blocks in a protected area
    // Solution: cancel event entirely
    @EventHandler
    fun onPistonRetract(event: BlockPistonRetractEvent) = handlePiston(event.blocks, event)

    // Problem: some blocks are in a protected area and piston is trying to move them
    // Solution: cancel event entirely
    private fun handlePiston(blocks: List<Block>, event: Cancellable) {
        if (event.isCancelled) return
        if (blocks.any { isLocationProtected(it.location) }) event.isCancelled = true
    }

    // Problem: location is in a protected area
    // Solution: cancel the event entirely
    private fun cancelIfProtected(loc: Location, event: Cancellable) {
        if (isLocationProtected(loc)) event.isCancelled = true
    }

    // Problem: check if a location is in a protected area
    // Solution: check both existing displays and current selections
    private fun isLocationProtected(loc: Location): Boolean =
        isContains(loc) != null || isLocationSelected(loc)

    // Problem: fire is trying to burn a block in a protected area
    // Solution: cancel event entirely
    @EventHandler
    fun onBlockBurn(event: BlockBurnEvent) = cancelIfProtected(event.block.location, event)

    // Problem: fire is trying to spread to a block in a protected area
    // Solution: cancel event entirely
    @EventHandler
    fun onBlockSpread(event: BlockSpreadEvent) {
        if (event.newState.type == Material.FIRE && isLocationProtected(event.block.location)) {
            event.isCancelled = true
        }
    }
}
