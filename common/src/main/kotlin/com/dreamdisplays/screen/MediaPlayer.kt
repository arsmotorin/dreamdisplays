package com.dreamdisplays.screen

import com.dreamdisplays.ModInitializer
import com.dreamdisplays.util.Utils.extractVideoId
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import org.freedesktop.gstreamer.*
import org.freedesktop.gstreamer.Bus.ERROR
import org.freedesktop.gstreamer.Bus.STATE_CHANGED
import org.freedesktop.gstreamer.elements.AppSink
import org.freedesktop.gstreamer.elements.AppSink.NEW_SAMPLE
import org.freedesktop.gstreamer.event.SeekFlags
import org.jspecify.annotations.NullMarked
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.lang.String
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.ToIntFunction
import kotlin.Boolean
import kotlin.Comparator
import kotlin.Double
import kotlin.Exception
import kotlin.Int
import kotlin.Long
import kotlin.NumberFormatException
import kotlin.Throws
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.text.contains
import kotlin.text.replace
import kotlin.text.substring
import kotlin.text.toInt
import kotlin.text.toRegex

@NullMarked
class MediaPlayer(private val youtubeUrl: kotlin.String, private val lang: kotlin.String, private val screen: DisplayScreen) {
    private val gstExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-gst") }
    private val frameExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r: Runnable? -> Thread(r, "MediaPlayer-frame") }
    private val terminated = AtomicBoolean(false)

    @Volatile
    private var currentVolume = 0.0

    @Volatile
    private var videoPipeline: Pipeline? = null

    @Volatile
    private var audioPipeline: Pipeline? = null

    @Volatile
    var availableQualities: MutableList<Int>? = null

    @Volatile
    private var currentVideoUrl: String? = null

    @Volatile
    private var currentAudioUrl: String? = null

    @Volatile
    var isInitialized: Boolean = false
        private set
    private var lastQuality = 0

    @Volatile
    private var currentFrameBuffer: ByteBuffer? = null

    @Volatile
    private var currentFrameWidth = 0

    @Volatile
    private var currentFrameHeight = 0

    @Volatile
    private var preparedBuffer: ByteBuffer? = null

    @Volatile
    private var preparedW = 0

    @Volatile
    private var preparedH = 0

    @Volatile
    private var userVolume = ModInitializer.config.defaultDisplayVolume

    @Volatile
    private var lastAttenuation = 1.0

    @Volatile
    private var brightness = 1.0

    @Volatile
    private var frameReady = false
    private var syncCheckCounter = 0
    private var convertBuffer: ByteBuffer? = null
    private var convertBufferSize = 0
    private var scaleBuffer: ByteBuffer? = null
    private var scaleBufferSize = 0

    @Volatile
    private var lastFrameTime: Long = 0

    init {
        LoggingManager.info("[MediaPlayer] Creating new instance for URL: " + youtubeUrl)
        Gst.init("MediaPlayer")
        INIT_EXECUTOR.submit(Runnable { this.initialize() })
    }

    private fun initialize() {
        LoggingManager.info("[MediaPlayer] === START INITIALIZATION ===")
        try {
            LoggingManager.info("[MediaPlayer] Initializing NewPipe with custom Downloader")
            NewPipe.init(object : Downloader() {
                @Throws(IOException::class)
                override fun execute(request: Request): Response {
                    val url = request.url()
                    val method = request.httpMethod()
                    val headers = request.headers()
                    val data = request.dataToSend()

                    LoggingManager.info("[MediaPlayer Downloader] Request: " + method + " " + url)

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
                    val `is` = if (code >= 400) conn.getErrorStream() else conn.getInputStream()

                    var body = ""
                    if (`is` != null) {
                        Scanner(`is`, StandardCharsets.UTF_8).useDelimiter("\\A").use { s ->
                            body = if (s.hasNext()) s.next() else ""
                        }
                    }

                    LoggingManager.info("[MediaPlayer Downloader] Response: " + code + " for " + url)
                    if (body.length > 500) {
                        LoggingManager.info("[MediaPlayer Downloader] Body preview: " + body.substring(0, 500) + "...")
                    } else {
                        LoggingManager.info("[MediaPlayer Downloader] Body: " + body)
                    }

                    return Response(code, conn.getResponseMessage(), conn.getHeaderFields(), body, url)
                }
            })

            NewPipe.setPreferredLocalization(Localization(lang.toString(), lang.uppercase(Locale.getDefault())))
            LoggingManager.info("[MediaPlayer] Preferred localization set to: " + lang)

            val videoId = extractVideoId(youtubeUrl.toString())
            if (videoId.isNullOrEmpty()) {
                LoggingManager.error("[MediaPlayer] FAILED to extract video ID from: " + youtubeUrl)
                return
            }
            LoggingManager.info("[MediaPlayer] Extracted video ID: " + videoId)

            val cleanUrl = "https://www.youtube.com/watch?v=" + videoId
            LoggingManager.info("[MediaPlayer] Fetching StreamInfo from: " + cleanUrl)

            val info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl))
            LoggingManager.info("[MediaPlayer] StreamInfo fetched. Title: " + info.getName() + " | Duration: " + info.getDuration() + "s")

            val videoStreams = info.getVideoStreams()
            val audioStreams = info.getAudioStreams()

            LoggingManager.info("[MediaPlayer] Video streams count: " + videoStreams.size)
            LoggingManager.info("[MediaPlayer] Audio streams count: " + audioStreams.size)

            videoStreams.forEach(Consumer { vs: VideoStream? -> LoggingManager.info("[MediaPlayer] Video: " + vs!!.getResolution() + " " + vs.getFormat() + " URL: " + vs.getUrl()) })
            audioStreams.forEach(Consumer { `as`: AudioStream? ->
                LoggingManager.info(
                    "[MediaPlayer] Audio: " + `as`!!.getFormat() + " locale: " + (if (`as`.getAudioLocale() != null) `as`.getAudioLocale()!!
                        .getLanguage() else "null") + " URL: " + `as`.getUrl()
                )
            })

            if (videoStreams.isEmpty() || audioStreams.isEmpty()) {
                LoggingManager.error("[MediaPlayer] No streams available!")
                return
            }

            availableQualities = videoStreams
                .mapNotNull { parseQuality(it.resolution) }
                .distinct()
                .filter { it <= if (ModInitializer.isPremium) 2160 else 1080 }
                .sorted()
                .toMutableList()

            LoggingManager.info("[MediaPlayer] Available qualities: " + availableQualities)

            val targetQuality = screen.quality.replace("p", "").toInt()
            var videoOpt = videoStreams.stream()
                .min(Comparator.comparingInt<VideoStream>(ToIntFunction { vs: VideoStream -> abs(parseQuality(vs.getResolution()) - targetQuality) }))

            if (videoOpt.isEmpty()) {
                videoOpt = Optional.of<VideoStream>(videoStreams.get(0))
                LoggingManager.warn("[MediaPlayer] No close quality match, using first video stream")
            }

            var audioOpt = audioStreams.stream()
                .filter { `as`: AudioStream ->
                    `as`.getAudioLocale() != null && `as`.getAudioLocale()!!.getLanguage().contains(lang)
                }
                .findFirst()

            if (audioOpt.isEmpty()) {
                audioOpt = Optional.of<AudioStream>(audioStreams.get(audioStreams.size - 1))
                LoggingManager.warn("[MediaPlayer] No audio in preferred language, using last one")
            }

            currentVideoUrl = videoOpt.get().getUrl() as String?
            currentAudioUrl = audioOpt.get().getUrl() as String?
            lastQuality = parseQuality(videoOpt.get().getResolution())

            LoggingManager.info("[MediaPlayer] Selected video URL: " + currentVideoUrl)
            LoggingManager.info("[MediaPlayer] Selected audio URL: " + currentAudioUrl)

            audioPipeline = buildAudioPipeline(currentAudioUrl!!)
            videoPipeline = buildVideoPipeline(currentVideoUrl!!.toString())

            if (videoPipeline == null || audioPipeline == null) {
                LoggingManager.error("[MediaPlayer] One or both pipelines failed to build")
                return
            }

            this.isInitialized = true
            LoggingManager.info("[MediaPlayer] === INITIALIZATION SUCCESSFUL ===")
        } catch (e: Exception) {
            LoggingManager.error("[MediaPlayer] === INITIALIZATION FAILED ===", e)
        }
    }

    private fun buildVideoPipeline(uri: kotlin.String): Pipeline? {
        LoggingManager.info("[MediaPlayer] Building VIDEO pipeline for: " + uri)
        val desc = String.join(
            " ",
            "souphttpsrc location=\"" + uri + "\"",
            "! typefind name=typefinder",
            "! decodebin ! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink sync=false"
        )
        LoggingManager.info("[MediaPlayer] Universal video pipeline desc: " + desc)

        val p = Gst.parseLaunch(desc) as Pipeline?
        if (p == null) {
            LoggingManager.error("[MediaPlayer] Gst.parseLaunch returned null for universal video pipeline")
            return null
        }

        configureVideoSink((p.getElementByName("videosink") as AppSink?)!!)
        p.pause()

        val bus = p.getBus()
        bus.connect(ERROR { src: GstObject?, code: Int, msg: kotlin.String? -> LoggingManager.error("[MediaPlayer VIDEO ERROR] " + src!!.getName() + ": " + msg) })
        bus.connect(Bus.EOS { src: GstObject? -> LoggingManager.info("[MediaPlayer VIDEO] EOS") })
        bus.connect(STATE_CHANGED { src: GstObject?, old: State?, cur: State?, pend: State? -> LoggingManager.info("[MediaPlayer VIDEO] State: " + old + " -> " + cur) })

        LoggingManager.info("[MediaPlayer] Universal video pipeline built and paused")
        return p
    }

    private fun buildAudioPipeline(uri: String): Pipeline? {
        LoggingManager.info("[MediaPlayer] Building AUDIO pipeline for: " + uri)
        val desc = "souphttpsrc location=\"" + uri + "\" ! decodebin ! audioconvert ! audioresample " +
                "! volume name=volumeElement volume=1 ! audioamplify name=ampElement amplification=" + currentVolume +
                " ! autoaudiosink"
        LoggingManager.info("[MediaPlayer] Audio pipeline desc: " + desc)

        val p = Gst.parseLaunch(desc) as Pipeline?
        if (p == null) {
            LoggingManager.error("[MediaPlayer] Gst.parseLaunch returned null for audio pipeline")
            return null
        }

        val bus = p.getBus()
        bus.connect(ERROR { src: GstObject?, code: Int, msg: kotlin.String? -> LoggingManager.error("[MediaPlayer AUDIO ERROR] " + src!!.getName() + ": " + msg) })
        bus.connect(Bus.EOS { src: GstObject? ->
            LoggingManager.info("[MediaPlayer AUDIO] EOS - looping")
            safeExecute(Runnable {
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
            })
        })
        bus.connect(STATE_CHANGED { src: GstObject?, old: State?, cur: State?, pend: State? -> LoggingManager.info("[MediaPlayer AUDIO] State: $old -> $cur") })

        LoggingManager.info("[MediaPlayer] Audio pipeline built")
        return p
    }

    private fun configureVideoSink(sink: AppSink) {
        LoggingManager.info("[MediaPlayer] Configuring AppSink")
        sink.set("emit-signals", true)
        sink.set("sync", true)
        sink.set("max-buffers", 1)
        sink.set("drop", true)
        sink.connect(NEW_SAMPLE { elem: AppSink? ->
            val s = elem!!.pullSample()
            if (s == null || !captureSamples) {
                LoggingManager.warn("[MediaPlayer] pullSample returned null or capture disabled")
                return@NEW_SAMPLE FlowReturn.OK
            }
            try {
                val st = s.getCaps().getStructure(0)
                val w = st.getInteger("width")
                val h = st.getInteger("height")
                currentFrameWidth = w
                currentFrameHeight = h
                currentFrameBuffer = sampleToBuffer(s)
                prepareBufferAsync()
            } catch (e: Exception) {
                LoggingManager.error("[MediaPlayer] Error in NEW_SAMPLE handler", e)
            } finally {
                s.dispose()
            }
            FlowReturn.OK
        })
    }

    private fun prepareBufferAsync() {
        if (currentFrameBuffer == null) return

        val now = System.nanoTime()
        if (now - lastFrameTime < MIN_FRAME_INTERVAL_NS) {
            LoggingManager.info("[MediaPlayer] Frame skipped (rate limit)")
            return
        }
        lastFrameTime = now

        try {
            frameExecutor.submit(Runnable { this.prepareBuffer() })
        } catch (ignored: RejectedExecutionException) {
            LoggingManager.warn("[MediaPlayer] Frame task rejected")
        }
    }

    //    private void prepareBuffer() {
    //        int targetW = screen.textureWidth;
    //        int targetH = screen.textureHeight;
    //        if (targetW == 0 || targetH == 0 || currentFrameBuffer == null) return;
    //
    //        ByteBuffer converted = convertToRGBA(currentFrameBuffer, currentFrameWidth, currentFrameHeight);
    //
    //        if (currentFrameWidth == targetW && currentFrameHeight == targetH) {
    //            applyBrightnessToBuffer(converted, brightness);
    //            preparedBuffer = converted;
    //            preparedW = targetW;
    //            preparedH = targetH;
    //            frameReady = true;
    //            Minecraft.getInstance().execute(screen::fitTexture);
    //            LoggingManager.info("[MediaPlayer] Frame ready (no scaling needed)");
    //            return;
    //        }
    //
    //        int scaleSize = targetW * targetH * 4;
    //        if (scaleBuffer == null || scaleBufferSize < scaleSize) {
    //            scaleBuffer = ByteBuffer.allocateDirect(scaleSize).order(ByteOrder.nativeOrder());
    //            scaleBufferSize = scaleSize;
    //        }
    //        scaleBuffer.clear();
    //
    //        scaleRGBA(converted, currentFrameWidth, currentFrameHeight, scaleBuffer, targetW, targetH);
    //
    //        applyBrightnessToBuffer(scaleBuffer, brightness);
    //        preparedBuffer = scaleBuffer;
    //        preparedW = targetW;
    //        preparedH = targetH;
    //        frameReady = true;
    //        Minecraft.getInstance().execute(screen::fitTexture);
    //    }
    private fun prepareBuffer() {
        // long startNs = System.nanoTime();

        val targetW = screen.textureWidth
        val targetH = screen.textureHeight
        if (targetW == 0 || targetH == 0 || currentFrameBuffer == null) return

        val needsScaling = currentFrameWidth != targetW || currentFrameHeight != targetH
        val bufferSize = targetW * targetH * 4

        if (needsScaling) {
            if (scaleBuffer == null || scaleBufferSize < bufferSize) {
                scaleBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                scaleBufferSize = bufferSize
            }

            Companion.scaleRGBA(
                currentFrameBuffer!!, currentFrameWidth, currentFrameHeight,
                scaleBuffer!!, targetW, targetH, brightness
            )

            preparedBuffer = scaleBuffer
        } else {
            if (convertBuffer == null || convertBufferSize < bufferSize) {
                convertBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                convertBufferSize = bufferSize
            }

            currentFrameBuffer!!.rewind()
            convertBuffer!!.clear()

            if (abs(brightness - 1.0) < 1e-5) {
                Companion.copy(currentFrameBuffer!!, convertBuffer!!, bufferSize)
            } else {
                Companion.applyBrightnessToBuffer(currentFrameBuffer!!, convertBuffer!!, bufferSize, brightness)
            }

            preparedBuffer = convertBuffer
        }

        preparedW = targetW
        preparedH = targetH
        frameReady = true

        // long elapsedNs = System.nanoTime() - startNs;
        // LoggingManager.info("[MediaPlayer] prepareBuffer: " + elapsedNs + "ns");
        Minecraft.getInstance().execute(Runnable { screen.fitTexture() })
    }

    private fun parseQuality(resolution: kotlin.String): Int {
        try {
            return resolution.replace("\\D+".toRegex(), "").toInt()
        } catch (e: Exception) {
            return Int.Companion.MAX_VALUE
        }
    }

    fun play() {
        safeExecute(Runnable { this.doPlay() })
    }

    fun pause() {
        safeExecute(Runnable { this.doPause() })
    }

    fun seekTo(nanos: Long, b: Boolean) {
        safeExecute(Runnable { doSeek(nanos, b) })
    }

    fun seekToFast(nanos: Long) {
        safeExecute(Runnable { doSeekFast(nanos) })
    }

    fun seekRelative(s: Double) {
        safeExecute(Runnable safeExecute@{
            if (!this.isInitialized) return@safeExecute
            val cur = audioPipeline!!.queryPosition(Format.TIME)
            val tgt = max(0, cur + (s * 1e9).toLong())
            val dur = max(0, audioPipeline!!.queryDuration(Format.TIME) - 1)
            doSeek(min(tgt, dur), true)
        })
    }

    val currentTime: Long
        get() = if (this.isInitialized) audioPipeline!!.queryPosition(Format.TIME) else 0

    val duration: Long
        get() = if (this.isInitialized) audioPipeline!!.queryDuration(Format.TIME) else 0

    fun stop() {
        if (terminated.getAndSet(true)) return
        safeExecute(Runnable {
            doStop()
            gstExecutor.shutdown()
            frameExecutor.shutdown()
        })
    }

    fun setVolume(volume: Double) {
        userVolume = max(0.0, min(2.0, volume))
        currentVolume = userVolume * lastAttenuation
        safeExecute(Runnable { this.applyVolume() })
    }

    fun setBrightness(brightness: Double) {
        this.brightness = max(0.0, min(2.0, brightness))
    }

    fun textureFilled(): Boolean {
        return preparedBuffer != null && preparedBuffer!!.remaining() > 0
    }

    fun updateFrame(texture: GpuTexture) {
        if (!frameReady) return

        // long startNs = System.nanoTime();

        // Quick validation
        val buf = preparedBuffer
        if (buf == null) return

        val w = preparedW
        val h = preparedH

        if (w != screen.textureWidth || h != screen.textureHeight) return

        buf.rewind()

        // Direct write without intermediate checks
        if (!texture.isClosed()) {
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

    fun setQuality(quality: kotlin.String) {
        safeExecute(Runnable { changeQuality(quality) })
    }

    private fun doPlay() {
        val audioPos = audioPipeline!!.queryPosition(Format.TIME)

        audioPipeline!!.pause()
        if (videoPipeline != null) videoPipeline!!.pause()

        audioPipeline!!.getState()
        if (videoPipeline != null) videoPipeline!!.getState()

        if (videoPipeline != null && audioPos > 0) {
            videoPipeline!!.seekSimple(
                Format.TIME,
                EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                audioPos
            )
            videoPipeline!!.getState()
        }

        val audioClock = audioPipeline!!.clock
        if (audioClock != null && videoPipeline != null) {
            videoPipeline!!.clock = audioClock
            videoPipeline!!.baseTime = audioPipeline!!.baseTime
        }

        if (!screen.getPaused()) {
            audioPipeline!!.play()
            if (videoPipeline != null) videoPipeline!!.play()
        }
    }

    private fun doPause() {
        if (!this.isInitialized) return
        if (videoPipeline != null) videoPipeline!!.pause()
        if (audioPipeline != null) audioPipeline!!.pause()
    }

    private fun doStop() {
        safeStopAndDispose(videoPipeline)
        safeStopAndDispose(audioPipeline)
        videoPipeline = null
        audioPipeline = null
    }

    private fun doSeek(nanos: Long, b: Boolean) {
        if (!this.isInitialized) return
        val flags = EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE)
        audioPipeline!!.pause()
        if (videoPipeline != null) videoPipeline!!.pause()
        if (videoPipeline != null) videoPipeline!!.seekSimple(Format.TIME, flags, nanos)
        audioPipeline!!.seekSimple(Format.TIME, flags, nanos)
        if (videoPipeline != null) videoPipeline!!.getState()
        audioPipeline!!.play()
        if (videoPipeline != null && !screen.getPaused()) videoPipeline!!.play()

        if (b) screen.afterSeek()
    }

    private fun doSeekFast(nanos: Long) {
        if (!this.isInitialized) return
        val flags = EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.KEY_UNIT)
        audioPipeline!!.pause()
        if (videoPipeline != null) videoPipeline!!.pause()
        if (videoPipeline != null) videoPipeline!!.seekSimple(Format.TIME, flags, nanos)
        audioPipeline!!.seekSimple(Format.TIME, flags, nanos)
        if (videoPipeline != null) videoPipeline!!.state
        audioPipeline!!.play()
        if (videoPipeline != null && !screen.getPaused()) videoPipeline!!.play()
    }

    private fun applyVolume() {
        if (!this.isInitialized) return
        val v = audioPipeline!!.getElementByName("volumeElement")
        if (v != null) v.set("volume", 1)
        val a = audioPipeline!!.getElementByName("ampElement")
        if (a != null) a.set("amplification", currentVolume)
    }

    private fun changeQuality(desired: kotlin.String) {
        if (!this.isInitialized || currentVideoUrl == null) return

        val target: Int
        try {
            target = desired.replace("\\D+".toRegex(), "").toInt()
        } catch (_: NumberFormatException) {
            return
        }

        try {
            val videoId = extractVideoId(youtubeUrl)
            val cleanUrl = "https://www.youtube.com/watch?v=$videoId"

            val info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl))
            val videoStreams = info.videoStreams

            // Check if exact match exists
            videoStreams.stream().anyMatch { vs: VideoStream? -> parseQuality(vs!!.getResolution()) == target }

            // Look for the best matching stream
            val best = videoStreams.stream()
                .min(Comparator.comparingInt<VideoStream>(ToIntFunction { vs: VideoStream -> abs(parseQuality(vs.getResolution()) - target) }))

            if (best.isEmpty || best.get().url == currentVideoUrl) {
                lastQuality = target
                return
            }

            Minecraft.getInstance().execute { screen.reloadTexture() }

            val pos = audioPipeline!!.queryPosition(Format.TIME)
            audioPipeline!!.pause()

            safeStopAndDispose(videoPipeline)

            val newVid = buildVideoPipeline(best.get().url!!)

            val clock = audioPipeline!!.clock
            if (clock != null) {
                newVid?.clock = clock
                newVid?.baseTime = audioPipeline!!.getBaseTime()
            }
            newVid?.pause()
            newVid?.getState()

            val flags = EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE)
            audioPipeline!!.seekSimple(Format.TIME, flags, pos)
            newVid?.seekSimple(Format.TIME, flags, pos)

            if (!screen.getPaused()) {
                audioPipeline!!.play()
                newVid?.play()
            }

            videoPipeline = newVid
            currentVideoUrl = best.get().getUrl() as String?
            lastQuality = parseQuality(best.get().getResolution())
        } catch (e: Exception) {
            LoggingManager.error("[MediaPlayer] Failed to change quality", e)
        }
    }

    fun tick(playerPos: BlockPos, maxRadius: Double) {
        if (!this.isInitialized) return

        val dist = screen.getDistanceToScreen(playerPos)
        val attenuation = (1.0 - min(1.0, dist / maxRadius)).pow(2.0)
        if (abs(attenuation - lastAttenuation) > 1e-5) {
            lastAttenuation = attenuation
            currentVolume = userVolume * attenuation
            LoggingManager.info("[MediaPlayer] Distance attenuation: " + currentVolume)
            safeExecute(Runnable { this.applyVolume() })
        }

        if (!screen.getPaused()) {
            syncCheckCounter++
            if (syncCheckCounter >= SYNC_CHECK_INTERVAL) {
                syncCheckCounter = 0
                safeExecute(Runnable { this.checkAndFixSync() })
            }
        }
    }

    // TODO: remove that shitty drift in the future
    private fun checkAndFixSync() {
        if (!this.isInitialized || videoPipeline == null || audioPipeline == null) return
        if (screen.getPaused()) return

        try {
            val audioPos = audioPipeline!!.queryPosition(Format.TIME)
            val videoPos = videoPipeline!!.queryPosition(Format.TIME)
            val drift = abs(audioPos - videoPos)

            if (drift > MAX_SYNC_DRIFT_NS) {
                LoggingManager.info("[MediaPlayer] Sync drift " + drift + " ns - resyncing video to audio")
                videoPipeline!!.seekSimple(
                    Format.TIME,
                    EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                    audioPos
                )
            }
        } catch (ignored: Exception) {
        }
    }

    private fun safeExecute(r: Runnable) {
        if (!gstExecutor.isShutdown()) {
            try {
                gstExecutor.submit(r)
            } catch (ignored: RejectedExecutionException) {
            }
        }
    }

    companion object {
        private val INIT_EXECUTOR: ExecutorService =
            Executors.newSingleThreadExecutor(ThreadFactory { r: Runnable? -> Thread(r, "MediaPlayer-init") })
        private const val SYNC_CHECK_INTERVAL = 100
        private const val MAX_SYNC_DRIFT_NS = 500000000L
        private const val MIN_FRAME_INTERVAL_NS = 16666667L
        var captureSamples: Boolean = true

        private fun copy(src: ByteBuffer, dst: ByteBuffer, bytes: Int) {
            val longs = bytes ushr 3
            val ints = (bytes and 7) ushr 2
            val remaining = bytes and 3

            // Copy 8 bytes at a time
            for (i in 0..<longs) {
                dst.putLong(src.getLong())
            }

            // Copy 4 bytes at a time
            for (i in 0..<ints) {
                dst.putInt(src.getInt())
            }

            // Copy remaining bytes
            for (i in 0..<remaining) {
                dst.put(src.get())
            }

            // Flip the destination buffer for reading
            dst.flip()
        }

        private fun sampleToBuffer(sample: Sample): ByteBuffer {
            val buf = sample.getBuffer()
            val bb = buf.map(false)

            if (bb.order() == ByteOrder.nativeOrder()) {
                val result = ByteBuffer.allocateDirect(bb.remaining()).order(ByteOrder.nativeOrder())
                result.put(bb)
                result.flip()
                buf.unmap()
                return result
            }

            val result = ByteBuffer.allocateDirect(bb.remaining()).order(ByteOrder.nativeOrder())
            bb.rewind()
            for (i in 0..<bb.remaining()) {
                result.put(bb.get())
            }
            result.flip()
            buf.unmap()
            return result
        }

        private fun applyBrightnessToBuffer(src: ByteBuffer, dst: ByteBuffer, bytes: Int, brightness: Double) {
            val pixels = bytes ushr 2
            val brightFixed = (brightness * 256.0).toInt()

            val pairs = pixels ushr 1
            val odd = pixels and 1
            if (brightness < 1.0) {
                // Process 2 pixels per iteration when possible
                for (i in 0..<pairs) {
                    // Pixel 1
                    val rgba1 = src.getInt()
                    val r1 = (((rgba1 ushr 24) and 0xFF) * brightFixed) ushr 8
                    val g1 = (((rgba1 ushr 16) and 0xFF) * brightFixed) ushr 8
                    val b1 = (((rgba1 ushr 8) and 0xFF) * brightFixed) ushr 8
                    val a1 = rgba1 and 0xFF

                    // Pixel 2
                    val rgba2 = src.getInt()
                    val r2 = (((rgba2 ushr 24) and 0xFF) * brightFixed) ushr 8
                    val g2 = (((rgba2 ushr 16) and 0xFF) * brightFixed) ushr 8
                    val b2 = (((rgba2 ushr 8) and 0xFF) * brightFixed) ushr 8
                    val a2 = rgba2 and 0xFF

                    // Write both pixels
                    dst.putInt((r1 shl 24) or (g1 shl 16) or (b1 shl 8) or a1)
                    dst.putInt((r2 shl 24) or (g2 shl 16) or (b2 shl 8) or a2)
                }

                // Handle odd pixel
                if (odd != 0) {
                    val rgba = src.getInt()
                    val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                    val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                    val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                    val a = rgba and 0xFF
                    dst.putInt((r shl 24) or (g shl 16) or (b shl 8) or a)
                }
            } else {
                for (i in 0..<pairs) {
                    // Pixel 1
                    val rgba1 = src.getInt()
                    val r1 = min(255, (((rgba1 ushr 24) and 0xFF) * brightFixed) ushr 8)
                    val g1 = min(255, (((rgba1 ushr 16) and 0xFF) * brightFixed) ushr 8)
                    val b1 = min(255, (((rgba1 ushr 8) and 0xFF) * brightFixed) ushr 8)
                    val a1 = rgba1 and 0xFF

                    // Pixel 2
                    val rgba2 = src.getInt()
                    val r2 = min(255, (((rgba2 ushr 24) and 0xFF) * brightFixed) ushr 8)
                    val g2 = min(255, (((rgba2 ushr 16) and 0xFF) * brightFixed) ushr 8)
                    val b2 = min(255, (((rgba2 ushr 8) and 0xFF) * brightFixed) ushr 8)
                    val a2 = rgba2 and 0xFF

                    // Write both pixels
                    dst.putInt((r1 shl 24) or (g1 shl 16) or (b1 shl 8) or a1)
                    dst.putInt((r2 shl 24) or (g2 shl 16) or (b2 shl 8) or a2)
                }

                // Handle odd pixel
                if (odd != 0) {
                    val rgba = src.getInt()
                    val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                    val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                    val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                    val a = rgba and 0xFF
                    dst.putInt((r shl 24) or (g shl 16) or (b shl 8) or a)
                }
            }

            dst.flip()
        }

        private fun scaleRGBA(
            src: ByteBuffer,
            srcW: Int,
            srcH: Int,
            dst: ByteBuffer,
            dstW: Int,
            dstH: Int,
            brightness: Double
        ) {
            // Calculate scaling factors
            val scaleW = dstW.toDouble() / srcW
            val scaleH = dstH.toDouble() / srcH

            // Take the larger scale to ensure the image fills the dst buffer
            val scale = max(scaleW, scaleH)

            // Calculate the scaled dimensions of the source image
            val scaledW = (srcW * scale + 0.5).toInt()
            val scaledH = (srcH * scale + 0.5).toInt()

            // Calculate offsets to center the image in the dst buffer
            val offsetX = (dstW - scaledW) ushr 1
            val offsetY = (dstH - scaledH) ushr 1

            // Precompute inverse scaling factors in fixed-point 16.16 format
            // int offsetX = (dstW - scaledW) / 2;
            // int offsetY = (dstH - scaledH) / 2;
            val invScaleWFixed = ((srcW shl 16) / scaledW.toDouble()).toInt()
            val invScaleHFixed = ((srcH shl 16) / scaledH.toDouble()).toInt()

            // Determine if brightness adjustment is needed
            val applyBright = abs(brightness - 1.0) >= 1e-5
            // Precompute brightness in fixed-point 8.8 format
            val brightFixed = (brightness * 256).toInt()

            // Prepare dst buffer: clear to black with transparency
            val totalBytes = dstW * dstH * 4 // Total bytes in dst buffer
            val longs = totalBytes ushr 3 // Total longs (8 bytes each)
            val remaining = totalBytes and 7 // Remaining bytes after longs

            dst.clear() // Set position to 0
            for (i in 0..<longs) {
                dst.putLong(0L) // Set 8 bytes to 0 (black pixel)
            }
            for (i in 0..<remaining) {
                dst.put(0.toByte()) // Set remaining bytes to 0
            }
            dst.clear() // Reset position for writing

            // Precompute row byte sizes
            val srcWBytes = srcW shl 2 // srcW * 4
            val dstWBytes = dstW shl 2 // dstW * 4

            // No brightness adjustment needed
            if (!applyBright) {
                // 4 pixels per iteration
                for (y in 0..<dstH) {
                    // Calculate corresponding srcY
                    val srcY = (((y - offsetY) * invScaleHFixed) ushr 16)
                    if (srcY >= srcH) continue  // Skip if out of bounds


                    // TODO: should we also check srcY < 0?
                    val srcRowBase = srcY * srcWBytes // Address start of row in src buffer
                    val dstRowBase = y * dstWBytes // Address start of row in dst buffer

                    var x = 0
                    val xLimit = dstW - 3

                    // 4 pixels at a time
                    while (x < xLimit) {
                        // Check for their coordinates in source image
                        val srcX0 = (((x - offsetX) * invScaleWFixed) ushr 16)
                        val srcX1 = (((x + 1 - offsetX) * invScaleWFixed) ushr 16)
                        val srcX2 = (((x + 2 - offsetX) * invScaleWFixed) ushr 16)
                        val srcX3 = (((x + 3 - offsetX) * invScaleWFixed) ushr 16)

                        // Copy pixels if within bounds
                        if (srcX0 < srcW) {
                            dst.putInt(dstRowBase + (x shl 2), src.getInt(srcRowBase + (srcX0 shl 2)))
                        }
                        if (srcX1 <= srcW) {
                            dst.putInt(dstRowBase + ((x + 1) shl 2), src.getInt(srcRowBase + (srcX1 shl 2)))
                        }
                        if (srcX2 < srcW) {
                            dst.putInt(dstRowBase + ((x + 2) shl 2), src.getInt(srcRowBase + (srcX2 shl 2)))
                        }
                        if (srcX3 < srcW) {
                            dst.putInt(dstRowBase + ((x + 3) shl 2), src.getInt(srcRowBase + (srcX3 shl 2)))
                        }
                        x += 4
                    }

                    // Single pixels remaining
                    while (x < dstW) {
                        val srcX = (((x - offsetX) * invScaleWFixed) ushr 16)
                        if (srcX < srcW) {
                            dst.putInt(dstRowBase + (x shl 2), src.getInt(srcRowBase + (srcX shl 2)))
                        }
                        x++
                    }
                }
                // If brightness < 1.0 (darkening)
            } else if (brightness < 1.0) {
                for (y in 0..<dstH) {
                    // int srcY = (int) (((y - offsetY) * srcH) / (double) scaledH);
                    // if (srcY < 0 || srcY >= srcH) continue;
                    val srcY = (((y - offsetY) * invScaleHFixed) ushr 16)
                    if (srcY >= srcH) continue

                    val srcRowBase = srcY * srcWBytes
                    val dstRowBase = y * dstWBytes

                    var x = 0
                    val xLimit = dstW - 1

                    while (x < xLimit) {
                        val srcX0 = (((x - offsetX) * invScaleWFixed) ushr 16)
                        val srcX1 = (((x + 1 - offsetX) * invScaleWFixed) ushr 16)

                        // 2 pixels at a time
                        if (srcX0 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX0 shl 2))
                            val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                            val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                            val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }

                        if (srcX1 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX1 shl 2))
                            val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                            val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                            val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + ((x + 1) shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x += 2
                    }

                    // Single pixel remaining
                    while (x < dstW) {
                        val srcX = (((x - offsetX) * invScaleWFixed) ushr 16)
                        if (srcX < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX shl 2))
                            val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                            val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                            val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x++
                    }
                }
            } else {
                for (y in 0..<dstH) {
                    val srcY = (((y - offsetY) * invScaleHFixed) ushr 16)
                    if (srcY >= srcH) continue

                    val srcRowBase = srcY * srcWBytes
                    val dstRowBase = y * dstWBytes

                    var x = 0
                    val xLimit = dstW - 1

                    while (x < xLimit) {
                        val srcX0 = (((x - offsetX) * invScaleWFixed) ushr 16)
                        val srcX1 = (((x + 1 - offsetX) * invScaleWFixed) ushr 16)

                        // 2 pixels at a time
                        if (srcX0 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX0 shl 2))
                            val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                            val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                            val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }

                        if (srcX1 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX1 shl 2))
                            val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                            val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                            val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + ((x + 1) shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x += 2
                    }

                    // Single pixel remaining
                    while (x < dstW) {
                        val srcX = (((x - offsetX) * invScaleWFixed) ushr 16)
                        if (srcX < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX shl 2))
                            val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                            val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                            val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x++
                    }
                }
            }
        }

        private fun safeStopAndDispose(e: Element?) {
            if (e == null) return
            try {
                e.setState(State.NULL)
            } catch (_: Exception) {
            }
            try {
                e.dispose()
            } catch (_: Exception) {
            }
        }
    }
}
