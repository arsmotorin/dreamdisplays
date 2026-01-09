package com.dreamdisplays

import com.dreamdisplays.WindowFocuser.Companion.instance
import com.dreamdisplays.net.common.DeletePacket
import com.dreamdisplays.net.common.DisplayEnabledPacket
import com.dreamdisplays.net.common.SyncPacket
import com.dreamdisplays.net.common.VersionPacket
import com.dreamdisplays.net.s2c.DisplayInfoPacket
import com.dreamdisplays.net.s2c.PremiumPacket
import com.dreamdisplays.net.s2c.ReportEnabledPacket
import com.dreamdisplays.screen.DisplayScreen
import com.dreamdisplays.screen.managers.ConfigurationManager
import com.dreamdisplays.screen.managers.ScreenManager
import com.dreamdisplays.screen.managers.ScreenManager.unloadAllDisplays
import com.dreamdisplays.screen.managers.SettingsManager
import com.dreamdisplays.util.Facing
import com.dreamdisplays.util.RayCasting
import com.dreamdisplays.util.Utils
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
    var isOnScreen = false
    var focusMode = false
    var displaysEnabled = true
    var isPremium = false
    var isReportingEnabled = true
    var ceilingFloorSupported = false
    var unloadCheckTick: Int = 0

    private var hoveredScreen: DisplayScreen? = null
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

        if (ScreenManager.screens.containsKey(packet.uuid)) {
            val screen = ScreenManager.screens[packet.uuid]
            screen?.updateData(packet)
            return
        }

        // Check if player is in range before creating the display
        val player = Minecraft.getInstance().player
        if (player != null) {
            // Get saved render distance or use default
            val savedData = SettingsManager.getDisplayData(packet.uuid)
            val renderDistance = savedData?.renderDistance ?: config.defaultDistance

            // Screen.getDistanceToScreen()
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

        createScreen(
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

    fun createScreen(
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
        val screen = DisplayScreen(
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
        screen.renderDistance = renderDistance

        ScreenManager.registerScreen(screen)
        if (code != "") screen.loadVideo(code, lang)
    }

    fun onSyncPacket(packet: SyncPacket) {
        if (!ScreenManager.screens.containsKey(packet.uuid)) return
        val screen = ScreenManager.screens[packet.uuid]
        screen?.updateData(packet)
    }

    // Restore a screen from cached data (when player enters render distance)
    private fun restoreScreen(data: SettingsManager.FullDisplayData) {
        val screen = DisplayScreen(
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

        screen.renderDistance = data.renderDistance
        screen.setSavedTimeNanos(data.currentTimeNanos)
        screen.volume = data.volume.toDouble()
        screen.quality = data.quality
        screen.muted = data.muted

        ScreenManager.screens[screen.uuid] = screen

        if (data.videoUrl.isNotEmpty()) {
            screen.loadVideo(data.videoUrl, data.lang)
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
                hoveredScreen = null

                checkVersionAndSendPacket()
            }

            wasInMultiplayer.set(true)
        } else {
            if (wasInMultiplayer.get()) {
                wasInMultiplayer.set(false)
                unloadAllDisplays()
                hoveredScreen = null
                lastLevel.set(null)
                return
            }
        }

        val result = RayCasting.rCBlock(64.0)
        hoveredScreen = null
        isOnScreen = false
        val player = minecraft.player ?: return

        unloadCheckTick++
        if (unloadCheckTick >= 10 && displaysEnabled && ScreenManager.unloadedScreens.isNotEmpty()) {
            unloadCheckTick = 0
            // Collect screens to restore first to avoid ConcurrentModificationException
            val toRestore = ArrayList<SettingsManager.FullDisplayData>()

            for (data in ScreenManager.unloadedScreens.values) {
                if (data.videoUrl.isEmpty()) continue

                // Screen.getDistanceToScreen
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
                ScreenManager.unloadedScreens.remove(data.uuid)
                restoreScreen(data)
            }
        }

        for (screen in ScreenManager.getScreens()) {
            val displayRenderDistance = screen.renderDistance.toDouble()

            if (
                displayRenderDistance <
                screen.getDistanceToScreen(player.blockPosition()) ||
                !displaysEnabled
            ) {
                ScreenManager.saveScreenData(screen)
                ScreenManager.unregisterScreen(screen)
                if (hoveredScreen == screen) {
                    hoveredScreen = null
                    isOnScreen = false
                }
            } else {
                if (result != null && screen.isInScreen(result.blockPos)) {
                    hoveredScreen = screen
                    isOnScreen = true
                }

                screen.tick(player.blockPosition())
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

        if (focusMode && hoveredScreen != null) {
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
        hoveredScreen?.let { ConfigurationManager.open(it) }
    }

    fun sendPacket(packet: CustomPacketPayload) {
        mod.sendPacket(packet)
    }

    fun onDeletePacket(packet: DeletePacket) {
        val screen = ScreenManager.screens[packet.uuid]
        if (screen != null) {
            ScreenManager.unregisterScreen(screen)
        }

        ScreenManager.unloadedScreens.remove(packet.uuid)

        SettingsManager.removeDisplay(packet.uuid)
        info(
            "Display deleted and removed from saved data: " + packet.uuid
        )
    }

    fun onStop() {
        ScreenManager.saveAllScreens()
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
