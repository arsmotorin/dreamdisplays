package com.dreamdisplays.ytdlp;

import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@NullMarked
public final class VideoTitleCache {

    private static final ConcurrentHashMap<String, String> TITLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DD-VideoTitle");
        t.setDaemon(true);
        return t;
    });

    private VideoTitleCache() {
    }

    public static void put(String videoId, String title) {
        if (videoId == null || videoId.isEmpty() || title == null || title.isEmpty()) return;
        TITLES.put(videoId, title);
    }

    public static @Nullable String get(String videoId) {
        return TITLES.get(videoId);
    }

    public static void requestAsync(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        if (TITLES.containsKey(videoId)) return;
        if (IN_FLIGHT.putIfAbsent(videoId, Boolean.TRUE) != null) return;
        EXEC.submit(() -> fetchAndStore(videoId));
    }

    private static void fetchAndStore(String videoId) {
        try {
            String title = fetchTitle(videoId);
            if (title != null && !title.isEmpty()) TITLES.put(videoId, title);
        } catch (Exception e) {
            LoggingManager.warn("Title fetch failed for " + videoId + ": " + e.getMessage());
        } finally {
            IN_FLIGHT.remove(videoId);
        }
    }

    private static @Nullable String fetchTitle(String videoId) throws Exception {
        try {
            YtVideoInfo meta = YouTubeWeb.metadata(videoId);
            if (meta != null && meta.getTitle() != null && !meta.getTitle().isEmpty()) {
                VideoMetadataCache.put(videoId, meta);
                return meta.getTitle();
            }
        } catch (Exception ignored) {
        }
        ProcessBuilder pb = new ProcessBuilder(
                YtDlp.binaryPath(),
                "--no-warnings",
                "--skip-download",
                "--print", "%(title)s",
                "https://www.youtube.com/watch?v=" + videoId
        );
        pb.redirectErrorStream(false);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) != -1) out.append(buf, 0, n);
        }
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return null;
        }
        if (p.exitValue() != 0) return null;
        return out.toString().trim();
    }
}
