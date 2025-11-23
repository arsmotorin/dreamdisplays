package com.dreamdisplays.listeners

import com.dreamdisplays.Main
import com.dreamdisplays.datatypes.Selection
import com.dreamdisplays.managers.Display
import com.dreamdisplays.utils.Message
import com.dreamdisplays.utils.Utils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
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
        if (!Main.getIsFolia() && Main.config.settings.particlesEnabled) {
            val runnable: BukkitRunnable = object : BukkitRunnable() {
                override fun run() {
                    selectionPoints.forEach { (key: UUID?, value: Selection?) -> value!!.drawBox() }
                }
            }

            runnable.runTaskTimer(plugin, 0, Main.config.settings.particleRenderDelay.toLong())
        }
    }

    @EventHandler
    fun onClick(event: PlayerInteractEvent) {
        if (event.getHand() != EquipmentSlot.HAND) return

        val player = event.getPlayer()

        if (player.getInventory().getItemInMainHand()
                .getType() != Main.config.settings.selectionMaterial
        ) return

        if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) && selectionPoints.containsKey(
                player.getUniqueId()
            )
        ) {
            selectionPoints.remove(player.getUniqueId())
            Message.sendColoredMessage(
                player,
                Main.config.messages.get("selectionClear") as String?
            )
            return
        }

        if (event.getClickedBlock() == null) return
        if (event.getClickedBlock()!!.getType() != Main.config.settings.baseMaterial) return

        event.setCancelled(true)

        val location = event.getClickedBlock()!!.getLocation()
        val data = selectionPoints.getOrDefault(player.getUniqueId(), Selection(player))

        if (data.pos1 != null && data.pos1!!.getWorld() !== location.getWorld() ||
            data.pos2 != null && data.pos2!!.getWorld() !== location.getWorld()
        ) {
            data.pos1 = null
            data.pos2 = null
        }

        data.isReady = false

        var face = event.getBlockFace()

        if (face == BlockFace.UP || face == BlockFace.DOWN) face = player.getFacing().getOppositeFace()

        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
            data.pos1 = location.clone()
            data.setFace(face)
            Message.sendColoredMessage(
                player,
                Main.config.messages.get("firstPointSelected") as String?
            )

            val validationCode: Int = isValidDisplay(data)
            if (validationCode == 6) data.isReady = true
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (data.pos1 == null) {
                Message.sendColoredMessage(
                    player,
                    Main.config.messages.get("noDisplayTerritories") as String?
                )
                return
            }
            data.pos2 = location.clone()
            Message.sendColoredMessage(
                player,
                Main.config.messages.get("secondPointSelected") as String?
            )

            val validationCode: Int = isValidDisplay(data)
            if (validationCode != 6) {
                data.isReady = false
                selectionPoints.put(player.getUniqueId(), data)

                sendErrorMessage(player, validationCode)

                return
            }

            data.isReady = true
            Message.sendColoredMessage(
                player,
                Main.config.messages.get("createDisplayCommand") as String?
            )
        }

        selectionPoints.put(player.getUniqueId(), data)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        handleBlockDestroy(event.getBlock().getType(), event.getBlock().getLocation(), event)
    }

    @EventHandler
    fun onExplodeEvent(event: EntityExplodeEvent) {
        val toRemove: MutableList<Block?> = ArrayList<Block?>()
        val dataList: MutableList<Selection> =
            selectionPoints.values.stream().filter(Selection::isReady).toList()

        event.blockList().stream()
            .filter { block: Block? -> block!!.getType() == Main.config.settings.baseMaterial }
            .forEach { block: Block? ->
                if (block!!.getType() != Main.config.settings.baseMaterial) return@forEach
                val location = block.getLocation()

                if (Display.isContains(location) != null) {
                    toRemove.add(block)
                    return@forEach
                }
                for (data in dataList) {
                    val p1 = data.pos1 ?: continue
                    val p2 = data.pos2 ?: continue
                    if (Utils.isInBoundaries(p1, p2, location)) {
                        toRemove.add(block)
                        break
                    }
                }
            }

        event.blockList().removeAll(toRemove)
    }

    private fun handleBlockDestroy(material: Material?, location: Location, event: Cancellable) {
        if (material != Main.config.settings.baseMaterial) return
        if (Display.isContains(location) != null) event.setCancelled(true)

        for (eData in selectionPoints.entries) {
            val uuid: UUID = eData.key!!
            val data = eData.value

            if (!data.isReady || Bukkit.getPlayer(uuid) != null) continue

            val p1 = data.pos1 ?: continue
            val p2 = data.pos2 ?: continue
            if (Utils.isInBoundaries(p1, p2, location)) {
                event.isCancelled = true
                break
            }
        }
    }

    @EventHandler
    fun onBlockPush(event: BlockPistonExtendEvent) {
        handlePistonEvent(event.getBlocks(), event)
    }

    @EventHandler
    fun onBlockPull(event: BlockPistonRetractEvent) {
        handlePistonEvent(event.getBlocks(), event)
    }

    private fun handlePistonEvent(blocks: MutableList<Block?>, event: Cancellable) {
        val dataList: MutableList<Selection> =
            selectionPoints.values.stream().filter(Selection::isReady).toList()

        blocks.stream()
            .filter { block: Block? -> block!!.getType() == Main.config.settings.baseMaterial }
            .forEach { block: Block? ->
                if (event.isCancelled()) return@forEach
                if (block!!.getType() != Main.config.settings.baseMaterial) return@forEach

                val location = block.getLocation()

                if (Display.isContains(location) != null) {
                    true.also { event.isCancelled = true }
                    return@forEach
                }
                for (data in dataList) {
                    val p1 = data.pos1 ?: continue
                    val p2 = data.pos2 ?: continue
                    if (Utils.isInBoundaries(p1, p2, location)) {
                        true.also { event.isCancelled = true }
                        break
                    }
                }
            }
    }

    companion object {
        var selectionPoints: MutableMap<UUID?, Selection> = HashMap<UUID?, Selection>()

        fun sendErrorMessage(player: Player, validationCode: Int) {
            when (validationCode) {
                0 -> Message.sendColoredMessage(
                    player,
                    Main.config.messages.get("secondPointNotSelected") as String?
                )

                1 -> Message.sendColoredMessage(
                    player,
                    Main.config.messages.get("displayOverlap") as String?
                )

                2 -> Message.sendColoredMessage(
                    player,
                    Main.config.messages.get("structureWrongDepth") as String?
                )

                3 -> Message.sendColoredMessage(
                    player,
                    Main.config.messages.get("structureTooSmall") as String?
                )

                4 -> Message.sendColoredMessage(
                    player,
                    Main.config.messages.get("structureTooLarge") as String?
                )

                5 -> Message.sendColoredMessage(
                    player,
                    Main.config.messages.get("wrongStructure") as String?
                )
            }
        }

        fun isValidDisplay(data: Selection): Int {
            val pos1 = data.pos1
            val pos2 = data.pos2

            if (pos1 == null || pos2 == null || data.getFace() == null) return 0

            if (pos1.getWorld() !== pos2.getWorld()) return 1

            val minX = min(pos1.getBlockX(), pos2.getBlockX())
            val minY = min(pos1.getBlockY(), pos2.getBlockY())
            val minZ = min(pos1.getBlockZ(), pos2.getBlockZ())

            val maxX = max(pos1.getBlockX(), pos2.getBlockX())
            val maxY = max(pos1.getBlockY(), pos2.getBlockY())
            val maxZ = max(pos1.getBlockZ(), pos2.getBlockZ())

            val deltaX = abs(pos1.getBlockX() - pos2.getBlockX()) + 1
            val deltaZ = abs(pos1.getBlockZ() - pos2.getBlockZ()) + 1

            if (deltaX != abs(data.getFace().getModX()) && deltaZ != abs(data.getFace().getModZ())) return 2

            val width = max(deltaX, deltaZ)
            val height = abs(pos1.getBlockY() - pos2.getBlockY()) + 1

            if (height < Main.config.settings.minHeight || width < Main.config.settings.minWidth) return 3
            if (height > Main.config.settings.maxHeight || width > Main.config.settings.maxWidth) return 4

            val requiredMaterial = Main.config.settings.baseMaterial

            val world = pos1.getWorld()
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val block = world!!.getBlockAt(x, y, z)
                        if (block.getType() != requiredMaterial) {
                            return 5
                        }
                    }
                }
            }

            return 6
        }
    }
}
