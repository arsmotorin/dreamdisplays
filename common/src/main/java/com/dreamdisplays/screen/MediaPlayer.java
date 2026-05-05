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
import org.freedesktop.gstreamer.elements.PlayBin;
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
 * Integrates with Minecraft's rendering system to display video frames on in-game screens.
 */
// TODO: replace with FFmpeg solution in version 2.0.0
@NullMarked
public class MediaPlayer {

    public static final boolean DEBUG =
            Boolean.getBoolean("dreamdisplays.debug")
                    || "1".equals(System.getenv("DREAMDISPLAYS_DEBUG"))
                    || "true".equalsIgnoreCase(System.getenv("DREAMDISPLAYS_DEBUG"));

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
    private final String youtubeUrl;
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

    private volatile @Nullable ScheduledExecutorService statsExecutor;
    private volatile double currentVolume = Initializer.config.defaultDisplayVolume;
    private volatile @Nullable Pipeline pipeline;
    private volatile java.util.@Nullable List<YtStream> availableVideoStreams;
    private volatile java.util.@Nullable List<YtStream> availableAudioStreams;
    private volatile @Nullable YtStream currentVideoStream;
    private volatile @Nullable YtStream currentAudioStream;
    private volatile boolean initialized;
    private volatile boolean liveStream;
    private volatile boolean seekable;
    private volatile long durationHintNanos;
    private int lastQuality;

    private volatile @Nullable ByteBuffer currentFrameBuffer;
    private volatile int currentFrameWidth = 0;
    private volatile int currentFrameHeight = 0;
    private volatile @Nullable ByteBuffer preparedBuffer;
    private int preparedBufferSize = 0;
    private volatile int lastTexW = 0, lastTexH = 0;
    private volatile int preparedW = 0, preparedH = 0;
    private volatile double userVolume = (Initializer.config.defaultDisplayVolume);
    private volatile double lastAttenuation = 1.0;
    private volatile double brightness = 1.0;
    private volatile boolean frameReady = false;
    private @Nullable ByteBuffer frameBufferPool = null;

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

    public void play() {
        safeExecute(this::doPlay);
    }

    public void pause() {
        safeExecute(this::doPause);
    }

    public void seekTo(long nanos, boolean b) {
        safeExecute(() -> doSeek(nanos, b));
    }

    public void seekRelative(double s) {
        safeExecute(() -> {
            Pipeline p = pipeline;
            if (!initialized || p == null || !seekable) return;
            long cur = p.queryPosition(Format.TIME);
            long tgt = Math.max(0, cur + (long) (s * 1e9));
            long dur = Math.max(0, getDuration() - 1);
            if (dur <= 0) return;
            doSeek(Math.min(tgt, dur), true);
        });
    }

    public long getCurrentTime() {
        Pipeline p = pipeline;
        return initialized && p != null ? p.queryPosition(Format.TIME) : 0;
    }

