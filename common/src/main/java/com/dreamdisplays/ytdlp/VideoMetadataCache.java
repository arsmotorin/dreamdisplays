package com.dreamdisplays.ytdlp;

import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@NullMarked
public final class VideoMetadataCache {

    private static final ConcurrentHashMap<String, YtVideoInfo> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DD-VideoMeta");
        t.setDaemon(true);
        return t;
    });

    private VideoMetadataCache() {
    }

    public static void put(String videoId, YtVideoInfo info) {
        if (videoId.isEmpty()) return;
        CACHE.put(videoId, info);
        // Also seed the title cache so the simpler consumers stay in sync
        VideoTitleCache.put(videoId, info.getTitle());
    }

    public static @Nullable YtVideoInfo get(String videoId) {
        return CACHE.get(videoId);
    }

    public static void requestAsync(String videoId) {
        if (videoId.isEmpty()) return;
        if (CACHE.containsKey(videoId)) return;
        if (IN_FLIGHT.putIfAbsent(videoId, Boolean.TRUE) != null) return;
        EXEC.submit(() -> fetchAndStore(videoId));
    }

    private static void fetchAndStore(String videoId) {
        try {
            YtVideoInfo meta = YouTubeWeb.metadata(videoId);
            if (meta != null) put(videoId, meta);
        } catch (Exception e) {
            LoggingManager.warn("Metadata fetch failed for " + videoId + ": " + e.getMessage());
        } finally {
            IN_FLIGHT.remove(videoId);
        }
    }
}
