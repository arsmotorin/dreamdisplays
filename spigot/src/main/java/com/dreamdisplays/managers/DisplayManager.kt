package com.dreamdisplays.managers

import com.dreamdisplays.DreamDisplaysPlugin
import com.dreamdisplays.datatypes.DisplayData
import com.dreamdisplays.datatypes.SelectionData
import com.dreamdisplays.utils.MessageUtil
import com.dreamdisplays.utils.ReportSender
import com.dreamdisplays.utils.net.PacketUtils
import me.inotsleep.utils.logging.LoggingManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BoundingBox
import java.lang.reflect.Proxy
import java.util.*
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

object DisplayManager {
    private val displays: MutableMap<UUID, DisplayData> = mutableMapOf()
    private val reportTime: MutableMap<UUID, Long> = mutableMapOf()

    @JvmStatic
    fun getDisplayData(id: UUID?): DisplayData? = displays[id]

    fun getDisplays(): List<DisplayData> = displays.values.toList()

    fun register(displayData: DisplayData) {
        displays[displayData.id] = displayData
        displayData.sendUpdatePacket(displayData.receivers)
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
                display.isInRange(player.location)
            }.toMutableList()

            display.sendUpdatePacket(receivers)
        }
    }

    fun delete(displayData: DisplayData) {
        val deleteTask = Runnable {
            DreamDisplaysPlugin.getInstance().storage.deleteDisplay(displayData)
        }

        if (DreamDisplaysPlugin.getIsFolia()) {
            try {
                val bukkitClass = Class.forName("org.bukkit.Bukkit")
                val asyncScheduler = bukkitClass.getMethod("getAsyncScheduler").invoke(null)
                val consumerClass = Class.forName("java.util.function.Consumer")
                val task = Proxy.newProxyInstance(
                    consumerClass.classLoader,
                    arrayOf(consumerClass)
                ) { _, _, _ ->
                    deleteTask.run()
                    null
                }
                asyncScheduler.javaClass.getMethod("runNow", Any::class.java, consumerClass)
                    .invoke(asyncScheduler, DreamDisplaysPlugin.getInstance(), task)
            } catch (_: Exception) {
                deleteTask.run()
            }
        } else {
            object : BukkitRunnable() {
                override fun run() = deleteTask.run()
            }.runTaskAsynchronously(DreamDisplaysPlugin.getInstance())
        }

        PacketUtils.sendDeletePacket(displayData.receivers as MutableList<Player?>, displayData.id)
        displays.remove(displayData.id)
    }

    @JvmStatic
    fun delete(id: UUID, player: Player) {
        val displayData = displays[id] ?: return

        if (displayData.ownerId != player.uniqueId) {
            LoggingManager.warn("Player ${player.name} sent delete packet while not owner!")
            return
        }

        delete(displayData)
    }

    @JvmStatic
    fun report(id: UUID, player: Player) {
        val displayData = displays[id] ?: return
        val lastReport = reportTime.getOrPut(id) { 0L }

        if (System.currentTimeMillis() - lastReport < DreamDisplaysPlugin.config.settings.reportCooldown) {
            MessageUtil.sendColoredMessage(
                player,
                DreamDisplaysPlugin.config.messages["reportTooQuickly"] as String?
            )
            return
        }

        reportTime[id] = System.currentTimeMillis()

        val reportTask = Runnable {
            try {
                if (DreamDisplaysPlugin.config.settings.webhookUrl.isEmpty()) return@Runnable
                ReportSender.sendReport(
                    displayData.pos1,
                    displayData.url,
                    displayData.id,
                    player,
                    DreamDisplaysPlugin.config.settings.webhookUrl,
                    Bukkit.getOfflinePlayer(displayData.ownerId).name
                )
                MessageUtil.sendColoredMessage(
                    player,
                    DreamDisplaysPlugin.config.messages["reportSent"] as String?
                )
            } catch (e: Exception) {
                LoggingManager.error("Unable to send webhook message", e)
            }
        }

        if (DreamDisplaysPlugin.getIsFolia()) {
            try {
                val bukkitClass = Class.forName("org.bukkit.Bukkit")
                val asyncScheduler = bukkitClass.getMethod("getAsyncScheduler").invoke(null)
                val consumerClass = Class.forName("java.util.function.Consumer")
                val task = Proxy.newProxyInstance(
                    consumerClass.classLoader,
                    arrayOf(consumerClass)
                ) { _, _, _ ->
                    reportTask.run()
                    null
                }
                asyncScheduler.javaClass.getMethod("runNow", Any::class.java, consumerClass)
                    .invoke(asyncScheduler, DreamDisplaysPlugin.getInstance(), task)
            } catch (_: Exception) {
                reportTask.run()
            }
        } else {
            object : BukkitRunnable() {
                override fun run() = reportTask.run()
            }.runTaskAsynchronously(DreamDisplaysPlugin.getInstance())
        }
    }

    fun isOverlaps(data: SelectionData): Boolean {
        val pos1 = data.pos1 ?: return false
        val pos2 = data.pos2 ?: return false
        val selWorld = pos1.world

        val minX = min(pos1.blockX, pos2.blockX)
        val minY = min(pos1.blockY, pos2.blockY)
        val minZ = min(pos1.blockZ, pos2.blockZ)
        val maxX = max(pos1.blockX, pos2.blockX) + 1
        val maxY = max(pos1.blockY, pos2.blockY) + 1
        val maxZ = max(pos1.blockZ, pos2.blockZ) + 1

        val box = BoundingBox(
            minX.toDouble(),
            minY.toDouble(),
            minZ.toDouble(),
            maxX.toDouble(),
            maxY.toDouble(),
            maxZ.toDouble()
        )

        return displays.values.any { display ->
            display.pos1.world == selWorld && box.overlaps(display.box)
        }
    }

    fun isContains(location: Location): DisplayData? {
        return displays.values.firstOrNull { display ->
            display.pos1.world == location.world && display.box.contains(location.toVector())
        }
    }

    fun register(list: List<DisplayData>) {
        list.forEach { display ->
            displays[display.id] = display
        }
    }

    fun save(saveDisplay: Consumer<DisplayData>) {
        displays.values.forEach(saveDisplay)
    }
}
