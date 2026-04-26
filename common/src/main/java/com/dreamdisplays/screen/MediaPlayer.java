package com.dreamdisplays.screen;

import com.dreamdisplays.Initializer;
import com.dreamdisplays.ytdlp.YtDlp;
import com.dreamdisplays.ytdlp.YtStream;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import me.inotsleep.utils.logging.LoggingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Media player for streaming YouTube videos using GStreamer.
 * Handles video and audio playback, quality selection, volume control, and frame processing.
 * <p>
 * Integrates with Minecraft's rendering system to display video frames on in-game screens.
 */
// TODO: replace with FFmpeg solution in version 2.0.0
@NullMarked
public class MediaPlayer {

    // === CONSTANTS =======================================================================
    private static final String MIME_VIDEO = "video/webm";
    private static final String MIME_AUDIO = "audio/webm";
    private static final String USER_AGENT_V =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final ExecutorService INIT_EXECUTOR =
            Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "MediaPlayer-init")
            );
    private static final int SYNC_CHECK_INTERVAL = 100;
    private static final long MAX_SYNC_DRIFT_NS = 1_500_000_000L;
    private static final long HTTP_QUEUE_BYTES = 64L * 1024 * 1024; // 64 MiB video
    private static final long HTTP_QUEUE_BYTES_AUDIO = 8L * 1024 * 1024; // 8 MiB audio
    private static final long HTTP_QUEUE_TIME_NS = 30L * 1_000_000_000L; // 30 s
    private static final long RAW_QUEUE_TIME_NS = 2L * 1_000_000_000L; // 2 s
    private static final long STOP_WAIT_TIMEOUT_SECONDS = 3;
    public static boolean captureSamples = true;
    public static final boolean DEBUG =
            Boolean.getBoolean("dreamdisplays.debug")
                    || "1".equals(System.getenv("DREAMDISPLAYS_DEBUG"))
                    || "true".equalsIgnoreCase(System.getenv("DREAMDISPLAYS_DEBUG"));
    private static final long STATS_INTERVAL_MS = 2000;
    private final java.util.concurrent.atomic.AtomicLong samplesIn =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong framesToGpu =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong framesDropped =
            new java.util.concurrent.atomic.AtomicLong();
    private @Nullable ScheduledExecutorService statsExecutor;
    private final String lang;
    // === PUBLIC API FIELDS ===============================================================
    private final String youtubeUrl;
    // === EXECUTORS & CONCURRENCY =========================================================
    private final ExecutorService gstExecutor =
            Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "MediaPlayer-gst")
            );
    private final ExecutorService frameExecutor =
            Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "MediaPlayer-frame")
            );
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicBoolean frameTaskQueued = new AtomicBoolean(false);
    private final Object frameLock = new Object();
    private final Screen screen;
    private final String debugLabel;
    private volatile double currentVolume = Initializer.config.defaultDisplayVolume;
    // === GST OBJECTS =====================================================================
    private volatile @Nullable Pipeline videoPipeline;
    private volatile @Nullable Pipeline audioPipeline;
    private volatile java.util.@Nullable List<YtStream> availableVideoStreams;
    private volatile @Nullable YtStream currentVideoStream;
    private volatile boolean initialized;
    private int lastQuality;
    // === FRAME BUFFERS ===================================================================
    private volatile @Nullable ByteBuffer currentFrameBuffer;
    private volatile int currentFrameWidth = 0;
    private volatile int currentFrameHeight = 0;
    private volatile @Nullable ByteBuffer preparedBuffer;
    private int preparedBufferSize = 0;
    private volatile int lastTexW = 0,
            lastTexH = 0;
    private volatile int preparedW = 0,
            preparedH = 0;
    private volatile double userVolume =
            (Initializer.config.defaultDisplayVolume);
    private volatile double lastAttenuation = 1.0;
    private volatile double brightness = 1.0;
    private volatile boolean frameReady = false;
    private int syncCheckCounter = 0;

    // === CONSTRUCTOR =====================================================================
    public MediaPlayer(String youtubeUrl, String lang, Screen screen) {
        this.youtubeUrl = youtubeUrl;
        this.screen = screen;
        this.lang = lang;
        this.debugLabel = screen.getUUID() + "/" + Integer.toHexString(System.identityHashCode(this));
        Gst.init("MediaPlayer");
        INIT_EXECUTOR.submit(this::initialize);
    }

    // === FRAME PROCESSING ================================================================
    // Reusable direct buffer for the latest decoded frame. Reallocating ~8MB per
    // frame at 30fps (1080p RGBA) thrashes JVM direct memory and stalls the
    // GStreamer streaming thread. We grow it as needed and otherwise overwrite
    // in place.
    private @Nullable ByteBuffer frameBufferPool = null;

    private ByteBuffer sampleToBuffer(Sample sample) {
        Buffer buf = sample.getBuffer();
        ByteBuffer bb = buf.map(false);
        try {
            int needed = bb.remaining();
            ByteBuffer dst = frameBufferPool;
            if (dst == null || dst.capacity() < needed) {
                dst = ByteBuffer.allocateDirect(needed)
                        .order(ByteOrder.nativeOrder());
                frameBufferPool = dst;
            }
            dst.clear();
            dst.put(bb);
            dst.flip();
            return dst;
        } finally {
            buf.unmap();
        }
    }

    private ByteBuffer ensurePreparedBufferCapacity(int size) {
        ByteBuffer buffer = preparedBuffer;
        if (buffer == null || preparedBufferSize < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
            preparedBuffer = buffer;
            preparedBufferSize = size;
        }
        buffer.clear();
        buffer.limit(size);
        return buffer;
    }

    private static void applyBrightnessToBuffer(ByteBuffer buffer, double brightness) {
        if (Math.abs(brightness - 1.0) < 1e-5) return; // Skip if brightness is 1.0

        buffer.rewind();
        while (buffer.remaining() >= 4) {
            int r = buffer.get() & 0xFF;
            int g = buffer.get() & 0xFF;
            int b = buffer.get() & 0xFF;
            byte a = buffer.get();

            // Apply brightness
            r = (int) Math.min(255, r * brightness);
            g = (int) Math.min(255, g * brightness);
            b = (int) Math.min(255, b * brightness);

            // Put values back (need to go back 4 bytes)
            buffer.position(buffer.position() - 4);
            buffer.put((byte) r);
            buffer.put((byte) g);
            buffer.put((byte) b);
            buffer.put(a);
        }
        buffer.flip();
    }

    private static int parseQuality(YtStream stream) {
        try {
            return Integer.parseInt(stream.getResolution().replaceAll("\\D+", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static int parseQualityValue(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            String digits = raw.replaceAll("\\D+", "");
            if (digits.isEmpty()) return fallback;
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void safeStopAndDispose(@Nullable Element e) {
        if (e == null) return;
        try {
            e.setState(State.NULL);
        } catch (Exception ignore) {
        }
        try {
            e.dispose();
        } catch (Exception ignore) {
        }
    }

    private void bindVideoToAudioClock(Pipeline audio, @Nullable Pipeline video) {
        if (video == null) return;
        Clock audioClock = audio.getClock();
        if (audioClock != null) {
            video.setClock(audioClock);
        }
        video.setBaseTime(audio.getBaseTime());
    }

    // === PUBLIC API ======================================================================
    public void play() {
        safeExecute(this::doPlay);
    }

    public void pause() {
        safeExecute(this::doPause);
    }

    public void seekTo(long nanos, boolean b) {
        safeExecute(() -> doSeek(nanos, b));
    }

    public void seekToFast(long nanos) {
        safeExecute(() -> doSeekFast(nanos));
    }

    public void seekRelative(double s) {
        safeExecute(() -> {
            Pipeline audio = audioPipeline;
            if (!initialized || audio == null) return;
            long cur = audio.queryPosition(Format.TIME);
            long tgt = Math.max(0, cur + (long) (s * 1e9));
            long dur = Math.max(
                    0,
                    audio.queryDuration(Format.TIME) - 1
            );
            doSeek(Math.min(tgt, dur), true);
        });
    }

    public long getCurrentTime() {
        Pipeline audio = audioPipeline;
        return initialized && audio != null ? audio.queryPosition(Format.TIME) : 0;
    }

    public long getDuration() {
        Pipeline audio = audioPipeline;
        return initialized && audio != null ? audio.queryDuration(Format.TIME) : 0;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void stop() {
        if (terminated.getAndSet(true)) return;
        Future<?> stopFuture = null;
        if (!gstExecutor.isShutdown()) {
            try {
                stopFuture = gstExecutor.submit(this::doStop);
            } catch (RejectedExecutionException ignored) {
            }
        }
        if (stopFuture != null) {
            try {
                stopFuture.get(STOP_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                LoggingManager.warn("[MP debug " + debugLabel + "] Timed out stopping media player cleanly");
                doStop();
            }
        } else {
            doStop();
        }
        gstExecutor.shutdownNow();
        frameExecutor.shutdownNow();
    }

    public void setVolume(double volume) {
        userVolume = Math.max(0, Math.min(2, volume));
        currentVolume = userVolume * lastAttenuation;
        safeExecute(this::applyVolume);
    }

    public void setBrightness(double brightness) {
        this.brightness = Math.max(0, Math.min(2, brightness));
    }

    public boolean textureFilled() {
        synchronized (frameLock) {
            return preparedBuffer != null && preparedBuffer.limit() > 0;
        }
    }

    public void updateFrame(GpuTexture texture) {
        synchronized (frameLock) {
            if (!frameReady || preparedBuffer == null) return;

            int w = screen.textureWidth,
                    h = screen.textureHeight;
            if (w != preparedW || h != preparedH) return;

            CommandEncoder encoder =
                    RenderSystem.getDevice().createCommandEncoder();

            preparedBuffer.rewind();

            int expectedSize = w * h * 4;
            if (preparedBuffer.remaining() < expectedSize) {
                LoggingManager.error(
                        "Buffer underrun: expected " +
                                expectedSize +
                                " bytes, but only " +
                                preparedBuffer.remaining() +
                                " remaining"
                );
                return;
            }

            if (w != lastTexW || h != lastTexH) {
                lastTexW = w;
                lastTexH = h;
            }

            if (!texture.isClosed()) {
                encoder.writeToTexture(
                        texture,
                        preparedBuffer,
                        NativeImage.Format.RGBA,
                        0,
                        0,
                        0,
                        0,
                        texture.getWidth(0),
                        texture.getHeight(0)
                );
            }

            if (DEBUG) framesToGpu.incrementAndGet();
            frameReady = false;
        }
    }

    public java.util.List<Integer> getAvailableQualities() {
        if (availableVideoStreams == null) return Collections.emptyList();
        return availableVideoStreams
                .stream()
                .map(YtStream::getResolution)
                .filter(Objects::nonNull)
                .map(r -> parseQualityValue(r, Integer.MAX_VALUE))
                .filter(r -> r != Integer.MAX_VALUE)
                .distinct()
                .filter(r -> r <= (Initializer.isPremium ? 2160 : 1080))
                .sorted()
                .collect(Collectors.toList());
    }

    public void setQuality(String quality) {
        safeExecute(() -> changeQuality(quality));
    }

    // === INITIALIZATION ==================================================================
    private void initialize() {
        try {
            // Extract video ID from URL to handle URLs with parameters like ?t=10&list=PLxxx
            String videoId = com.dreamdisplays.util.Utils.extractVideoId(
                    youtubeUrl
            );
            if (videoId == null || videoId.isEmpty()) {
                LoggingManager.error(
                        "Could not extract video ID from URL: " + youtubeUrl
                );
                screen.errored = true;
                return;
            }

            // Build proper YouTube URL with just the video ID
            String cleanUrl = "https://www.youtube.com/watch?v=" + videoId;

            java.util.List<YtStream> all = YtDlp.fetch(cleanUrl);
            if (terminated.get()) return;
            java.util.List<YtStream> audioS = all;

            availableVideoStreams = all
                    .stream()
                    .filter(s -> MIME_VIDEO.equals(s.getMimeType()))
                    .toList();

            int requestedQuality = parseQualityValue(screen.getQuality(), 720);
            Optional<YtStream> videoOpt = pickVideo(
                    requestedQuality
            ).or(() -> availableVideoStreams.stream().findFirst());
            Optional<YtStream> audioOpt = audioS
                    .stream()
                    .filter(s -> MIME_AUDIO.equals(s.getMimeType()))
                    .filter(
                            s ->
                                    (s.getAudioTrackId() != null &&
                                            s.getAudioTrackId().contains(lang)) ||
                                            (s.getAudioTrackName() != null &&
                                                    s.getAudioTrackName().contains(lang))
                    )
                    .reduce((f, n) -> n);

            if (audioOpt.isEmpty()) {
                audioOpt = all
                        .stream()
                        .filter(s -> MIME_AUDIO.equals(s.getMimeType()))
                        .reduce((f, n) -> n);
            }
            if (videoOpt.isEmpty() || audioOpt.isEmpty()) {
                LoggingManager.error("No streams available");
                screen.errored = true;
                return;
            }

            currentVideoStream = videoOpt.get();
            lastQuality = parseQuality(currentVideoStream);

            audioPipeline = buildAudioPipeline(audioOpt.get().getUrl());
            videoPipeline = buildVideoPipeline(currentVideoStream.getUrl());

            videoPipeline.getState();
            initialized = true;
            if (DEBUG) {
                LoggingManager.info("[MP debug " + debugLabel + "] picked video: " + currentVideoStream);
                LoggingManager.info("[MP debug " + debugLabel + "] picked audio: " + audioOpt.get());
                LoggingManager.info("[MP debug " + debugLabel + "] available video count="
                        + availableVideoStreams.size()
                        + " resolutions=" + getAvailableQualities());
                startStatsReporter();
            }
        } catch (Exception e) {
            LoggingManager.error("Failed to initialize MediaPlayer ", e);
            screen.errored = true;
        }
    }

    private Pipeline buildVideoPipeline(String uri) {
        String desc = String.join(
                " ",
                "souphttpsrc location=\"" + uri + "\"",
                "user-agent=\"" + USER_AGENT_V + "\"",
                "extra-headers=\"origin:https://www.youtube.com\\nreferer:https://www.youtube.com\\n\"",
                "blocksize=131072 retries=5 timeout=15",
                "! queue2 name=httpQueueV",
                "max-size-bytes=" + HTTP_QUEUE_BYTES,
                "max-size-time=" + HTTP_QUEUE_TIME_NS,
                "max-size-buffers=0",
                "! matroskademux",
                "! decodebin",
                "! queue name=rawQueueV max-size-buffers=4 max-size-bytes=0 max-size-time=" + RAW_QUEUE_TIME_NS,
                "! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink"
        );
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        configureVideoSink((AppSink) p.getElementByName("videosink"));
        p.pause();

        Bus bus = p.getBus();
        final AtomicReference<Bus.ERROR> errRef = new AtomicReference<>();
        errRef.set((source, code, message) -> {
            LoggingManager.error(
                    "[MediaPlayer V " + debugLabel + "] [ERROR] GStreamer: " + message
            );
            bus.disconnect(errRef.get());
            screen.errored = true;
            initialized = false;
        });
        bus.connect(errRef.get());
        return p;
    }

    private Pipeline buildAudioPipeline(String uri) {
        String desc = String.join(
                " ",
                "souphttpsrc location=\"" + uri + "\"",
                "user-agent=\"" + USER_AGENT_V + "\"",
                "extra-headers=\"origin:https://www.youtube.com\\nreferer:https://www.youtube.com\\n\"",
                "blocksize=65536 retries=5 timeout=15",
                "! queue2 name=httpQueueA",
                "max-size-bytes=" + HTTP_QUEUE_BYTES_AUDIO,
                "max-size-time=" + HTTP_QUEUE_TIME_NS,
                "max-size-buffers=0",
                "! decodebin",
                "! queue name=rawQueueA max-size-buffers=0 max-size-bytes=0 max-size-time=" + RAW_QUEUE_TIME_NS,
                "! audioconvert ! audioresample",
                "! volume name=volumeElement volume=1",
                "! audioamplify name=ampElement amplification=" + currentVolume,
                "! autoaudiosink"
        );
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        p
                .getBus()
                .connect(
                        (Bus.ERROR) (source, code, message) ->
                                LoggingManager.error(
                                        "[MediaPlayer A " + debugLabel + "] [ERROR] GStreamer: " + message
                                )
                );

        p
                .getBus()
                .connect(
                        (Bus.EOS) source -> {
                            safeExecute(() -> {
                                Pipeline audio = audioPipeline;
                                Pipeline video = videoPipeline;
                                if (audio == null) return;
                                audio.seekSimple(
                                        Format.TIME,
                                        EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                                        0L
                                );
                                audio.play();

                                // If a video pipeline exists, seek it too
                                if (video != null) {
                                    video.seekSimple(
                                            Format.TIME,
                                            EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                                            0L
                                    );
                                    video.play();
                                }
                            });
                        }
                );

        return p;
    }

    private void configureVideoSink(AppSink sink) {
        sink.set("emit-signals", true);
        // Let GStreamer pace frames against the shared playback clock.
        // The Java side should only transform/upload the latest frame, not act
        // as the primary timing source.
        sink.set("sync", true);
        sink.set("max-buffers", 1);
        sink.set("drop", true);
        sink.connect(
                (AppSink.NEW_SAMPLE) elem -> {
                    Sample s = elem.pullSample();
                    if (s == null || !captureSamples || terminated.get()) return FlowReturn.OK;
                    boolean scheduled = false;
                    try {
                        if (!frameTaskQueued.compareAndSet(false, true)) {
                            if (DEBUG) framesDropped.incrementAndGet();
                            return FlowReturn.OK;
                        }
                        Structure st = s.getCaps().getStructure(0);
                        synchronized (frameLock) {
                            currentFrameWidth = st.getInteger("width");
                            currentFrameHeight = st.getInteger("height");
                            currentFrameBuffer = sampleToBuffer(s);
                        }
                        if (DEBUG) samplesIn.incrementAndGet();
                        scheduled = prepareBufferAsync();
                    } finally {
                        if (!scheduled) {
                            frameTaskQueued.set(false);
                        }
                        s.dispose();
                    }
                    return FlowReturn.OK;
                }
        );
    }

    private boolean prepareBufferAsync() {
        int w = screen.textureWidth,
                h = screen.textureHeight;

        // Skip if screen dimensions are zero
        if (w == 0 || h == 0) return false;

        try {
            frameExecutor.submit(this::prepareBuffer);
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private void prepareBuffer() {
        try {
            int targetW = screen.textureWidth,
                    targetH = screen.textureHeight;
            if (targetW == 0 || targetH == 0) return;

            synchronized (frameLock) {
                if (currentFrameBuffer == null) return;

                int sourceW = currentFrameWidth;
                int sourceH = currentFrameHeight;
                int outputSize = targetW * targetH * 4;
                ByteBuffer source = currentFrameBuffer.duplicate().order(ByteOrder.nativeOrder());
                source.rewind();
                ByteBuffer output = ensurePreparedBufferCapacity(outputSize);

                if (sourceW == targetW && sourceH == targetH) {
                    output.put(source);
                    output.flip();
                } else {
                    output.position(0);
                    output.limit(outputSize);
                    Converter.scaleRGBA(
                            source,
                            sourceW,
                            sourceH,
                            output,
                            targetW,
                            targetH
                    );
                    output.position(0);
                    output.limit(outputSize);
                }

                applyBrightnessToBuffer(output, brightness);
                preparedW = targetW;
                preparedH = targetH;
                frameReady = true;
            }

            Minecraft.getInstance().execute(screen::fitTexture);
        } finally {
            frameTaskQueued.set(false);
        }
    }

    // === PLAYBACK HELPERS ================================================================
    private void doPlay() {
        if (!initialized) return;
        Pipeline audio = audioPipeline;
        if (audio == null) return;
        Pipeline video = videoPipeline;
        bindVideoToAudioClock(audio, video);

        if (!screen.getPaused()) {
            audio.play();
            if (video != null) {
                video.play();
            }
        }
    }

    private void doPause() {
        if (!initialized) return;
        Pipeline video = videoPipeline;
        Pipeline audio = audioPipeline;
        if (video != null) video.pause();
        if (audio != null) audio.pause();
    }

    private void doStop() {
        initialized = false;
        frameTaskQueued.set(false);
        synchronized (frameLock) {
            frameReady = false;
            preparedBuffer = null;
            preparedBufferSize = 0;
            currentFrameBuffer = null;
            frameBufferPool = null;
        }
        stopStatsReporter();
        safeStopAndDispose(videoPipeline);
        safeStopAndDispose(audioPipeline);
        videoPipeline = null;
        audioPipeline = null;
    }

    private void startStatsReporter() {
        if (statsExecutor != null) return;
        statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MediaPlayer-stats");
            t.setDaemon(true);
            return t;
        });
        statsExecutor.scheduleAtFixedRate(
                this::reportStats,
                STATS_INTERVAL_MS,
                STATS_INTERVAL_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS
        );
    }

    private void stopStatsReporter() {
        java.util.concurrent.ScheduledExecutorService ex = statsExecutor;
        if (ex != null) {
            ex.shutdownNow();
            statsExecutor = null;
        }
    }

    private void reportStats() {
        try {
            Pipeline v = videoPipeline;
            Pipeline a = audioPipeline;
            if (v == null || a == null) return;
            long inN = samplesIn.getAndSet(0);
            long outN = framesToGpu.getAndSet(0);
            long dropN = framesDropped.getAndSet(0);
            double seconds = STATS_INTERVAL_MS / 1000.0;
            String vState = String.valueOf(v.getState(0));
            String aState = String.valueOf(a.getState(0));
            long aPos = a.queryPosition(Format.TIME);
            long vPos = v.queryPosition(Format.TIME);
            long drift = (aPos - vPos) / 1_000_000L;
            LoggingManager.info(String.format(
                    "[MP debug %s] decode=%.1ffps gpu=%.1ffps dropped=%.1f/s"
                            + " | a/v=%s/%s drift=%dms"
                            + " | %s",
                    debugLabel,
                    inN / seconds, outN / seconds, dropN / seconds,
                    aState, vState, drift, queueLevels(v, a)
            ));
        } catch (Throwable t) {}
    }

    private String queueLevels(Pipeline v, Pipeline a) {
        StringBuilder sb = new StringBuilder();
        appendLevel(sb, v, "httpQueueV");
        appendLevel(sb, v, "demuxQueueV");
        appendLevel(sb, v, "rawQueueV");
        appendLevel(sb, a, "httpQueueA");
        appendLevel(sb, a, "rawQueueA");
        return sb.toString();
    }

    private static void appendLevel(StringBuilder sb, Pipeline p, String name) {
        Element e = p.getElementByName(name);
        if (e == null) return;
        try {
            Object curBytes = e.get("current-level-bytes");
            Object curTime = e.get("current-level-time");
            long timeMs = curTime instanceof Number
                    ? ((Number) curTime).longValue() / 1_000_000L
                    : -1;
            long kb = curBytes instanceof Number
                    ? ((Number) curBytes).longValue() / 1024
                    : -1;
            sb.append(' ').append(name).append('=')
                    .append(kb).append("KiB/").append(timeMs).append("ms");
        } catch (Throwable ignored) {
        }
    }

    private void doSeek(long nanos, boolean b) {
        if (!initialized) return;
        Pipeline audio = audioPipeline;
        if (audio == null) return;
        Pipeline video = videoPipeline;
        EnumSet<SeekFlags> flags = EnumSet.of(
                SeekFlags.FLUSH,
                SeekFlags.ACCURATE
        );
        audio.pause();
        if (video != null) video.pause();
        if (video != null) video.seekSimple(
                Format.TIME,
                flags,
                nanos
        );
        audio.seekSimple(Format.TIME, flags, nanos);
        audio.getState();
        if (video != null) {
            bindVideoToAudioClock(audio, video);
            video.getState();
        }
        if (!screen.getPaused()) {
            audio.play();
            if (video != null) video.play();
        }

        if (b) screen.afterSeek();
    }

    private void doSeekFast(long nanos) {
        if (!initialized) return;
        Pipeline audio = audioPipeline;
        if (audio == null) return;
        Pipeline video = videoPipeline;
        EnumSet<SeekFlags> flags = EnumSet.of(
                SeekFlags.FLUSH,
                SeekFlags.KEY_UNIT
        );
        audio.pause();
        if (video != null) video.pause();
        if (video != null) video.seekSimple(
                Format.TIME,
                flags,
                nanos
        );
        audio.seekSimple(Format.TIME, flags, nanos);
        audio.getState();
        if (video != null) {
            bindVideoToAudioClock(audio, video);
            video.getState();
        }
        if (!screen.getPaused()) {
            audio.play();
            if (video != null) video.play();
        }
    }

    private void applyVolume() {
        Pipeline audio = audioPipeline;
        if (!initialized || audio == null) return;
        Element v = audio.getElementByName("volumeElement");
        if (v != null) v.set("volume", 1);
        Element a = audio.getElementByName("ampElement");
        if (a != null) a.set("amplification", currentVolume);
    }

    // === QUALITY HELPERS =================================================================
    private Optional<YtStream> pickVideo(int target) {
        return availableVideoStreams
                .stream()
                .filter(s -> s.getResolution() != null)
                .min(
                        Comparator.comparingInt(s -> Math.abs(parseQuality(s) - target))
                );
    }

    private void changeQuality(String desired) {
        if (!initialized || availableVideoStreams == null) return;
        Pipeline audio = audioPipeline;
        YtStream current = currentVideoStream;
        if (audio == null || current == null) return;
        int target;
        try {
            target = Integer.parseInt(desired.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return;
        }
        if (target == lastQuality) return;

        Optional<YtStream> best = pickVideo(target);
        if (best.isEmpty()) return;
        YtStream chosen = best.get();
        if (chosen.getUrl().equals(current.getUrl())) return;

        Minecraft.getInstance().execute(screen::reloadTexture);

        long pos = audio.queryPosition(Format.TIME);
        audio.pause();

        safeStopAndDispose(videoPipeline);

        Pipeline newVid = buildVideoPipeline(chosen.getUrl());

        // Get the clock from audio pipeline and use it for video
        bindVideoToAudioClock(audio, newVid);
        newVid.pause();

        // Pre-roll the pipeline to ensure it's ready
        newVid.getState();

        EnumSet<SeekFlags> flags = EnumSet.of(
                SeekFlags.FLUSH,
                SeekFlags.ACCURATE
        );
        audio.seekSimple(Format.TIME, flags, pos);
        newVid.seekSimple(Format.TIME, flags, pos);

        if (!screen.getPaused()) {
            audio.play();
            newVid.play();
        }

        videoPipeline = newVid;
        currentVideoStream = chosen;
        lastQuality = parseQuality(chosen);
    }

    // === TICK ================================================================
    public void tick(BlockPos playerPos, double maxRadius) {
        if (!initialized) return;

        // Volume attenuation based on distance
        double dist = screen.getDistanceToScreen(playerPos);
        double attenuation = Math.pow(1.0 - Math.min(1.0, dist / maxRadius), 2);
        if (Math.abs(attenuation - lastAttenuation) > 1e-5) {
            lastAttenuation = attenuation;
            currentVolume = userVolume * attenuation;
            safeExecute(this::applyVolume);
        }

        // Periodic sync check - only when playing
        if (!screen.getPaused()) {
            syncCheckCounter++;
            if (syncCheckCounter >= SYNC_CHECK_INTERVAL) {
                syncCheckCounter = 0;
                safeExecute(this::checkAndFixSync);
            }
        }
    }

    private void checkAndFixSync() {
        if (!initialized || videoPipeline == null || audioPipeline == null) return;
        if (screen.getPaused()) return;

        try {
            long audioPos = audioPipeline.queryPosition(Format.TIME);
            long videoPos = videoPipeline.queryPosition(Format.TIME);
            long drift = Math.abs(audioPos - videoPos);

            if (drift > MAX_SYNC_DRIFT_NS) {
                // Sync video to audio (audio is master)
                videoPipeline.pause();
                videoPipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), audioPos);
                if (!screen.getPaused()) {
                    videoPipeline.play();
                }
            }
        } catch (Exception ignored) {
            // Ignore query errors during playback
        }
    }

    // === CONCURRENCY HELPERS =============================================================
    private void safeExecute(Runnable action) {
        if (!terminated.get() && !gstExecutor.isShutdown()) {
            try {
                gstExecutor.submit(action);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }
}
