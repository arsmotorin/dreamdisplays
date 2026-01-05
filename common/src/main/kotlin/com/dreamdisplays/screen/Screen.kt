package com.dreamdisplays.screen

import com.dreamdisplays.Initializer
import com.dreamdisplays.net.c2s.RequestSync
import com.dreamdisplays.net.common.Sync
import com.dreamdisplays.net.s2c.DisplayInfo
import com.dreamdisplays.screen.configuration.ConfigurationScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Represents a video display screen in the game world.
 */
@NullMarked
class Screen(
    val uuid: UUID,
    val ownerUuid: UUID,
    private var x: Int,
    private var y: Int,
    private var z: Int,
    private var facing: String,
    private var width: Int,
    private var height: Int,
    @JvmField var isSync: Boolean,
) {
    @JvmField
    var owner: Boolean = false
    @JvmField
    var errored: Boolean = false
    @JvmField
    var muted: Boolean = false
    @JvmField
    var texture: DynamicTexture? = null
    @JvmField
    var textureId: Identifier? = null
    @JvmField
    var renderType: RenderType? = null
    @JvmField
    var textureWidth: Int = 0
    @JvmField
    var textureHeight: Int = 0

    var volume: Double = 0.0
        set(value) {
            field = value
            setVideoVolume(value.toFloat())
            Settings.updateSettings(uuid, value.toFloat(), quality, brightness, muted, paused)
        }

    var brightness: Float = 0f
        set(value) {
            field = max(0f, min(2f, value))
            mediaPlayer?.setBrightness(field.toDouble())
            // Save settings
            Settings.updateSettings(uuid, volume.toFloat(), quality, field, muted, paused)
        }

    private var videoStarted: Boolean = false
    private var paused: Boolean = false

    var quality: String = "720"
        set(value) {
            field = value
            mediaPlayer?.setQuality(value)
            // reloadTexture();
            // Save settings
            Settings.updateSettings(uuid, volume.toFloat(), value, brightness, muted, paused)
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
        owner = Minecraft.getInstance().player != null &&
                (ownerUuid.toString() == Minecraft.getInstance().player!!.uuid.toString())

        // Load saved settings for this display
        val savedSettings = Settings.getSettings(uuid)
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

        // Load the video URL and language into the screen
        this.videoUrl = videoUrl
        this.lang = lang
        val shouldBePaused = this.paused
        CompletableFuture.runAsync {
            mediaPlayer = MediaPlayer(videoUrl, lang.toString(), this)
            val qualityInt = Integer.parseInt(this.quality.replace("p", ""))
            textureWidth = ((width / height.toDouble()) * qualityInt).toInt()
            textureHeight = qualityInt
        }

        waitForMFInit {
            startVideo()
            if (shouldBePaused) {
                this.paused = true
                mediaPlayer?.pause()
            }
        }

        Minecraft.getInstance().execute { reloadTexture() }
    }

    // Updates the screen data based on a DisplayInfoPacket
    fun updateData(packet: DisplayInfo) {
        this.x = packet.pos.x
        this.y = packet.pos.y
        this.z = packet.pos.z

        this.facing = packet.facing.toString()

        this.width = packet.width
        this.height = packet.height
        this.isSync = packet.isSync

        owner = Minecraft.getInstance().player != null &&
                (packet.ownerUuid.toString() == Minecraft.getInstance().player!!.uuid.toString())

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
        Initializer.sendPacket(RequestSync(uuid))
    }

    // Updates the screen data based on a SyncPacket
    fun updateData(packet: Sync) {
        isSync = packet.isSync
        if (!isSync) return

        val nanos = System.nanoTime()

        waitForMFInit {
            if (!videoStarted) {
                startVideo()
                setVideoVolume(Initializer.config.syncDisplayVolume.toFloat())
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

    // Reloads the video quality
    fun reloadQuality() {
        mediaPlayer?.setQuality(quality)
    }

    // Checks if a given BlockPos is within the screen boundaries
    fun isInScreen(pos: BlockPos): Boolean {
        var maxX = x
        val maxY = y + height - 1
        var maxZ = z

        when (facing) {
            "NORTH", "SOUTH" -> maxX += width - 1
            else -> maxZ += width - 1
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
        val maxY = y + height - 1
        var maxZ = z

        when (facing) {
            "NORTH", "SOUTH" -> maxX += width - 1
            "EAST", "WEST" -> maxZ += width - 1
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
        mediaPlayer?.setVolume(volume.toDouble())
    }


    // Returns list of available video qualities
    fun getQualityList(): List<Int> {
        if (mediaPlayer == null) return emptyList()
        return mediaPlayer!!.availableQualities
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
                setVideoVolume(Initializer.config.defaultDisplayVolume.toFloat())
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
        Settings.updateSettings(uuid, volume.toFloat(), quality, brightness, muted, paused)
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

    fun unregister() {
        mediaPlayer?.stop()

        // Schedule texture cleanup on render thread to avoid "Rendersystem called from wrong thread" error
        val minecraft = getMinecraft()

        if (minecraft.screen is ConfigurationScreen) {
            val displayConfScreen = minecraft.screen as ConfigurationScreen
            if (displayConfScreen.screen === this) displayConfScreen.onClose()
        }
    }

    private fun getMinecraft(): Minecraft {
        val minecraft = Minecraft.getInstance()
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

        setVideoVolume(if (!status) volume.toFloat() else 0f)
        Settings.updateSettings(uuid, volume.toFloat(), quality, brightness, muted, paused)
    }


    // Creates a new texture for the screen based on its dimensions and quality
    fun createTexture() {
        val qualityInt = Integer.parseInt(this.quality.replace("p", ""))
        textureWidth = ((width / height.toDouble()) * qualityInt).toInt()
        textureHeight = qualityInt

        if (texture != null) {
            texture!!.close()
            if (textureId != null) Minecraft.getInstance()
                .textureManager
                .release(textureId!!)
        }
        texture = DynamicTexture(
            UUID.randomUUID().toString(),
            textureWidth,
            textureHeight,
            true
        )
        textureId = Identifier.fromNamespaceAndPath(
            Initializer.MOD_ID,
            "screen-main-texture-" + uuid + "-" + UUID.randomUUID()
        )

        Minecraft.getInstance()
            .textureManager
            .register(textureId!!, texture!!)
        renderType = createRenderType(textureId!!)
    }

    fun sendSync() {
        if (mediaPlayer != null) {
            Initializer.sendPacket(
                Sync(
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
                    Thread.sleep(100) // TODO: this is ugly
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
            Initializer.config.defaultDistance.toDouble()
        )
    }

    fun afterSeek() {
        if (owner && isSync) sendSync()
    }

    companion object {
        // Creates a custom RenderType for rendering the screen texture
        private fun createRenderType(id: Identifier): RenderType {
            return RenderType.create(
                "dream-displays",
                RenderSetup.builder(RenderPipelines.SOLID_BLOCK)
                    .withTexture("Sampler0", id)
                    .bufferSize(RenderType.BIG_BUFFER_SIZE)
                    .affectsCrumbling()
                    .useLightmap()
                    .createRenderSetup()
            )
        }
    }
}
