package com.dreamdisplays.ytdlp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persistent on-disk cache for resolved YouTube format URLs. YouTube signed URLs are
 * typically valid for ~6 hours, so caching across sessions removes the cold-start
 * yt-dlp invocation when a player rejoins a server with the same displays.
 */
@NullMarked
public final class FormatDiskCache {

    private static final Path CACHE_DIR = Path.of("config", "dreamdisplays", "yt-cache");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type STREAM_LIST_TYPE = new TypeToken<List<YtStream>>() {
    }.getType();
    private static final long DEFAULT_TTL_MS = 5L * 60L * 60L * 1_000L; // 5h

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DD-FormatCache-writer");
        t.setDaemon(true);
        return t;
    });

    private FormatDiskCache() {
    }

    public static @Nullable List<YtStream> load(String videoUrl, long maxAgeMs) {
        File f = fileFor(videoUrl);
        if (!f.isFile()) return null;
        try {
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            long ts = obj.has("ts") ? obj.get("ts").getAsLong() : 0L;
            if (System.currentTimeMillis() - ts > maxAgeMs) {
                f.delete();
                return null;
            }
            List<YtStream> streams = GSON.fromJson(obj.get("streams"), STREAM_LIST_TYPE);
            return streams == null || streams.isEmpty() ? null : streams;
        } catch (Exception e) {
            // Treat corrupted entries as cache miss
            try {
                f.delete();
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    public static @Nullable List<YtStream> load(String videoUrl) {
        return load(videoUrl, DEFAULT_TTL_MS);
    }

    public static void saveAsync(String videoUrl, List<YtStream> streams) {
        if (streams.isEmpty()) return;
        WRITER.submit(() -> writeNow(videoUrl, streams));
    }

    private static void writeNow(String videoUrl, List<YtStream> streams) {
        try {
            Files.createDirectories(CACHE_DIR);
            File target = fileFor(videoUrl);
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            JsonObject root = new JsonObject();
            root.addProperty("ts", System.currentTimeMillis());
            root.addProperty("url", videoUrl);
            root.add("streams", GSON.toJsonTree(streams, STREAM_LIST_TYPE));
            Files.writeString(tmp.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LoggingManager.warn("FormatDiskCache write failed: " + e.getMessage());
        }
    }

    public static void sweepExpired() {
        sweepExpired(DEFAULT_TTL_MS);
    }

    public static void sweepExpired(long maxAgeMs) {
        try {
            if (!Files.isDirectory(CACHE_DIR)) return;
            long now = System.currentTimeMillis();
            try (var stream = Files.list(CACHE_DIR)) {
                stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    try {
                        String json = Files.readString(p, StandardCharsets.UTF_8);
                        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                        long ts = obj.has("ts") ? obj.get("ts").getAsLong() : 0L;
                        if (now - ts > maxAgeMs) Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        try { Files.deleteIfExists(p); } catch (Exception ignored2) {}
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    private static File fileFor(String videoUrl) {
        return new File(CACHE_DIR.toFile(), hash(videoUrl) + ".json");
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
