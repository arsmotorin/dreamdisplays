package com.dreamdisplays.displays.mediaplayer

import com.dreamdisplays.ModInitializer
import com.dreamdisplays.displays.mediaplayer.controls.MediaPlayerControls.lastQuality
import com.dreamdisplays.displays.mediaplayer.managers.FrameManager
import com.dreamdisplays.displays.mediaplayer.pipelines.AudioPipeline
import com.dreamdisplays.displays.mediaplayer.pipelines.VideoPipeline
import com.dreamdisplays.displays.DisplayScreen
import com.dreamdisplays.displays.mediaplayer.buffer.BufferPreparator
import com.dreamdisplays.displays.mediaplayer.controls.MediaPlayerControls
import com.dreamdisplays.utils.Utils
import com.mojang.blaze3d.textures.GpuTexture
import me.inotsleep.utils.logging.LoggingManager.error
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
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.Locale
import java.util.Optional
import java.util.Scanner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.function.ToIntFunction
import kotlin.math.abs

// TODO: only 360p version on GStreamer, need to implement adaptive streaming (DASH/HLS) for better quality options and switch from GStreamer to FFmpeg
// TODO: we also need to add downloader class that downloads FFmpeg libraries before initializing FFmpeg itself
// TODO: implement better error handling and recovery (e.g., network issues, stream errors, etc.)
@NullMarked
class MediaPlayer(
    internal val youtubeUrl: String,
    private val lang: String,
    internal val display: DisplayScreen,
) {
    private val initExecutor: ExecutorService = newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-init") }
    internal val gstExecutor: ExecutorService = newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-gst") }
    internal val frameExecutor: ExecutorService = newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-frame") }
    internal var currentVolume = 0.0
    internal var videoPipeline: Pipeline? = null
    internal var audioPipeline: Pipeline? = null
    var availableQualities: MutableList<Int>? = null
    internal var currentVideoUrl: String? = null
    internal var currentAudioUrl: String? = null
    var isInitialized: Boolean = false
    internal val bufferPreparator = BufferPreparator(this)

    init {
        Gst.init("MediaPlayer")
        initExecutor.submit {
            this.initialize()
        }
    }

    private fun initialize() {
        try {
            init(object : Downloader() {
                @Throws(IOException::class)
                override fun execute(request: Request): Response {
                    val url = request.url()
                    val method = request.httpMethod()
                    val headers = request.headers()
                    val data = request.dataToSend()
                    val u = URI.create(url).toURL()
                    val conn = u.openConnection() as HttpURLConnection
                    conn.setRequestMethod(method)
                    conn.setUseCaches(false)
                    conn.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )

                    headers.forEach { (k, vList) ->
                        vList?.forEach { v ->
                            conn.addRequestProperty(k, v)
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

                    return Response(code, conn.getResponseMessage(), conn.headerFields, body, url)
                }
            })

            setPreferredLocalization(Localization(lang, lang.uppercase(Locale.getDefault())))

            val videoId = Utils.extractVideoId(youtubeUrl)
            val cleanUrl = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl))

            val videoStreams = info.videoStreams
            val audioStreams = info.audioStreams

            availableQualities = videoStreams
                .asSequence()
                .mapNotNull { MediaPlayerControls.parseQuality(it.resolution) }
                .distinct()
                .filter { it <= if (ModInitializer.isPremium) 2160 else 1080 }
                .sorted()
                .toMutableList()

            val targetQuality = display.quality.replace("p", "").toInt()
            var videoOpt = videoStreams.stream()
                .min(Comparator.comparingInt<VideoStream>(ToIntFunction { vs: VideoStream -> abs(MediaPlayerControls.parseQuality(vs.getResolution()) - targetQuality) }))

            if (videoOpt.isEmpty) {
                videoOpt = Optional.of<VideoStream>(videoStreams[0])
            }

            var audioOpt = audioStreams.stream()
                .filter { `as`: AudioStream ->
                    `as`.audioLocale != null && `as`.audioLocale!!.language.contains(lang)
                }
                .findFirst()

            if (audioOpt.isEmpty) {
                audioOpt = Optional.of<AudioStream>(audioStreams[audioStreams.size - 1])
            }

            currentVideoUrl = videoOpt.get().url
            currentAudioUrl = audioOpt.get().url
            lastQuality = MediaPlayerControls.parseQuality(videoOpt.get().getResolution())

            val onEos = {
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

            audioPipeline = AudioPipeline.build(currentAudioUrl!!, currentVolume, onEos)
            videoPipeline = VideoPipeline.build(currentVideoUrl!!) { sink ->
                bufferPreparator.configureVideoSink(sink)
            }

            onEos()

            this.isInitialized = true
        } catch (e: Exception) {
            error("[MediaPlayer] Initialization failed", e)
        }
    }

    // Audio position in nanoseconds
    val currentTime: Long
        get() = if (this.isInitialized) audioPipeline!!.queryPosition(Format.TIME) else 0

    // Audio duration in nanoseconds
    val duration: Long
        get() = if (this.isInitialized) audioPipeline!!.queryDuration(Format.TIME) else 0

    fun textureFilled() = FrameManager.textureFilled(bufferPreparator)

    fun updateFrame(texture: GpuTexture) = FrameManager.updateFrame(bufferPreparator, texture)

    // Save executor-safe execution for EOS callbacks
    fun safeExecute(r: Runnable) {
        MediaPlayerControls.safeExecute(this, r)
    }
}
