package com.dreamdisplays

import com.dreamdisplays.Focuser.Companion.instance
import com.dreamdisplays.downloader.Init
import com.dreamdisplays.net.common.Delete
import com.dreamdisplays.net.common.DisplayEnabled
import com.dreamdisplays.net.common.Sync
import com.dreamdisplays.net.common.Version
import com.dreamdisplays.net.s2c.DisplayInfo
import com.dreamdisplays.net.s2c.Premium
import com.dreamdisplays.net.s2c.ReportEnabled
import com.dreamdisplays.screen.Manager
import com.dreamdisplays.screen.Manager.registerScreen
import com.dreamdisplays.screen.Manager.saveAllScreens
import com.dreamdisplays.screen.Manager.saveScreenData
import com.dreamdisplays.screen.Manager.screens
import com.dreamdisplays.screen.Manager.unloadAll
import com.dreamdisplays.screen.Manager.unloadedScreens
import com.dreamdisplays.screen.Manager.unregisterScreen
import com.dreamdisplays.screen.Screen
import com.dreamdisplays.screen.Settings
import com.dreamdisplays.screen.Settings.FullDisplayData
import com.dreamdisplays.screen.Settings.load
import com.dreamdisplays.screen.Settings.removeDisplay
import com.dreamdisplays.screen.configuration.ConfigurationScreen
import com.dreamdisplays.util.Facing
import com.dreamdisplays.util.RayCasting
import com.dreamdisplays.util.Utils
import me.inotsleep.utils.logging.LoggingManager.error
import me.inotsleep.utils.logging.LoggingManager.info
import me.inotsleep.utils.logging.LoggingManager.setLogger
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
object Initializer {

    const val MOD_ID = "dreamdisplays"
    private val wasPressed = booleanArrayOf(false)
    private val wasInMultiplayer = AtomicBoolean(false)
    private val lastLevel = AtomicReference<ClientLevel?>(null)
    private val wasFocused = AtomicBoolean(false)
    private var unloadCheckTick = 0

    @JvmField
    val config = Config(File("./config/$MOD_ID"))

    @JvmField
    val timerThread = Thread {
        var lastDistance = 64
        var isErrored = false
        while (!isErrored) {
            Manager.getScreens().forEach { it.reloadQuality() }
            if (config.defaultDistance != lastDistance) {
                config.defaultDistance = lastDistance
                config.save()
            }
            try {
                Thread.sleep(2500)
            } catch (_: InterruptedException) {
                isErrored = true
            }
        }
    }

    @JvmField
    var isOnScreen = false

    @JvmField
    var focusMode = false

    @JvmField
    var displaysEnabled = true

    @JvmField
    var isPremium = false

    @JvmField
    var isReportingEnabled = true

    private var hoveredScreen: Screen? = null
    private lateinit var mod: Mod

    @JvmStatic
    fun onModInit(dreamDisplaysMod: Mod) {
        mod = dreamDisplaysMod
        setLogger(getLogger(MOD_ID))
        info("Starting Dream Displays...")
        config.reload()

        // Load client display settings
        load()

        Init.init()
        instance.start()

        timerThread.start()
    }

    @JvmStatic
    fun onDisplayInfoPacket(packet: DisplayInfo) {
        if (!displaysEnabled) return

        if (screens.containsKey(packet.uuid)) {
            val screen = screens[packet.uuid]
            screen?.updateData(packet)
            return
        }

        // Check if player is in range before creating the display
        val player = Minecraft.getInstance().player
        if (player != null) {
            // Get saved render distance or use default
            val savedData = Settings.getDisplayData(packet.uuid)
            val renderDistance = savedData?.renderDistance ?: config.defaultDistance

            // Screen.getDistanceToScreen()
            val x = packet.pos.x
            val y = packet.pos.y
            val z = packet.pos.z
            val width = packet.width
            val height = packet.height
            val facing = packet.facing.toString()

            var maxX = x
            val maxY = y + height - 1
            var maxZ = z

            when (facing) {
                "NORTH", "SOUTH" -> maxX += width - 1
                "EAST", "WEST" -> maxZ += width - 1
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

        unloadedScreens.remove(packet.uuid)

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

    @JvmStatic
    fun onDisplayEnabledPacket(packet: DisplayEnabled) {
        displaysEnabled = packet.enabled
        config.displaysEnabled = packet.enabled
        config.save()
    }

    @JvmStatic
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
        val screen = Screen(
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

        val savedData = Settings.getDisplayData(uuid)
        val renderDistance = savedData?.renderDistance ?: config.defaultDistance
        screen.renderDistance = renderDistance

        registerScreen(screen)
        if (code != "") screen.loadVideo(code, lang)
    }

    @JvmStatic
    fun onSyncPacket(packet: Sync) {
        if (!screens.containsKey(packet.uuid)) return
        val screen = screens[packet.uuid]
        screen?.updateData(packet)
    }

    // Restore a screen from cached data (when player enters render distance)
    private fun restoreScreen(data: FullDisplayData) {
        val screen = Screen(
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

        screens[screen.uuid] = screen

        if (data.videoUrl.isNotEmpty()) {
            screen.loadVideo(data.videoUrl, data.lang)
        }
    }

    private fun checkVersionAndSendPacket() {
        try {
            val version = Utils.getModVersion()
            sendPacket(Version(version))
        } catch (e: Exception) {
            error("Unable to get version", e)
        }
    }

    @JvmStatic
    fun onEndTick(minecraft: Minecraft) {
        val level = minecraft.level
        if (level != null && minecraft.currentServer != null) {
            if (lastLevel.get() == null) {
                lastLevel.set(level)
                checkVersionAndSendPacket()
            }

            if (level != lastLevel.get()) {
                lastLevel.set(level)

                unloadAll()
                hoveredScreen = null

                checkVersionAndSendPacket()
            }

            wasInMultiplayer.set(true)
        } else {
            if (wasInMultiplayer.get()) {
                wasInMultiplayer.set(false)
                unloadAll()
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
        if (unloadCheckTick >= 10 && displaysEnabled && !unloadedScreens.isEmpty()) {
            unloadCheckTick = 0
            // Collect screens to restore first to avoid ConcurrentModificationException
            val toRestore = ArrayList<FullDisplayData>()

            for (data in unloadedScreens.values) {
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
                unloadedScreens.remove(data.uuid)
                restoreScreen(data)
            }
        }

        for (screen in Manager.getScreens()) {
            val displayRenderDistance = screen.renderDistance.toDouble()

            if (
                displayRenderDistance <
                screen.getDistanceToScreen(player.blockPosition()) ||
                !displaysEnabled
            ) {
                saveScreenData(screen)
                unregisterScreen(screen)
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
        hoveredScreen?.let { ConfigurationScreen.open(it) }
    }

    @JvmStatic
    fun sendPacket(packet: CustomPacketPayload) {
        mod.sendPacket(packet)
    }

    @JvmStatic
    fun onDeletePacket(packet: Delete) {
        val screen = screens[packet.uuid]
        if (screen != null) {
            unregisterScreen(screen)
        }

        unloadedScreens.remove(packet.uuid)

        removeDisplay(packet.uuid)
        info(
            "Display deleted and removed from saved data: " + packet.uuid
        )
    }

    @JvmStatic
    fun onStop() {
        saveAllScreens()
        timerThread.interrupt()
        unloadAll()
        instance.interrupt()
    }

    @JvmStatic
    fun onPremiumPacket(packet: Premium) {
        isPremium = packet.premium
    }

    @JvmStatic
    fun onReportEnabledPacket(packet: ReportEnabled) {
        isReportingEnabled = packet.enabled
    }
}
