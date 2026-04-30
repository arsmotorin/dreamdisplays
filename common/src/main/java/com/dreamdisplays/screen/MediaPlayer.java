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
import org.freedesktop.gstreamer.event.SeekType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

    public static final boolean DEBUG =
            Boolean.getBoolean("dreamdisplays.debug")
                    || "1".equals(System.getenv("DREAMDISPLAYS_DEBUG"))
                    || "true".equalsIgnoreCase(System.getenv("DREAMDISPLAYS_DEBUG"));
    // === CONSTANTS =======================================================================
    private static final String USER_AGENT_V =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final AtomicInteger INIT_THREAD_COUNTER = new AtomicInteger();
    private static final ExecutorService INIT_EXECUTOR =
            Executors.newFixedThreadPool(
                    Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
                    r -> {
                        Thread thread = new Thread(
                                r,
                                "MediaPlayer-init-" + INIT_THREAD_COUNTER.incrementAndGet()
                        );
                        thread.setDaemon(true);
                        return thread;
                    }
            );
    private static final int SYNC_CHECK_INTERVAL = 20;
    private static final long MAX_SYNC_DRIFT_NS = 400_000_000L;
    private static final long POST_SEEK_RESYNC_DELAY_MS = 250L;
    private static final long HTTP_QUEUE_BYTES = 64L * 1024 * 1024; // 64 MiB video
    private static final long HTTP_QUEUE_BYTES_AUDIO = 8L * 1024 * 1024; // 8 MiB audio
    private static final long HTTP_QUEUE_TIME_NS = 30L * 1_000_000_000L; // 30 s
    private static final long LIVE_HTTP_QUEUE_TIME_NS = 5L * 1_000_000_000L; // 5 s
    private static final long RAW_QUEUE_TIME_NS = 2L * 1_000_000_000L; // 2 s
    private static final long LIVE_RAW_QUEUE_TIME_NS = 500L * 1_000_000L; // 500 ms
    private static final long STOP_WAIT_TIMEOUT_SECONDS = 3;
    private static final long STATS_INTERVAL_MS = 2000;
    private static final long SEEK_STATE_WAIT_NS = 4L * 1_000_000_000L;
    public static boolean captureSamples = true;
    private final java.util.concurrent.atomic.AtomicLong samplesIn =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong framesToGpu =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong framesDropped =
            new java.util.concurrent.atomic.AtomicLong();
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
    private @Nullable ScheduledExecutorService statsExecutor;
    private volatile double currentVolume = Initializer.config.defaultDisplayVolume;
    // === GST OBJECTS =====================================================================
    private volatile @Nullable Pipeline videoPipeline;
    private volatile @Nullable Pipeline audioPipeline;
    private volatile boolean sharedAvPipeline;
    private volatile java.util.@Nullable List<YtStream> availableVideoStreams;
    private volatile java.util.@Nullable List<YtStream> availableAudioStreams;
    private volatile @Nullable YtStream currentVideoStream;
    private volatile @Nullable YtStream currentAudioStream;
    private volatile boolean initialized;
    private volatile boolean liveStream;
    private volatile boolean seekable;
    private volatile long durationHintNanos;
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
    // === FRAME PROCESSING ================================================================
    private @Nullable ByteBuffer frameBufferPool = null;

    // === CONSTRUCTOR =====================================================================
    public MediaPlayer(String youtubeUrl, String lang, Screen screen) {
        this.youtubeUrl = youtubeUrl;
        this.screen = screen;
        this.lang = lang;
        this.debugLabel = screen.getUUID() + "/" + Integer.toHexString(System.identityHashCode(this));
        Gst.init("MediaPlayer");
        INIT_EXECUTOR.submit(this::initialize);
    }

    private static void applyBrightnessToBuffer(ByteBuffer buffer, double brightness) {
        if (Math.abs(brightness - 1.0) < 1e-5) return;

        buffer.rewind();
        while (buffer.remaining() >= 4) {
            int r = buffer.get() & 0xFF;
            int g = buffer.get() & 0xFF;
            int b = buffer.get() & 0xFF;
            byte a = buffer.get();

            r = (int) Math.min(255, r * brightness);
            g = (int) Math.min(255, g * brightness);
            b = (int) Math.min(255, b * brightness);

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

    private static boolean isHlsStream(YtStream stream) {
        String protocol = String.valueOf(stream.getProtocol()).toLowerCase(Locale.ENGLISH);
        String container = String.valueOf(stream.getContainer()).toLowerCase(Locale.ENGLISH);
        String url = stream.getUrl().toLowerCase(Locale.ENGLISH);
        return protocol.contains("m3u8") || container.contains("m3u8") || url.contains(".m3u8");
    }

    private static boolean isDashStream(YtStream stream) {
        String protocol = String.valueOf(stream.getProtocol()).toLowerCase(Locale.ENGLISH);
        String url = stream.getUrl().toLowerCase(Locale.ENGLISH);
        return protocol.contains("dash_manifest") || url.contains(".mpd");
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

    private void bindVideoToAudioClock(Pipeline audio, @Nullable Pipeline video) {
        if (video == null) return;
        if (audio == video) return;
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
            if (!initialized || audio == null || !seekable) return;
            long cur = audio.queryPosition(Format.TIME);
            long tgt = Math.max(0, cur + (long) (s * 1e9));
            long dur = Math.max(0, getDuration() - 1);
            if (dur <= 0) return;
            doSeek(Math.min(tgt, dur), true);
        });
    }

    public long getCurrentTime() {
        Pipeline audio = audioPipeline;
        return initialized && audio != null ? audio.queryPosition(Format.TIME) : 0;
    }

    public long getDuration() {
        if (liveStream) return 0L;
        Pipeline audio = audioPipeline;
        if (!initialized || audio == null) return durationHintNanos;
        long duration = audio.queryDuration(Format.TIME);
        return duration > 0L ? duration : durationHintNanos;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isLive() {
        return liveStream;
    }

    public boolean canSeek() {
        return initialized && seekable;
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
            if (all.isEmpty()) {
                LoggingManager.error("No streams available");
                screen.errored = true;
                return;
            }

            liveStream = all.stream().anyMatch(YtStream::isLive);
            seekable = all.stream().anyMatch(YtStream::isSeekable);
            durationHintNanos = all.stream()
                    .mapToLong(YtStream::getDurationNanos)
                    .max()
                    .orElse(0L);

            availableVideoStreams = all
                    .stream()
                    .filter(YtStream::hasVideo)
                    .toList();
            availableAudioStreams = all
                    .stream()
                    .filter(YtStream::hasAudio)
                    .toList();

            int requestedQuality = parseQualityValue(screen.getQuality(), 720);
            Optional<YtStream> videoOpt = pickVideo(
                    requestedQuality
            ).or(() -> availableVideoStreams.stream().findFirst());
            Optional<YtStream> audioOpt = pickAudio(availableAudioStreams, videoOpt.orElse(null));
            if (videoOpt.isEmpty() || audioOpt.isEmpty()) {
                LoggingManager.error("No streams available");
                screen.errored = true;
                return;
            }

            currentVideoStream = videoOpt.get();
            currentAudioStream = audioOpt.get();
            lastQuality = parseQuality(currentVideoStream);

            if (shouldUseSharedAvPipeline(currentVideoStream, currentAudioStream)) {
                sharedAvPipeline = true;
                audioPipeline = buildSharedAvPipeline(currentVideoStream);
                videoPipeline = null;
            } else {
                sharedAvPipeline = false;
                audioPipeline = buildAudioPipeline(currentAudioStream);
                videoPipeline = buildVideoPipeline(currentVideoStream);
            }

            Pipeline readyPipeline = videoPipeline != null ? videoPipeline : audioPipeline;
            if (readyPipeline != null) {
                readyPipeline.getState(0);
            }
            initialized = true;
            if (DEBUG) {
                LoggingManager.info("[MP debug " + debugLabel + "] picked video: " + currentVideoStream);
                LoggingManager.info("[MP debug " + debugLabel + "] picked audio: " + currentAudioStream);
                LoggingManager.info("[MP debug " + debugLabel + "] live=" + liveStream
                        + " seekable=" + seekable
                        + " duration=" + durationHintNanos
                        + " sharedAv=" + sharedAvPipeline);
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

    private Pipeline buildVideoPipeline(YtStream stream) {
        long rawQueueTime = stream.isLive() ? LIVE_RAW_QUEUE_TIME_NS : RAW_QUEUE_TIME_NS;
        String desc = buildSourceChain(stream, "httpQueueV", HTTP_QUEUE_BYTES, 131072)
                + " ! decodebin"
                + " ! queue name=rawQueueV max-size-buffers=4 max-size-bytes=0 max-size-time=" + rawQueueTime
                + " ! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink";
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        configureVideoSink((AppSink) p.getElementByName("videosink"));
        p.pause();

        Bus bus = p.getBus();
        bus.connect((Bus.ERROR) (source, code, message) -> {
            LoggingManager.error(
                    "[MediaPlayer V " + debugLabel + "] [ERROR] GStreamer: " + message
            );
            screen.errored = true;
            initialized = false;
        });
        return p;
    }

    private Pipeline buildAudioPipeline(YtStream stream) {
        long rawQueueTime = stream.isLive() ? LIVE_RAW_QUEUE_TIME_NS : RAW_QUEUE_TIME_NS;
        String desc = buildSourceChain(stream, "httpQueueA", HTTP_QUEUE_BYTES_AUDIO, 65536)
                + " ! decodebin"
                + " ! queue name=rawQueueA max-size-buffers=0 max-size-bytes=0 max-size-time=" + rawQueueTime
                + " ! audioconvert ! audioresample"
                + " ! volume name=volumeElement volume=1"
                + " ! audioamplify name=ampElement amplification=" + currentVolume
                + " ! autoaudiosink";
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        p.getBus().connect(
                (Bus.ERROR) (source, code, message) ->
                        LoggingManager.error(
                                "[MediaPlayer A " + debugLabel + "] [ERROR] GStreamer: " + message
                        )
        );

        p.getBus().connect(
                (Bus.EOS) source -> {
                    if (liveStream) return;
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

    private Pipeline buildSharedAvPipeline(YtStream stream) {
        long rawQueueTime = stream.isLive() ? LIVE_RAW_QUEUE_TIME_NS : RAW_QUEUE_TIME_NS;
        String desc = buildSourceChain(stream, "httpQueueAV", HTTP_QUEUE_BYTES, 131072)
                + " ! decodebin name=avDecoder"
                + " avDecoder. ! queue name=rawQueueV max-size-buffers=4 max-size-bytes=0 max-size-time=" + rawQueueTime
                + " ! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink"
                + " avDecoder. ! queue name=rawQueueA max-size-buffers=0 max-size-bytes=0 max-size-time=" + rawQueueTime
                + " ! audioconvert ! audioresample"
                + " ! volume name=volumeElement volume=1"
                + " ! audioamplify name=ampElement amplification=" + currentVolume
                + " ! autoaudiosink";
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        configureVideoSink((AppSink) p.getElementByName("videosink"));
        p.pause();

        Bus bus = p.getBus();
        bus.connect(
                (Bus.ERROR) (source, code, message) -> {
                    LoggingManager.error(
                            "[MediaPlayer AV " + debugLabel + "] [ERROR] GStreamer: " + message
                    );
                    screen.errored = true;
                    initialized = false;
                }
        );
        bus.connect(
                (Bus.EOS) source -> {
                    if (liveStream) return;
                    safeExecute(() -> {
                        Pipeline audio = audioPipeline;
                        if (audio == null) return;
                        audio.seekSimple(
                                Format.TIME,
                                EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                                0L
                        );
                        audio.play();
                    });
                }
        );
        return p;
    }

    private boolean shouldUseSharedAvPipeline(YtStream video, YtStream audio) {
        return liveStream
                && !seekable
                && video.isMuxed()
                && audio.isMuxed()
                && video.getUrl().equals(audio.getUrl());
    }

    private String buildSourceChain(YtStream stream, String queueName, long queueBytes, int blockSize) {
        long queueTime = stream.isLive() ? LIVE_HTTP_QUEUE_TIME_NS : HTTP_QUEUE_TIME_NS;
        StringBuilder desc = new StringBuilder();
        desc.append("souphttpsrc location=\"")
                .append(stream.getUrl())
                .append("\" user-agent=\"")
                .append(USER_AGENT_V)
                .append("\" extra-headers=\"origin:https://www.youtube.com\\nreferer:https://www.youtube.com\\n\" ")
                .append("blocksize=")
                .append(blockSize)
                .append(" retries=5 timeout=15")
                .append(" ! queue2 name=")
                .append(queueName)
                .append(" max-size-bytes=")
                .append(queueBytes)
                .append(" max-size-time=")
                .append(queueTime)
                .append(" max-size-buffers=0");

        if (isDashStream(stream)) {
            desc.append(" ! dashdemux");
        } else if (isHlsStream(stream)) {
            desc.append(" ! hlsdemux");
        }

        return desc.toString();
    }

    private void configureVideoSink(AppSink sink) {
        sink.set("emit-signals", true);
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
        if (!sharedAvPipeline) {
            bindVideoToAudioClock(audio, video);
        }

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
        if (audio != null) audio.pause();
        if (video != null) video.pause();
    }

    private void doStop() {
        boolean shared = sharedAvPipeline;
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
        if (shared) {
            safeStopAndDispose(audioPipeline);
        } else {
            safeStopAndDispose(videoPipeline);
            safeStopAndDispose(audioPipeline);
        }
        videoPipeline = null;
        audioPipeline = null;
        sharedAvPipeline = false;
        currentVideoStream = null;
        currentAudioStream = null;
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
            Pipeline a = audioPipeline;
            Pipeline v = videoPipeline != null ? videoPipeline : a;
            if (v == null || a == null) return;
            long inN = samplesIn.getAndSet(0);
            long outN = framesToGpu.getAndSet(0);
            long dropN = framesDropped.getAndSet(0);
            double seconds = STATS_INTERVAL_MS / 1000.0;
            String vState = String.valueOf(v.getState(0));
            String aState = String.valueOf(a.getState(0));
            long aPos = a.queryPosition(Format.TIME);
            long vPos = sharedAvPipeline ? aPos : v.queryPosition(Format.TIME);
            long drift = (aPos - vPos) / 1_000_000L;
            LoggingManager.info(String.format(
                    "[MP debug %s] decode=%.1ffps gpu=%.1ffps dropped=%.1f/s"
                            + " | a/v=%s/%s drift=%dms"
                            + " | %s",
                    debugLabel,
                    inN / seconds, outN / seconds, dropN / seconds,
                    aState, vState, drift, queueLevels(v, a)
            ));
        } catch (Throwable t) {
        }
    }

    private String queueLevels(Pipeline v, Pipeline a) {
        StringBuilder sb = new StringBuilder();
        appendLevel(sb, v, "httpQueueAV");
        appendLevel(sb, v, "httpQueueV");
        appendLevel(sb, v, "demuxQueueV");
        appendLevel(sb, v, "rawQueueV");
        if (a != v) {
            appendLevel(sb, a, "httpQueueA");
        }
        appendLevel(sb, a, "rawQueueA");
        return sb.toString();
    }

    private void doSeek(long nanos, boolean b) {
        seekUnified(nanos, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE));
        if (b) screen.afterSeek();
    }

    private void doSeekFast(long nanos) {
        seekUnified(nanos, EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT));
    }

    // Seek using GStreamer's full seek API. Audio is seeked in-place (works
    // reliably). Video pipeline is REBUILT — souphttpsrc cannot reliably seek
    // on YouTube's fragmented mp4_dash streams once playback has started.
    // Building a fresh pipeline issues a clean HTTP connection, then we seek
    // the NEW pipeline before it starts playing.
    private void seekUnified(long nanos, EnumSet<SeekFlags> flags) {
        if (!initialized || !seekable) return;
        Pipeline audio = audioPipeline;
        if (audio == null) return;
        Pipeline video = videoPipeline;
        YtStream vidStream = currentVideoStream;

        // Drop pre-seek frames
        synchronized (frameLock) {
            frameReady = false;
            preparedBuffer = null;
            preparedBufferSize = 0;
        }

        // Pause audio before seeking
        audio.pause();

        // Seek audio using the full API
        boolean audioOk = audio.seek(1.0, Format.TIME, flags,
                SeekType.SET, nanos, SeekType.NONE, -1);
        audio.getState(SEEK_STATE_WAIT_NS);

        if (video != null && !sharedAvPipeline && vidStream != null) {
            // Rebuild the video pipeline instead of seeking in-place
            Pipeline newVid = buildVideoPipeline(vidStream);
            bindVideoToAudioClock(audio, newVid);
            newVid.pause();
            newVid.getState(SEEK_STATE_WAIT_NS);

            // Seek the fresh pipeline to the target position
            EnumSet<SeekFlags> videoFlags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE);
            newVid.seek(1.0, Format.TIME, videoFlags,
                    SeekType.SET, nanos, SeekType.NONE, -1);
            newVid.getState(SEEK_STATE_WAIT_NS);

            if (DEBUG) {
                LoggingManager.info("[MP debug " + debugLabel + "] seek audio=" + audioOk
                        + " video=rebuild target=" + (nanos / 1_000_000L) + "ms");
            }

            // Swap pipelines
            videoPipeline = newVid;
            if (!screen.getPaused()) newVid.play();
            safeStopAndDispose(video);
        } else if (sharedAvPipeline) {
            // For shared A/V pipeline, just seek – audio and video are in one pipeline
            if (DEBUG) {
                LoggingManager.info("[MP debug " + debugLabel + "] seek shared pipeline"
                        + " audio=" + audioOk + " target=" + (nanos / 1_000_000L) + "ms");
            }
        }

        if (!screen.getPaused()) {
            audio.play();
        }

        scheduleResync();
    }

    // Sync correction.
    private void scheduleResync() {
        if (sharedAvPipeline) return;
        gstExecutor.execute(() -> {
            try {
                Thread.sleep(POST_SEEK_RESYNC_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            safeExecute(this::checkAndFixSync);
        });
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
                        Comparator
                                .comparingInt((YtStream s) -> Math.abs(parseQuality(s) - target))
                                .thenComparingInt(s -> s.hasAudio() ? 1 : 0)
                );
    }

    private Optional<YtStream> pickAudio(
            java.util.List<YtStream> audioStreams,
            @Nullable YtStream chosenVideo
    ) {
        Optional<YtStream> preferred = audioStreams
                .stream()
                .filter(s -> !s.hasVideo())
                .filter(this::matchesRequestedLanguage)
                .reduce((f, n) -> n);
        if (preferred.isPresent()) return preferred;

        preferred = audioStreams
                .stream()
                .filter(s -> !s.hasVideo())
                .reduce((f, n) -> n);
        if (preferred.isPresent()) return preferred;

        if (chosenVideo != null && chosenVideo.hasAudio()) {
            return Optional.of(chosenVideo);
        }

        preferred = audioStreams
                .stream()
                .filter(this::matchesRequestedLanguage)
                .reduce((f, n) -> n);
        return preferred.isPresent() ? preferred : audioStreams.stream().findFirst();
    }

    private boolean matchesRequestedLanguage(YtStream stream) {
        return (stream.getAudioTrackId() != null && stream.getAudioTrackId().contains(lang))
                || (stream.getAudioTrackName() != null && stream.getAudioTrackName().contains(lang));
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

        if (liveStream) {
            changeLiveQuality(chosen);
            return;
        }

        Minecraft.getInstance().execute(screen::reloadTexture);

        // Build the new video pipeline first while audio keeps playing
        Pipeline newVid = buildVideoPipeline(chosen);
        bindVideoToAudioClock(audio, newVid);
        newVid.pause();
        newVid.getState(SEEK_STATE_WAIT_NS);

        // Query audio position as late as possible
        long pos = audio.queryPosition(Format.TIME);
        if (pos < 0) pos = 0;

        // Seek the new video to the audio position using the full seek API.
        // seekSimple fails on fragmented mp4_dash; the full API with SeekType.SET
        // works reliably on a freshly built pipeline.
        EnumSet<SeekFlags> flags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE);
        newVid.seek(1.0, Format.TIME, flags, SeekType.SET, pos, SeekType.NONE, -1);
        newVid.getState(SEEK_STATE_WAIT_NS);

        // Drop any pre-swap video frame
        synchronized (frameLock) {
            frameReady = false;
            preparedBuffer = null;
            preparedBufferSize = 0;
        }

        Pipeline oldVid = videoPipeline;
        videoPipeline = newVid;
        currentVideoStream = chosen;
        lastQuality = parseQuality(chosen);
        if (!screen.getPaused()) newVid.play();
        safeStopAndDispose(oldVid);
    }

    private void changeLiveQuality(YtStream chosenVideo) {
        java.util.List<YtStream> audioStreams = availableAudioStreams;
        if (audioStreams == null || audioStreams.isEmpty()) return;

        Optional<YtStream> audioOpt = pickAudio(audioStreams, chosenVideo);
        if (audioOpt.isEmpty()) return;

        YtStream chosenAudio = audioOpt.get();
        boolean newSharedPipeline = shouldUseSharedAvPipeline(chosenVideo, chosenAudio);
        boolean shouldPlay = !screen.getPaused();
        Pipeline oldAudio = audioPipeline;
        Pipeline oldVideo = videoPipeline;
        boolean oldSharedPipeline = sharedAvPipeline;
        if (oldAudio == null) return;

        if (oldVideo != null) oldVideo.pause();
        oldAudio.pause();

        Pipeline newAudio = null;
        Pipeline newVideo = null;
        try {
            if (newSharedPipeline) {
                newAudio = buildSharedAvPipeline(chosenVideo);
            } else {
                newAudio = buildAudioPipeline(chosenAudio);
                newVideo = buildVideoPipeline(chosenVideo);
                bindVideoToAudioClock(newAudio, newVideo);
            }

            Pipeline readyPipeline = newVideo != null ? newVideo : newAudio;
            if (readyPipeline == null) {
                throw new IllegalStateException("Failed to build replacement pipeline");
            }
            readyPipeline.getState(0);

            Minecraft.getInstance().execute(screen::reloadTexture);

            audioPipeline = newAudio;
            videoPipeline = newVideo;
            sharedAvPipeline = newSharedPipeline;
            currentAudioStream = chosenAudio;
            currentVideoStream = chosenVideo;
            lastQuality = parseQuality(chosenVideo);
            applyVolume();

            if (shouldPlay) {
                newAudio.play();
                if (newVideo != null) newVideo.play();
            }

            if (oldSharedPipeline) {
                safeStopAndDispose(oldAudio);
            } else {
                safeStopAndDispose(oldVideo);
                safeStopAndDispose(oldAudio);
            }
        } catch (Exception e) {
            safeStopAndDispose(newVideo);
            safeStopAndDispose(newAudio);
            LoggingManager.warn("[MP debug " + debugLabel + "] Failed to change live quality", e);
            if (shouldPlay) {
                oldAudio.play();
                if (oldVideo != null) oldVideo.play();
            }
        }
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

        // Periodic sync check – only when playing
        if (!screen.getPaused()) {
            syncCheckCounter++;
            if (syncCheckCounter >= SYNC_CHECK_INTERVAL) {
                syncCheckCounter = 0;
                safeExecute(this::checkAndFixSync);
            }
        }
    }

    private void checkAndFixSync() {
        if (!initialized || !seekable || sharedAvPipeline || videoPipeline == null || audioPipeline == null) return;
        if (screen.getPaused()) return;

        try {
            long audioPos = audioPipeline.queryPosition(Format.TIME);
            long videoPos = videoPipeline.queryPosition(Format.TIME);
            long drift = Math.abs(audioPos - videoPos);

            if (drift > MAX_SYNC_DRIFT_NS) {
                EnumSet<SeekFlags> flags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT);
                videoPipeline.seekSimple(Format.TIME, flags, audioPos);
            }
        } catch (Exception ignored) {
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
