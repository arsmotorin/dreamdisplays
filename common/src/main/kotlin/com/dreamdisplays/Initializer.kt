package com.dreamdisplays

import com.dreamdisplays.client.ui.DisplayMenu
import com.dreamdisplays.display.DisplayManager
import com.dreamdisplays.display.DisplayScreen
import com.dreamdisplays.display.DisplaySettings
import com.dreamdisplays.ffmpeg.FFmpegBinary
import com.dreamdisplays.net.Packets
import com.dreamdisplays.util.FacingUtil
import com.dreamdisplays.util.GeneralUtil
import com.dreamdisplays.util.RayCastingUtil
import com.dreamdisplays.ytdlp.FormatDiskCache
import com.dreamdisplays.ytdlp.YtDlp
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import org.joml.Vector3i
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/** Main mod initializer. */
object Initializer {

    const val MOD_ID: String = "dreamdisplays"

    private val wasPressed = booleanArrayOf(false)
    private val wasInMultiplayer = AtomicBoolean(false)
    private val lastLevel = AtomicReference<ClientLevel?>(null)
    private val wasFocused = AtomicBoolean(false)

    var config: Config = Config(File("./config/$MOD_ID"))

    val timerThread: Thread = Thread({
        var running = true
        while (running) {
            DisplayManager.getScreens().forEach(DisplayScreen::reloadQuality)
            try {
                Thread.sleep(2500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                running = false
            }
        }
    }, "dreamdisplays-quality-refresh")

    var isOnScreen: Boolean = false
    var focusMode: Boolean = false
    var displaysEnabled: Boolean = true
    var isPremium: Boolean = false
    var isReportingEnabled: Boolean = true

    private var unloadCheckTick: Int = 0
    private var hoveredDisplayScreen: DisplayScreen? = null
    private lateinit var mod: Mod

    fun onModInit(dreamDisplaysMod: Mod) {
        mod = dreamDisplaysMod
        LoggingManager.setLogger(LoggerFactory.getLogger(MOD_ID))
        LoggingManager.info("[Initializer] Starting Dream Displays...")
        config.reload()

        DisplaySettings.load()

        YtDlp.prewarmAsync()
        FFmpegBinary.prewarmAsync()
        Thread({ FormatDiskCache.sweepExpired() }, "dreamdisplays-cache-sweep").start()
        Focuser().start()

        timerThread.start()
    }

    fun onDisplayInfoPacket(packet: Packets.Info) {
        if (!displaysEnabled) return

        if (DisplayManager.screens.containsKey(packet.uuid)) {
            val displayScreen = DisplayManager.screens[packet.uuid]
            displayScreen?.updateData(packet)
            return
        }

        Minecraft.getInstance().player?.let { player ->
            val savedData = DisplaySettings.getDisplayData(packet.uuid)
            val renderDistance = savedData?.renderDistance ?: config.defaultDistance

            val x = packet.pos.x
            val y = packet.pos.y
            val z = packet.pos.z
            val w = packet.width
            val h = packet.height
            val facing = packet.facingUtil.toString()

            var maxX = x
            val maxY = y + h - 1
            var maxZ = z

            when (facing) {
                "NORTH", "SOUTH" -> maxX += w - 1
                "EAST", "WEST" -> maxZ += w - 1
            }

            val playerPos = player.blockPosition()
            val clampedX = minOf(maxOf(playerPos.x, x), maxX)
            val clampedY = minOf(maxOf(playerPos.y, y), maxY)
            val clampedZ = minOf(maxOf(playerPos.z, z), maxZ)
            val closestPos = BlockPos(clampedX, clampedY, clampedZ)
            val distance = sqrt(playerPos.distSqr(closestPos))

            if (distance > renderDistance) return
        }

        YtDlp.prefetchFormats(packet.url)
        DisplayManager.unloadedScreens.remove(packet.uuid)

        createScreen(
            packet.uuid, packet.ownerUuid, packet.pos, packet.facingUtil,
            packet.width, packet.height, packet.url, packet.lang, packet.isSync
        )
    }

    fun onDisplayEnabledPacket(packet: Packets.DisplayEnabled) {
        displaysEnabled = packet.enabled
        config.displaysEnabled = packet.enabled
        config.save()
    }

    fun createScreen(
        uuid: UUID, ownerUuid: UUID, pos: Vector3i, facingUtil: FacingUtil,
        width: Int, height: Int, code: String, lang: String, isSync: Boolean,
    ) {
        val displayScreen = DisplayScreen(
            uuid, ownerUuid, pos.x(), pos.y(), pos.z(), facingUtil.toString(),
            width, height, isSync
        )

        val savedData = DisplaySettings.getDisplayData(uuid)
        displayScreen.setRenderDistance(savedData?.renderDistance ?: config.defaultDistance)

        DisplayManager.registerScreen(displayScreen)
        if (code != "") displayScreen.loadVideo(code, lang)
    }

    fun onSyncPacket(packet: Packets.Sync) {
        if (!DisplayManager.screens.containsKey(packet.uuid)) return
        DisplayManager.screens[packet.uuid]?.updateData(packet)
    }

    private fun restoreScreen(data: DisplaySettings.FullDisplayData) {
        val displayScreen = DisplayScreen(
            data.uuid, data.ownerUuid, data.x, data.y, data.z, data.facing,
            data.width, data.height, data.isSync
        )
        displayScreen.setRenderDistance(data.renderDistance)
        displayScreen.setSavedTimeNanos(data.currentTimeNanos)
        displayScreen.volume = data.volume
        displayScreen.quality = data.quality
        displayScreen.brightness = data.brightness
        displayScreen.muted = data.muted

        DisplayManager.screens[displayScreen.uuid] = displayScreen

        if (data.videoUrl.isNotEmpty()) {
            displayScreen.loadVideo(data.videoUrl, data.lang)
        }
    }

    private fun checkVersionAndSendPacket() {
        try {
            sendPacket(Packets.Version(GeneralUtil.getModVersion()))
        } catch (e: Exception) {
            LoggingManager.error("[Initializer] Unable to get version", e)
        }
    }

    fun onEndTick(minecraft: Minecraft) {
        val level = minecraft.level
        if (level != null && minecraft.currentServer != null) {
            if (lastLevel.get() == null) {
                lastLevel.set(level)
                checkVersionAndSendPacket()
            }
            if (level !== lastLevel.get()) {
                lastLevel.set(level)
                DisplayManager.unloadAll()
                hoveredDisplayScreen = null
                checkVersionAndSendPacket()
            }
            wasInMultiplayer.set(true)
        } else {
            if (wasInMultiplayer.get()) {
                wasInMultiplayer.set(false)
                DisplayManager.unloadAll()
                hoveredDisplayScreen = null
                lastLevel.set(null)
                return
            }
        }

        val result = RayCastingUtil.rCBlock(64.0)
        hoveredDisplayScreen = null
        isOnScreen = false
        val player = minecraft.player ?: return

        unloadCheckTick++
        if (unloadCheckTick >= 10 && displaysEnabled && !DisplayManager.unloadedScreens.isEmpty()) {
            unloadCheckTick = 0
            val toRestore = ArrayList<DisplaySettings.FullDisplayData>()

            for (data in DisplayManager.unloadedScreens.values) {
                if (data.videoUrl.isEmpty()) continue

                var maxX = data.x
                val maxY = data.y + data.height - 1
                var maxZ = data.z
                when (data.facing) {
                    "NORTH", "SOUTH" -> maxX += data.width - 1
                    "EAST", "WEST" -> maxZ += data.width - 1
                }

                val playerPos = player.blockPosition()
                val clampedX = minOf(maxOf(playerPos.x, data.x), maxX)
                val clampedY = minOf(maxOf(playerPos.y, data.y), maxY)
                val clampedZ = minOf(maxOf(playerPos.z, data.z), maxZ)
                val closestPos = BlockPos(clampedX, clampedY, clampedZ)
                val distance = sqrt(playerPos.distSqr(closestPos))

                if (distance <= data.renderDistance) toRestore.add(data)
            }

            for (data in toRestore) {
                DisplayManager.unloadedScreens.remove(data.uuid)
                restoreScreen(data)
            }
        }

        for (displayScreen in DisplayManager.getScreens()) {
            val displayRenderDistance = displayScreen.renderDistance.toDouble()

            if (displayRenderDistance < displayScreen.getDistanceToScreen(player.blockPosition()) || !displaysEnabled) {
                DisplayManager.saveScreenData(displayScreen)
                DisplayManager.unregisterScreen(displayScreen)
                if (hoveredDisplayScreen === displayScreen) {
                    hoveredDisplayScreen = null
                    isOnScreen = false
                }
            } else {
                if (result != null && displayScreen.isInScreen(result.blockPos)) {
                    hoveredDisplayScreen = displayScreen
                    isOnScreen = true
                }
                displayScreen.tick(player.blockPosition())
            }
        }

        val window = minecraft.window.handle()
        val pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

        if (pressed && !wasPressed[0]) {
            if (player.isShiftKeyDown) checkAndOpenScreen()
        }
        wasPressed[0] = pressed

        if (focusMode && hoveredDisplayScreen != null) {
            player.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 20 * 2, 1, false, false, false))
            wasFocused.set(true)
        } else if (!focusMode && wasFocused.get()) {
            player.removeEffect(MobEffects.BLINDNESS)
            wasFocused.set(false)
        }
    }

    private fun checkAndOpenScreen() {
        hoveredDisplayScreen?.let { DisplayMenu.open(it) }
    }

    fun sendPacket(packet: CustomPacketPayload) {
        mod.sendPacket(packet)
    }

    fun onDeletePacket(packet: Packets.Delete) {
        DisplayManager.screens[packet.uuid]?.let { DisplayManager.unregisterScreen(it) }
        DisplayManager.unloadedScreens.remove(packet.uuid)
        DisplaySettings.removeDisplay(packet.uuid)
        LoggingManager.info("[Initializer] Display deleted and removed from saved data: ${packet.uuid}")
    }

    fun onStop() {
        DisplayManager.saveAllScreens()
        timerThread.interrupt()
        DisplayManager.unloadAll()
        Focuser.instance.interrupt()
    }

    fun onPremiumPacket(packet: Packets.Premium) {
        isPremium = packet.premium
    }

    fun onReportEnabledPacket(packet: Packets.ReportEnabled) {
        isReportingEnabled = packet.enabled
    }

    fun onClearCachePacket(packet: Packets.ClearCache) {
        for (displayUuid in packet.displayUuids) {
            DisplayManager.screens[displayUuid]?.let {
                it.unregister()
                DisplayManager.screens.remove(displayUuid)
            }
            DisplaySettings.removeDisplay(displayUuid)
        }
    }
}
