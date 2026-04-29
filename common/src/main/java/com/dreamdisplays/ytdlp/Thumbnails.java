package com.dreamdisplays.ytdlp;

import com.dreamdisplays.Initializer;
import com.mojang.blaze3d.platform.NativeImage;
import me.inotsleep.utils.logging.LoggingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@NullMarked
public final class Thumbnails {

    private static final ConcurrentHashMap<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.clamp(Runtime.getRuntime().availableProcessors() * 2, 4, 8),
            r -> {
        Thread t = new Thread(r, "DD-Thumbnail-" + COUNTER.incrementAndGet());
        t.setDaemon(true);
        return t;
            }
    );

    static {
        try {
            ImageIO.scanForPlugins();
        } catch (Throwable t) {
            LoggingManager.warn("ImageIO.scanForPlugins failed: " + t.getMessage());
        }
    }

    private Thumbnails() {
    }

    public static @Nullable Identifier get(String videoId) {
        return READY.get(videoId);
    }

    public static void request(String videoId, String url) {
        if (READY.containsKey(videoId)) return;
        if (IN_FLIGHT.putIfAbsent(videoId, Boolean.TRUE) != null) return;
        EXECUTOR.submit(() -> download(videoId, url));
    }

    public static void requestBatch(Iterable<YtVideoInfo> infos) {
        for (YtVideoInfo info : infos) {
            request(info.getId(), info.getThumbnailUrl());
        }
    }

    private static void download(String videoId, String url) {
        try {
            byte[] finalBytes = Objects.requireNonNull(fetch(url));
            Minecraft.getInstance().execute(() -> register(videoId, finalBytes));
        } catch (Exception e) {
            LoggingManager.warn("Thumbnail fetch failed for " + videoId + ": " + e.getMessage());
            IN_FLIGHT.remove(videoId);
        }
    }

    private static void register(String videoId, byte[] bytes) {
        try {
            NativeImage image = decode(bytes);
            DynamicTexture tex = new DynamicTexture(() -> "yt-thumb-" + videoId, image);
            String safe = videoId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            Identifier id = Identifier.fromNamespaceAndPath(
                    Initializer.MOD_ID, "yt_thumb/" + safe
            );
            Minecraft.getInstance().getTextureManager().register(id, tex);
            READY.put(videoId, id);
        } catch (IOException e) {
            LoggingManager.warn("Thumbnail decode failed for " + videoId + ": " + e.getMessage());
        } finally {
            IN_FLIGHT.remove(videoId);
        }
    }

    // TODO: fix
    private static NativeImage decode(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                String head = bytes.length >= 4
                        ? String.format("%02X %02X %02X %02X",
                                bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF)
                        : "<empty>";
                throw new IOException("Unsupported image format (first bytes: " + head
                        + ", size=" + bytes.length + ")");
            }
            int w = src.getWidth();
            int h = src.getHeight();
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            int[] argb = new int[w * h];
            src.getRGB(0, 0, w, h, argb, 0, w);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = argb[y * w + x];
                    int a = (p >>> 24) & 0xFF;
                    int r = (p >>> 16) & 0xFF;
                    int g = (p >>> 8) & 0xFF;
                    int b = p & 0xFF;
                    image.setPixel(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            return image;
        }
    }

    private static byte @Nullable [] fetch(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 Dream Displays");
            conn.setRequestProperty("Accept", "image/jpeg,image/png");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
