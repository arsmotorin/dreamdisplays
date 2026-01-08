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

import static java.lang.Math.*;

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
                    .min(Comparator.comparingInt(vs -> abs(parseQuality(vs.getResolution()) - targetQuality)));

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
        bus.connect((src, old, cur, pend) -> LoggingManager.info("[MediaPlayer AUDIO] State: " + old + " -> " + cur));

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

        try {
            frameExecutor.submit(this::prepareBuffer);
        } catch (RejectedExecutionException ignored) {
            LoggingManager.warn("[MediaPlayer] Frame task rejected");
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

    private void prepareBuffer() {
        // long startNs = System.nanoTime();

        int targetW = screen.textureWidth;
        int targetH = screen.textureHeight;
        if (targetW == 0 || targetH == 0 || currentFrameBuffer == null) return;

        boolean needsScaling = currentFrameWidth != targetW || currentFrameHeight != targetH;
        int bufferSize = targetW * targetH * 4;

        if (needsScaling) {
            if (scaleBuffer == null || scaleBufferSize < bufferSize) {
                scaleBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
                scaleBufferSize = bufferSize;
            }

            scaleRGBA(currentFrameBuffer, currentFrameWidth, currentFrameHeight,
                    scaleBuffer, targetW, targetH, brightness);

            preparedBuffer = scaleBuffer;
        } else {
            if (convertBuffer == null || convertBufferSize < bufferSize) {
                convertBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
                convertBufferSize = bufferSize;
            }

            currentFrameBuffer.rewind();
            convertBuffer.clear();

            if (abs(brightness - 1.0) < 1e-5) {
                copy(currentFrameBuffer, convertBuffer, bufferSize);
            } else {
                applyBrightnessToBuffer(currentFrameBuffer, convertBuffer, bufferSize, brightness);
            }

            preparedBuffer = convertBuffer;
        }

        preparedW = targetW;
        preparedH = targetH;
        frameReady = true;

        // long elapsedNs = System.nanoTime() - startNs;
        // LoggingManager.info("[MediaPlayer] prepareBuffer: " + elapsedNs + "ns");

        Minecraft.getInstance().execute(screen::fitTexture);
    }

    private static void copy(ByteBuffer src, ByteBuffer dst, int bytes) {
        int longs = bytes >>> 3;
        int ints = (bytes & 7) >>> 2;
        int remaining = bytes & 3;

        // Copy 8 bytes at a time
        for (int i = 0; i < longs; i++) {
            dst.putLong(src.getLong());
        }

        // Copy 4 bytes at a time
        for (int i = 0; i < ints; i++) {
            dst.putInt(src.getInt());
        }

        // Copy remaining bytes
        for (int i = 0; i < remaining; i++) {
            dst.put(src.get());
        }

        // Flip the destination buffer for reading
        dst.flip();
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

    private static void applyBrightnessToBuffer(ByteBuffer src, ByteBuffer dst, int bytes, double brightness) {
        int pixels = bytes >>> 2;
        int brightFixed = (int)(brightness * 256.0);

        int pairs = pixels >>> 1;
        int odd = pixels & 1;
        if (brightness < 1.0) {
            // Process 2 pixels per iteration when possible
            for (int i = 0; i < pairs; i++) {
                // Pixel 1
                int rgba1 = src.getInt();
                int r1 = (((rgba1 >>> 24) & 0xFF) * brightFixed) >>> 8;
                int g1 = (((rgba1 >>> 16) & 0xFF) * brightFixed) >>> 8;
                int b1 = (((rgba1 >>> 8) & 0xFF) * brightFixed) >>> 8;
                int a1 = rgba1 & 0xFF;

                // Pixel 2
                int rgba2 = src.getInt();
                int r2 = (((rgba2 >>> 24) & 0xFF) * brightFixed) >>> 8;
                int g2 = (((rgba2 >>> 16) & 0xFF) * brightFixed) >>> 8;
                int b2 = (((rgba2 >>> 8) & 0xFF) * brightFixed) >>> 8;
                int a2 = rgba2 & 0xFF;

                // Write both pixels
                dst.putInt((r1 << 24) | (g1 << 16) | (b1 << 8) | a1);
                dst.putInt((r2 << 24) | (g2 << 16) | (b2 << 8) | a2);
            }

            // Handle odd pixel
            if (odd != 0) {
                int rgba = src.getInt();
                int r = (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8;
                int g = (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8;
                int b = (((rgba >>> 8) & 0xFF) * brightFixed) >>> 8;
                int a = rgba & 0xFF;
                dst.putInt((r << 24) | (g << 16) | (b << 8) | a);
            }
        } else {

            for (int i = 0; i < pairs; i++) {
                // Pixel 1
                int rgba1 = src.getInt();
                int r1 = min(255, (((rgba1 >>> 24) & 0xFF) * brightFixed) >>> 8);
                int g1 = min(255, (((rgba1 >>> 16) & 0xFF) * brightFixed) >>> 8);
                int b1 = min(255, (((rgba1 >>> 8) & 0xFF) * brightFixed) >>> 8);
                int a1 = rgba1 & 0xFF;

                // Pixel 2
                int rgba2 = src.getInt();
                int r2 = min(255, (((rgba2 >>> 24) & 0xFF) * brightFixed) >>> 8);
                int g2 = min(255, (((rgba2 >>> 16) & 0xFF) * brightFixed) >>> 8);
                int b2 = min(255, (((rgba2 >>> 8) & 0xFF) * brightFixed) >>> 8);
                int a2 = rgba2 & 0xFF;

                // Write both pixels
                dst.putInt((r1 << 24) | (g1 << 16) | (b1 << 8) | a1);
                dst.putInt((r2 << 24) | (g2 << 16) | (b2 << 8) | a2);
            }

            // Handle odd pixel
            if (odd != 0) {
                int rgba = src.getInt();
                int r = min(255, (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8);
                int g = min(255, (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8);
                int b = min(255, (((rgba >>> 8) & 0xFF) * brightFixed) >>> 8);
                int a = rgba & 0xFF;
                dst.putInt((r << 24) | (g << 16) | (b << 8) | a);
            }
        }

        dst.flip();
    }

    private static void scaleRGBA(ByteBuffer src, int srcW, int srcH, ByteBuffer dst, int dstW, int dstH, double brightness) {
        // Calculate scaling factors
        double scaleW = (double) dstW / srcW;
        double scaleH = (double) dstH / srcH;

        // Take the larger scale to ensure the image fills the dst buffer
        double scale = max(scaleW, scaleH);

        // Calculate the scaled dimensions of the source image
        int scaledW = (int) (srcW * scale + 0.5);
        int scaledH = (int) (srcH * scale + 0.5);

        // Calculate offsets to center the image in the dst buffer
        int offsetX = (dstW - scaledW) >>> 1;
        int offsetY = (dstH - scaledH) >>> 1;

        // Precompute inverse scaling factors in fixed-point 16.16 format
        // int offsetX = (dstW - scaledW) / 2;
        // int offsetY = (dstH - scaledH) / 2;
        int invScaleWFixed = (int)((srcW << 16) / (double) scaledW);
        int invScaleHFixed = (int)((srcH << 16) / (double) scaledH);

        // Determine if brightness adjustment is needed
        boolean applyBright = abs(brightness - 1.0) >= 1e-5;
        // Precompute brightness in fixed-point 8.8 format
        int brightFixed = (int)(brightness * 256);

        // Prepare dst buffer: clear to black with transparency
        int totalBytes = dstW * dstH * 4;    // Total bytes in dst buffer
        int longs = totalBytes >>> 3;        // Total longs (8 bytes each)
        int remaining = totalBytes & 7;      // Remaining bytes after longs

        dst.clear();    // Set position to 0
        for (int i = 0; i < longs; i++) {
            dst.putLong(0L);    // Set 8 bytes to 0 (black pixel)
        }
        for (int i = 0; i < remaining; i++) {
            dst.put((byte) 0);    // Set remaining bytes to 0
        }
        dst.clear();    // Reset position for writing

        // Precompute row byte sizes
        int srcWBytes = srcW << 2;    // srcW * 4
        int dstWBytes = dstW << 2;    // dstW * 4

        // No brightness adjustment needed
        if (!applyBright) {
            // 4 pixels per iteration
            for (int y = 0; y < dstH; y++) {
                // Calculate corresponding srcY
                int srcY = (((y - offsetY) * invScaleHFixed) >>> 16);
                if (srcY >= srcH) continue; // Skip if out of bounds
                // TODO: should we also check srcY < 0?

                int srcRowBase = srcY * srcWBytes;    // Address start of row in src buffer
                int dstRowBase = y * dstWBytes;       // Address start of row in dst buffer

                int x = 0;
                int xLimit = dstW - 3;

                // 4 pixels at a time
                for (; x < xLimit; x += 4) {
                    // Check for their coordinates in source image
                    int srcX0 = (((x     - offsetX) * invScaleWFixed) >>> 16);
                    int srcX1 = (((x + 1 - offsetX) * invScaleWFixed) >>> 16);
                    int srcX2 = (((x + 2 - offsetX) * invScaleWFixed) >>> 16);
                    int srcX3 = (((x + 3 - offsetX) * invScaleWFixed) >>> 16);

                    // Copy pixels if within bounds
                    if (srcX0 < srcW) {
                        dst.putInt(dstRowBase + (x << 2), src.getInt(srcRowBase + (srcX0 << 2)));
                    }
                    if (srcX1 <= srcW) {
                        dst.putInt(dstRowBase + ((x + 1) << 2), src.getInt(srcRowBase + (srcX1 << 2)));
                    }
                    if (srcX2 < srcW) {
                        dst.putInt(dstRowBase + ((x + 2) << 2), src.getInt(srcRowBase + (srcX2 << 2)));
                    }
                    if (srcX3 < srcW) {
                        dst.putInt(dstRowBase + ((x + 3) << 2), src.getInt(srcRowBase + (srcX3 << 2)));
                    }
                }

                // Single pixels remaining
                for (; x < dstW; x++) {
                    int srcX = (((x - offsetX) * invScaleWFixed) >>> 16);
                    if (srcX < srcW) {
                        dst.putInt(dstRowBase + (x << 2), src.getInt(srcRowBase + (srcX << 2)));
                    }
                }
            }
            // If brightness < 1.0 (darkening)
        } else if (brightness < 1.0) {
            for (int y = 0; y < dstH; y++) {
                // int srcY = (int) (((y - offsetY) * srcH) / (double) scaledH);
                // if (srcY < 0 || srcY >= srcH) continue;
                int srcY = (((y - offsetY) * invScaleHFixed) >>> 16);
                if (srcY >= srcH) continue;

                int srcRowBase = srcY * srcWBytes;
                int dstRowBase = y * dstWBytes;

                int x = 0;
                int xLimit = dstW - 1;

                for (; x < xLimit; x += 2) {
                    int srcX0 = (((x     - offsetX) * invScaleWFixed) >>> 16);
                    int srcX1 = (((x + 1 - offsetX) * invScaleWFixed) >>> 16);

                    // 2 pixels at a time
                    if (srcX0 < srcW) {
                        int rgba = src.getInt(srcRowBase + (srcX0 << 2));
                        int r = (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8;
                        int g = (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8;
                        int b = (((rgba >>>  8) & 0xFF) * brightFixed) >>> 8;
                        int a = rgba & 0xFF;
                        dst.putInt(dstRowBase + (x << 2), (r << 24) | (g << 16) | (b << 8) | a);
                    }

                    if (srcX1 < srcW) {
                        int rgba = src.getInt(srcRowBase + (srcX1 << 2));
                        int r = (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8;
                        int g = (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8;
                        int b = (((rgba >>>  8) & 0xFF) * brightFixed) >>> 8;
                        int a = rgba & 0xFF;
                        dst.putInt(dstRowBase + ((x + 1) << 2), (r << 24) | (g << 16) | (b << 8) | a);
                    }
                }

                // Single pixel remaining
                for (; x < dstW; x++) {
                    int srcX = (((x - offsetX) * invScaleWFixed) >>> 16);
                    if (srcX < srcW) {
                        int rgba = src.getInt(srcRowBase + (srcX << 2));
                        int r = (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8;
                        int g = (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8;
                        int b = (((rgba >>>  8) & 0xFF) * brightFixed) >>> 8;
                        int a = rgba & 0xFF;
                        dst.putInt(dstRowBase + (x << 2), (r << 24) | (g << 16) | (b << 8) | a);
                    }
                }
            }
        } else {
            for (int y = 0; y < dstH; y++) {
                int srcY = (((y - offsetY) * invScaleHFixed) >>> 16);
                if (srcY >= srcH) continue;

                int srcRowBase = srcY * srcWBytes;
                int dstRowBase = y * dstWBytes;

                int x = 0;
                int xLimit = dstW - 1;

                for (; x < xLimit; x += 2) {
                    int srcX0 = (((x     - offsetX) * invScaleWFixed) >>> 16);
                    int srcX1 = (((x + 1 - offsetX) * invScaleWFixed) >>> 16);

                    // 2 pixels at a time
                    if (srcX0 < srcW) {
                        int rgba = src.getInt(srcRowBase + (srcX0 << 2));
                        int r = min(255, (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8);
                        int g = min(255, (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8);
                        int b = min(255, (((rgba >>>  8) & 0xFF) * brightFixed) >>> 8);
                        int a = rgba & 0xFF;
                        dst.putInt(dstRowBase + (x << 2), (r << 24) | (g << 16) | (b << 8) | a);
                    }

                    if (srcX1 < srcW) {
                        int rgba = src.getInt(srcRowBase + (srcX1 << 2));
                        int r = min(255, (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8);
                        int g = min(255, (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8);
                        int b = min(255, (((rgba >>>  8) & 0xFF) * brightFixed) >>> 8);
                        int a = rgba & 0xFF;
                        dst.putInt(dstRowBase + ((x + 1) << 2), (r << 24) | (g << 16) | (b << 8) | a);
                    }
                }

                // Single pixel remaining
                for (; x < dstW; x++) {
                    int srcX = (((x - offsetX) * invScaleWFixed) >>> 16);
                    if (srcX < srcW) {
                        int rgba = src.getInt(srcRowBase + (srcX << 2));
                        int r = min(255, (((rgba >>> 24) & 0xFF) * brightFixed) >>> 8);
                        int g = min(255, (((rgba >>> 16) & 0xFF) * brightFixed) >>> 8);
                        int b = min(255, (((rgba >>>  8) & 0xFF) * brightFixed) >>> 8);
                        int a = rgba & 0xFF;
                        dst.putInt(dstRowBase + (x << 2), (r << 24) | (g << 16) | (b << 8) | a);
                    }
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
            if (!initialized) return;
            long cur = audioPipeline.queryPosition(Format.TIME);
            long tgt = max(0, cur + (long) (s * 1e9));
            long dur = max(0, audioPipeline.queryDuration(Format.TIME) - 1);
            doSeek(min(tgt, dur), true);
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
        userVolume = max(0, min(2, volume));
        currentVolume = userVolume * lastAttenuation;
        safeExecute(this::applyVolume);
    }

    public void setBrightness(double brightness) {
        this.brightness = max(0, min(2, brightness));
    }

    public boolean textureFilled() {
        return preparedBuffer != null && preparedBuffer.remaining() > 0;
    }

    public void updateFrame(GpuTexture texture) {
        if (!frameReady) return;

        // long startNs = System.nanoTime();

        // Quick validation
        ByteBuffer buf = preparedBuffer;
        if (buf == null) return;

        int w = preparedW;
        int h = preparedH;

        if (w != screen.textureWidth || h != screen.textureHeight) return;

        buf.rewind();

        // Direct write without intermediate checks
        if (!texture.isClosed()) {
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToTexture(texture, buf, NativeImage.Format.RGBA,
                            0, 0, 0, 0, w, h);
        }

        frameReady = false;

        // long elapsedNs = System.nanoTime() - startNs;
        // LoggingManager.info("[MediaPlayer] updateFrame: " + elapsedNs + "ns");
    }

    public List<Integer> getAvailableQualities() {
        return availableQualities != null ? availableQualities : Collections.emptyList();
    }

    public void setQuality(String quality) {
        safeExecute(() -> changeQuality(quality));
    }

    private void doPlay() {
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

        try {
            String videoId = com.dreamdisplays.util.Utils.extractVideoId(youtubeUrl);
            String cleanUrl = "https://www.youtube.com/watch?v=" + videoId;

            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl));
            List<VideoStream> videoStreams = info.getVideoStreams();

            // Check if exact match exists
            videoStreams.stream().anyMatch(vs -> parseQuality(vs.getResolution()) == target);

            // Look for the best matching stream
            Optional<VideoStream> best = videoStreams.stream()
                    .min(Comparator.comparingInt(vs -> abs(parseQuality(vs.getResolution()) - target)));

            if (best.isEmpty() || best.get().getUrl().equals(currentVideoUrl)) {
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
        } catch (Exception e) {
            LoggingManager.error("[MediaPlayer] Failed to change quality", e);
        }
    }

    public void tick(BlockPos playerPos, double maxRadius) {
        if (!initialized) return;

        double dist = screen.getDistanceToScreen(playerPos);
        double attenuation = pow(1.0 - min(1.0, dist / maxRadius), 2);
        if (abs(attenuation - lastAttenuation) > 1e-5) {
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

    // TODO: remove that shitty drift in the future
    private void checkAndFixSync() {
        if (!initialized || videoPipeline == null || audioPipeline == null) return;
        if (screen.getPaused()) return;

        try {
            long audioPos = audioPipeline.queryPosition(Format.TIME);
            long videoPos = videoPipeline.queryPosition(Format.TIME);
            long drift = abs(audioPos - videoPos);

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
