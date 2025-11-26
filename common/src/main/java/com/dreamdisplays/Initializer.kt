package com.dreamdisplays

import com.dreamdisplays.net.*
import com.dreamdisplays.screen.Manager
import com.dreamdisplays.screen.Menu
import com.dreamdisplays.screen.Screen
import com.dreamdisplays.screen.Settings
import com.dreamdisplays.util.Facing
import com.dreamdisplays.util.RayCasting
import com.dreamdisplays.util.Utils
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import org.bytedeco.ffmpeg.global.*
import org.bytedeco.javacpp.Loader
import org.joml.Vector3i
import org.jspecify.annotations.NullMarked
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@NullMarked
object Initializer {
    const val MOD_ID: String = "dreamdisplays"
    private val wasPressed = booleanArrayOf(false)
    private val wasInMultiplayer = AtomicBoolean(false)
    private val lastLevel = AtomicReference<ClientLevel?>(null)
    private val wasFocused = AtomicBoolean(false)
    @JvmField
    var config: Config = Config(File("./config/$MOD_ID"))
    var timerThread: Thread = Thread {
        val lastDistance = 64
        var isErrored = false
        while (!isErrored) {
            Manager.getScreens().forEach { obj: Screen? -> obj!!.reloadQuality() }
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
    var isOnScreen: Boolean = false
    var focusMode: Boolean = false
    @JvmField
    var displaysEnabled: Boolean = true
    var isPremium: Boolean = false
    private var hoveredScreen: Screen? = null
    private var mod: Mod? = null

    @JvmStatic
    fun onModInit(dreamDisplaysMod: Mod) {
        mod = dreamDisplaysMod
        LoggingManager.setLogger(LoggerFactory.getLogger(MOD_ID))
        LoggingManager.info("Starting Dream Displays")
        config.reload()

        // Load client display settings
        Settings.load()

        // Initialize FFmpeg native libraries
        try {
            LoggingManager.info("Loading FFmpeg native libraries...")
            Loader.load(avutil::class.java)
            Loader.load(avcodec::class.java)
            Loader.load(avformat::class.java)
            Loader.load(swscale::class.java)
            Loader.load(swresample::class.java)
            LoggingManager.info("FFmpeg libraries loaded successfully!")
        } catch (e: Exception) {
            LoggingManager.error("Failed to load FFmpeg libraries", e)
        }

        Focuser().start()

        timerThread.start()
    }

    @JvmStatic
    fun onDisplayInfoPacket(packet: Info) {
        if (!displaysEnabled) return

        if (Manager.screens.containsKey(packet.id)) {
            val screen: Screen = Manager.screens.get(packet.id)!!
            screen.updateData(packet)
            return
        }

        createScreen(
            packet.id,
            packet.ownerId,
            packet.pos,
            packet.facing,
            packet.width,
            packet.height,
            packet.url,
            packet.lang,
            packet.isSync
        )
    }

    fun createScreen(
        id: UUID,
        ownerId: UUID,
        pos: Vector3i,
        facing: Facing,
        width: Int,
        height: Int,
        code: String,
        lang: String,
        isSync: Boolean
    ) {
        val screen = Screen(id, ownerId, pos.x(), pos.y(), pos.z(), facing.toString(), width, height, isSync)
        checkNotNull(Minecraft.getInstance().player)
        if (screen.getDistanceToScreen(Minecraft.getInstance().player!!.blockPosition()) > config.defaultDistance) return
        Manager.registerScreen(screen)
        if (code != "") screen.loadVideo(code, lang)
    }

    @JvmStatic
    fun onSyncPacket(packet: Sync) {
        if (!Manager.screens.containsKey(packet.id)) return
        val screen = Manager.screens.get(packet.id)
        screen?.updateData(packet)
    }

    private fun checkVersionAndSendPacket() {
        try {
            val version = Utils.modVersion
            sendPacket(Version(version))
        } catch (e: Exception) {
            LoggingManager.error("Unable to get version", e)
        }
    }

    @JvmStatic
    fun onEndTick(minecraft: Minecraft) {
        if (minecraft.level != null && minecraft.currentServer != null) {
            if (lastLevel.get() == null) {
                lastLevel.set(minecraft.level)
                checkVersionAndSendPacket()
            }

            if (minecraft.level !== lastLevel.get()) {
                lastLevel.set(minecraft.level)

                Manager.unloadAll()
                hoveredScreen = null

                checkVersionAndSendPacket()
            }

            wasInMultiplayer.set(true)
        } else {
            if (wasInMultiplayer.get()) {
                wasInMultiplayer.set(false)
                Manager.unloadAll()
                hoveredScreen = null
                lastLevel.set(null)
                return
            }
        }

        if (minecraft.player == null) return

        val result = RayCasting.rCBlock(64.0)
        hoveredScreen = null
        isOnScreen = false

        for (screen in Manager.getScreens()) {
            if (config.defaultDistance < screen.getDistanceToScreen(minecraft.player!!.blockPosition()) || !displaysEnabled) {
                Manager.unregisterScreen(screen)
                if (hoveredScreen === screen) {
                    hoveredScreen = null
                    isOnScreen = false
                }
            } else {
                if (result != null) if (screen.isInScreen(result.blockPos)) {
                    hoveredScreen = screen
                    isOnScreen = true
                }

                screen.tick(minecraft.player!!.blockPosition())
            }
        }

        val window = minecraft.window.handle()
        val pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

        if (pressed && !wasPressed[0]) {
            if (minecraft.player != null && minecraft.player!!.isShiftKeyDown) {
                checkAndOpenScreen()
            }
        }

        wasPressed[0] = pressed

        if (focusMode && minecraft.player != null && hoveredScreen != null) {
            minecraft.player!!.addEffect(
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
        } else if (!focusMode && wasFocused.get() && minecraft.player != null) {
            minecraft.player!!.removeEffect(MobEffects.BLINDNESS)
            wasFocused.set(false)
        }
    }

    private fun checkAndOpenScreen() {
        if (hoveredScreen == null) return
        Menu.open(hoveredScreen!!)
    }

    @JvmStatic
    fun sendPacket(packet: CustomPacketPayload) {
        mod!!.sendPacket(packet)
    }

    @JvmStatic
    fun onDeletePacket(deletePacket: Delete) {
        val screen = Manager.screens.get(deletePacket.id) ?: return

        Manager.unregisterScreen(screen)
    }

    @JvmStatic
    fun onStop() {
        timerThread.interrupt()
        Manager.unloadAll()
        Focuser.instance.interrupt()
    }

    @JvmStatic
    fun onPremiumPacket(packet: Premium) {
        isPremium = packet.premium
    }
}
