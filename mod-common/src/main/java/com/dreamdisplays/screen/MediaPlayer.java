package com.dreamdisplays.screen;

import com.dreamdisplays.Initializer;
import com.github.felipeucelli.javatube.Stream;
import com.github.felipeucelli.javatube.Youtube;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import me.inotsleep.utils.logging.LoggingManager;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.sound.sampled.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@NullMarked
public class MediaPlayer {

    // Constants
    private static final String MIME_VIDEO = "video/webm";
    private static final String MIME_AUDIO = "audio/webm";
    private static final String USER_AGENT_V = "ANDROID_VR";
    private static final ExecutorService INIT_EXECUTOR =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-init"));
    public static boolean captureSamples = true;
    private static volatile long conversionTimeTotal = 0;
    private static volatile int conversionCount = 0;
    private static volatile boolean loggedImageToDirect = false;
    private static volatile boolean useNativeConversion = false; // Temporarily disabled
    private final String lang;
    // Public fields
    private final String youtubeUrl;
    // Executors and state flags
    private final ExecutorService videoExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-video"));
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-audio"));
    private final ExecutorService frameExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "MediaPlayer-frame"));
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(true);
    private final Screen screen;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private volatile double currentVolume;
    // FFmpeg fields
    private volatile @Nullable FFmpegFrameGrabber videoGrabber;
    private volatile @Nullable FFmpegFrameGrabber audioGrabber;
    private volatile @Nullable SourceDataLine audioLine;
    private volatile java.util.@Nullable List<Stream> availableVideoStreams;
    private volatile @Nullable Stream currentVideoStream;
    private volatile boolean initialized;
    private int lastQuality;
    // Frame buffering
    private org.bytedeco.javacv.@Nullable Frame currentVideoFrame;
    private volatile @Nullable ByteBuffer preparedBuffer;
    private volatile int lastTexW = 0, lastTexH = 0;
    private volatile int preparedW = 0, preparedH = 0;
    private volatile double userVolume = (Initializer.config.defaultDisplayVolume);
    private volatile double lastAttenuation = 1.0;
    private @Nullable BufferedImage textureImage;
    private volatile long videoTimestamp = 0;
    private volatile long audioTimestamp = 0;
    private volatile long startTime = 0;
    private volatile int cachedAudioChannels = 2; // Cached to avoid calling getAudioChannels() in loop
    // Diagnostics
    private volatile long videoFramesDecoded = 0;
    private volatile long audioFramesDecoded = 0;
    private volatile long framesRendered = 0;
    private volatile long lastDiagnosticTime = 0;
    private volatile boolean loggedTextureFilled = false;
    private volatile boolean loggedFirstFrame = false;
    // Frame processing
    private volatile boolean loggedFirstPrepare = false;
    private volatile boolean loggedFirstBufferPrepare = false;

    // Constructor
    public MediaPlayer(String youtubeUrl, String lang, Screen screen) {
        this.youtubeUrl = youtubeUrl;
        this.screen = screen;
        this.lang = lang;

        try {
            LoggingManager.info("Initializing MediaPlayer with FFmpeg");
            avutil.av_log_set_level(avutil.AV_LOG_INFO);
            LoggingManager.info("FFmpeg log level set");
        } catch (Exception e) {
            LoggingManager.error("Failed to set FFmpeg log level", e);
        }

        INIT_EXECUTOR.submit(this::initialize);
    }

    private static ByteBuffer imageToDirect(BufferedImage img) {
        if (!loggedImageToDirect) {
            LoggingManager.info("imageToDirect: Extracting ABGR data from BufferedImage...");
        }
        byte[] abgr = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();

        if (!loggedImageToDirect) {
            LoggingManager.info("imageToDirect: Allocating direct ByteBuffer (" + abgr.length + " bytes)...");
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(abgr.length).order(ByteOrder.nativeOrder());

        if (useNativeConversion) {
            // Native conversion is temporarily disabled
            if (!loggedImageToDirect) {
                LoggingManager.info("imageToDirect: Using native ABGR→RGBA converter...");
            }
            try {
                Converter.abgrToRgbaDirect(abgr, buf, abgr.length);
            } catch (Exception e) {
                LoggingManager.error("Native conversion failed, falling back to Java", e);
                useNativeConversion = false;
                convertABGRtoRGBAJava(abgr, buf);
            }
        } else {
            // Java conversion
            if (!loggedImageToDirect) {
                LoggingManager.info("imageToDirect: Using Java ABGR -> RGBA converter...");
            }
            long startConv = System.nanoTime();
            convertABGRtoRGBAJava(abgr, buf);
            long endConv = System.nanoTime();

            // Update diagnostics
            conversionTimeTotal += (endConv - startConv);
            conversionCount++;
        }

        if (!loggedImageToDirect) {
            LoggingManager.info("imageToDirect: Conversion complete, flipping buffer...");
            loggedImageToDirect = true;
        }

        buf.position(abgr.length);
        buf.flip();

        return buf;
    }

    private static void convertABGRtoRGBAJava(byte[] abgr, ByteBuffer rgba) {
        final int chunkSize = 4096;
        int length = abgr.length;
        byte[] temp = new byte[chunkSize];

        for (int base = 0; base < length; base += chunkSize) {
            int size = Math.min(chunkSize, length - base);

            System.arraycopy(abgr, base, temp, 0, size);

            for (int i = 0; i < size; i += 4) {
                byte a = temp[i];
                byte b = temp[i + 1];
                byte g = temp[i + 2];
                byte r = temp[i + 3];

                temp[i] = r;
                temp[i + 1] = g;
                temp[i + 2] = b;
                temp[i + 3] = a;
            }

            rgba.put(temp, 0, size);
        }
    }

    // Public API
    public void play() {
        LoggingManager.info("play() called - initialized: " + initialized + ", playing: " + playing.get());
        if (!initialized) {
            LoggingManager.warn("Cannot play - not initialized yet");
            return;
        }
        paused.set(false);
        if (!playing.get()) {
            playing.set(true);
            startTime = System.nanoTime();
            LoggingManager.info("Starting playback threads...");
            videoExecutor.submit(this::videoPlaybackLoop);
            audioExecutor.submit(this::audioPlaybackLoop);
            LoggingManager.info("Playback threads submitted");
        }
        if (audioLine != null && !audioLine.isRunning()) {
            audioLine.start();
            LoggingManager.info("Audio line started");
        }
    }

    public void pause() {
        if (!initialized) return;
        paused.set(true);
        if (audioLine != null && audioLine.isRunning()) {
            audioLine.stop();
        }
    }

    public void seekTo(long ns, boolean notify) {
        if (!initialized || videoGrabber == null) return;
        try {
            long timestampMicros = ns / 1000;
            videoGrabber.setTimestamp(timestampMicros);
            if (audioGrabber != null) {
                audioGrabber.setTimestamp(timestampMicros);
            }
            videoTimestamp = timestampMicros;
            audioTimestamp = timestampMicros;
            startTime = System.nanoTime() - ns;

            if (notify) {
                screen.afterSeek();
            }
        } catch (Exception e) {
            LoggingManager.error("Failed to seek", e);
        }
    }

    public void seekRelative(double seconds) {
        if (!initialized) return;
        long currentNs = getCurrentTime();
        long targetNs = Math.max(0, currentNs + (long) (seconds * 1e9));
        long durationNs = getDuration();
        seekTo(Math.min(targetNs, durationNs - 1000000), true);
    }

    public long getCurrentTime() {
        if (!initialized || videoGrabber == null) return 0;
        return videoTimestamp * 1000;
    }

    public long getDuration() {
        if (!initialized || videoGrabber == null) return 0;
        return videoGrabber.getLengthInTime() * 1000;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void stop() {
        if (terminated.getAndSet(true)) return;
        playing.set(false);
        paused.set(true);

        try {
            if (audioLine != null) {
                audioLine.stop();
                audioLine.close();
            }

            if (videoGrabber != null) {
                videoGrabber.stop();
                videoGrabber.release();
            }

            if (audioGrabber != null) {
                audioGrabber.stop();
                audioGrabber.release();
            }

            videoExecutor.shutdown();
            audioExecutor.shutdown();
            frameExecutor.shutdown();
        } catch (Exception e) {
            LoggingManager.error("Error stopping MediaPlayer", e);
        }
    }

    public void setVolume(double volume) {
        userVolume = Math.max(0, Math.min(1, volume));
        currentVolume = userVolume * lastAttenuation;

        if (audioLine != null && audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log10(Math.max(0.0001, currentVolume)) * 20.0);
            float min = gainControl.getMinimum();
            float max = gainControl.getMaximum();
            gainControl.setValue(Math.max(min, Math.min(max, dB)));
        }
    }

    public boolean textureFilled() {
        boolean filled = preparedBuffer != null && preparedBuffer.capacity() > 0;
        if (!loggedTextureFilled && filled) {
            LoggingManager.info("textureFilled() returning true - buffer is ready");
            loggedTextureFilled = true;
        }
        return filled;
    }

    public void updateFrame(GpuTexture texture) {
        if (preparedBuffer == null) return;
        int w = screen.textureWidth, h = screen.textureHeight;
        if (w != preparedW || h != preparedH) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

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
            encoder.writeToTexture(
                texture,
                preparedBuffer,
                NativeImage.Format.RGBA,
                0, 0, 0, 0,
                texture.getWidth(0), texture.getHeight(0)
            );

            framesRendered++;

            if (!loggedFirstFrame) {
                LoggingManager.info("First frame uploaded to GPU texture");
                loggedFirstFrame = true;
            }
        }
    }

    public List<Integer> getAvailableQualities() {
        if (!initialized || availableVideoStreams == null) return Collections.emptyList();
        return availableVideoStreams.stream()
            .map(Stream::getResolution)
            .filter(Objects::nonNull)
            .map(r -> Integer.parseInt(r.replaceAll("\\D+", "")))
            .distinct()
            .filter(r -> r <= (Initializer.isPremium ? 2160 : 1080))
            .sorted()
            .collect(Collectors.toList());
    }

    public void setQuality(String q) {
        CompletableFuture.runAsync(() -> changeQuality(q));
    }

    // Initialization
    private void initialize() {
        try {
            // Use the same client for both video and audio to avoid 403 errors
            Youtube yt = new Youtube(youtubeUrl, USER_AGENT_V);
            List<Stream> all = yt.streams().getAll();

            availableVideoStreams = all.stream()
                .filter(s -> MIME_VIDEO.equals(s.getMimeType()))
                .toList();

            Optional<Stream> videoOpt = pickVideo(Integer.parseInt(screen.getQuality().replace("p", "")))
                .or(() -> availableVideoStreams.stream().findFirst());

            // Get audio from the same client to maintain consistent tokens/cookies
            Optional<Stream> audioOpt = all.stream()
                .filter(s -> MIME_AUDIO.equals(s.getMimeType()))
                .filter(s -> (s.getAudioTrackId() != null && s.getAudioTrackId().contains(lang)) ||
                    (s.getAudioTrackName() != null && s.getAudioTrackName().contains(lang)))
                .reduce((f, n) -> n);

            if (audioOpt.isEmpty()) {
                LoggingManager.warn("No audio stream available with lang " + lang);
                LoggingManager.warn("Choosing first available audio stream...");

                audioOpt = all.stream()
                    .filter(s -> MIME_AUDIO.equals(s.getMimeType()))
                    .reduce((f, n) -> n);
            }

            if (videoOpt.isEmpty() || audioOpt.isEmpty()) {
                LoggingManager.error("No streams available");
                return;
            }

            currentVideoStream = videoOpt.get();
            lastQuality = parseQuality(currentVideoStream);

            LoggingManager.info("Initializing video and audio grabbers...");
            initVideoGrabber(currentVideoStream.getUrl());
            initAudioGrabber(audioOpt.get().getUrl());

            LoggingManager.info("Setting initialized flag to true");
            initialized = true;
            LoggingManager.info("MediaPlayer fully initialized");
            LoggingManager.info("Ready for playback - call play() to start");
        } catch (Exception e) {
            LoggingManager.error("Failed to initialize MediaPlayer", e);
            screen.errored = true;
            initialized = false;
        }
    }

    private void initVideoGrabber(String url) throws Exception {
        LoggingManager.info("Initializing video grabber for URL: " + url.substring(0, Math.min(100, url.length())) + "...");

        videoGrabber = new FFmpegFrameGrabber(url);
        LoggingManager.info("FFmpegFrameGrabber created");

        videoGrabber.setOption("user_agent", USER_AGENT_V);
        videoGrabber.setOption("referer", "https://www.youtube.com");
        videoGrabber.setOption("headers", "Origin: https://www.youtube.com\r\n");
        videoGrabber.setOption("timeout", "10000000");
        videoGrabber.setVideoOption("threads", "auto");

        LoggingManager.info("Starting video grabber...");
        videoGrabber.start();
        LoggingManager.info("Video grabber started successfully!");

        LoggingManager.info("Video grabber info: " + videoGrabber.getImageWidth() + "x" + videoGrabber.getImageHeight() +
            " @ " + videoGrabber.getFrameRate() + " fps, format: " + videoGrabber.getFormat());
    }

    private void initAudioGrabber(String url) {
        try {
            LoggingManager.info("Initializing audio grabber for URL: " + url.substring(0, Math.min(100, url.length())) + "...");

            audioGrabber = new FFmpegFrameGrabber(url);
            // Use same user agent as video to maintain session consistency
            audioGrabber.setOption("user_agent", USER_AGENT_V);
            audioGrabber.setOption("referer", "https://www.youtube.com");
            audioGrabber.setOption("headers", "Origin: https://www.youtube.com\r\n");
            // Set timeout to avoid hanging
            audioGrabber.setOption("timeout", "10000000");

            LoggingManager.info("Starting audio grabber...");
            audioGrabber.start();
            LoggingManager.info("Audio grabber started successfully!");

            // Initialize audio line
            LoggingManager.info("Getting sample rate from audio grabber...");
            int sampleRate = audioGrabber.getSampleRate();
            LoggingManager.info("Sample rate: " + sampleRate);

            LoggingManager.info("Getting channel count from audio grabber...");
            int channels;
            try {
                // Use ExecutorService with timeout to avoid hanging
                Future<Integer> channelFuture = Executors.newSingleThreadExecutor().submit(() -> audioGrabber.getAudioChannels());
                channels = channelFuture.get(5, TimeUnit.SECONDS);
                LoggingManager.info("Channels: " + channels);
            } catch (Exception e) {
                LoggingManager.warn("Failed to get channel count, defaulting to stereo (2 channels)", e);
                channels = 2; // Default to stereo
            }

            LoggingManager.info("Audio format: " + sampleRate + " Hz, " + channels + " channels");

            AudioFormat format = new AudioFormat(
                sampleRate,
                16,
                channels,
                true,
                false
            );

            LoggingManager.info("Getting audio line from system...");
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            LoggingManager.info("Audio line obtained, opening...");

            audioLine.open(format);
            LoggingManager.info("Audio line opened successfully");

            // Start the line immediately so it's ready to receive data
            audioLine.start();
            LoggingManager.info("Audio line started (pre-play)");

            setVolume(userVolume);

            cachedAudioChannels = channels; // Cache for use in playback loop
            LoggingManager.info("✓ Audio grabber fully initialized: " + sampleRate + " Hz, " + channels + " channels");
        } catch (Exception e) {
            LoggingManager.error("Failed to initialize audio, continuing without sound", e);
            // Continue without audio :/
            audioGrabber = null;
            audioLine = null;
        }
    }

    // Playback loops
    private void videoPlaybackLoop() {
        LoggingManager.info("Video playback loop started");
        try {
            int frameCount = 0;
            while (!terminated.get() && playing.get()) {
                if (paused.get()) {
                    Thread.sleep(10);
                    continue;
                }

                if (videoGrabber == null) {
                    LoggingManager.error("Video grabber is null!");
                    break;
                }

                org.bytedeco.javacv.Frame frame = videoGrabber.grabImage();
                if (frame == null) {
                    // End of stream, loop back
                    LoggingManager.info("Video stream ended, looping...");
                    seekTo(0, false);
                    continue;
                }

                frameCount++;
                if (frameCount == 1) {
                    LoggingManager.info("First video frame grabbed successfully!");
                }

                videoTimestamp = frame.timestamp;
                videoFramesDecoded++;

                if (captureSamples && frame.image != null) {
                    currentVideoFrame = frame.clone();
                    prepareBufferAsync();
                }

                // Sync with audio
                long targetTime = startTime + (videoTimestamp * 1000);
                long currentTime = System.nanoTime();
                long sleepTime = (targetTime - currentTime) / 1_000_000;

                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }
        } catch (Exception e) {
            if (!terminated.get()) {
                LoggingManager.error("Video playback error", e);
                screen.errored = true;
            }
        }
    }

    private void audioPlaybackLoop() {
        LoggingManager.info("Audio playback loop started - audioGrabber: " + (audioGrabber != null) + ", audioLine: " + (audioLine != null));

        if (audioGrabber == null) {
            LoggingManager.error("Audio grabber is null - audio disabled!");
            return;
        }

        if (audioLine == null) {
            LoggingManager.error("Audio line is null - audio disabled!");
            return;
        }

        try {
            int sampleCount = 0;
            int nullFrames = 0;
            long lastLogTime = System.currentTimeMillis();

            LoggingManager.info("Starting audio grabbing loop...");

            while (!terminated.get() && playing.get()) {
                if (paused.get()) {
                    Thread.sleep(10);
                    continue;
                }

                org.bytedeco.javacv.Frame frame = audioGrabber.grabSamples();
                if (frame == null) {
                    nullFrames++;

                    // Log every second if null frames persist
                    long now = System.currentTimeMillis();
                    if (now - lastLogTime > 1000) {
                        LoggingManager.warn("Audio grabSamples() returning null constantly! Total nulls: " + nullFrames + ", decoded: " + sampleCount);
                        lastLogTime = now;
                    }

                    Thread.sleep(1); // Small sleep to avoid busy wait
                    continue;
                }

                sampleCount++;
                audioFramesDecoded++;

                if (sampleCount == 1) {
                    LoggingManager.info("First audio samples grabbed successfully");
                }

                audioTimestamp = frame.timestamp;

                if (frame.samples != null) {
                    ShortBuffer[] samples = (ShortBuffer[]) frame.samples;
                    int channels = cachedAudioChannels;
                    int samplesPerChannel = samples[0].limit();

                    byte[] audioBytes = new byte[samplesPerChannel * channels * 2];
                    int idx = 0;

                    for (int i = 0; i < samplesPerChannel; i++) {
                        for (int c = 0; c < channels; c++) {
                            short sample = samples[c].get(i);
                            audioBytes[idx++] = (byte) (sample & 0xFF);
                            audioBytes[idx++] = (byte) ((sample >> 8) & 0xFF);
                        }
                    }

                    int written = audioLine.write(audioBytes, 0, audioBytes.length);
                    if (sampleCount <= 3) {
                        LoggingManager.info("Audio samples written: " + written + "/" + audioBytes.length + " bytes");
                    }
                } else {
                    if (sampleCount <= 3) {
                        LoggingManager.warn("Audio frame.samples is null!");
                    }
                }
            }
        } catch (Exception e) {
            if (!terminated.get()) {
                LoggingManager.error("Audio playback error", e);
            }
        }
    }

    private void prepareBufferAsync() {
        if (currentVideoFrame == null) {
            if (!loggedFirstPrepare) {
                LoggingManager.warn("prepareBufferAsync: currentVideoFrame is null");
            }
            return;
        }
        int w = screen.textureWidth, h = screen.textureHeight;
        if (w == 0 || h == 0) {
            if (!loggedFirstPrepare) {
                LoggingManager.warn("prepareBufferAsync: texture dimensions are " + w + "x" + h);
            }
            return;
        }
        if (!loggedFirstPrepare) {
            LoggingManager.info("Submitting first frame to prepare buffer (" + w + "x" + h + ")");
            loggedFirstPrepare = true;
        }
        try {
            frameExecutor.submit(this::prepareBuffer);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void prepareBuffer() {
        if (currentVideoFrame == null) return;
        int w = screen.textureWidth, h = screen.textureHeight;
        if (w == 0 || h == 0) return;

        try {
            if (!loggedFirstBufferPrepare) {
                LoggingManager.info("Converting frame to BufferedImage...");
            }
            BufferedImage frameImage = converter.convert(currentVideoFrame);
            if (frameImage == null) {
                if (!loggedFirstBufferPrepare) {
                    LoggingManager.error("Failed to convert frame to BufferedImage!");
                }
                return;
            }

            if (!loggedFirstBufferPrepare) {
                LoggingManager.info("Frame converted: " + frameImage.getWidth() + "x" + frameImage.getHeight());
            }

            if (textureImage == null || textureImage.getWidth() != w || textureImage.getHeight() != h) {
                textureImage = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
            }

            Graphics2D g = textureImage.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver);

            double scale = Math.max((double) w / frameImage.getWidth(),
                (double) h / frameImage.getHeight());
            int sw = (int) Math.round(frameImage.getWidth() * scale);
            int sh = (int) Math.round(frameImage.getHeight() * scale);
            int x = (w - sw) / 2, y = (h - sh) / 2;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(frameImage, x, y, sw, sh, null);
            g.dispose();

            if (!loggedFirstBufferPrepare) {
                LoggingManager.info("Converting BufferedImage to direct ByteBuffer...");
            }

            try {
                preparedBuffer = imageToDirect(textureImage);
                preparedW = w;
                preparedH = h;

                if (!loggedFirstBufferPrepare) {
                    LoggingManager.info("Buffer prepared successfully. Size: " + preparedBuffer.capacity() + " bytes, dimensions: " + preparedW + "x" + preparedH);
                    loggedFirstBufferPrepare = true;
                }
            } catch (Exception convertEx) {
                LoggingManager.error("Failed to convert image to direct buffer", convertEx);
                preparedBuffer = null;
            }
        } catch (Exception e) {
            LoggingManager.error("Error preparing buffer", e);
        }
    }

    // Quality switching
    private void changeQuality(String targetQuality) {
        if (!initialized || availableVideoStreams == null) return;

        int q = Integer.parseInt(targetQuality.replace("p", ""));
        Optional<Stream> newStream = pickVideo(q);

        if (newStream.isEmpty() || newStream.get().equals(currentVideoStream)) {
            return;
        }

        try {
            long currentPos = getCurrentTime();
            boolean wasPlaying = !paused.get();

            pause();

            if (videoGrabber != null) {
                videoGrabber.stop();
                videoGrabber.release();
            }

            currentVideoStream = newStream.get();
            lastQuality = parseQuality(currentVideoStream);

            initVideoGrabber(currentVideoStream.getUrl());

            seekTo(currentPos, false);

            if (wasPlaying) {
                play();
            }

            LoggingManager.info("Quality changed to " + targetQuality);
        } catch (Exception e) {
            LoggingManager.error("Failed to change quality", e);
        }
    }

    private Optional<Stream> pickVideo(int targetQuality) {
        if (availableVideoStreams == null) return Optional.empty();

        return availableVideoStreams.stream()
            .filter(s -> {
                String res = s.getResolution();
                if (res == null) return false;
                int q = Integer.parseInt(res.replaceAll("\\D+", ""));
                return q <= targetQuality;
            })
            .max(Comparator.comparingInt(s -> {
                String res = s.getResolution();
                return res == null ? 0 : Integer.parseInt(res.replaceAll("\\D+", ""));
            }));
    }

    private int parseQuality(Stream stream) {
        String res = stream.getResolution();
        if (res == null) return 0;
        return Integer.parseInt(res.replaceAll("\\D+", ""));
    }

    public void updateAttenuation(double attenuation) {
        lastAttenuation = Math.max(0, Math.min(1, attenuation));
        currentVolume = userVolume * lastAttenuation;
        setVolume(userVolume);
    }

    private void printDiagnostics() {
        int bufferFillPercent = 0;
        if (preparedBuffer != null) {
            bufferFillPercent = (preparedBuffer.capacity() > 0) ? 100 : 0;
        }

        long avgConversionTime = (conversionCount > 0) ? (conversionTimeTotal / conversionCount) : 0;

        LoggingManager.info("Video frames decoded: " + videoFramesDecoded + " | Audio frames: " + audioFramesDecoded);
        LoggingManager.info("Frames rendered: " + framesRendered + " | Buffer: " + bufferFillPercent + "%");
        LoggingManager.info("Avg conversion time: " + avgConversionTime + "ns | Playing: " + playing.get() + " | Paused: " + paused.get());
        LoggingManager.info("Video timestamp: " + (videoTimestamp / 1000) + "ms | Audio timestamp: " + (audioTimestamp / 1000) + "ms");
        LoggingManager.info("Audio grabber: " + (audioGrabber != null) + " | Audio line: " + (audioLine != null) + " | Line running: " + (audioLine != null && audioLine.isRunning()));
    }

    public void tick(net.minecraft.core.BlockPos playerPos, int maxDistance) {
        if (!initialized) return;

        // Periodic diagnostics (every 5 seconds)
        long now = System.currentTimeMillis();
        if (now - lastDiagnosticTime > 5000) {
            printDiagnostics();
            lastDiagnosticTime = now;
        }

        // Calculate distance-based attenuation
        int dx = playerPos.getX() - screen.getX();
        int dy = playerPos.getY() - screen.getY();
        int dz = playerPos.getZ() - screen.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double attenuation = 1.0;
        if (distance > maxDistance * 0.5) {
            attenuation = Math.max(0, 1.0 - (distance - maxDistance * 0.5) / (maxDistance * 0.5));
        }

        updateAttenuation(attenuation);
    }
}
