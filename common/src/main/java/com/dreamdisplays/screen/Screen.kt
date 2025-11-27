package com.dreamdisplays.screen

import com.dreamdisplays.Initializer
import com.dreamdisplays.Initializer.sendPacket
import com.dreamdisplays.net.Info
import com.dreamdisplays.net.RequestSync
import com.dreamdisplays.net.Sync
import com.dreamdisplays.screen.Settings.getSettings
import com.dreamdisplays.screen.Settings.updateSettings
import com.dreamdisplays.screen.mediaplayer.player.MediaPlayer
import com.dreamdisplays.screen.mediaplayer.player.MediaPlayerConfig
import com.dreamdisplays.screen.mediaplayer.player.VideoQuality
import com.dreamdisplays.screen.mediaplayer.player.VideoQuality.Companion.fromString
import com.dreamdisplays.util.Image.fetchImageTextureFromUrl
import com.dreamdisplays.util.Utils.extractVideoId
import me.inotsleep.utils.logging.LoggingManager
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
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Represents a video display screen in the Minecraft world.
 */

@NullMarked
class Screen(
    val iD: UUID, ownerId: UUID, var x: Int, var y: Int, var z: Int, // Returns screen facing direction
    @JvmField var facing: String, width: Int, height: Int, isSync: Boolean
) {
    @JvmField
    var owner: Boolean
    @JvmField
    var errored: Boolean = false
    @JvmField
    var isSync: Boolean = false
    var muted: Boolean
    @JvmField
    var texture: DynamicTexture? = null
    var textureId: Identifier? = null
    @JvmField
    var renderType: RenderType? = null
    var textureWidth: Int = 0
    var textureHeight: Int = 0
    var previewTextureId: Identifier? = null
    @JvmField
    var previewRenderType: RenderType? = null
    private var width: Int
    private var height: Int
    private var volume: Float
    private var videoStarted = false
    private var paused = false
    private var quality = "720"
    private var lastLoadedQuality = "720"
    private var mediaPlayer: MediaPlayer? = null
    private var videoUrl: String? = null

    @Transient
    private var blockPos: BlockPos? = null
    var previewTexture: DynamicTexture? = null
        private set
    private var lang: String? = null

    // Returns list of available video qualities
    val qualityList: MutableList<Int> = mutableListOf(144, 240, 360, 480, 720, 1080, 1440, 2160)

    // Constructor for the Screen class
    init {
        this.facing = facing
        this.width = width
        this.height = height
        owner =
            Minecraft.getInstance().player != null && (ownerId.toString() + "") == Minecraft.getInstance().player!!.getUUID()
                .toString() + ""

        // Load saved settings for this display
        val savedSettings = getSettings(
            iD
        )
        this.volume = savedSettings.volume
        this.quality = savedSettings.quality
        this.muted = savedSettings.muted

        if (isSync) {
            sendRequestSyncPacket()
        }
    }

    // Loads a video from a given URL and language
    fun loadVideo(videoUrl: String, lang: String) {
        if (videoUrl == "") return

        if (mediaPlayer != null) {
            unregister()
        }

        this.videoUrl = videoUrl
        this.lang = lang
        this.lastLoadedQuality = this.quality
        CompletableFuture.runAsync {
            try {
                this.videoUrl = videoUrl
                val qualityInt = this.quality.replace("p", "").toInt()
                textureWidth = (width / height.toDouble() * qualityInt).toInt()
                textureHeight = qualityInt

                var videoQuality = fromString(this.quality)
                if (videoQuality == null) {
                    videoQuality = VideoQuality.P720
                }
                val config = MediaPlayerConfig(
                    videoUrl,
                    lang,
                    volume.toDouble(),
                    videoQuality,
                    32
                )
                mediaPlayer = MediaPlayer(config, this)
            } catch (e: Throwable) {
                LoggingManager.error("Screen: Failed to load video", e)
            }
            fetchImageTextureFromUrl("https://img.youtube.com/vi/" + extractVideoId(videoUrl) + "/maxresdefault.jpg")
                .thenAcceptAsync(Consumer { nativeImageBackedTexture: DynamicTexture? ->
                    previewTexture = nativeImageBackedTexture
                    previewTextureId = Identifier.fromNamespaceAndPath(
                        Initializer.MOD_ID,
                        "screen-preview-" + this.iD + "-" + UUID.randomUUID()
                    )
                    if (previewTexture != null) {
                        Minecraft.getInstance().textureManager.register(previewTextureId!!, previewTexture!!)
                        previewRenderType = createRenderType(previewTextureId!!)
                    }
                })
        }

        waitForMFInit { this.startVideo() }

        Minecraft.getInstance().execute { this.reloadTexture() }
    }

    // Updates the screen data based on a DisplayInfoPacket
    fun updateData(packet: Info) {
        this.x = packet.pos.x
        this.y = packet.pos.y
        this.z = packet.pos.z

        this.facing = packet.facing.toString()

        this.width = packet.width
        this.height = packet.height
        this.isSync = packet.isSync

        owner =
            Minecraft.getInstance().player != null && (packet.ownerId.toString() + "") == Minecraft.getInstance().player!!.getUUID()
                .toString() + ""

        if (videoUrl != packet.url || lang != packet.lang) {
            loadVideo(packet.url, packet.lang)
            if (isSync) {
                sendRequestSyncPacket()
            }
        }
    }

    // Sends a RequestSyncPacket to the server to request synchronization data
    private fun sendRequestSyncPacket() {
        sendPacket(RequestSync(this.iD))
    }

    // Updates the screen data based on a SyncPacket
    fun updateData(packet: Sync) {
        isSync = packet.isSync
        if (!isSync) return

        val nanos = System.nanoTime()

        waitForMFInit {
            if (!videoStarted) {
                startVideo()
                setVolume(Initializer.config.syncDisplayVolume.toFloat())
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

    // Reloads the video quality (requires re-initialization)
    fun reloadQuality() {
        // Quality changes require re-creating the MediaPlayer with new config
        // Only reload if quality has actually changed
        if (mediaPlayer != null && videoUrl != null && (quality != lastLoadedQuality)) {
            val currentVideoUrl = videoUrl
            val currentLang = lang
            val wasPlaying = videoStarted && !paused
            lastLoadedQuality = quality
            // Execute on render thread to avoid IllegalStateException
            Minecraft.getInstance().execute {
                unregister()
                reloadTexture() // Recreate texture with new dimensions
                loadVideo(currentVideoUrl!!, currentLang!!)
                // Restore playback state
                if (wasPlaying) {
                    waitForMFInit { this.startVideo() }
                }
            }
        }
    }

    // Checks if a given BlockPos is within the screen boundaries
    fun isInScreen(pos: BlockPos): Boolean {
        var maxX = x
        val maxY: Int = y + height - 1
        var maxZ = z

        when (facing) {
            "NORTH", "SOUTH" -> maxX += width - 1
            else -> maxZ += width - 1
        }

        return pos.x in x..maxX && y <= pos.y && maxY >= pos.y && z <= pos.z && maxZ >= pos.z
    }

    // Checks if the video has started playing
    fun isVideoStarted(): Boolean {
        return mediaPlayer != null && mediaPlayer!!.isInitialized && mediaPlayer!!.isPlaying
    }

    // Calculates the distance from a given BlockPos to the closest point on the screen
    fun getDistanceToScreen(pos: BlockPos): Double {
        var maxX = x
        val maxY: Int = y + height - 1
        var maxZ = z

        when (facing) {
            "NORTH", "SOUTH" -> maxX += width - 1
            "EAST", "WEST" -> maxZ += width - 1
        }

        val clampedX = min(max(pos.x, x), maxX)
        val clampedY = min(max(pos.y, y), maxY)
        val clampedZ = min(max(pos.z, z), maxZ)

        val closestPoint = BlockPos(clampedX, clampedY, clampedZ)

        return sqrt(pos.distSqr(closestPoint))
    }

    // Updates the texture to fit the current video frame
    fun fitTexture() {
        if (mediaPlayer != null && texture != null) {
            mediaPlayer!!.updateTexture(texture!!.getTexture())
        }
    }

    val pos: BlockPos
        // Returns screen position as BlockPos
        get() {
            if (blockPos == null) {
                blockPos = BlockPos(x, y, z)
            }
            return blockPos!!
        }

    // Returns screen width
    fun getWidth(): Float {
        return width.toFloat()
    }

    // Returns screen height
    fun getHeight(): Float {
        return height.toFloat()
    }

    // Sets video volume (requires re-initialization to apply)
    fun setVideoVolume(volume: Float) {
        this.volume = volume
        // Volume changes are applied via spatial attenuation in tick()
        // Initial volume would require re-creating the MediaPlayer
    }

    // Returns video quality
    fun getQuality(): String {
        return quality
    }

    // Sets video quality (e.g., "480", "720", "1080", "2160")
    fun setQuality(quality: String) {
        this.quality = quality
        // Save settings
        updateSettings(this.iD, volume, quality, muted)
    }

    // Starts video playback
    fun startVideo() {
        if (mediaPlayer != null) {
            mediaPlayer!!.play()
            videoStarted = true
            paused = false
        }
    }

    // Returns the paused state of the video
    fun getPaused(): Boolean {
        return paused
    }

    // Sets the paused state of the video
    fun setPaused(paused: Boolean) {
        if (!videoStarted) {
            this.paused = false
            waitForMFInit {
                startVideo()
                setVolume(Initializer.config.defaultDisplayVolume.toFloat())
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
        if (mediaPlayer != null) {
            mediaPlayer!!.seekRelative(seconds.toDouble())
        }
    }

    // Absolute (cinema) seek video: moves to a specific second
    fun seekVideoTo(nanos: Long) {
        if (mediaPlayer != null) {
            val seconds = nanos / 1000000000.0
            mediaPlayer!!.seek(seconds)
        }
    }

    fun unregister() {
        if (mediaPlayer != null) mediaPlayer!!.stop()

        // Release textures on render thread to avoid IllegalStateException
        Minecraft.getInstance().execute {
            val manager = Minecraft.getInstance().textureManager
            if (textureId != null) manager.release(textureId!!)
            if (previewTextureId != null) manager.release(previewTextureId!!)
        }

        if (Minecraft.getInstance().screen is Menu) {
            val menuScreen = Minecraft.getInstance().screen as Menu
            if (menuScreen.screen === this) menuScreen.onClose()
        }
    }

    fun hasPreviewTexture(): Boolean {
        return false
    }

    fun mute(status: Boolean) {
        if (muted == status) return
        muted = status

        setVideoVolume(if (!status) volume else 0f)
        // Save settings
        updateSettings(this.iD, volume, quality, muted)
    }

    fun getVolume(): Double {
        return volume.toDouble()
    }

    // Sets video volume (0.0 to 1.0)
    fun setVolume(volume: Float) {
        this.volume = volume
        setVideoVolume(volume)
        // Save settings
        updateSettings(this.iD, volume, quality, muted)
    }

    // Creates a new texture for the screen based on its dimensions and quality
    fun createTexture() {
        val qualityInt = this.quality.replace("p", "").toInt()
        textureWidth = (width / height.toDouble() * qualityInt).toInt()
        textureHeight = qualityInt

        if (texture != null) {
            texture!!.close()
            if (textureId != null) Minecraft.getInstance()
                .textureManager
                .release(textureId!!)
        }
        texture = DynamicTexture(UUID.randomUUID().toString(), textureWidth, textureHeight, true)
        textureId = Identifier.fromNamespaceAndPath(
            Initializer.MOD_ID,
            "screen-main-texture-" + this.iD + "-" + UUID.randomUUID()
        )

        Minecraft.getInstance().textureManager.register(textureId!!, texture!!)
        renderType = createRenderType(textureId!!)
    }

    fun sendSync() {
        if (mediaPlayer != null) {
            val currentTimeNanos = (mediaPlayer!!.currentTimeSeconds * 1000000000).toLong()
            val durationNanos = 0L // Duration not available in current implementation
            sendPacket(Sync(this.iD, isSync, paused, currentTimeNanos, durationNanos))
        }
    }

    // TODO: rewrite this as soon as possible
    fun waitForMFInit(action: Runnable) {
        Thread {
            while (mediaPlayer == null || !mediaPlayer!!.isInitialized) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }
            action.run()
        }.start()
    }

    fun tick(pos: BlockPos) {
        if (mediaPlayer != null) mediaPlayer!!.tick(pos)
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
