package com.dreamdisplays.screen;

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
import com.dreamdisplays.PlatformlessInitializer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MediaPlayer {

    private final String lang;
    String[] clients = new String[] { "WEB_MUSIC", "ANDROID", "ANDROID_VR", "ANDROID_TESTSUITE", "IOS", "IOS_MUSIC" };

    // === CONSTANTS =======================================================================
    private static final String MIME_VIDEO = "video/webm";
    private static final String MIME_AUDIO = "audio/webm";
    private static final String USER_AGENT_V = "ANDROID_VR";
    private static final String USER_AGENT_A = "ANDROID_TESTSUITE";

    private static final ExecutorService INIT_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-init"));

    // === PUBLIC API FIELDS ===============================================================
    private final String youtubeUrl;
    private volatile double currentVolume;
    public static boolean captureSamples = true;

    // === GST OBJECTS =====================================================================
    private volatile Pipeline videoPipeline;
    private volatile Pipeline audioPipeline;

    private volatile java.util.List<Stream> availableVideoStreams;
    private volatile Stream currentVideoStream;
    private volatile boolean initialized;
    private int lastQuality;

    // === FRAME BUFFERS ===================================================================
    private BufferedImage currentFrame;
    private volatile ByteBuffer   preparedBuffer;

    private volatile int lastTexW = 0, lastTexH = 0;
    private volatile int preparedW = 0, preparedH = 0;

    private volatile double userVolume = (PlatformlessInitializer.config.defaultDisplayVolume);
    private volatile double lastAttenuation = 1.0;

    // === EXECUTORS & CONCURRENCY =========================================================
    private final ExecutorService gstExecutor   = Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-gst"));
    private final ExecutorService frameExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-frame"));
    private final AtomicBoolean   terminated    = new AtomicBoolean(false);

    private BufferedImage textureImage;
    private final Screen screen;

    // === CONSTRUCTOR =====================================================================
    public MediaPlayer(String youtubeUrl, String lang, Screen screen) {
        this.youtubeUrl = youtubeUrl;
        this.screen     = screen;
        this.lang = lang;
        Gst.init("MediaPlayer");
        INIT_EXECUTOR.submit(this::initialize);
    }

    // === PUBLIC API ======================================================================
    public void play()               { safeExecute(this::doPlay);  }
    public void pause()              { safeExecute(this::doPause); }
    public void seekTo(long ns, boolean b)      { safeExecute(() -> doSeek(ns, b)); }
    public void seekRelative(double s) {
        safeExecute(() -> {
            if (!initialized) return;
            long cur = audioPipeline.queryPosition(Format.TIME);
            long tgt = Math.max(0, cur + (long)(s * 1e9));
            long dur = Math.max(0, audioPipeline.queryDuration(Format.TIME) - 1);
            doSeek(Math.min(tgt, dur), true);
        });
    }
    public long  getCurrentTime()    { return initialized ? audioPipeline.queryPosition(Format.TIME) : 0; }
    public long  getDuration()       { return initialized ? audioPipeline.queryDuration(Format.TIME) : 0; }

    public boolean isInitialized()   { return initialized; }

    public void stop() {
        if (terminated.getAndSet(true)) return;
        safeExecute(() -> {
            doStop();
            gstExecutor.shutdown();
            frameExecutor.shutdown();
        });
    }

    public void setVolume(double v) {
        userVolume = Math.max(0, Math.min(1, v));
        currentVolume = userVolume * lastAttenuation;
        safeExecute(this::applyVolume);
    }

    public boolean textureFilled() {
        return screen != null && screen.textureWidth > 0 && screen.textureHeight > 0;
    }

    public void updateFrame(GpuTexture texture) {
        if (preparedBuffer == null) return;
        int w = screen.textureWidth, h = screen.textureHeight;
        if (w != preparedW || h != preparedH) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        preparedBuffer.position(0);

        if (w != lastTexW || h != lastTexH) {
            lastTexW = w; lastTexH = h;
        }

        if (!texture.isClosed()) {
            encoder.writeToTexture(
                    texture,
                    preparedBuffer,
                    NativeImage.Format.RGBA,
                    0, 0, 0, 0,
                    texture.getWidth(0), texture.getHeight(0)
            );
        }
    }

    public java.util.List<Integer> getAvailableQualities() {
        if (!initialized || availableVideoStreams == null) return Collections.emptyList();
        return availableVideoStreams.stream()
                .map(Stream::getResolution)
                .filter(Objects::nonNull)
                .map(r -> Integer.parseInt(r.replaceAll("\\D+", "")))
                .distinct()
                .filter(r -> r <= (PlatformlessInitializer.isPremium ? 2160 : 1080))
                .sorted()
                .collect(Collectors.toList());
    }

    public void setQuality(String q) { safeExecute(() -> changeQuality(q)); }

    // === INITIALIZATION ==================================================================
    private void initialize() {
        try {
            Youtube yt = new Youtube(youtubeUrl, USER_AGENT_V);
            java.util.List<Stream> all = yt.streams().getAll();

            Youtube ytA = new Youtube(youtubeUrl, USER_AGENT_A);
            java.util.List<Stream> audioS = ytA.streams().getAll();

            availableVideoStreams = all.stream()
                    .filter(s -> MIME_VIDEO.equals(s.getMimeType()))
                    .toList();

            Optional<Stream> videoOpt = pickVideo(Integer.parseInt(screen.getQuality().replace("p", ""))).or(() -> availableVideoStreams.stream().findFirst());
            Optional<Stream> audioOpt = audioS.stream()
                    .filter(s -> MIME_AUDIO.equals(s.getMimeType()))
                    .filter(s -> s.getAudioTrackId() != null && s.getAudioTrackId().contains(lang) || s.getAudioTrackName() != null && s.getAudioTrackName().contains(lang))
                    .reduce((f, n) -> n);

            if (audioOpt.isEmpty()) {
                LoggingManager.warn("No audio stream available with lang " + lang);
                LoggingManager.warn("Choosing random one...");

                audioOpt = all.stream()
                        .filter(s -> MIME_AUDIO.equals(s.getMimeType()))
                        .reduce((f, n) -> n);
            }
            if (videoOpt.isEmpty() || audioOpt.isEmpty()) {
                LoggingManager.error("No streams available");


                return;
            }

            currentVideoStream = videoOpt.get();
            lastQuality        = parseQuality(currentVideoStream);

            audioPipeline = buildAudioPipeline(audioOpt.get().getUrl());
            videoPipeline = buildVideoPipeline(currentVideoStream.getUrl());

            videoPipeline.getState();
            initialized = true;
        } catch (Exception e) {
            LoggingManager.error("Failed to initialize MediaPlayer ", e);
        }
    }

    private Pipeline buildVideoPipeline(String uri) {
        String desc = String.join(" ",
                "souphttpsrc location=\"" + uri + "\"",
                "user-agent=\"" + USER_AGENT_V + "\"",
                "extra-headers=\"origin:https://www.youtube.com\\nreferer:https://www.youtube.com\\n\"",
                "! matroskademux ! decodebin ! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink");
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        configureVideoSink((AppSink) p.getElementByName("videosink"));
        p.pause();

        Bus bus = p.getBus();
        final AtomicReference<Bus.ERROR> errRef = new AtomicReference<>();
        errRef.set((source, code, message) -> {
            LoggingManager.error("[MediaPlayer V][ERROR] GStreamer: " + message);
            bus.disconnect(errRef.get());
            screen.errored = true;
            initialized = false;
        });
        bus.connect(errRef.get());
        return p;
    }

    private Pipeline buildAudioPipeline(String uri) {
        String desc = "souphttpsrc location=\"" + uri + "\" ! decodebin ! audioconvert ! audioresample " +
                "! volume name=volumeElement volume=" + currentVolume + " ! autoaudiosink";
        Pipeline p = (Pipeline) Gst.parseLaunch(desc);
        p.getBus().connect((Bus.ERROR) (source, code, message) ->
                LoggingManager.error("[MediaPlayer A][ERROR] GStreamer: " + message));

        p.getBus().connect((Bus.EOS) source -> {
            LoggingManager.info("Got EOS, looping back to start");
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
        });

        return p;
    }

    private void configureVideoSink(AppSink sink) {
        sink.set("emit-signals", true);
        sink.set("sync", true);
        sink.connect((AppSink.NEW_SAMPLE) elem -> {
            Sample s = elem.pullSample();
            if (s == null || !captureSamples) return FlowReturn.OK;
            try {
                currentFrame = sampleToImage(s, currentFrame);
                prepareBufferAsync();
            } finally {
                s.dispose();
            }
            return FlowReturn.OK;
        });
    }

    // === FRAME PROCESSING ================================================================
    private static BufferedImage sampleToImage(Sample sample, BufferedImage img) {
        Structure st = sample.getCaps().getStructure(0);
        int w = st.getInteger("width"), h = st.getInteger("height");
        Buffer buf = sample.getBuffer();
        ByteBuffer bb = buf.map(false);
        try {
            if (img == null || img.getWidth() != w || img.getHeight() != h)
                img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
            byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            byte[] src = new byte[dst.length];
            bb.get(src);
            for (int i = 0; i < src.length; i += 4) {
                byte r = src[i], g = src[i + 1], b = src[i + 2], a = src[i + 3];
                dst[i] = a; dst[i + 1] = b; dst[i + 2] = g; dst[i + 3] = r;
            }
        } finally { buf.unmap(); }
        return img;
    }

    private void prepareBufferAsync() {
        if (currentFrame == null) return;
        int w = screen.textureWidth, h = screen.textureHeight;
        if (w == 0 || h == 0) return;
        try { frameExecutor.submit(this::prepareBuffer); }
        catch (RejectedExecutionException ignored) {}
    }

    private void prepareBuffer() {
        int w = screen.textureWidth, h = screen.textureHeight;
        if (w == 0 || h == 0) return;

        if (textureImage == null || textureImage.getWidth() != w || textureImage.getHeight() != h)
            textureImage = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);

        Graphics2D g = textureImage.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, w, h);
        g.setComposite(AlphaComposite.SrcOver);

        double scale = Math.max((double) w / currentFrame.getWidth(),
                (double) h / currentFrame.getHeight());
        int sw = (int) Math.round(currentFrame.getWidth()  * scale);
        int sh = (int) Math.round(currentFrame.getHeight() * scale);
        int x  = (w - sw) / 2, y = (h - sh) / 2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(currentFrame, x, y, sw, sh, null);
        g.dispose();

        preparedBuffer = imageToDirect(textureImage);
        preparedW = w; preparedH = h;
    }

    private static ByteBuffer imageToDirect(BufferedImage img) {
        byte[] abgr = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        ByteBuffer buf = ByteBuffer.allocateDirect(abgr.length).order(ByteOrder.nativeOrder());
        for (int i = 0; i < abgr.length; i += 4) {
            byte a = abgr[i], b = abgr[i + 1], g = abgr[i + 2], r = abgr[i + 3];
            buf.put(r).put(g).put(b).put(a);
        }
        buf.flip();
        return buf;
    }

    // === PLAYBACK HELPERS ================================================================
    private void doPlay() {
        if (!initialized) return;
        audioPipeline.play();
        Clock c = audioPipeline.getClock();
        if (c != null && videoPipeline != null) videoPipeline.setClock(c);
        if (!screen.getPaused()) videoPipeline.play();
        else {
            videoPipeline.play();
            videoPipeline.pause();
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

    private void doSeek(long ns, boolean b) {
        if (!initialized) return;
        EnumSet<SeekFlags> flags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE);
        audioPipeline.pause();
        if (videoPipeline != null) videoPipeline.pause();
        if (videoPipeline != null) videoPipeline.seekSimple(Format.TIME, flags, ns);
        audioPipeline.seekSimple(Format.TIME, flags, ns);
        if (videoPipeline != null) videoPipeline.getState(); // Waiting for pre-roll
        audioPipeline.play();
        if (videoPipeline != null && !screen.getPaused()) videoPipeline.play();

        if (b) screen.afterSeek();
    }

    private void applyVolume() {
        if (!initialized) return;
        Element v = audioPipeline.getElementByName("volumeElement");
        if (v != null) v.set("volume", currentVolume);
    }

    // === QUALITY HELPERS =================================================================
    private Optional<Stream> pickVideo(int target) {
        return availableVideoStreams.stream()
                .filter(s -> s.getResolution() != null)
                .min(Comparator.comparingInt(s -> Math.abs(parseQuality(s) - target)));
    }

    private static int parseQuality(Stream s) {
        try { return Integer.parseInt(s.getResolution().replaceAll("\\D+", "")); }
        catch (Exception e) { return Integer.MAX_VALUE; }
    }

    private void changeQuality(String desired) {
        if (!initialized || availableVideoStreams == null) return;
        int target;
        try { target = Integer.parseInt(desired.replaceAll("\\D+", "")); }
        catch (NumberFormatException e) { return; }
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
        Clock clock = audioPipeline.getClock();
        if (clock != null) newVid.setClock(clock);
        newVid.pause();

        // Pre-roll the pipeline to ensure it's ready
        newVid.getState();

        EnumSet<SeekFlags> flags = EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE);
        audioPipeline.seekSimple(Format.TIME, flags, pos);
        newVid.seekSimple (Format.TIME, flags, pos);

        audioPipeline.play();
        if (!screen.getPaused()) newVid.play();

        videoPipeline      = newVid;
        currentVideoStream = chosen;
        lastQuality        = parseQuality(chosen);
    }

    // === TICK ================================================================
    public void tick(BlockPos playerPos, double maxRadius) {
        if (!initialized) return;
        double dist = screen.getDistanceToScreen(playerPos);
        double attenuation = Math.pow(1.0 - Math.min(1.0, dist / maxRadius), 2);
        if (Math.abs(attenuation - lastAttenuation) < 1e-5) return;

        lastAttenuation = attenuation;
        currentVolume = userVolume * attenuation;
        safeExecute(this::applyVolume);
    }

    // === CONCURRENCY HELPERS =============================================================
    private void safeExecute(Runnable r) {
        if (!gstExecutor.isShutdown()) {
            try { gstExecutor.submit(r); } catch (RejectedExecutionException ignored) {}
        }
    }

    private static void safeStopAndDispose(Element e) {
        if (e == null) return;
        try { e.setState(State.NULL); } catch (Exception ignore) {}
        try { e.dispose(); }           catch (Exception ignore) {}
    }
}
