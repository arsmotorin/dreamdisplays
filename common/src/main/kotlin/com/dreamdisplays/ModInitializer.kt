package com.dreamdisplays

import com.dreamdisplays.WindowFocuser.Companion.instance
import com.dreamdisplays.net.common.DeletePacket
import com.dreamdisplays.net.common.DisplayEnabledPacket
import com.dreamdisplays.net.common.SyncPacket
import com.dreamdisplays.net.common.VersionPacket
import com.dreamdisplays.net.s2c.DisplayInfoPacket
import com.dreamdisplays.net.s2c.PremiumPacket
import com.dreamdisplays.net.s2c.ReportEnabledPacket
import com.dreamdisplays.displays.DisplayScreen
import com.dreamdisplays.displays.managers.ConfigurationManager
import com.dreamdisplays.displays.managers.DisplayManager
import com.dreamdisplays.displays.managers.DisplayManager.unloadAllDisplays
import com.dreamdisplays.displays.managers.SettingsManager
import com.dreamdisplays.utils.Facing
import com.dreamdisplays.utils.RayCasting
import com.dreamdisplays.utils.Utils
import me.inotsleep.utils.logging.LoggingManager.*
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import org.joml.Vector3i
import org.jspecify.annotations.NullMarked
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Main initializer.
 */
@NullMarked
object ModInitializer {

    const val MOD_ID = "dreamdisplays"
    private val wasPressed = booleanArrayOf(false)
    private val wasInMultiplayer = AtomicBoolean(false)
    private val lastLevel = AtomicReference<ClientLevel?>(null)
    private val wasFocused = AtomicBoolean(false)

    val config = ClientConfig(File("./config/$MOD_ID"))
    var isOnDisplay = false
    var focusMode = false
    var displaysEnabled = true
    var isPremium = false
    var isReportingEnabled = true
    var ceilingFloorSupported = false
    var unloadCheckTick: Int = 0

    private var hoveredDisplay: DisplayScreen? = null
    private lateinit var mod: ModPacketSender

    fun onModInit(dreamDisplaysMod: ModPacketSender) {
        mod = dreamDisplaysMod
        setLogger(getLogger(MOD_ID))
        info("Starting Dream Displays...")
        config.reload()

        // Load client display settings
        SettingsManager.load()

        instance.start()
    }

    fun onDisplayInfoPacket(packet: DisplayInfoPacket) {
        if (!displaysEnabled) return

        // Ignore ceiling/floor displays if client doesn't support them
        if (!ceilingFloorSupported && (packet.facing == Facing.UP || packet.facing == Facing.DOWN)) {
            return
        }

        if (DisplayManager.displays.containsKey(packet.uuid)) {
            val display = DisplayManager.displays[packet.uuid]
            display?.updateData(packet)
            return
        }

        // Check if player is in range before creating the display
        val player = Minecraft.getInstance().player
        if (player != null) {
            // Get saved render distance or use default
            val savedData = SettingsManager.getDisplayData(packet.uuid)
            val renderDistance = savedData?.renderDistance ?: config.defaultDistance

            val x = packet.pos.x
            val y = packet.pos.y
            val z = packet.pos.z
            val width = packet.width
            val height = packet.height
            val facing = packet.facing.toString()

            var maxX = x
            var maxY = y
            var maxZ = z

            when (facing) {
                "NORTH", "SOUTH" -> {
                    maxX += width - 1
                    maxY += height - 1
                }

                "EAST", "WEST" -> {
                    maxZ += width - 1
                    maxY += height - 1
                }

                "UP", "DOWN" -> {
                    maxX += width - 1
                    maxZ += height - 1
                }
            }

            val playerPos = player.blockPosition()
            val clampedX = playerPos.x.coerceIn(x, maxX)
            val clampedY = playerPos.y.coerceIn(y, maxY)
            val clampedZ = playerPos.z.coerceIn(z, maxZ)

            val closestPos = BlockPos(clampedX, clampedY, clampedZ)
            val distance = sqrt(playerPos.distSqr(closestPos))

            // Only create if within render distance
            if (distance > renderDistance) {
                return
            }
        }

        createDisplay(
            packet.uuid,
            packet.ownerUuid,
            packet.pos,
            packet.facing,
            packet.width,
            packet.height,
            packet.url,
            packet.lang,
            packet.isSync
        )
    }

    fun onDisplayEnabledPacket(packet: DisplayEnabledPacket) {
        displaysEnabled = packet.enabled
        config.displaysEnabled = packet.enabled
        config.save()
    }

    fun createDisplay(
        uuid: UUID,
        ownerUuid: UUID,
        pos: Vector3i,
        facing: Facing,
        width: Int,
        height: Int,
        code: String,
        lang: String,
        isSync: Boolean,
    ) {
        val display = DisplayScreen(
            uuid,
            ownerUuid,
            pos.x,
            pos.y,
            pos.z,
            facing.toString(),
            width,
            height,
            isSync
        )

        val savedData = SettingsManager.getDisplayData(uuid)
        val renderDistance = savedData?.renderDistance ?: config.defaultDistance
        display.renderDistance = renderDistance

        DisplayManager.registerDisplay(display)
        if (code != "") display.loadVideo(code, lang)
    }

    fun onSyncPacket(packet: SyncPacket) {
        if (!DisplayManager.displays.containsKey(packet.uuid)) return
        val display = DisplayManager.displays[packet.uuid]
        display?.updateData(packet)
    }

