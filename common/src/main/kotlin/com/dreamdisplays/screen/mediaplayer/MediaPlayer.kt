package com.dreamdisplays.screen.mediaplayer

import com.dreamdisplays.ModInitializer
import com.dreamdisplays.screen.DisplayScreen
import com.dreamdisplays.util.Utils
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import me.inotsleep.utils.logging.LoggingManager
import me.inotsleep.utils.logging.LoggingManager.info
import net.minecraft.core.BlockPos
import org.freedesktop.gstreamer.Format
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.event.SeekFlags
import org.jspecify.annotations.NullMarked
import org.schabi.newpipe.extractor.NewPipe.init
import org.schabi.newpipe.extractor.NewPipe.setPreferredLocalization
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.Locale
import java.util.Optional
import java.util.Scanner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.ToIntFunction
import kotlin.math.abs

// TODO: only 360p version on GStreamer, need to implement adaptive streaming (DASH/HLS) for better quality options and switch from GStreamer to FFmpeg
// TODO: we also need to add downloader class that downloads FFmpeg libraries before initializing FFmpeg itself
// TODO: implement better error handling and recovery (e.g., network issues, stream errors, etc.)
@NullMarked
class MediaPlayer(
    internal val youtubeUrl: String,
    private val lang: String,
    internal val screen: DisplayScreen,
) {
    internal val gstExecutor: ExecutorService =
        newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-gst") }
    internal val frameExecutor: ExecutorService =
        newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-frame") }
    internal val terminated = AtomicBoolean(false)
    internal var currentVolume = 0.0
    internal var videoPipeline: Pipeline? = null
    internal var audioPipeline: Pipeline? = null
    var availableQualities: MutableList<Int>? = null
    internal var currentVideoUrl: java.lang.String? = null
    internal var currentAudioUrl: java.lang.String? = null
    var isInitialized: Boolean = false
        private set
    internal var lastQuality = 0
    internal var currentFrameBuffer: ByteBuffer? = null
    internal var currentFrameWidth = 0
    internal var currentFrameHeight = 0
    internal var preparedBuffer: ByteBuffer? = null
    internal var preparedW = 0
    internal var preparedH = 0
    internal var userVolume = ModInitializer.config.defaultDisplayVolume
    internal var lastAttenuation = 1.0
    internal var brightness = 1.0
    internal var frameReady = false
    internal var convertBuffer: ByteBuffer? = null
    internal var convertBufferSize = 0
    internal var scaleBuffer: ByteBuffer? = null
    internal var scaleBufferSize = 0
    internal var lastFrameTime: Long = 0
    internal val bufferPreparator = BufferPreparator(this)

    init {
        info("[MediaPlayer] Creating new instance for URL: $youtubeUrl")
        Gst.init("MediaPlayer")
        INIT_EXECUTOR.submit { this.initialize() }
    }

    private fun initialize() {
        info("[MediaPlayer] === START INITIALIZATION ===")
        try {
            info("[MediaPlayer] Initializing NewPipe with custom Downloader")
            init(object : Downloader() {
                @Throws(IOException::class)
                override fun execute(request: Request): Response {
                    val url = request.url()
                    val method = request.httpMethod()
                    val headers = request.headers()
                    val data = request.dataToSend()

                    info("[MediaPlayer Downloader] Request: $method $url")

                    val u = URI.create(url).toURL()
                    val conn = u.openConnection() as HttpURLConnection
                    conn.setRequestMethod(method)
                    conn.setUseCaches(false)
                    conn.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )

                    if (headers != null) {
                        headers.forEach { (k, vList) ->
                            vList?.forEach { v ->
                                conn.addRequestProperty(k, v)
                            }
                        }
                    }

                    if (data != null && data.isNotEmpty()) {
                        conn.setDoOutput(true)
                        conn.getOutputStream().use { os ->
                            os.write(data)
                        }
                    }

                    conn.connect()
                    val code = conn.getResponseCode()
                    val `is` = if (code >= 400) conn.errorStream else conn.getInputStream()

                    var body = ""
                    if (`is` != null) {
                        Scanner(`is`, StandardCharsets.UTF_8).useDelimiter("\\A").use { s ->
                            body = if (s.hasNext()) s.next() else ""
                        }
                    }

                    info("[MediaPlayer Downloader] Response: $code for $url")
                    if (body.length > 500) {
                        info("[MediaPlayer Downloader] Body preview: " + body.substring(0, 500) + "...")
                    } else {
                        info("[MediaPlayer Downloader] Body: $body")
                    }

                    return Response(code, conn.getResponseMessage(), conn.headerFields, body, url)
                }
            })

            setPreferredLocalization(Localization(lang, lang.uppercase(Locale.getDefault())))
            info("[MediaPlayer] Preferred localization set to: $lang")

            val videoId = Utils.extractVideoId(youtubeUrl)
            if (videoId.isNullOrEmpty()) {
                LoggingManager.error("[MediaPlayer] FAILED to extract video ID from: $youtubeUrl")
                return
            }
            info("[MediaPlayer] Extracted video ID: $videoId")

            val cleanUrl = "https://www.youtube.com/watch?v=$videoId"
            info("[MediaPlayer] Fetching StreamInfo from: $cleanUrl")

            val info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl))
            info("[MediaPlayer] StreamInfo fetched. Title: " + info.name + " | Duration: " + info.duration + "s")

            val videoStreams = info.videoStreams
            val audioStreams = info.audioStreams

            info("[MediaPlayer] Video streams count: " + videoStreams.size)
            info("[MediaPlayer] Audio streams count: " + audioStreams.size)

            videoStreams.forEach(Consumer { vs: VideoStream? -> info("[MediaPlayer] Video: " + vs!!.getResolution() + " " + vs.format + " URL: " + vs.url) })
            audioStreams.forEach(Consumer { `as`: AudioStream? ->
                info(
                    "[MediaPlayer] Audio: " + `as`!!.format + " locale: " + (if (`as`.audioLocale != null) `as`.audioLocale!!
                        .language else "null") + " URL: " + `as`.url
                )
            })

            if (videoStreams.isEmpty() || audioStreams.isEmpty()) {
                LoggingManager.error("[MediaPlayer] No streams available!")
                return
            }

            availableQualities = videoStreams
                .asSequence()
                .mapNotNull { MediaPlayerControls.parseQuality(it.resolution) }
                .distinct()
                .filter { it <= if (ModInitializer.isPremium) 2160 else 1080 }
                .sorted()
                .toMutableList()

            info("[MediaPlayer] Available qualities: $availableQualities")

            val targetQuality = screen.quality.replace("p", "").toInt()
            var videoOpt = videoStreams.stream()
                .min(Comparator.comparingInt<VideoStream>(ToIntFunction { vs: VideoStream -> abs(MediaPlayerControls.parseQuality(vs.getResolution()) - targetQuality) }))

            if (videoOpt.isEmpty) {
                videoOpt = Optional.of<VideoStream>(videoStreams[0])
                LoggingManager.warn("[MediaPlayer] No close quality match, using first video stream")
            }

            var audioOpt = audioStreams.stream()
                .filter { `as`: AudioStream ->
                    `as`.audioLocale != null && `as`.audioLocale!!.language.contains(lang)
                }
                .findFirst()

            if (audioOpt.isEmpty) {
                audioOpt = Optional.of<AudioStream>(audioStreams[audioStreams.size - 1])
                LoggingManager.warn("[MediaPlayer] No audio in preferred language, using last one")
            }

            currentVideoUrl = videoOpt.get().url as java.lang.String?
            currentAudioUrl = audioOpt.get().url as java.lang.String?
            lastQuality = MediaPlayerControls.parseQuality(videoOpt.get().getResolution())

            info("[MediaPlayer] Selected video URL: $currentVideoUrl")
            info("[MediaPlayer] Selected audio URL: $currentAudioUrl")

            audioPipeline = AudioPipelineBuilder.build(currentAudioUrl!!.toString(), currentVolume) {
                // EOS callback
                safeExecute {
                    audioPipeline!!.seekSimple(Format.TIME, EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE), 0L)
                    audioPipeline!!.play()
                    if (videoPipeline != null) {
                        videoPipeline!!.seekSimple(
                            Format.TIME,
                            EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                            0L
                        )
                        videoPipeline!!.play()
                    }
                }
            }
            videoPipeline = VideoPipelineBuilder.build(currentVideoUrl!!.toString()) { sink ->
                bufferPreparator.configureVideoSink(this, sink)
            }

            if (videoPipeline == null || audioPipeline == null) {
                LoggingManager.error("[MediaPlayer] One or both pipelines failed to build")
                return
            }

            this.isInitialized = true
            info("[MediaPlayer] === INITIALIZATION SUCCESSFUL ===")
        } catch (e: Exception) {
            LoggingManager.error("[MediaPlayer] === INITIALIZATION FAILED ===", e)
        }
    }

    fun play() {
        MediaPlayerControls.play(this)
    }

    fun pause() {
        MediaPlayerControls.pause(this)
    }

    fun seekTo(nanos: Long, b: Boolean) {
        MediaPlayerControls.seekTo(this, nanos, b)
    }

    fun seekToFast(nanos: Long) {
        MediaPlayerControls.seekToFast(this, nanos)
    }

    fun seekRelative(s: Double) {
        MediaPlayerControls.seekRelative(this, s)
    }

    fun stop() {
        MediaPlayerControls.stop(this)
    }

    fun setVolume(volume: Double) {
        MediaPlayerControls.setVolume(this, volume)
    }

    fun setBrightness(brightness: Double) {
        MediaPlayerControls.setBrightness(this, brightness)
    }

    fun setQuality(quality: String) {
        MediaPlayerControls.setQuality(this, quality)
    }

    fun tick(playerPos: BlockPos, maxRadius: Double) {
        MediaPlayerControls.tick(this, playerPos, maxRadius)
    }

    val currentTime: Long
        get() = if (this.isInitialized) audioPipeline!!.queryPosition(Format.TIME) else 0

    val duration: Long
        get() = if (this.isInitialized) audioPipeline!!.queryDuration(Format.TIME) else 0

    fun textureFilled(): Boolean {
        return preparedBuffer != null && preparedBuffer!!.remaining() > 0
    }

    fun updateFrame(texture: GpuTexture) {
        if (!frameReady) return

        // long startNs = System.nanoTime();

        // Quick validation
        val buf = preparedBuffer ?: return

        val w = preparedW
        val h = preparedH

        if (w != screen.textureWidth || h != screen.textureHeight) return

        buf.rewind()

        // Direct write without intermediate checks
        if (!texture.isClosed) {
            RenderSystem.getDevice()
                .createCommandEncoder()
                .writeToTexture(
                    texture, buf, NativeImage.Format.RGBA,
                    0, 0, 0, 0, w, h
                )
        }

        frameReady = false

        // long elapsedNs = System.nanoTime() - startNs;
        // LoggingManager.info("[MediaPlayer] updateFrame: " + elapsedNs + "ns");
    }

    fun safeExecute(r: Runnable) {
        if (!gstExecutor.isShutdown) {
            try {
                gstExecutor.submit(r)
            } catch (_: RejectedExecutionException) {
            }
        }
    }

    companion object {
        private val INIT_EXECUTOR: ExecutorService =
            newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-init") }
        internal const val MIN_FRAME_INTERVAL_NS = 16666667L
        internal var captureSamples: Boolean = true
    }
}
