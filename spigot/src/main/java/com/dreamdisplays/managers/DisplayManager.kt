package com.dreamdisplays.managers

import com.dreamdisplays.Main
import com.dreamdisplays.datatypes.Display
import com.dreamdisplays.datatypes.Selection
import com.dreamdisplays.utils.Message
import com.dreamdisplays.utils.Region
import com.dreamdisplays.utils.Reporter
import com.dreamdisplays.utils.Scheduler
import com.dreamdisplays.utils.net.Utils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.jspecify.annotations.NullMarked
import java.util.*
import java.util.function.Consumer

/**
 * Manager for display data and operations with displays.
 */
@NullMarked
object DisplayManager {
    private val displays: MutableMap<UUID, Display> = mutableMapOf()
    private val reportTime: MutableMap<UUID, Long> = mutableMapOf()

    // Get display data by ID
    @JvmStatic
    fun getDisplayData(id: UUID?): Display? = displays[id]

    // Get all displays
    fun getDisplays(): List<Display> = displays.values.toList()

    // Register a new display
    fun register(displayData: Display) {
        displays[displayData.id] = displayData
        displayData.sendUpdatePacket(displayData.receivers)
    }

    // Update all displays and send updates to players in range
    fun updateAllDisplays() {
        val playersByWorld = displays.values
            .mapNotNull { it.pos1.world }
            .distinct()
            .associateWith { it.players.toMutableList() }

        displays.values.forEach { display ->
            val world = display.pos1.world ?: return@forEach
            val worldPlayers = playersByWorld[world] ?: mutableListOf()

            val receivers = worldPlayers.filter { player ->
                display.isInRange(player.location)
            }.toMutableList()

            display.sendUpdatePacket(receivers)
        }
    }

    // Delete a display
    fun delete(displayData: Display) {
        Scheduler.runAsync {
            Main.getInstance().storage.deleteDisplay(displayData)
        }

        @Suppress("UNCHECKED_CAST")
        Utils.sendDeletePacket(displayData.receivers as MutableList<Player?>, displayData.id)
        displays.remove(displayData.id)
    }

    // Delete a display by ID, checking ownership
    @JvmStatic
    fun delete(id: UUID, player: Player) {
        val displayData = displays[id] ?: return

        if (displayData.ownerId != player.uniqueId) {
            Main.getInstance().logger.warning("Player ${player.name} sent delete packet while not owner!")
            return
        }

        delete(displayData)
    }

    // Report a display
    @JvmStatic
    fun report(id: UUID, player: Player) {
        val displayData = displays[id] ?: return
        val lastReport = reportTime.getOrPut(id) { 0L }

        if (System.currentTimeMillis() - lastReport < Main.config.settings.reports.cooldown * 1000L) {
            Message.sendMessage(player, "reportTooQuickly")
            return
        }

        reportTime[id] = System.currentTimeMillis()

        Scheduler.runAsync {
            try {
                if (Main.config.settings.reports.webhook_url.isEmpty()) {
                    Message.sendMessage(player, "reportNotConfigured")
                    return@runAsync
                }
                Reporter.sendReport(
                    displayData.pos1,
                    displayData.url,
                    displayData.id,
                    player,
                    Main.config.settings.reports.webhook_url,
                    Bukkit.getOfflinePlayer(displayData.ownerId).name
                )
                Message.sendMessage(player, "reportSent")
            } catch (e: Exception) {
                Main.getInstance().logger.severe("Unable to send webhook message: ${e.message}")
            }
        }
    }

    // Check if a selection overlaps with existing displays
    fun isOverlaps(data: Selection): Boolean {
        val pos1 = data.pos1 ?: return false
        val pos2 = data.pos2 ?: return false
        val selWorld = pos1.world

        val region = Region.calculateRegion(pos1, pos2)
        val box = BoundingBox(
            region.minX.toDouble(),
            region.minY.toDouble(),
            region.minZ.toDouble(),
            (region.maxX + 1).toDouble(),
            (region.maxY + 1).toDouble(),
            (region.maxZ + 1).toDouble()
        )

        return displays.values.any { display ->
            display.pos1.world == selWorld && box.overlaps(display.box)
        }
    }

    // Check if a location is within any display
    fun isContains(location: Location): Display? {
        return displays.values.firstOrNull { display ->
            display.pos1.world == location.world && display.box.contains(location.toVector())
        }
    }

    // Register a list of displays
    fun register(list: List<Display>) {
        list.forEach { display ->
            displays[display.id] = display
        }
    }

    // Save all displays using the provided consumer
    fun save(saveDisplay: Consumer<Display>) {
        displays.values.forEach(saveDisplay)
    }
}
