package com.dreamdisplays.screen;

import com.dreamdisplays.ModInitializer;
import com.mojang.blaze3d.platform.NativeImage;
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

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@NullMarked
public class MediaPlayer {

    private static final ExecutorService INIT_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-init"));
    private static final int SYNC_CHECK_INTERVAL = 100;
    private static final long MAX_SYNC_DRIFT_NS = 500_000_000L;
    private static final long MIN_FRAME_INTERVAL_NS = 16_666_667L;
    public static boolean captureSamples = true;

    private final String lang;
    private final String youtubeUrl;
    private final ExecutorService gstExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-gst"));
    private final ExecutorService frameExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-frame"));
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final DisplayScreen screen;
    private volatile double currentVolume;
    private volatile @Nullable Pipeline videoPipeline;
    private volatile @Nullable Pipeline audioPipeline;
    private volatile @Nullable List<Integer> availableQualities;
    private volatile @Nullable String currentVideoUrl;
    private volatile @Nullable String currentAudioUrl;
    private volatile boolean initialized;
    private int lastQuality;
    private volatile @Nullable ByteBuffer currentFrameBuffer;
    private volatile int currentFrameWidth = 0;
    private volatile int currentFrameHeight = 0;
    private volatile @Nullable ByteBuffer preparedBuffer;
    private volatile int lastTexW = 0, lastTexH = 0;
    private volatile int preparedW = 0, preparedH = 0;
    private volatile double userVolume = ModInitializer.config.defaultDisplayVolume;
    private volatile double lastAttenuation = 1.0;
    private volatile double brightness = 1.0;
    private volatile boolean frameReady = false;
    private int syncCheckCounter = 0;
    private @Nullable ByteBuffer convertBuffer = null;
    private int convertBufferSize = 0;
    private @Nullable ByteBuffer scaleBuffer = null;
    private int scaleBufferSize = 0;
    private volatile long lastFrameTime = 0;

    public MediaPlayer(String youtubeUrl, String lang, DisplayScreen screen) {
        this.youtubeUrl = youtubeUrl;
        this.screen = screen;
        this.lang = lang;
        LoggingManager.info("[MediaPlayer] Creating new instance for URL: " + youtubeUrl);
        Gst.init("MediaPlayer");
        INIT_EXECUTOR.submit(this::initialize);
    }

    private void initialize() {
        LoggingManager.info("[MediaPlayer] === START INITIALIZATION ===");
        try {
            LoggingManager.info("[MediaPlayer] Initializing NewPipe with custom Downloader");
            NewPipe.init(new Downloader() {
                @Override
                public Response execute(Request request) throws IOException {
                    String url = request.url();
                    String method = request.httpMethod();
                    Map<String, List<String>> headers = request.headers();
                    byte[] data = request.dataToSend();

                    LoggingManager.info("[MediaPlayer Downloader] Request: " + method + " " + url);

                    URL u = URI.create(url).toURL();
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod(method);
                    conn.setUseCaches(false);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

                    if (headers != null) {
                        headers.forEach((k, vList) -> vList.forEach(v -> conn.addRequestProperty(k, v)));
                    }

                    if (data != null && data.length > 0) {
                        conn.setDoOutput(true);
                        try (java.io.OutputStream os = conn.getOutputStream()) {
                            os.write(data);
                        }
                    }

                    conn.connect();
                    int code = conn.getResponseCode();
                    InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();

                    String body = "";
                    if (is != null) {
                        try (Scanner s = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                            body = s.hasNext() ? s.next() : "";
                        }
                    }

                    LoggingManager.info("[MediaPlayer Downloader] Response: " + code + " for " + url);
                    if (body.length() > 500) {
                        LoggingManager.info("[MediaPlayer Downloader] Body preview: " + body.substring(0, 500) + "...");
                    } else {
                        LoggingManager.info("[MediaPlayer Downloader] Body: " + body);
                    }

                    return new Response(code, conn.getResponseMessage(), conn.getHeaderFields(), body, url);
                }
            });

            NewPipe.setPreferredLocalization(new Localization(lang, lang.toUpperCase()));
            LoggingManager.info("[MediaPlayer] Preferred localization set to: " + lang);

            String videoId = com.dreamdisplays.util.Utils.extractVideoId(youtubeUrl);
            if (videoId == null || videoId.isEmpty()) {
                LoggingManager.error("[MediaPlayer] FAILED to extract video ID from: " + youtubeUrl);
                return;
            }
            LoggingManager.info("[MediaPlayer] Extracted video ID: " + videoId);

            String cleanUrl = "https://www.youtube.com/watch?v=" + videoId;
            LoggingManager.info("[MediaPlayer] Fetching StreamInfo from: " + cleanUrl);

            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl));
            LoggingManager.info("[MediaPlayer] StreamInfo fetched. Title: " + info.getName() + " | Duration: " + info.getDuration() + "s");