    public long getDuration() {
        if (liveStream) return 0L;
        Pipeline p = pipeline;
        if (!initialized || p == null) return durationHintNanos;
        long duration = p.queryDuration(Format.TIME);
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

    private void initialize() {
        try {
            String videoId = com.dreamdisplays.util.Utils.extractVideoId(youtubeUrl);
            if (videoId == null || videoId.isEmpty()) {
                LoggingManager.error("Could not extract video ID from URL: " + youtubeUrl);
                screen.errored = true;
                return;
            }

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

            availableVideoStreams = all.stream().filter(YtStream::hasVideo).toList();
            availableAudioStreams = all.stream().filter(YtStream::hasAudio).toList();

            int requestedQuality = parseQualityValue(screen.getQuality(), 720);
            Optional<YtStream> videoOpt = pickVideo(requestedQuality)
                    .or(() -> availableVideoStreams.stream().findFirst());
            Optional<YtStream> audioOpt = pickAudio(availableAudioStreams, videoOpt.orElse(null));
            if (videoOpt.isEmpty() || audioOpt.isEmpty()) {
                LoggingManager.error("No streams available");
                screen.errored = true;
                return;
            }

            currentVideoStream = videoOpt.get();
            currentAudioStream = audioOpt.get();
            lastQuality = parseQuality(currentVideoStream);

            pipeline = buildPipeline(currentVideoStream, currentAudioStream);

            Pipeline p = pipeline;
            if (p != null) {
                p.getState(0);
            }
            initialized = true;
            if (DEBUG) {
                LoggingManager.info("[MP debug " + debugLabel + "] picked video: " + currentVideoStream);
                LoggingManager.info("[MP debug " + debugLabel + "] picked audio: " + currentAudioStream);
                LoggingManager.info("[MP debug " + debugLabel + "] live=" + liveStream
                        + " seekable=" + seekable
                        + " duration=" + durationHintNanos);
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

    private Pipeline buildPipeline(YtStream videoStream, YtStream audioStream) {
        YtStream stream = pickPlayableStream(videoStream, audioStream);
        PlayBin p = new PlayBin("mediaPlaybin");
        p.setVideoSink(buildPlaybinVideoSink());
        p.setURI(java.net.URI.create(stream.getUrl()));
        p.connect((PlayBin.SOURCE_SETUP) (playbin, source) -> configureHttpSource(source, 131072));
        p.pause();
        PlayBin self = p;

        Bus bus = p.getBus();
        bus.connect((Bus.ERROR) (source, code, message) -> {
            if (pipeline != self) return;
            LoggingManager.error("[MediaPlayer playbin " + debugLabel + "] GStreamer error: " + message);
            screen.errored = true;
            initialized = false;
        });
        bus.connect((Bus.EOS) source -> {
            if (liveStream) return;
            safeExecute(() -> {
                if (pipeline != self) return;
                self.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), 0L);
                if (!screen.getPaused()) {
                    self.play();
                }
            });
        });
        return p;
    }

    private AppSink buildPlaybinVideoSink() {
        AppSink appSink = new AppSink("videosink");
        appSink.setCaps(Caps.fromString("video/x-raw,format=RGBA"));
        configureVideoSink(appSink);
        return appSink;
    }

    private YtStream pickPlayableStream(YtStream videoStream, YtStream audioStream) {
        if (videoStream.isMuxed()) return videoStream;
        if (audioStream.isMuxed() && audioStream.hasVideo()) return audioStream;
        java.util.List<YtStream> streams = availableVideoStreams;
        if (streams == null) return videoStream;
        int target = parseQuality(videoStream);
        return streams.stream()
                .filter(YtStream::isMuxed)
                .filter(YtStream::hasVideo)
                .min(Comparator.comparingInt((YtStream s) -> Math.abs(parseQuality(s) - target)))
                .orElse(videoStream);
    }

    private void configureHttpSource(Element source, int blockSize) {
        setElementPropertyIfPresent(source, "user-agent", USER_AGENT_V);
        setElementPropertyIfPresent(source, "blocksize", blockSize);
        setElementPropertyIfPresent(source, "retries", 5);
        setElementPropertyIfPresent(source, "timeout", 15);
    }

    private void setElementPropertyIfPresent(Element element, String property, Object value) {
        try {
            element.set(property, value);
        } catch (Throwable ignored) {
        }
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

    private void doPlay() {
        if (!initialized) return;
        Pipeline p = pipeline;
        if (p == null) return;
        if (!screen.getPaused()) {
            p.play();
        }
    }

    private void doPause() {
        if (!initialized) return;
        Pipeline p = pipeline;
        if (p != null) p.pause();
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
        safeStopAndDispose(pipeline);
        pipeline = null;
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
            Pipeline p = pipeline;
            if (p == null) return;
            long inN = samplesIn.getAndSet(0);
            long outN = framesToGpu.getAndSet(0);
            long dropN = framesDropped.getAndSet(0);
            double seconds = STATS_INTERVAL_MS / 1000.0;
            String pState = String.valueOf(p.getState(0));
            long pos = p.queryPosition(Format.TIME);
            LoggingManager.info(String.format(
                    "[MP debug %s] decode=%.1ffps gpu=%.1ffps dropped=%.1f/s"
                            + " | state=%s pos=%dms"
                            + " | %s",
                    debugLabel,
                    inN / seconds, outN / seconds, dropN / seconds,
                    pState, pos / 1_000_000L, queueLevels(p)
            ));
        } catch (Throwable t) {
        }
    }

    private String queueLevels(Pipeline p) {
        StringBuilder sb = new StringBuilder();
        appendLevel(sb, p, "httpQueueAV");
        appendLevel(sb, p, "httpQueueV");
        appendLevel(sb, p, "rawQueueV");
        appendLevel(sb, p, "httpQueueA");
        appendLevel(sb, p, "rawQueueA");
        return sb.toString();
    }

    private void doSeek(long nanos, boolean b) {
        if (!initialized || !seekable) return;
        YtStream vs = currentVideoStream;
        YtStream as = currentAudioStream;
        if (vs == null || as == null) return;
        boolean rebuilt = rebuildAtPosition(vs, as, nanos, !screen.getPaused());
        if (rebuilt && b) screen.afterSeek();
    }

    private void doSeekFast(long nanos) {
        doSeek(nanos, false);
    }

    // Tear down the current pipeline and build a fresh HTTP connection before seeking.
    // Do not rewrite YouTube's sq/rn URL parameters here: those sequence numbers are
    // not a stable time index, and opening a partial segment can make decoded PTS
    // inconsistent with the absolute seek target.
    private boolean rebuildAtPosition(YtStream video, YtStream audio, long nanos, boolean play) {
        Pipeline old = pipeline;
        YtStream oldVideoStream = currentVideoStream;
        YtStream oldAudioStream = currentAudioStream;
        if (old != null) {
            old.pause();
        }

        synchronized (frameLock) {
            frameReady = false;
            preparedBuffer = null;
            preparedBufferSize = 0;
        }

        Pipeline newPipeline;
        try {
            newPipeline = buildPipeline(video, audio);
            newPipeline.getState(SEEK_STATE_WAIT_NS);
        } catch (Exception e) {
            LoggingManager.warn("[MP debug " + debugLabel + "] rebuildAtPosition: failed to build pipeline", e);
            resumeOldPipeline(old, play);
            return false;
        }

        pipeline = newPipeline;
        currentVideoStream = video;
        currentAudioStream = audio;
        applyVolume();

        boolean seekOk = true;
        try {
            if (seekable && nanos > 0) {
                newPipeline.play();
                newPipeline.getState(SEEK_STATE_WAIT_NS);
                seekOk = newPipeline.seek(1.0, Format.TIME,
                        EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                        SeekType.SET, nanos, SeekType.NONE, -1);
                if (!seekOk) {
                    seekOk = newPipeline.seek(1.0, Format.TIME,
                            EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT),
                            SeekType.SET, nanos, SeekType.NONE, -1);
                }
                if (!seekOk) {
                    pipeline = old;
                    currentVideoStream = oldVideoStream;
                    currentAudioStream = oldAudioStream;
                    safeStopAndDispose(newPipeline);
                    resumeOldPipeline(old, play);
                    LoggingManager.warn("[MP debug " + debugLabel + "] rebuildAtPosition: replacement pipeline refused seek to "
                            + (nanos / 1_000_000L) + "ms");
                    return false;
                }
                newPipeline.getState(SEEK_STATE_WAIT_NS);
            }
        } catch (Exception e) {
            pipeline = old;
            currentVideoStream = oldVideoStream;
            currentAudioStream = oldAudioStream;
            safeStopAndDispose(newPipeline);
            resumeOldPipeline(old, play);
            LoggingManager.warn("[MP debug " + debugLabel + "] rebuildAtPosition: failed to seek replacement pipeline", e);
            return false;
        }

        if (play) {
            newPipeline.play();
        } else {
            newPipeline.pause();
        }

        synchronized (frameLock) {
            frameReady = false;
        }

        safeStopAndDispose(old);

        if (DEBUG) {
            LoggingManager.info("[MP debug " + debugLabel + "] rebuildAtPosition target="
                    + (nanos / 1_000_000L) + "ms seekOk=" + seekOk
                    + " video=" + video.getResolution());
        }
        return true;
    }

    private void resumeOldPipeline(@Nullable Pipeline old, boolean play) {
        if (play && old != null && pipeline == old && !terminated.get()) {
            old.play();
        }
    }

    private void applyVolume() {
        Pipeline p = pipeline;
        if (!initialized || p == null) return;
        if (p instanceof PlayBin playBin) {
            playBin.set("volume", Math.max(0.0D, currentVolume));
            return;
        }
        Element v = p.getElementByName("volumeElement");
        if (v != null) v.set("volume", 1);
        Element a = p.getElementByName("ampElement");
        if (a != null) a.set("amplification", currentVolume);
    }

    private Optional<YtStream> pickVideo(int target) {
        return availableVideoStreams
                .stream()
                .filter(s -> s.getResolution() != null)
                .min(
                        Comparator
                                .comparingInt((YtStream s) -> Math.abs(parseQuality(s) - target))
                                .thenComparingInt(s -> s.isMuxed() ? 0 : 1)
                                .thenComparingInt(s -> s.hasAudio() ? 0 : 1)
                );
    }

    private Optional<YtStream> pickAudio(
            java.util.List<YtStream> audioStreams,
            @Nullable YtStream chosenVideo
    ) {
        LoggingManager.info("[pickAudio] lang='" + lang + "' candidates: " +
                audioStreams.stream()
                        .filter(s -> !s.hasVideo())
                        .map(s -> "trackId=" + s.getAudioTrackId() + " note=" + s.getAudioTrackName())
                        .collect(Collectors.joining(", ")));

        Optional<YtStream> preferred = audioStreams
                .stream()
                .filter(s -> !s.hasVideo())
                .filter(this::matchesRequestedLanguage)
                .reduce((f, n) -> n);
        if (preferred.isPresent()) return preferred;

        preferred = audioStreams
                .stream()
                .filter(s -> !s.hasVideo())
                .filter(s -> s.getAudioTrackId() == null || s.getAudioTrackId().equals("und"))
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
        Pipeline p = pipeline;
        YtStream current = currentVideoStream;
        YtStream currentAudio = currentAudioStream;
        if (p == null || current == null || currentAudio == null) return;

        int target;
        try {
            target = Integer.parseInt(desired.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return;
        }
        if (target == lastQuality) return;

        Optional<YtStream> best = pickVideo(target);
        if (best.isEmpty()) return;
        YtStream chosenVideo = best.get();
        if (chosenVideo.getUrl().equals(current.getUrl())) return;

        YtStream chosenAudio = currentAudio;
        if (liveStream && availableAudioStreams != null) {
            Optional<YtStream> audioOpt = pickAudio(availableAudioStreams, chosenVideo);
            if (audioOpt.isPresent()) {
                chosenAudio = audioOpt.get();
            }
        }

        long pos = liveStream ? 0L : Math.max(0L, p.queryPosition(Format.TIME));
        Minecraft.getInstance().execute(screen::reloadTexture);
        if (rebuildAtPosition(chosenVideo, chosenAudio, pos, !screen.getPaused())) {
            lastQuality = parseQuality(chosenVideo);
        }
    }

    public void tick(BlockPos playerPos, double maxRadius) {
        if (!initialized) return;

        double dist = screen.getDistanceToScreen(playerPos);
        double attenuation = Math.pow(1.0 - Math.min(1.0, dist / maxRadius), 2);
        if (Math.abs(attenuation - lastAttenuation) > 1e-5) {
            lastAttenuation = attenuation;
            currentVolume = userVolume * attenuation;
            safeExecute(this::applyVolume);
        }
    }

    private void safeExecute(Runnable action) {
        if (!terminated.get() && !gstExecutor.isShutdown()) {
            try {
                gstExecutor.submit(action);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }
}
