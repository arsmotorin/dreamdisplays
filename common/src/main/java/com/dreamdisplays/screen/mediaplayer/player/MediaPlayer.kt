package com.dreamdisplays.screen.mediaplayer.player

import com.dreamdisplays.screen.Screen
import com.dreamdisplays.screen.mediaplayer.audio.AudioPlayer
import com.dreamdisplays.screen.mediaplayer.decoder.AudioDecoder
import com.dreamdisplays.screen.mediaplayer.decoder.VideoDecoder
import com.dreamdisplays.screen.mediaplayer.renderer.TextureUploader
import com.dreamdisplays.screen.mediaplayer.renderer.VideoFrameRenderer
import com.dreamdisplays.screen.mediaplayer.sync.AVSynchronizer
import com.dreamdisplays.screen.mediaplayer.utils.Diagnostics
import com.dreamdisplays.screen.mediaplayer.youtube.YoutubeStreamProvider
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.core.BlockPos
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.sqrt

class MediaPlayer(
    private val config: MediaPlayerConfig,
    private val screen: Screen
) {
    private val playing = AtomicBoolean(false)
    private val paused = AtomicBoolean(true)

    private val avSync = AVSynchronizer()
    private lateinit var videoRenderer: VideoFrameRenderer

    // Декодеры и плееры
    private var videoDecoder: VideoDecoder? = null
    private var audioDecoder: AudioDecoder? = null
    private var audioPlayer: AudioPlayer? = null

    // Текущее состояние
    private var initialized = false
    private var errored = false

    init {
        try {
            val thread = Thread({
                try {
                    initialize()
                } catch (e: Throwable) {
                    LoggingManager.error("MediaPlayer initialization failed", e)
                    errored = true
                    screen.errored = true
                }
            }, "MediaPlayer-Init")
            thread.start()
        } catch (e: Throwable) {
            LoggingManager.error("Failed to start MediaPlayer init thread", e)
            errored = true
            screen.errored = true
        }
    }

    private fun initialize() {
        if (initialized || errored) return
        try {
            val provider = YoutubeStreamProvider(config.youtubeUrl)
            val videoStream = provider.selectBestVideo(config.initialQuality)
                ?: throw IllegalStateException("No suitable video stream found")
            val audioStream = provider.selectAudio(config.language)
                ?: throw IllegalStateException("No audio stream found")

            videoDecoder = VideoDecoder(videoStream.url)
            audioDecoder = AudioDecoder(audioStream.url)

            val sampleRate = audioDecoder!!.grabber.sampleRate.toFloat()
            val channels = 2
            val format = AudioFormat(sampleRate, 16, channels, true, false)
            val line = AudioSystem.getSourceDataLine(format).apply { open(format) }

            audioPlayer = AudioPlayer(audioDecoder!!, line).apply {
                setVolume(config.initialVolume)
            }

            videoRenderer = VideoFrameRenderer(screen.textureWidth, screen.textureHeight)
            initialized = true
        } catch (e: Exception) {
            errored = true
            LoggingManager.error("MediaPlayer failed to initialize", e)
            screen.errored = true
        }
    }

    fun play() {
        if (!initialized || playing.get()) return

        playing.set(true)
        paused.set(false)
        avSync.start()

        LoggingManager.info("Starting audio player...")
        audioPlayer?.start(
            isPlaying = { playing.get() },
            isPaused = { paused.get() }
        )
        LoggingManager.info("Audio player started")

        Thread({
            while (playing.get()) {
                if (paused.get()) {
                    Thread.sleep(10)
                    continue
                }

                val frame = videoDecoder!!.nextVideoFrame() ?: continue

                Diagnostics.videoFrameDecoded(frame.timestamp)

                val sleepMs = avSync.calculateSleepTime(frame.timestamp)
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs.coerceAtMost(50))
                } else if (sleepMs < -100) {
                    Diagnostics.videoFrameDropped()
                    continue
                }

                renderFrame(frame)
                Diagnostics.videoFrameRendered()
            }
        }, "VideoPlayback").start()
    }

    fun pause() {
        if (!playing.get()) return
        paused.set(true)
    }

    fun resume() {
        if (!playing.get()) return
        if (!paused.get()) return
        paused.set(false)
        avSync.resume()
        audioPlayer?.resume()
        LoggingManager.info("Video resumed")
    }

    fun togglePause() = if (paused.get()) resume() else pause()

    fun seek(seconds: Double) {
        if (!initialized) return
        val micros = (seconds * 1_000_000).toLong()
        videoDecoder?.seek(micros)
        audioDecoder?.seek(micros)
        avSync.seek(micros)
    }

    fun seekRelative(seconds: Double) {
        val current = avSync.currentPositionMicros() / 1_000_000.0
        val target = (current + seconds).coerceAtLeast(0.0)
        seek(target)
    }

    fun stop() {
        playing.set(false)
        audioPlayer?.stop()
        videoDecoder?.release()
        audioDecoder?.release()
    }

    fun updateTexture(texture: com.mojang.blaze3d.textures.GpuTexture) {
        if (!playing.get() || paused.get() || texture.isClosed) return

        val bufferInfo = videoRenderer.getCurrentBuffer() ?: return
        val (w, h) = videoRenderer.getCurrentDimensions()

        if (w == screen.textureWidth && h == screen.textureHeight) {
            TextureUploader.upload(texture, bufferInfo, w, h)
        }
    }

    private fun renderFrame(frame: org.bytedeco.javacv.Frame) {
        videoRenderer.render(frame)
    }

    fun tick(playerPos: BlockPos) {
        if (!initialized || !playing.get()) return

        val screenPos = screen.pos
        val dx = playerPos.x - screenPos.x
        val dy = playerPos.y - screenPos.y
        val dz = playerPos.z - screenPos.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz * 1.0)

        val maxDist = config.maxSoundDistance.toDouble()
        val attenuation = if (distance > maxDist * 0.5) {
            ((maxDist - distance) / (maxDist * 0.5)).coerceIn(0.0, 1.0)
        } else 1.0

        audioPlayer?.setVolume(config.initialVolume * attenuation)

        Diagnostics.tick(avSync.currentPositionMicros())
    }

    // Getters
    val isPlaying get() = playing.get() && !paused.get()
    val isPaused get() = paused.get()
    val isInitialized get() = initialized
    val hasError get() = errored
    val currentTimeSeconds get() = avSync.currentPositionMicros() / 1_000_000.0
}
