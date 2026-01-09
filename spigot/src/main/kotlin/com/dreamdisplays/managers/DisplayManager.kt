package com.dreamdisplays.managers

import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.Main.Companion.getInstance
import com.dreamdisplays.datatypes.DisplayData
import com.dreamdisplays.datatypes.SelectionData
import com.dreamdisplays.utils.Message.sendMessage
import com.dreamdisplays.utils.Region.calculateRegion
import com.dreamdisplays.utils.Reporter.sendReport
import com.dreamdisplays.utils.Scheduler.runAsync
import com.dreamdisplays.utils.Scheduler.runSync
import com.dreamdisplays.utils.net.PacketUtils
import com.dreamdisplays.utils.net.PacketUtils.sendDelete
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.jspecify.annotations.NullMarked
import java.lang.System.currentTimeMillis
import java.util.*
import java.util.function.Consumer

/**
 * Manages all displays in the server.
 */
@NullMarked
object DisplayManager {
    private val displays: MutableMap<UUID, DisplayData> = mutableMapOf()
    private val reportTime: MutableMap<UUID, Long> = mutableMapOf()

    fun getDisplayData(id: UUID?): DisplayData? = displays[id]

    fun getDisplays(): List<DisplayData> = displays.values.toList()

    fun isContains(location: Location): DisplayData? {
        return displays.values.firstOrNull { display ->
            display.pos1.world == location.world && display.box.contains(location.toVector())
        }
    }

    fun isOverlaps(data: SelectionData): Boolean {
        val pos1 = data.pos1 ?: return false
        val pos2 = data.pos2 ?: return false
        val selWorld = pos1.world

        val region = calculateRegion(pos1, pos2, data.getFace())
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

    fun register(data: DisplayData) {
        displays[data.id] = data
        sendUpdate(data, getReceivers(data))
    }

    fun register(list: List<DisplayData>) {
        list.forEach { display ->
            displays[display.id] = display
        }
    }

    fun updateAllDisplays() {
        val playersByWorld = displays.values
            .mapNotNull { it.pos1.world }
            .distinct()
            .associateWith { it.players.toMutableList() }

        displays.values.forEach { display ->
            val world = display.pos1.world ?: return@forEach
            val worldPlayers = playersByWorld[world] ?: mutableListOf()

            val receivers = worldPlayers.filter { player ->
                player.location.isInRange(display)
            }.toMutableList()

            sendUpdate(display, receivers)
        }
    }

    fun getReceivers(display: DisplayData): List<Player> =
        display.pos1.world?.players
            ?.filter { it.location.isInRange(display) }
            ?: emptyList()

    private fun Location.isInRange(display: DisplayData): Boolean {
        val maxRender = config.settings.maxRenderDistance
        val clampedX = blockX.coerceIn(display.box.minX.toInt(), display.box.maxX.toInt())
        val clampedY = blockY.coerceIn(display.box.minY.toInt(), display.box.maxY.toInt())
        val clampedZ = blockZ.coerceIn(display.box.minZ.toInt(), display.box.maxZ.toInt())
        val dx = blockX - clampedX
        val dy = blockY - clampedY
        val dz = blockZ - clampedZ
        return dx * dx + dy * dy + dz * dz <= maxRender * maxRender
    }

    fun sendUpdate(display: DisplayData, players: List<Player>) {
        @Suppress("UNCHECKED_CAST")
        PacketUtils.sendDisplayInfo(
            players as MutableList<Player?>,
            display.id,
            display.ownerId,
            display.box.min,
            display.width,
            display.height,
            display.url,
            display.lang,
            display.facing,
            display.isSync
        )
    }

    fun delete(displayData: DisplayData) {
        runAsync {
            getInstance().storage.deleteDisplay(displayData)
        }

        @Suppress("UNCHECKED_CAST")
        (sendDelete(getReceivers(displayData) as MutableList<Player?>, displayData.id))
        displays.remove(displayData.id)
    }

    fun delete(id: UUID, player: Player) {
        val displayData = displays[id] ?: return

        if (displayData.ownerId != player.uniqueId) {
            getInstance().logger.warning("Player ${player.name} sent delete packet while not owner!")
            return
        }

        delete(displayData)
    }

    fun report(id: UUID, player: Player) {
        val displayData = displays[id] ?: return
        val lastReport = reportTime.getOrPut(id) { 0L }

        if (currentTimeMillis() - lastReport < config.settings.reportCooldown) {
            sendMessage(player, "reportTooQuickly")
            return
        }

        reportTime[id] = currentTimeMillis()

        runAsync {
            try {
                if (config.settings.webhookUrl.isEmpty()) return@runAsync
                sendReport(
                    displayData.pos1,
                    displayData.url,
                    displayData.id,
                    player,
                    config.settings.webhookUrl,
                    getOfflinePlayer(displayData.ownerId).name
                )
                runSync { sendMessage(player, "reportSent") }
            } catch (e: Exception) {
                getInstance().logger.warning("Exception while sending report: ${e.message}")
                runSync { sendMessage(player, "reportFailed") }
            }
        }
    }

    fun save(saveDisplay: Consumer<DisplayData>) {
        displays.values.forEach(saveDisplay)
    }
}
