package com.dreamdisplays.screen;

import com.dreamdisplays.Initializer;
import com.github.felipeucelli.javatube.Stream;
import com.github.felipeucelli.javatube.Youtube;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Media player for streaming YouTube videos using GStreamer.
 * Handles video and audio playback, quality selection, volume control, and frame processing.
 *
 * Integrates with Minecraft's rendering system to display video frames on in-game screens.
 */
// TODO: replace with FFmpeg solution in version 2.0.0
@NullMarked
public class MediaPlayer {

    // === CONSTANTS =======================================================================
    private static final String MIME_VIDEO = "video/webm";
    private static final String MIME_AUDIO = "audio/webm";
    private static final String USER_AGENT_V = "ANDROID_VR";
    private static final String USER_AGENT_A = "ANDROID_TESTSUITE";
    private static final ExecutorService INIT_EXECUTOR =
            Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "MediaPlayer-init")
            );
    public static boolean captureSamples = true;
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
    private final Screen screen;
    private volatile double currentVolume;
    // === GST OBJECTS =====================================================================
    private volatile @Nullable Pipeline videoPipeline;
    private volatile @Nullable Pipeline audioPipeline;
    private volatile java.util.@Nullable List<Stream> availableVideoStreams;
    private volatile @Nullable Stream currentVideoStream;
    private volatile boolean initialized;
    private int lastQuality;
    // === FRAME BUFFERS ===================================================================
    private volatile @Nullable ByteBuffer currentFrameBuffer;
    private volatile int currentFrameWidth = 0;
    private volatile int currentFrameHeight = 0;
    private volatile @Nullable ByteBuffer preparedBuffer;
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
    private static final int SYNC_CHECK_INTERVAL = 100; // Check sync every 100 ticks (~5 seconds)
    private static final long MAX_SYNC_DRIFT_NS = 500_000_000L; // 500ms max drift before resync

    // Buffer system
    private @Nullable ByteBuffer convertBuffer = null;
    private int convertBufferSize = 0;
    private @Nullable ByteBuffer scaleBuffer = null;
    private int scaleBufferSize = 0;

    // Frame rate limiting
    private volatile long lastFrameTime = 0;
    private static final long MIN_FRAME_INTERVAL_NS = 16_666_667L; // ~60fps max

    // === CONSTRUCTOR =====================================================================
    public MediaPlayer(String youtubeUrl, String lang, Screen screen) {
        this.youtubeUrl = youtubeUrl;
        this.screen = screen;
        this.lang = lang;
        Gst.init("MediaPlayer");
        INIT_EXECUTOR.submit(this::initialize);
    }

    // === FRAME PROCESSING ================================================================
    private static ByteBuffer sampleToBuffer(Sample sample) {
        Buffer buf = sample.getBuffer();
        ByteBuffer bb = buf.map(false);

        // If buffer is already in native order, use it directly
        if (bb.order() == ByteOrder.nativeOrder()) {
            ByteBuffer result = ByteBuffer.allocateDirect(bb.remaining()).order(
                    ByteOrder.nativeOrder()
            );
            result.put(bb);
            result.flip();
            buf.unmap();
            return result;
        }

        // Otherwise convert to native order
        ByteBuffer result = ByteBuffer.allocateDirect(bb.remaining()).order(
                ByteOrder.nativeOrder()
        );
        bb.rewind();
        for (int i = 0; i < bb.remaining(); i++) {
            result.put(bb.get());
        }
        result.flip();
        buf.unmap();
        return result;
    }

    private ByteBuffer convertToRGBA(
            ByteBuffer srcBuffer,
            int width,
            int height
    ) {
        int size = width * height * 4;

        // Reuse buffer if possible, otherwise allocate new one
        if (convertBuffer == null || convertBufferSize < size) {
            convertBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
            convertBufferSize = size;
        }

        convertBuffer.clear();
        srcBuffer.rewind();
        convertBuffer.put(srcBuffer);
        convertBuffer.flip();

        return convertBuffer;
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

    private static int parseQuality(Stream stream) {
        try {
            return Integer.parseInt(stream.getResolution().replaceAll("\\D+", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
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

    public void seekRelative(double s) {
        safeExecute(() -> {
            if (!initialized) return;
            long cur = audioPipeline.queryPosition(Format.TIME);
            long tgt = Math.max(0, cur + (long) (s * 1e9));
            long dur = Math.max(
                    0,
                    audioPipeline.queryDuration(Format.TIME) - 1
            );
            doSeek(Math.min(tgt, dur), true);
        });
    }

    public long getCurrentTime() {
        return initialized ? audioPipeline.queryPosition(Format.TIME) : 0;
    }

    public long getDuration() {
        return initialized ? audioPipeline.queryDuration(Format.TIME) : 0;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void stop() {
        if (terminated.getAndSet(true)) return;
        safeExecute(() -> {
            doStop();
            gstExecutor.shutdown();
            frameExecutor.shutdown();
        });
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
        return preparedBuffer != null && preparedBuffer.remaining() > 0;
    }

    public void updateFrame(GpuTexture texture) {
        // Only update if a new frame is ready
        if (!frameReady || preparedBuffer == null) return;

        int w = screen.textureWidth,
                h = screen.textureHeight;
        if (w != preparedW || h != preparedH) return;

        CommandEncoder encoder =
                RenderSystem.getDevice().createCommandEncoder();

        // Rewind buffer to beginning.
        // Position was set to end of data after prepareBuffer()
        preparedBuffer.rewind();

        // Validate buffer has enough data
        int expectedSize = w * h * 4; // RGBA = 4 bytes per pixel
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

        // Mark frame as processed - don't process same frame again
        frameReady = false;
    }

    public java.util.List<Integer> getAvailableQualities() {
        if (
                !initialized || availableVideoStreams == null
        ) return Collections.emptyList();
        return availableVideoStreams
                .stream()
                .map(Stream::getResolution)
                .filter(Objects::nonNull)
                .map(r -> Integer.parseInt(r.replaceAll("\\D+", "")))
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
                return;
            }

            // Build proper YouTube URL with just the video ID
            String cleanUrl = "https://www.youtube.com/watch?v=" + videoId;

            Youtube yt = new Youtube(cleanUrl, USER_AGENT_V);
            java.util.List<Stream> all = yt.streams().getAll();

            Youtube ytA = new Youtube(cleanUrl, USER_AGENT_A);
            java.util.List<Stream> audioS = ytA.streams().getAll();

            availableVideoStreams = all
                    .stream()
                    .filter(s -> MIME_VIDEO.equals(s.getMimeType()))
                    .toList();

            Optional<Stream> videoOpt = pickVideo(
                    Integer.parseInt(screen.getQuality().replace("p", ""))
            ).or(() -> availableVideoStreams.stream().findFirst());
            Optional<Stream> audioOpt = audioS
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

                return;
            }

            currentVideoStream = videoOpt.get();
            lastQuality = parseQuality(currentVideoStream);

            audioPipeline = buildAudioPipeline(audioOpt.get().getUrl());
            videoPipeline = buildVideoPipeline(currentVideoStream.getUrl());

            videoPipeline.getState();
            initialized = true;
        } catch (Exception e) {
            LoggingManager.error("Failed to initialize MediaPlayer ", e);
        }
    }

    private Pipeline buildVideoPipeline(String uri) {
        String desc = String.join(
                " ",
                "souphttpsrc location=\"" + uri + "\"",
                "user-agent=\"" + USER_AGENT_V + "\"",
                "extra-headers=\"origin:https://www.youtube.com\\nreferer:https://www.youtube.com\\n\"",
                "! matroskademux ! decodebin ! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink"
        );
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        configureVideoSink((AppSink) p.getElementByName("videosink"));
        p.pause();

        Bus bus = p.getBus();
        final AtomicReference<Bus.ERROR> errRef = new AtomicReference<>();
        errRef.set((source, code, message) -> {
            LoggingManager.error(
                    "[MediaPlayer V] [ERROR] GStreamer: " + message
            );
            bus.disconnect(errRef.get());
            screen.errored = true;
            initialized = false;
        });
        bus.connect(errRef.get());
        return p;
    }

    private Pipeline buildAudioPipeline(String uri) {
        String desc =
                "souphttpsrc location=\"" +
                        uri +
                        "\" ! decodebin ! audioconvert ! audioresample " +
                        "! volume name=volumeElement volume=1 ! audioamplify name=ampElement amplification=" +
                        currentVolume +
                        " ! autoaudiosink";
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        p
                .getBus()
                .connect(
                        (Bus.ERROR) (source, code, message) ->
                                LoggingManager.error(
                                        "[MediaPlayer A] [ERROR] GStreamer: " + message
                                )
                );

        p
                .getBus()
                .connect(
                        (Bus.EOS) source -> {
                            safeExecute(() -> {
                                audioPipeline.seekSimple(
                                        Format.TIME,
                                        EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                                        0L
                                );
                                audioPipeline.play();

                                // If a video pipeline exists, seek it too
                                if (videoPipeline != null) {
                                    videoPipeline.seekSimple(
                                            Format.TIME,
                                            EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                                            0L
                                    );
                                    videoPipeline.play();
                                }
                            });
                        }
                );

        return p;
    }

    private void configureVideoSink(AppSink sink) {
        sink.set("emit-signals", true);
        sink.set("sync", true);
        sink.set("max-buffers", 1); // Drop old frames to stay in sync
        sink.set("drop", true); // Drop frames when queue is full
        sink.connect(
                (AppSink.NEW_SAMPLE) elem -> {
                    Sample s = elem.pullSample();
                    if (s == null || !captureSamples) return FlowReturn.OK;
                    try {
                        Structure st = s.getCaps().getStructure(0);
                        currentFrameWidth = st.getInteger("width");
                        currentFrameHeight = st.getInteger("height");
                        currentFrameBuffer = sampleToBuffer(s);
                        prepareBufferAsync();
                    } finally {
                        s.dispose();
                    }
                    return FlowReturn.OK;
                }
        );
    }

    private void prepareBufferAsync() {
        if (currentFrameBuffer == null) return;

        // Frame rate limiting - skip if too soon since last frame
        long now = System.nanoTime();
        if (now - lastFrameTime < MIN_FRAME_INTERVAL_NS) return;
        lastFrameTime = now;

        int w = screen.textureWidth,
                h = screen.textureHeight;

        // Skip if screen dimensions are zero
        if (w == 0 || h == 0) return;

        try {
            frameExecutor.submit(this::prepareBuffer);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void prepareBuffer() {
        int targetW = screen.textureWidth,
                targetH = screen.textureHeight;
        if (targetW == 0 || targetH == 0 || currentFrameBuffer == null) return;

        // Convert the frame buffer to RGBA (reuses buffer internally)
        ByteBuffer converted = convertToRGBA(
                currentFrameBuffer,
                currentFrameWidth,
                currentFrameHeight
        );

        // If dimensions match, use directly
        if (currentFrameWidth == targetW && currentFrameHeight == targetH) {
            applyBrightnessToBuffer(converted, brightness);
            preparedBuffer = converted;
            preparedW = targetW;
            preparedH = targetH;
            frameReady = true;
            Minecraft.getInstance().execute(screen::fitTexture);
            return;
        }

        // Reuse scale buffer if possible
        int scaleSize = targetW * targetH * 4;
        if (scaleBuffer == null || scaleBufferSize < scaleSize) {
            scaleBuffer = ByteBuffer.allocateDirect(scaleSize).order(ByteOrder.nativeOrder());
            scaleBufferSize = scaleSize;
        }
        scaleBuffer.clear();

        Converter.scaleRGBA(
                converted,
                currentFrameWidth,
                currentFrameHeight,
                scaleBuffer,
                targetW,
                targetH
        );

        applyBrightnessToBuffer(scaleBuffer, brightness);
        preparedBuffer = scaleBuffer;
        preparedW = targetW;
        preparedH = targetH;
        frameReady = true;

        Minecraft.getInstance().execute(screen::fitTexture);
    }

    // === PLAYBACK HELPERS ================================================================
    private void doPlay() {
        if (!initialized) return;

        // Get current audio position FIRST (audio is the master clock)
        long audioPos = audioPipeline.queryPosition(Format.TIME);

        audioPipeline.pause();
        if (videoPipeline != null) {
            videoPipeline.pause();
        }

        // Wait for pipelines to be ready
        audioPipeline.getState();
        if (videoPipeline != null) {
            videoPipeline.getState();
        }

        // Sync video to audio position (audio is master)
        if (videoPipeline != null && audioPos > 0) {
            videoPipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), audioPos);
            videoPipeline.getState(); // Wait for seek to complete
        }

        // Use audio pipeline's clock for video sync
        Clock audioClock = audioPipeline.getClock();
        if (audioClock != null && videoPipeline != null) {
            videoPipeline.setClock(audioClock);
            videoPipeline.setBaseTime(audioPipeline.getBaseTime());
        }

        if (!screen.getPaused()) {
            audioPipeline.play();
            if (videoPipeline != null) {
                videoPipeline.play();
            }
        }
    }

    private void doPause() {
        if (!initialized) return;
        if (videoPipeline != null) videoPipeline.pause();
        if (audioPipeline != null) audioPipeline.pause();
    }

    private void doStop() {
        safeStopAndDispose(videoPipeline);
        safeStopAndDispose(audioPipeline);
        videoPipeline = null;
        audioPipeline = null;
    }

    private void doSeek(long nanos, boolean b) {
        if (!initialized) return;
        EnumSet<SeekFlags> flags = EnumSet.of(
                SeekFlags.FLUSH,
                SeekFlags.ACCURATE
        );
        audioPipeline.pause();
        if (videoPipeline != null) videoPipeline.pause();
        if (videoPipeline != null) videoPipeline.seekSimple(
                Format.TIME,
                flags,
                nanos
        );
        audioPipeline.seekSimple(Format.TIME, flags, nanos);
        if (videoPipeline != null) videoPipeline.getState(); // Waiting for pre-roll
        audioPipeline.play();
        if (videoPipeline != null && !screen.getPaused()) videoPipeline.play();

        if (b) screen.afterSeek();
    }

    private void applyVolume() {
        if (!initialized) return;
        Element v = audioPipeline.getElementByName("volumeElement");
        if (v != null) v.set("volume", 1);
        Element a = audioPipeline.getElementByName("ampElement");
        if (a != null) a.set("amplification", currentVolume);
    }

    // === QUALITY HELPERS =================================================================
    private Optional<Stream> pickVideo(int target) {
        return availableVideoStreams
                .stream()
                .filter(s -> s.getResolution() != null)
                .min(
                        Comparator.comparingInt(s -> Math.abs(parseQuality(s) - target))
                );
    }

    private void changeQuality(String desired) {
        if (!initialized || availableVideoStreams == null) return;
        int target;
        try {
            target = Integer.parseInt(desired.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return;
        }
        if (target == lastQuality) return;
        Minecraft.getInstance().execute(screen::reloadTexture);

        Optional<Stream> best = pickVideo(target);
        if (best.isEmpty()) return;
        Stream chosen = best.get();
        if (chosen.getUrl().equals(currentVideoStream.getUrl())) return;

        long pos = audioPipeline.queryPosition(Format.TIME);
        audioPipeline.pause();

        safeStopAndDispose(videoPipeline);

        Pipeline newVid = buildVideoPipeline(chosen.getUrl());

        // Get the clock from audio pipeline and use it for video
        Clock clock = audioPipeline.getClock();
        if (clock != null) {
            newVid.setClock(clock);
            newVid.setBaseTime(audioPipeline.getBaseTime());
        }
        newVid.pause();

        // Pre-roll the pipeline to ensure it's ready
        newVid.getState();

        EnumSet<SeekFlags> flags = EnumSet.of(
                SeekFlags.FLUSH,
                SeekFlags.ACCURATE
        );
        audioPipeline.seekSimple(Format.TIME, flags, pos);
        newVid.seekSimple(Format.TIME, flags, pos);

        if (!screen.getPaused()) {
            audioPipeline.play();
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
                videoPipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), audioPos);
            }
        } catch (Exception ignored) {
            // Ignore query errors during playback
        }
    }

    // === CONCURRENCY HELPERS =============================================================
    private void safeExecute(Runnable action) {
        if (!gstExecutor.isShutdown()) {
            try {
                gstExecutor.submit(action);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }
}
