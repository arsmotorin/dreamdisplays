package com.dreamdisplays.ytdlp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@NullMarked
public final class YtDlp {

    private static final String[] CANDIDATE_PATHS = {
            "yt-dlp",
            "/opt/homebrew/bin/yt-dlp",
            "/usr/local/bin/yt-dlp",
            "/usr/bin/yt-dlp",
            "C:\\Program Files\\yt-dlp\\yt-dlp.exe"
    };
    private static final Path BUNDLED_DIR = Path.of("libs", "yt-dlp");
    private static final String DOWNLOAD_BASE =
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
    private static final long CACHE_TTL_MS = 30_000L;

    private static volatile @Nullable String resolvedBinary;
    private static final ExecutorService PREWARM_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "YtDlp-prewarm");
                thread.setDaemon(true);
                return thread;
            });
    private static final ConcurrentMap<String, CacheEntry> FORMAT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<List<YtStream>>> IN_FLIGHT_FETCHES = new ConcurrentHashMap<>();

    private YtDlp() {
    }

    public static List<YtStream> fetch(String videoUrl) throws IOException {
        CacheEntry cached = FORMAT_CACHE.get(videoUrl);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMs) <= CACHE_TTL_MS) {
            return cached.streams;
        }

        CompletableFuture<List<YtStream>> future = IN_FLIGHT_FETCHES.computeIfAbsent(
                videoUrl,
                ignored -> CompletableFuture.supplyAsync(() -> {
                    try {
                        List<YtStream> streams = fetchUncached(videoUrl);
                        List<YtStream> cachedStreams = List.copyOf(streams);
                        FORMAT_CACHE.put(videoUrl, new CacheEntry(cachedStreams, System.currentTimeMillis()));
                        return cachedStreams;
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
        );

        try {
            return future.get(65, TimeUnit.SECONDS);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("yt-dlp fetch failed for url: " + videoUrl, cause != null ? cause : e);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("yt-dlp fetch failed for url: " + videoUrl, cause != null ? cause : e);
        } finally {
            IN_FLIGHT_FETCHES.remove(videoUrl, future);
        }
    }

    public static void prewarmAsync() {
        if (resolvedBinary != null) return;
        PREWARM_EXECUTOR.submit(() -> {
            try {
                resolveBinary();
            } catch (IOException e) {
                LoggingManager.warn("Failed to prewarm yt-dlp", e);
            }
        });
    }

    private static List<YtStream> fetchUncached(String videoUrl) throws IOException {
        String binary = resolveBinary();
        ProcessBuilder pb = new ProcessBuilder(
                binary,
                "-J",
                "--no-playlist",
                "--no-warnings",
                videoUrl
        );
        pb.redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) stdout.append(buf, 0, n);
        }

        StringBuilder stderr = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
        )) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) stderr.append(buf, 0, n);
        }

        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("yt-dlp timed out for url: " + videoUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for yt-dlp", e);
        }

        if (process.exitValue() != 0) {
            throw new IOException(
                    "yt-dlp exited with code " + process.exitValue()
                            + ": " + stderr.toString().trim()
            );
        }

        return parseFormats(stdout.toString());
    }

    private record CacheEntry(List<YtStream> streams, long createdAtMs) {
    }

    private static List<YtStream> parseFormats(String json) throws IOException {
        List<YtStream> result = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            throw new IOException("Failed to parse yt-dlp JSON output", e);
        }
        if (!root.isJsonObject()) {
            throw new IOException("yt-dlp returned unexpected JSON shape");
        }
        JsonObject obj = root.getAsJsonObject();
        boolean isLive = isLive(obj);
        long durationNanos = getDurationNanos(obj);
        boolean seekable = !isLive && durationNanos > 0L;
        if (!obj.has("formats") || !obj.get("formats").isJsonArray()) {
            return result;
        }

        JsonArray formats = obj.getAsJsonArray("formats");
        for (JsonElement el : formats) {
            if (!el.isJsonObject()) continue;
            JsonObject f = el.getAsJsonObject();

            String url = optString(f, "url");
            if (url == null || url.isEmpty()) continue;

            String protocol = optString(f, "protocol");
            if (!isSupportedProtocol(protocol, url)) continue;

            String vcodec = optString(f, "vcodec");
            String acodec = optString(f, "acodec");
            String ext = optString(f, "ext");
            String container = optString(f, "container");

            boolean hasVideo = vcodec != null && !vcodec.equals("none");
            boolean hasAudio = acodec != null && !acodec.equals("none");
            if (!hasVideo && !hasAudio) continue;

            String mime = (hasVideo ? "video/" : "audio/") + (ext == null ? "webm" : ext);

            String resolution = null;
            if (hasVideo) {
                Integer h = optInt(f, "height");
                if (h != null && h > 0) {
                    resolution = h + "p";
                } else {
                    resolution = extractResolution(
                            optString(f, "resolution"),
                            optString(f, "format_note"),
                            optString(f, "format")
                    );
                }
            }

            String language = optString(f, "language");
            String formatNote = optString(f, "format_note");
            Double fps = optDouble(f, "fps");
            Double tbr = optDouble(f, "tbr");

            result.add(new YtStream(
                    url,
                    mime,
                    container,
                    protocol,
                    resolution,
                    language,
                    formatNote,
                    vcodec,
                    acodec,
                    fps,
                    tbr,
                    hasVideo,
                    hasAudio,
                    isLive,
                    seekable,
                    durationNanos
            ));
        }
        return result;
    }

    private static boolean isLive(JsonObject obj) {
        if (optBoolean(obj, "is_live")) return true;
        String liveStatus = optString(obj, "live_status");
        return liveStatus != null && switch (liveStatus) {
            case "is_live", "is_upcoming", "post_live" -> true;
            default -> false;
        };
    }

    private static long getDurationNanos(JsonObject obj) {
        Double durationSeconds = optDouble(obj, "duration");
        if (durationSeconds == null || durationSeconds <= 0.0D) {
            return 0L;
        }
        double nanos = durationSeconds * 1_000_000_000.0D;
        if (nanos >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(nanos));
    }

    private static boolean isSupportedProtocol(@Nullable String protocol, String url) {
        if (protocol == null || protocol.isBlank()) return true;
        if (protocol.startsWith("http")) return true;
        if (protocol.contains("m3u8")) return true;
        if (protocol.contains("dash")) return true;

        String lowerUrl = url.toLowerCase(Locale.ENGLISH);
        return lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd");
    }

    private static @Nullable String extractResolution(@Nullable String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d{3,4})p")
                    .matcher(candidate);
            if (matcher.find()) {
                return matcher.group(1) + "p";
            }

            matcher = java.util.regex.Pattern
                    .compile("(\\d{3,4})")
                    .matcher(candidate);
            if (matcher.find()) {
                return matcher.group(1) + "p";
            }
        }
        return null;
    }

    private static @Nullable String optString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable Integer optInt(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable Double optDouble(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean optBoolean(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return false;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static String resolveBinary() throws IOException {
        String cached = resolvedBinary;
        if (cached != null) return cached;
        synchronized (YtDlp.class) {
            if (resolvedBinary != null) return resolvedBinary;

            List<String> candidates = new ArrayList<>();
            String override = System.getProperty("dreamdisplays.ytdlp");
            if (override == null || override.isEmpty()) {
                override = System.getenv("DREAMDISPLAYS_YTDLP");
            }
            if (override != null && !override.isEmpty()) candidates.add(override);

            Path bundled = BUNDLED_DIR.resolve(bundledBinaryName());
            candidates.add(bundled.toString());
            candidates.addAll(Arrays.asList(CANDIDATE_PATHS));

            for (String c : candidates) {
                if (canExecute(c)) {
                    LoggingManager.info("Using yt-dlp at " + c);
                    resolvedBinary = c;
                    return c;
                }
            }

            LoggingManager.info("yt-dlp not found, downloading bundled copy...");
            String downloaded = downloadBundled(bundled);
            resolvedBinary = downloaded;
            return downloaded;
        }
    }

    private static String bundledBinaryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        return os.contains("win") ? "yt-dlp.exe" : "yt-dlp";
    }

    private static String downloadAssetName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
        if (os.contains("win")) return "yt-dlp.exe";
        if (os.contains("mac")) return "yt-dlp_macos";
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "yt-dlp_linux_aarch64";
        }
        if (arch.contains("arm")) {
            return "yt-dlp_linux_armv7l";
        }
        return "yt-dlp_linux";
    }

    private static String downloadBundled(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        String url = DOWNLOAD_BASE + downloadAssetName();
        LoggingManager.info("Downloading yt-dlp from " + url);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL()
                .openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "DreamDisplays-yt-dlp-bootstrap");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }

        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

        if (!System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("win")) {
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
                Files.setPosixFilePermissions(target, perms);
            } catch (UnsupportedOperationException ignored) {
                target.toFile().setExecutable(true, false);
            }
        }

        String path = target.toString();
        if (!canExecute(path)) {
            throw new IOException("Downloaded yt-dlp at " + path + " is not executable");
        }
        LoggingManager.info("yt-dlp ready at " + path);
        return path;
    }

    private static boolean canExecute(String path) {
        try {
            File f = new File(path);
            if (f.isAbsolute() || path.contains(File.separator)) {
                if (!f.isFile() || !f.canExecute()) return false;
            }
            ProcessBuilder pb = new ProcessBuilder(path, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