    // Restore a display from cached data (when player enters render distance)
    private fun restoreDisplay(data: SettingsManager.FullDisplayData) {
        val display = DisplayScreen(
            data.uuid,
            data.ownerUuid,
            data.x,
            data.y,
            data.z,
            data.facing,
            data.width,
            data.height,
            data.isSync
        )

        display.renderDistance = data.renderDistance
        display.setSavedTimeNanos(data.currentTimeNanos)
        display.volume = data.volume.toDouble()
        display.quality = data.quality
        display.muted = data.muted

        DisplayManager.displays[display.uuid] = display

        if (data.videoUrl.isNotEmpty()) {
            display.loadVideo(data.videoUrl, data.lang)
        }
    }

    private fun checkVersionAndSendPacket() {
        try {
            val version = Utils.getModVersion()
            sendPacket(VersionPacket(version))
        } catch (e: Exception) {
            error("Unable to get version", e)
        }
    }

    fun onEndTick(minecraft: Minecraft) {
        val level = minecraft.level
        if (level != null && minecraft.currentServer != null) {
            if (lastLevel.get() == null) {
                lastLevel.set(level)
                checkVersionAndSendPacket()
            }

            if (level != lastLevel.get()) {
                lastLevel.set(level)

                unloadAllDisplays()
                hoveredDisplay = null

                checkVersionAndSendPacket()
            }

            wasInMultiplayer.set(true)
        } else {
            if (wasInMultiplayer.get()) {
                wasInMultiplayer.set(false)
                unloadAllDisplays()
                hoveredDisplay = null
                lastLevel.set(null)
                return
            }
        }

        val result = RayCasting.rCBlock(64.0)
        hoveredDisplay = null
        isOnDisplay = false
        val player = minecraft.player ?: return

        unloadCheckTick++
        if (unloadCheckTick >= 10 && displaysEnabled && DisplayManager.unloadedDisplays.isNotEmpty()) {
            unloadCheckTick = 0
            // Collect displays to restore first to avoid ConcurrentModificationException
            val toRestore = ArrayList<SettingsManager.FullDisplayData>()

            for (data in DisplayManager.unloadedDisplays.values) {
                if (data.videoUrl.isEmpty()) continue

                var maxX = data.x
                val maxY = data.y + data.height - 1
                var maxZ = data.z

                when (data.facing) {
                    "NORTH", "SOUTH" -> maxX += data.width - 1
                    "EAST", "WEST" -> maxZ += data.width - 1
                }

                val playerPos = player.blockPosition()
                val clampedX = playerPos.x.coerceIn(data.x, maxX)
                val clampedY = playerPos.y.coerceIn(data.y, maxY)
                val clampedZ = playerPos.z.coerceIn(data.z, maxZ)

                val closestPos = BlockPos(clampedX, clampedY, clampedZ)
                val distance = sqrt(playerPos.distSqr(closestPos))

                // If player is now in range, mark for restoration
                if (distance <= data.renderDistance) {
                    toRestore.add(data)
                }
            }

            // Now restore outside the iteration
            for (data in toRestore) {
                DisplayManager.unloadedDisplays.remove(data.uuid)
                restoreDisplay(data)
            }
        }

        for (display in DisplayManager.getDisplays()) {
            val displayRenderDistance = display.renderDistance.toDouble()

            if (
                displayRenderDistance <
                display.getDistanceToDisplay(player.blockPosition()) ||
                !displaysEnabled
            ) {
                DisplayManager.saveDisplayData(display)
                DisplayManager.unregisterDisplay(display)
                if (hoveredDisplay == display) {
                    hoveredDisplay = null
                    isOnDisplay = false
                }
            } else {
                if (result != null && display.isInDisplay(result.blockPos)) {
                    hoveredDisplay = display
                    isOnDisplay = true
                }

                display.tick(player.blockPosition())
            }
        }

        val window = minecraft.window.handle()
        val pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

        if (pressed && !wasPressed[0]) {
            if (player.isShiftKeyDown) {
                checkAndOpenScreen()
            }
        }

        wasPressed[0] = pressed

        if (focusMode && hoveredDisplay != null) {
            player.addEffect(
                MobEffectInstance(
                    MobEffects.BLINDNESS,
                    20 * 2,
                    1,
                    false,
                    false,
                    false
                )
            )

            wasFocused.set(true)
        } else if (!focusMode && wasFocused.get()) {
            player.removeEffect(MobEffects.BLINDNESS)
            wasFocused.set(false)
        }
    }

    private fun checkAndOpenScreen() {
        hoveredDisplay?.let { ConfigurationManager.open(it) }
    }

    fun sendPacket(packet: CustomPacketPayload) {
        mod.sendPacket(packet)
    }

    fun onDeletePacket(packet: DeletePacket) {
        val display = DisplayManager.displays[packet.uuid]
        if (display != null) {
            DisplayManager.unregisterDisplay(display)
        }

        DisplayManager.unloadedDisplays.remove(packet.uuid)

        SettingsManager.removeDisplay(packet.uuid)
        info(
            "Display deleted and removed from saved data: " + packet.uuid
        )
    }

    fun onStop() {
        DisplayManager.saveAllDisplays()
        unloadAllDisplays()
        instance.interrupt()
    }

    fun onPremiumPacket(packet: PremiumPacket) {
        isPremium = packet.premium
    }

    fun onReportEnabledPacket(packet: ReportEnabledPacket) {
        isReportingEnabled = packet.enabled
    }

    fun onCeilingFloorSupportPacket(packet: com.dreamdisplays.net.s2c.CeilingFloorSupportPacket) {
        ceilingFloorSupported = packet.supported
    }
}
