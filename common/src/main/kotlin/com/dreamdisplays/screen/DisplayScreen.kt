package com.dreamdisplays.screen

import com.dreamdisplays.ModInitializer.MOD_ID
import com.dreamdisplays.ModInitializer.config
import com.dreamdisplays.ModInitializer.sendPacket
import com.dreamdisplays.net.c2s.RequestSyncPacket
import com.dreamdisplays.net.common.SyncPacket
import com.dreamdisplays.net.s2c.DisplayInfoPacket
import com.dreamdisplays.screen.managers.ConfigurationManager
import com.dreamdisplays.screen.managers.SettingsManager
import com.dreamdisplays.screen.managers.SettingsManager.updateSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.Minecraft.getInstance
import net.minecraft.client.renderer.RenderPipelines.SOLID_BLOCK
import net.minecraft.client.renderer.rendertype.RenderSetup.builder
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderType.BIG_BUFFER_SIZE
import net.minecraft.client.renderer.rendertype.RenderType.create
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.resources.Identifier.fromNamespaceAndPath
import org.jspecify.annotations.NullMarked
import java.lang.Integer.parseInt
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CompletableFuture.runAsync
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Represents a video display screen in the game world.
 */
@NullMarked
class DisplayScreen(
    val uuid: UUID,
    val ownerUuid: UUID,
    private var x: Int,
    private var y: Int,
    private var z: Int,
    private var facing: String,
    private var width: Int,
    private var height: Int,
    var isSync: Boolean,
) {
    var owner: Boolean = false
    var errored: Boolean = false
    var muted: Boolean = false
    var texture: DynamicTexture? = null
    var textureId: Identifier? = null
    var renderType: RenderType? = null
    var textureWidth: Int = 0
    var textureHeight: Int = 0

    var volume: Double = 0.0
        set(value) {
            field = value
            setVideoVolume(value.toFloat())
            updateSettings(uuid, value.toFloat(), quality, brightness, muted, paused)
        }

    var brightness: Float = 0f
        set(value) {
            field = max(0f, min(2f, value))
            mediaPlayer?.setBrightness(field.toDouble())
            // Save settings
            updateSettings(uuid, volume.toFloat(), quality, field, muted, paused)
        }

    private var videoStarted: Boolean = false
    private var paused: Boolean = false

    var quality: String = "720"
        set(value) {
            field = value
            mediaPlayer?.setQuality(value)
            // reloadTexture();
            // Save settings
            updateSettings(uuid, volume.toFloat(), value, brightness, muted, paused)
        }
    private var savedTimeNanos: Long = 0
    var renderDistance: Int = 64

    // Use a combined MediaPlayer instead of the separate VideoDecoder and AudioPlayer.
    private var mediaPlayer: MediaPlayer? = null
    var videoUrl: String? = null
        private set

    // Cache (good for performance)
    private var blockPos: BlockPos? = null
    var lang: String? = null
        private set

    init {
        owner = getInstance().player != null &&
                (ownerUuid.toString() == getInstance().player!!.uuid.toString())

        // Load saved settings for this display
        val savedSettings = SettingsManager.getSettings(uuid)
        val savedVolume = savedSettings.volume.toDouble()
        val savedQuality = savedSettings.quality
        val savedBrightness = savedSettings.brightness
        val savedMuted = savedSettings.muted
        val savedPaused = savedSettings.paused

        this.volume = savedVolume
        this.quality = savedQuality
        this.brightness = savedBrightness
        this.muted = savedMuted
        this.paused = savedPaused

        if (isSync) {
            sendRequestSyncPacket()
        }
    }

    // Loads a video from a given URL and language
    fun loadVideo(videoUrl: String, lang: String?) {
        if (videoUrl == "") return

        if (mediaPlayer != null) unregister()

        // Extract timecode from URL (t parameter in seconds)
        val timecodeSeconds = com.dreamdisplays.util.Utils.extractTimecode(videoUrl)

        // Load the video URL and language into the screen
        this.videoUrl = videoUrl
        this.lang = lang
        val shouldBePaused = this.paused
        runAsync {
            try {
                mediaPlayer = MediaPlayer(videoUrl, lang.toString(), this)
                val qualityInt = parseInt(this.quality.replace("p", ""))
                textureWidth = ((width / height.toDouble()) * qualityInt).toInt()
                textureHeight = qualityInt
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        waitForMFInit {
            if (timecodeSeconds > 0) {
                val compensatedSeconds = timecodeSeconds + 10
                val timecodeNanos = compensatedSeconds * 1_000_000_000L
                setSavedTimeNanos(timecodeNanos)
            }

            startVideo()
            if (shouldBePaused) {
                this.paused = true
                mediaPlayer?.pause()
            }
        }

        getInstance().execute { reloadTexture() }
    }

    // Updates the screen data based on a DisplayInfoPacket
    fun updateData(packet: DisplayInfoPacket) {
        this.x = packet.pos.x
        this.y = packet.pos.y
        this.z = packet.pos.z

        this.facing = packet.facing.toString()

        this.width = packet.width
        this.height = packet.height
        this.isSync = packet.isSync

        owner = getInstance().player != null &&
                (packet.ownerUuid.toString() == getInstance().player!!.uuid.toString())

        if (videoUrl != packet.url || lang != packet.lang) {
            this.paused = false
            loadVideo(packet.url, packet.lang)
            if (isSync) {
                sendRequestSyncPacket()
            }
        }
    }

    // Sends a RequestSyncPacket to the server to request synchronization data
    private fun sendRequestSyncPacket() {
        sendPacket(RequestSyncPacket(uuid))
    }

    // Updates the screen data based on a SyncPacket
    fun updateData(packet: SyncPacket) {
        isSync = packet.isSync
        if (!isSync) return

        val nanos = System.nanoTime()

        waitForMFInit {
            if (!videoStarted) {
                startVideo()
                setVideoVolume(config.syncDisplayVolume.toFloat())
            }

            if (paused) setPaused(false)

            val lostTime = System.nanoTime() - nanos

            seekVideoTo(packet.currentTime + lostTime)
            setPaused(packet.currentState)
        }
    }

    fun reloadTexture() {
        this.createTexture()
    }

    // Checks if a given BlockPos is within the screen boundaries
    fun isInScreen(pos: BlockPos): Boolean {
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

        return (pos.x in x..maxX &&
                y <= pos.y &&
                maxY >= pos.y &&
                z <= pos.z &&
                maxZ >= pos.z)
    }

    // Checks if the video has started playing
    fun isVideoStarted(): Boolean {
        return mediaPlayer != null && mediaPlayer!!.textureFilled()
    }

    // Calculates the distance from a given BlockPos to the closest point on the screen
    fun getDistanceToScreen(pos: BlockPos): Double {
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

        val clampedX = pos.x.coerceIn(x, maxX)
        val clampedY = pos.y.coerceIn(y, maxY)
        val clampedZ = pos.z.coerceIn(z, maxZ)

        val closestPos = BlockPos(clampedX, clampedY, clampedZ)

        return sqrt(pos.distSqr(closestPos))
    }

    // Updates the texture to fit the current video frame
    fun fitTexture() {
        if (mediaPlayer != null && texture != null) {
            try {
                mediaPlayer!!.updateFrame(texture!!.texture)
            } catch (_: Exception) {
                // Ignore errors if texture is not ready
            }
        }
    }

    // Returns screen position as BlockPos
    fun getPos(): BlockPos {
        if (blockPos == null) {
            blockPos = BlockPos(x, y, z)
        }
        return blockPos!!
    }

    // Returns screen facing direction
    fun getFacing(): String {
        return facing
    }

    // Returns screen width
    fun getWidth(): Float {
        return width.toFloat()
    }

    // Returns screen height
    fun getHeight(): Float {
        return height.toFloat()
    }

    // Sets video volume
    fun setVideoVolume(volume: Float) {
        try {
            mediaPlayer?.setVolume(volume.toDouble())
        } catch (_: Exception) {
            // Ignore errors if MediaPlayer is stopped or in invalid state (e.g., during server restart)
        }
    }

    // Returns list of available video qualities
    fun getQualityList(): List<Int> {
        if (mediaPlayer == null) return emptyList()
        return mediaPlayer!!.availableQualities!!
    }


    // Starts video playback
    fun startVideo() {
        if (mediaPlayer != null) {
            videoStarted = true
            if (paused) {
                mediaPlayer!!.pause()
            } else {
                mediaPlayer!!.play()
                paused = false
            }
            restoreSavedTime()
        }
    }

    // Returns the paused state of the video
    fun getPaused(): Boolean {
        return paused
    }

    // Sets the paused state of the video
    fun setPaused(paused: Boolean) {
        if (!videoStarted) {
            this.paused = paused
            waitForMFInit {
                startVideo()
                setVideoVolume(config.defaultDisplayVolume.toFloat())
            }
            return
        }
        this.paused = paused
        if (mediaPlayer != null) {
            if (paused) {
                mediaPlayer!!.pause()
            } else {
                mediaPlayer!!.play()
            }
        }
        updateSettings(uuid, volume.toFloat(), quality, brightness, muted, paused)
        if (owner && isSync) sendSync()
    }

    // Relative seek video: moves the video by a specified number of seconds (in our case it's +5 seconds) relative to the current position
    fun seekForward() {
        seekVideoRelative(5)
    }

    //  Relative seek video: moves the video by a specified number of seconds (in our case it's -5 seconds) relative to the current position
    fun seekBackward() {
        seekVideoRelative(-5)
    }

    // Relative seek video: moves the video by a specified number of seconds relative to the current position
    fun seekVideoRelative(seconds: Long) {
        mediaPlayer?.seekRelative(seconds.toDouble())
    }

    // Absolute (cinema) seek video: moves to a specific second
    fun seekVideoTo(nanos: Long) {
        mediaPlayer?.seekTo(nanos, false)
    }

    fun getDurationNanos(): Long {
        if (mediaPlayer != null) {
            return mediaPlayer!!.duration
        }
        return 0
    }

    fun unregister() {
        mediaPlayer?.stop()

        // Schedule texture cleanup on render thread to avoid "Rendersystem called from wrong thread" error
        val minecraft = getMinecraft()

        if (minecraft.screen is ConfigurationManager) {
            val displayConfScreen = minecraft.screen as ConfigurationManager
            if (displayConfScreen.screen === this) displayConfScreen.onClose()
        }
    }

    private fun getMinecraft(): Minecraft {
        val minecraft = getInstance()
        if (textureId != null) {
            minecraft.execute {
                val manager = minecraft.textureManager
                if (textureId != null) {
                    try {
                        manager.release(textureId!!)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return minecraft
    }

    fun mute(status: Boolean) {
        if (muted == status) return
        muted = status

        try {
            setVideoVolume(if (!status) volume.toFloat() else 0f)
            updateSettings(uuid, volume.toFloat(), quality, brightness, muted, paused)
        } catch (_: Exception) {
            // Ignore errors if mediaPlayer is stopped or in invalid state (e.g., during server restart)
        }
    }


    // Creates a new texture for the screen based on its dimensions and quality
    fun createTexture() {
        val qualityInt = parseInt(this.quality.replace("p", ""))
        textureWidth = ((width / height.toDouble()) * qualityInt).toInt()
        textureHeight = qualityInt

        if (texture != null) {
            texture!!.close()
            if (textureId != null) getInstance()
                .textureManager
                .release(textureId!!)
        }
        texture = DynamicTexture(
            UUID.randomUUID().toString(),
            textureWidth,
            textureHeight,
            true
        )
        textureId = fromNamespaceAndPath(
            MOD_ID,
            "screen-main-texture-" + uuid + "-" + UUID.randomUUID()
        )

        getInstance()
            .textureManager
            .register(textureId!!, texture!!)
        renderType = createRenderType(textureId!!)
    }

    fun sendSync() {
        if (mediaPlayer != null) {
            sendPacket(
                SyncPacket(
                    uuid,
                    isSync,
                    paused,
                    mediaPlayer!!.currentTime,
                    mediaPlayer!!.duration
                )
            )
        }
    }

    fun getCurrentTimeNanos(): Long {
        if (mediaPlayer != null) {
            return mediaPlayer!!.currentTime
        }
        return 0
    }

    // Set the saved time to restore when video loads
    fun setSavedTimeNanos(timeNanos: Long) {
        this.savedTimeNanos = timeNanos
    }

    // Restore the saved video playback time
    fun restoreSavedTime() {
        if (savedTimeNanos > 0 &&
            mediaPlayer != null &&
            mediaPlayer!!.isInitialized
        ) {
            mediaPlayer!!.seekToFast(savedTimeNanos)
        }
    }

    fun waitForMFInit(action: Runnable) {
        Thread {
            while (mediaPlayer == null || !mediaPlayer!!.isInitialized) {
                try {
                    sleep(100) // TODO: this is ugly
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }
            action.run()
        }.start()
    }

    fun tick(pos: BlockPos) {
        mediaPlayer?.tick(
            pos,
            config.defaultDistance.toDouble()
        )
    }

    fun afterSeek() {
        if (owner && isSync) sendSync()
    }

    companion object {
        // Creates a custom RenderType for rendering the screen texture
        private fun createRenderType(id: Identifier): RenderType {
            return create(
                "dream-displays",
                builder(SOLID_BLOCK)
                    .withTexture("Sampler0", id)
                    .bufferSize(BIG_BUFFER_SIZE)
                    .affectsCrumbling()
                    .useLightmap()
                    .createRenderSetup()
            )
        }
    }
}