            List<VideoStream> videoStreams = info.getVideoStreams();
            List<AudioStream> audioStreams = info.getAudioStreams();

            LoggingManager.info("[MediaPlayer] Video streams count: " + videoStreams.size());
            LoggingManager.info("[MediaPlayer] Audio streams count: " + audioStreams.size());

            videoStreams.forEach(vs -> LoggingManager.info("[MediaPlayer] Video: " + vs.getResolution() + " " + vs.getFormat() + " URL: " + vs.getUrl()));
            audioStreams.forEach(as -> LoggingManager.info("[MediaPlayer] Audio: " + as.getFormat() + " locale: " + (as.getAudioLocale() != null ? as.getAudioLocale().getLanguage() : "null") + " URL: " + as.getUrl()));

            if (videoStreams.isEmpty() || audioStreams.isEmpty()) {
                LoggingManager.error("[MediaPlayer] No streams available!");
                return;
            }

            availableQualities = videoStreams.stream()
                    .map(VideoStream::getResolution)
                    .filter(Objects::nonNull)
                    .map(this::parseQuality)
                    .distinct()
                    .filter(q -> q <= (ModInitializer.isPremium ? 2160 : 1080))
                    .sorted()
                    .collect(Collectors.toList());

            LoggingManager.info("[MediaPlayer] Available qualities: " + availableQualities);

            int targetQuality = Integer.parseInt(screen.getQuality().replace("p", ""));
            Optional<VideoStream> videoOpt = videoStreams.stream()
                    .min(Comparator.comparingInt(vs -> Math.abs(parseQuality(vs.getResolution()) - targetQuality)));

            if (videoOpt.isEmpty()) {
                videoOpt = Optional.of(videoStreams.get(0));
                LoggingManager.warn("[MediaPlayer] No close quality match, using first video stream");
            }

            Optional<AudioStream> audioOpt = audioStreams.stream()
                    .filter(as -> as.getAudioLocale() != null && as.getAudioLocale().getLanguage().contains(lang))
                    .findFirst();

            if (audioOpt.isEmpty()) {
                audioOpt = Optional.of(audioStreams.get(audioStreams.size() - 1));
                LoggingManager.warn("[MediaPlayer] No audio in preferred language, using last one");
            }

            currentVideoUrl = videoOpt.get().getUrl();
            currentAudioUrl = audioOpt.get().getUrl();
            lastQuality = parseQuality(videoOpt.get().getResolution());

            LoggingManager.info("[MediaPlayer] Selected video URL: " + currentVideoUrl);
            LoggingManager.info("[MediaPlayer] Selected audio URL: " + currentAudioUrl);

            audioPipeline = buildAudioPipeline(currentAudioUrl);
            videoPipeline = buildVideoPipeline(currentVideoUrl);

            if (videoPipeline == null || audioPipeline == null) {
                LoggingManager.error("[MediaPlayer] One or both pipelines failed to build");
                return;
            }

