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
            Math.clamp(Runtime.getRuntime().availableProcessors() * 2L, 4, 8),
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

    private static void download(String videoId, String url) {
        try {
            byte[] cached = Objects.requireNonNull(readDiskCache(videoId));
            Minecraft.getInstance().execute(() -> register(videoId, cached));
            byte[] finalBytes = Objects.requireNonNull(fetch(url));
            writeDiskCacheAsync(videoId, finalBytes);
            Minecraft.getInstance().execute(() -> register(videoId, finalBytes));
        } catch (Exception e) {
            LoggingManager.warn("Thumbnail fetch failed for " + videoId + ": " + e.getMessage());
            IN_FLIGHT.remove(videoId);
        }
    }

    private static final java.nio.file.Path THUMB_CACHE_DIR =
            java.nio.file.Path.of("config", "dreamdisplays", "thumb-cache");
    private static final long THUMB_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1_000L;

    private static byte @Nullable [] readDiskCache(String videoId) {
        try {
            java.io.File f = thumbFile(videoId);
            if (!f.isFile()) return null;
            if (System.currentTimeMillis() - f.lastModified() > THUMB_CACHE_TTL_MS) {
                f.delete();
                return null;
            }
            return java.nio.file.Files.readAllBytes(f.toPath());
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeDiskCacheAsync(String videoId, byte[] bytes) {
        EXECUTOR.submit(() -> {
            try {
                java.nio.file.Files.createDirectories(THUMB_CACHE_DIR);
                java.io.File target = thumbFile(videoId);
                java.io.File tmp = new java.io.File(target.getParentFile(),
                        target.getName() + ".tmp");
                java.nio.file.Files.write(tmp.toPath(), bytes);
                java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
            }
        });
    }

    private static java.io.File thumbFile(String videoId) {
        String safe = videoId.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
        return new java.io.File(THUMB_CACHE_DIR.toFile(), safe + ".jpg");
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
                    image.setPixel(x, y, argb[y * w + x]);
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