            initialized = true;
            LoggingManager.info("[MediaPlayer] === INITIALIZATION SUCCESSFUL ===");
        } catch (Exception e) {
            LoggingManager.error("[MediaPlayer] === INITIALIZATION FAILED ===", e);
        }
    }
    private Pipeline buildVideoPipeline(String uri) {
        LoggingManager.info("[MediaPlayer] Building VIDEO pipeline for: " + uri);
        String desc = String.join(" ",
                "souphttpsrc location=\"" + uri + "\"",
                "! typefind name=typefinder",
                "! decodebin ! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink sync=false"
        );
        LoggingManager.info("[MediaPlayer] Universal video pipeline desc: " + desc);

        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        if (p == null) {
            LoggingManager.error("[MediaPlayer] Gst.parseLaunch returned null for universal video pipeline");
            return null;
        }

        configureVideoSink((AppSink) p.getElementByName("videosink"));
        p.pause();

        Bus bus = p.getBus();
        bus.connect((Bus.ERROR) (src, code, msg) -> LoggingManager.error("[MediaPlayer VIDEO ERROR] " + src.getName() + ": " + msg));
        bus.connect((Bus.EOS) src -> LoggingManager.info("[MediaPlayer VIDEO] EOS"));
        bus.connect((Bus.STATE_CHANGED) (src, old, cur, pend) -> LoggingManager.info("[MediaPlayer VIDEO] State: " + old + " -> " + cur));

        LoggingManager.info("[MediaPlayer] Universal video pipeline built and paused");
        return p;
    }

    private Pipeline buildAudioPipeline(String uri) {
        LoggingManager.info("[MediaPlayer] Building AUDIO pipeline for: " + uri);
        String desc = "souphttpsrc location=\"" + uri + "\" ! decodebin ! audioconvert ! audioresample " +
                "! volume name=volumeElement volume=1 ! audioamplify name=ampElement amplification=" + currentVolume +
                " ! autoaudiosink";
        LoggingManager.info("[MediaPlayer] Audio pipeline desc: " + desc);

        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        if (p == null) {
            LoggingManager.error("[MediaPlayer] Gst.parseLaunch returned null for audio pipeline");
            return null;
        }

        Bus bus = p.getBus();
        bus.connect((Bus.ERROR) (src, code, msg) -> LoggingManager.error("[MediaPlayer AUDIO ERROR] " + src.getName() + ": " + msg));
        bus.connect((Bus.EOS) src -> {
            LoggingManager.info("[MediaPlayer AUDIO] EOS - looping");
            safeExecute(() -> {
                audioPipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), 0L);
                audioPipeline.play();
                if (videoPipeline != null) {
                    videoPipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), 0L);
                    videoPipeline.play();
                }
            });
        });
        bus.connect((Bus.STATE_CHANGED) (src, old, cur, pend) -> LoggingManager.info("[MediaPlayer AUDIO] State: " + old + " -> " + cur));

        LoggingManager.info("[MediaPlayer] Audio pipeline built");
        return p;
    }

    private void configureVideoSink(AppSink sink) {
        LoggingManager.info("[MediaPlayer] Configuring AppSink");
        sink.set("emit-signals", true);
        sink.set("sync", true);
        sink.set("max-buffers", 1);
        sink.set("drop", true);
        sink.connect((AppSink.NEW_SAMPLE) elem -> {
            Sample s = elem.pullSample();
            if (s == null || !captureSamples) {
                LoggingManager.warn("[MediaPlayer] pullSample returned null or capture disabled");
                return FlowReturn.OK;
            }
            try {
                Structure st = s.getCaps().getStructure(0);
                int w = st.getInteger("width");
                int h = st.getInteger("height");
                currentFrameWidth = w;
                currentFrameHeight = h;
                currentFrameBuffer = sampleToBuffer(s);
                prepareBufferAsync();
            } catch (Exception e) {
                LoggingManager.error("[MediaPlayer] Error in NEW_SAMPLE handler", e);
            } finally {
                s.dispose();
            }
            return FlowReturn.OK;
        });
    }

    private void prepareBufferAsync() {
        if (currentFrameBuffer == null) return;

        long now = System.nanoTime();
        if (now - lastFrameTime < MIN_FRAME_INTERVAL_NS) {
            LoggingManager.info("[MediaPlayer] Frame skipped (rate limit)");
            return;
        }
        lastFrameTime = now;

        LoggingManager.info("[MediaPlayer] Submitting frame preparation task");
        try {
            frameExecutor.submit(this::prepareBuffer);
        } catch (RejectedExecutionException ignored) {
            LoggingManager.warn("[MediaPlayer] Frame task rejected");
        }
    }

    private void prepareBuffer() {
        LoggingManager.info("[MediaPlayer] Preparing buffer for texture update");
        int targetW = screen.textureWidth;
        int targetH = screen.textureHeight;
        if (targetW == 0 || targetH == 0 || currentFrameBuffer == null) return;

        ByteBuffer converted = convertToRGBA(currentFrameBuffer, currentFrameWidth, currentFrameHeight);

        if (currentFrameWidth == targetW && currentFrameHeight == targetH) {
            applyBrightnessToBuffer(converted, brightness);
            preparedBuffer = converted;
            preparedW = targetW;
            preparedH = targetH;
            frameReady = true;
            Minecraft.getInstance().execute(screen::fitTexture);
            LoggingManager.info("[MediaPlayer] Frame ready (no scaling needed)");
            return;
        }

        int scaleSize = targetW * targetH * 4;
        if (scaleBuffer == null || scaleBufferSize < scaleSize) {
            scaleBuffer = ByteBuffer.allocateDirect(scaleSize).order(ByteOrder.nativeOrder());
            scaleBufferSize = scaleSize;
        }
        scaleBuffer.clear();

        scaleRGBA(converted, currentFrameWidth, currentFrameHeight, scaleBuffer, targetW, targetH);

        applyBrightnessToBuffer(scaleBuffer, brightness);
        preparedBuffer = scaleBuffer;
        preparedW = targetW;
        preparedH = targetH;
        frameReady = true;
        Minecraft.getInstance().execute(screen::fitTexture);
        LoggingManager.info("[MediaPlayer] Frame ready (after scaling)");
    }

    private static ByteBuffer sampleToBuffer(Sample sample) {
        Buffer buf = sample.getBuffer();
        ByteBuffer bb = buf.map(false);

        if (bb.order() == ByteOrder.nativeOrder()) {
            ByteBuffer result = ByteBuffer.allocateDirect(bb.remaining()).order(ByteOrder.nativeOrder());
            result.put(bb);
            result.flip();
            buf.unmap();
            return result;
        }

        ByteBuffer result = ByteBuffer.allocateDirect(bb.remaining()).order(ByteOrder.nativeOrder());
        bb.rewind();
        for (int i = 0; i < bb.remaining(); i++) {
            result.put(bb.get());
        }
        result.flip();
        buf.unmap();
        return result;
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

    private ByteBuffer convertToRGBA(ByteBuffer srcBuffer, int width, int height) {
        int size = width * height * 4;

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

    private static void scaleRGBA(ByteBuffer srcBuffer, int srcW, int srcH, ByteBuffer dstBuffer, int dstW, int dstH) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }

        double scaleW = (double) dstW / srcW;
        double scaleH = (double) dstH / srcH;
        double scale = Math.max(scaleW, scaleH);
        int scaledW = (int) (srcW * scale + 0.5);
        int scaledH = (int) (srcH * scale + 0.5);

        int offsetX = (dstW - scaledW) / 2;
        int offsetY = (dstH - scaledH) / 2;

        for (int i = 0; i < dstW * dstH * 4; i++) {
            dstBuffer.put(i, (byte) 0);
        }

        for (int y = 0; y < dstH; y++) {
            int srcY = (int) (((y - offsetY) * srcH) / (double) scaledH);
            if (srcY < 0 || srcY >= srcH) continue;

            for (int x = 0; x < dstW; x++) {
                int srcX = (int) (((x - offsetX) * srcW) / (double) scaledW);
                if (srcX >= 0 && srcX < srcW) {
                    int srcIdx = (srcY * srcW + srcX) * 4;
                    int dstIdx = (y * dstW + x) * 4;

                    int pixel = srcBuffer.getInt(srcIdx);
                    dstBuffer.putInt(dstIdx, pixel);
                }
            }
        }
    }

    private int parseQuality(String resolution) {
        try {
            return Integer.parseInt(resolution.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static void safeStopAndDispose(@Nullable Element e) {
        if (e == null) return;
        try {
            e.setState(State.NULL);
        } catch (Exception ignore) {}
        try {
            e.dispose();
        } catch (Exception ignore) {}
    }

    public void play() {
        LoggingManager.info("[MediaPlayer] play() called");
        safeExecute(this::doPlay);
    }

    public void pause() {
        LoggingManager.info("[MediaPlayer] pause() called");
        safeExecute(this::doPause);
    }

    public void seekTo(long nanos, boolean b) {
        LoggingManager.info("[MediaPlayer] seekTo(" + nanos + ", " + b + ") called");
        safeExecute(() -> doSeek(nanos, b));
    }

    public void seekToFast(long nanos) {
        LoggingManager.info("[MediaPlayer] seekToFast(" + nanos + ") called");
        safeExecute(() -> doSeekFast(nanos));
    }

    public void seekRelative(double s) {
        LoggingManager.info("[MediaPlayer] seekRelative(" + s + "s) called");
        safeExecute(() -> {
            if (!initialized) return;
            long cur = audioPipeline.queryPosition(Format.TIME);
            long tgt = Math.max(0, cur + (long) (s * 1e9));
            long dur = Math.max(0, audioPipeline.queryDuration(Format.TIME) - 1);
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
        LoggingManager.info("[MediaPlayer] stop() called");
        if (terminated.getAndSet(true)) return;
        safeExecute(() -> {
            doStop();
            gstExecutor.shutdown();
            frameExecutor.shutdown();
        });
    }

    public void setVolume(double volume) {
        LoggingManager.info("[MediaPlayer] setVolume(" + volume + ") called");
        userVolume = Math.max(0, Math.min(2, volume));
        currentVolume = userVolume * lastAttenuation;
        safeExecute(this::applyVolume);
    }

    public void setBrightness(double brightness) {
        LoggingManager.info("[MediaPlayer] setBrightness(" + brightness + ") called");
        this.brightness = Math.max(0, Math.min(2, brightness));
    }

    public boolean textureFilled() {
        return preparedBuffer != null && preparedBuffer.remaining() > 0;
    }

    public void updateFrame(GpuTexture texture) {
        if (!frameReady || preparedBuffer == null) return;

        LoggingManager.info("[MediaPlayer] Updating texture with frame " + preparedW + "x" + preparedH);

        int w = screen.textureWidth, h = screen.textureHeight;
        if (w != preparedW || h != preparedH) return;

        var encoder = RenderSystem.getDevice().createCommandEncoder();

        preparedBuffer.rewind();

        int expectedSize = w * h * 4;
        if (preparedBuffer.remaining() < expectedSize) {
            LoggingManager.error("Buffer underrun: expected " + expectedSize + " bytes, but only " + preparedBuffer.remaining() + " remaining");
            return;
        }

        if (w != lastTexW || h != lastTexH) {
            lastTexW = w;
            lastTexH = h;
        }

        if (!texture.isClosed()) {
            encoder.writeToTexture(texture, preparedBuffer, NativeImage.Format.RGBA, 0, 0, 0, 0, texture.getWidth(0), texture.getHeight(0));
        }

        frameReady = false;
    }

    public List<Integer> getAvailableQualities() {
        return availableQualities != null ? availableQualities : Collections.emptyList();
    }

    public void setQuality(String quality) {
        LoggingManager.info("[MediaPlayer] setQuality(" + quality + ") called");
        safeExecute(() -> changeQuality(quality));
    }

    private void doPlay() {
        if (!initialized) {
            LoggingManager.warn("[MediaPlayer] doPlay called but not initialized");
            return;
        }
        LoggingManager.info("[MediaPlayer] Executing play");

        long audioPos = audioPipeline.queryPosition(Format.TIME);

        audioPipeline.pause();
        if (videoPipeline != null) videoPipeline.pause();

        audioPipeline.getState();
        if (videoPipeline != null) videoPipeline.getState();

        if (videoPipeline != null && audioPos > 0) {
            videoPipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), audioPos);
            videoPipeline.getState();
        }

        Clock audioClock = audioPipeline.getClock();
        if (audioClock != null && videoPipeline != null) {
            videoPipeline.setClock(audioClock);
            videoPipeline.setBaseTime(audioPipeline.getBaseTime());
        }

        if (!screen.getPaused()) {
            audioPipeline.play();
            if (videoPipeline != null) videoPipeline.play();
        }
    }

    private void doPause() {
        if (!initialized) return;
        LoggingManager.info("[MediaPlayer] Executing pause");
        if (videoPipeline != null) videoPipeline.pause();
        if (audioPipeline != null) audioPipeline.pause();
    }

    private void doStop() {
        LoggingManager.info("[MediaPlayer] Executing stop");
        safeStopAndDispose(videoPipeline);
        safeStopAndDispose(audioPipeline);
        videoPipeline = null;
        audioPipeline = null;
    }

    private void doSeek(long nanos, boolean b) {
        if (!initialized) return;
        LoggingManager.info("[MediaPlayer] Executing seek to " + nanos + " ns");
        EnumSet<SeekFlags> flags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE);
        audioPipeline.pause();
        if (videoPipeline != null) videoPipeline.pause();
        if (videoPipeline != null) videoPipeline.seekSimple(Format.TIME, flags, nanos);
        audioPipeline.seekSimple(Format.TIME, flags, nanos);
        if (videoPipeline != null) videoPipeline.getState();
        audioPipeline.play();
        if (videoPipeline != null && !screen.getPaused()) videoPipeline.play();

        if (b) screen.afterSeek();
    }

    private void doSeekFast(long nanos) {
        if (!initialized) return;
        LoggingManager.info("[MediaPlayer] Executing fast seek to " + nanos + " ns");
        EnumSet<SeekFlags> flags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT);
        audioPipeline.pause();
        if (videoPipeline != null) videoPipeline.pause();
        if (videoPipeline != null) videoPipeline.seekSimple(Format.TIME, flags, nanos);
        audioPipeline.seekSimple(Format.TIME, flags, nanos);
        if (videoPipeline != null) videoPipeline.getState();
        audioPipeline.play();
        if (videoPipeline != null && !screen.getPaused()) videoPipeline.play();
    }

    private void applyVolume() {
        if (!initialized) return;
        LoggingManager.info("[MediaPlayer] Applying volume " + currentVolume);
        Element v = audioPipeline.getElementByName("volumeElement");
        if (v != null) v.set("volume", 1);
        Element a = audioPipeline.getElementByName("ampElement");
        if (a != null) a.set("amplification", currentVolume);
    }

    private void changeQuality(String desired) {
        if (!initialized || currentVideoUrl == null) return;

        int target;
        try {
            target = Integer.parseInt(desired.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return;
        }

        // If the desired quality is the same as current, skip
        if (target == lastQuality) {
            LoggingManager.info("[MediaPlayer] Quality already " + target + "p, skipping change");
            return;
        }

        LoggingManager.info("[MediaPlayer] Attempting to change quality to " + target + "p");

        try {
            String videoId = com.dreamdisplays.util.Utils.extractVideoId(youtubeUrl);
            String cleanUrl = "https://www.youtube.com/watch?v=" + videoId;

            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl));
            List<VideoStream> videoStreams = info.getVideoStreams();

            // Cecck if exact quality is available
            boolean hasExactQuality = videoStreams.stream()
                    .anyMatch(vs -> parseQuality(vs.getResolution()) == target);

            if (!hasExactQuality) {
                LoggingManager.info("[MediaPlayer] No exact " + target + "p stream available, keeping current quality");
                return;
            }

            // Look for the best matching stream
            Optional<VideoStream> best = videoStreams.stream()
                    .min(Comparator.comparingInt(vs -> Math.abs(parseQuality(vs.getResolution()) - target)));

            if (best.isEmpty() || best.get().getUrl().equals(currentVideoUrl)) {
                LoggingManager.info("[MediaPlayer] Best stream is the same as current, no change needed");
                lastQuality = target;
                return;
            }

            Minecraft.getInstance().execute(screen::reloadTexture);

            long pos = audioPipeline.queryPosition(Format.TIME);
            audioPipeline.pause();

            safeStopAndDispose(videoPipeline);

            Pipeline newVid = buildVideoPipeline(best.get().getUrl());

            Clock clock = audioPipeline.getClock();
            if (clock != null) {
                newVid.setClock(clock);
                newVid.setBaseTime(audioPipeline.getBaseTime());
            }
            newVid.pause();
            newVid.getState();

            EnumSet<SeekFlags> flags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE);
            audioPipeline.seekSimple(Format.TIME, flags, pos);
            newVid.seekSimple(Format.TIME, flags, pos);

            if (!screen.getPaused()) {
                audioPipeline.play();
                newVid.play();
            }

            videoPipeline = newVid;
            currentVideoUrl = best.get().getUrl();
            lastQuality = parseQuality(best.get().getResolution());

            LoggingManager.info("[MediaPlayer] Quality changed successfully to " + lastQuality + "p");
        } catch (Exception e) {
            LoggingManager.error("[MediaPlayer] Failed to change quality", e);
        }
    }

    public void tick(BlockPos playerPos, double maxRadius) {
        if (!initialized) return;

        double dist = screen.getDistanceToScreen(playerPos);
        double attenuation = Math.pow(1.0 - Math.min(1.0, dist / maxRadius), 2);
        if (Math.abs(attenuation - lastAttenuation) > 1e-5) {
            lastAttenuation = attenuation;
            currentVolume = userVolume * attenuation;
            LoggingManager.info("[MediaPlayer] Distance attenuation: " + currentVolume);
            safeExecute(this::applyVolume);
        }

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
                LoggingManager.info("[MediaPlayer] Sync drift " + drift + " ns - resyncing video to audio");
                videoPipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), audioPos);
            }
        } catch (Exception ignored) {}
    }

    private void safeExecute(Runnable r) {
        if (!gstExecutor.isShutdown()) {
            try {
                gstExecutor.submit(r);
            } catch (RejectedExecutionException ignored) {}
        }
    }
}
