package com.dreamdisplays.ytdlp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;

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
    private static final long CACHE_TTL_MS = 5L * 60L * 60L * 1_000L;
    private static final long INFO_CACHE_TTL_MS = 30L * 60L * 1_000L;
    private static final ExecutorService PREWARM_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "YtDlp-prewarm");
                thread.setDaemon(true);
                return thread;
            });
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "YtDlp-search");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentMap<String, CacheEntry> FORMAT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<List<YtStream>>> IN_FLIGHT_FETCHES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, InfoCacheEntry> SEARCH_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, InfoCacheEntry> RELATED_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<List<YtVideoInfo>>> IN_FLIGHT_SEARCHES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<List<YtVideoInfo>>> IN_FLIGHT_RELATED = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, TitleCacheEntry> TITLE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<@Nullable String>> IN_FLIGHT_TITLES = new ConcurrentHashMap<>();
    private static volatile @Nullable String resolvedBinary;

    private YtDlp() {
    }

    public static List<YtStream> fetch(String videoUrl) throws IOException {
        CacheEntry cached = FORMAT_CACHE.get(videoUrl);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMs) <= CACHE_TTL_MS) {
            return cached.streams;
        }

        List<YtStream> fromDisk = FormatDiskCache.load(videoUrl, CACHE_TTL_MS);
        if (fromDisk != null && !fromDisk.isEmpty()) {
            List<YtStream> immutable = List.copyOf(fromDisk);
            FORMAT_CACHE.put(videoUrl, new CacheEntry(immutable, System.currentTimeMillis()));
            return immutable;
        }

        CompletableFuture<List<YtStream>> future = startFetchInternal(videoUrl);

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

    public static void prefetchFormats(String videoUrl) {
        if (videoUrl.isBlank()) return;
        CacheEntry cached = FORMAT_CACHE.get(videoUrl);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMs) <= CACHE_TTL_MS) return;
        startFetchInternal(videoUrl);
    }

    private static CompletableFuture<List<YtStream>> startFetchInternal(String videoUrl) {
        return IN_FLIGHT_FETCHES.computeIfAbsent(
                videoUrl,
                ignored -> CompletableFuture.supplyAsync(() -> {
                    try {
                        List<YtStream> streams = List.copyOf(fetchUncached(videoUrl));
                        FORMAT_CACHE.put(videoUrl, new CacheEntry(streams, System.currentTimeMillis()));
                        FormatDiskCache.saveAsync(videoUrl, streams);
                        return streams;
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
        );
    }

    public static List<YtVideoInfo> search(String query, int limit) throws IOException {
        if (query.isBlank()) return new ArrayList<>();
        int n = Math.clamp(limit, 1, 25);
        String key = query.trim().toLowerCase(Locale.ENGLISH) + "|" + n;
        InfoCacheEntry cached = SEARCH_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMs) <= INFO_CACHE_TTL_MS) {
            // LoggingManager.info("[YtDlp] search cache hit '" + query + "' (" + cached.results.size() + " results)");
            return cached.results;
        }
        CompletableFuture<List<YtVideoInfo>> future = IN_FLIGHT_SEARCHES.computeIfAbsent(
                key,
                ignored -> CompletableFuture.supplyAsync(() -> {
                    long t0 = System.nanoTime();
                    try {
                        List<YtVideoInfo> web = YouTubeWeb.search(query.trim(), n);
                        if (!web.isEmpty()) {
                            List<YtVideoInfo> results = List.copyOf(web);
                            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                            // LoggingManager.info("[YouTubeWeb] search '" + query + "' -> " + results.size() + " in " + elapsedMs + " ms");
                            SEARCH_CACHE.put(key, new InfoCacheEntry(results, System.currentTimeMillis()));
                            return results;
                        }
                    } catch (Exception webEx) {
                        LoggingManager.warn("[YouTubeWeb] search failed, falling back to yt-dlp: " + webEx.getMessage());
                    }
                    try {
                        List<YtVideoInfo> results = List.copyOf(searchUncached(query.trim(), n));
                        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                        // LoggingManager.info("[YtDlp] search '" + query + "' -> " + results.size() + " results in " + elapsedMs + " ms");
                        SEARCH_CACHE.put(key, new InfoCacheEntry(results, System.currentTimeMillis()));
                        return results;
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, SEARCH_EXECUTOR)
        );
        return waitForInfoFuture(future, "search '" + query + "'", () -> IN_FLIGHT_SEARCHES.remove(key, future));
    }

    public static List<YtVideoInfo> related(String videoId, int limit) throws IOException {
        if (videoId.isBlank()) return new ArrayList<>();
        int n = Math.clamp(limit, 1, 25);
        String key = videoId + "|" + n;
        InfoCacheEntry cached = RELATED_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMs) <= INFO_CACHE_TTL_MS) {
            // LoggingManager.info("[YtDlp] related cache hit " + videoId + " (" + cached.results.size() + " results)");
            return cached.results;
        }
        CompletableFuture<List<YtVideoInfo>> future = IN_FLIGHT_RELATED.computeIfAbsent(
                key,
                ignored -> CompletableFuture.supplyAsync(() -> {
                    long t0 = System.nanoTime();
                    try {
                        List<YtVideoInfo> web = YouTubeWeb.related(videoId, n);
                        if (!web.isEmpty()) {
                            List<YtVideoInfo> immutable = List.copyOf(web);
                            RELATED_CACHE.put(key, new InfoCacheEntry(immutable, System.currentTimeMillis()));
                            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                            // LoggingManager.info("[YouTubeWeb] related " + videoId + " -> " + immutable.size() + " in " + elapsedMs + " ms");
                            return immutable;
                        }
                    } catch (Exception webEx) {
                        // LoggingManager.warn("[YouTubeWeb] related failed, falling back to yt-dlp: " + webEx.getMessage());
                    }
                    try {
                        List<YtVideoInfo> immutable = loadRelatedUncached(videoId, n, key);
                        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                        // LoggingManager.info("[YtDlp] related " + videoId + " -> " + immutable.size() + " results in " + elapsedMs + " ms");
                        return immutable;
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, SEARCH_EXECUTOR)
        );
        return waitForInfoFuture(future, "related " + videoId, () -> IN_FLIGHT_RELATED.remove(key, future));
    }

    public static @Nullable String extractVideoId(@Nullable String url) {
        if (url == null) return null;
        String s = url.trim();
        if (s.isEmpty()) return null;
        if (s.length() == 11 && s.matches("[A-Za-z0-9_-]{11}")) return s;
        try {
            URI uri = URI.create(s);
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ENGLISH);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.contains("youtu.be")) {
                String p = path.startsWith("/") ? path.substring(1) : path;
                int slash = p.indexOf('/');
                return slash >= 0 ? p.substring(0, slash) : p;
            }
            if (host.contains("youtube.com")) {
                String q = uri.getQuery();
                if (q != null) {
                    for (String part : q.split("&")) {
                        if (part.startsWith("v=")) return part.substring(2);
                    }
                }
                if (path.startsWith("/shorts/") || path.startsWith("/embed/")
                        || path.startsWith("/live/")) {
                    String rest = path.substring(path.indexOf('/', 1) + 1);
                    int slash = rest.indexOf('/');
                    return slash >= 0 ? rest.substring(0, slash) : rest;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<YtVideoInfo> searchUncached(String query, int limit) throws IOException {
        String binary = resolveBinary();
        ProcessBuilder pb = new ProcessBuilder(
                binary,
                "-J",
                "--flat-playlist",
                "--no-warnings",
                // Force the lightweight "web" client only
                "--extractor-args", "youtube:player_client=web",
                "--playlist-end", String.valueOf(limit),
                "ytsearch" + limit + ":" + query
        );
        return parseEntries(runJsonProcess(pb, "search:" + query, 25));
    }

    private static List<YtVideoInfo> loadRelatedUncached(String videoId, int limit, String cacheKey) throws IOException {
        // YouTube Mix playlists (RD<videoId>) are auto-generated "related" lists and
        // are dramatically faster to fetch than search-by-title (one yt-dlp call vs
        // two, no title round-trip). Falls back to title search if Mix isn't returned.
        String binary = resolveBinary();
        ProcessBuilder pb = new ProcessBuilder(
                binary,
                "-J",
                "--flat-playlist",
                "--no-warnings",
                "--extractor-args", "youtube:player_client=web",
                "--playlist-end", String.valueOf(limit + 2),
                "--lazy-playlist",
                "https://www.youtube.com/watch?v=" + videoId + "&list=RD" + videoId
        );
        List<YtVideoInfo> hits;
        try {
            hits = new ArrayList<>(parseEntries(runJsonProcess(pb, "mix:" + videoId, 20)));
        } catch (IOException e) {
            // LoggingManager.info("[YtDlp] mix fallback for " + videoId + ": " + e.getMessage());
            String title = null;
            try {
                YtVideoInfo meta = YouTubeWeb.metadata(videoId);
                if (meta != null) title = meta.getTitle();
            } catch (Exception webEx) {
                // LoggingManager.warn("[YouTubeWeb] metadata fallback failed for " + videoId + ": " + webEx.getMessage());
            }
            if (title == null || title.isBlank()) {
                title = fetchVideoTitle(videoId);
            }
            if (title == null || title.isBlank()) {
                RELATED_CACHE.put(cacheKey, new InfoCacheEntry(List.of(), System.currentTimeMillis()));
                return List.of();
            }
            hits = new ArrayList<>(search(title, limit + 2));
        }
        hits.removeIf(v -> videoId.equals(v.getId()));
        if (hits.size() > limit) hits = hits.subList(0, limit);
        List<YtVideoInfo> immutable = List.copyOf(hits);
        RELATED_CACHE.put(cacheKey, new InfoCacheEntry(immutable, System.currentTimeMillis()));
        return immutable;
    }

    private static @Nullable String fetchVideoTitle(String videoId) throws IOException {
        TitleCacheEntry cached = TITLE_CACHE.get(videoId);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMs) <= INFO_CACHE_TTL_MS) {
            return cached.title;
        }
        CompletableFuture<@Nullable String> future = IN_FLIGHT_TITLES.computeIfAbsent(
                videoId,
                ignored -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String title = fetchVideoTitleUncached(videoId);
                        TITLE_CACHE.put(videoId, new TitleCacheEntry(title, System.currentTimeMillis()));
                        return title;
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, SEARCH_EXECUTOR)
        );
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("yt-dlp title fetch failed for video: " + videoId, cause != null ? cause : e);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("yt-dlp title fetch failed for video: " + videoId, cause != null ? cause : e);
        } finally {
            IN_FLIGHT_TITLES.remove(videoId, future);
        }
    }

    private static @Nullable String fetchVideoTitleUncached(String videoId) throws IOException {
        String binary = resolveBinary();
        ProcessBuilder pb = new ProcessBuilder(
                binary,
                "--print", "%(title)s",
                "--no-warnings",
                "--skip-download",
                "https://www.youtube.com/watch?v=" + videoId
        );
        String out = runJsonProcess(pb, "title:" + videoId, 25).trim();
        return out.isEmpty() ? null : out;
    }

    private static String runJsonProcess(ProcessBuilder pb, String tag, int timeoutSec) throws IOException {
        pb.redirectErrorStream(false);
        long tStart = System.nanoTime();
        Process process = pb.start();
        long tProcStarted = System.nanoTime();
        StringBuilder stdout = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) stdout.append(buf, 0, n);
        }
        StringBuilder stderr = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) stderr.append(buf, 0, n);
        }
        try {
            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("yt-dlp timed out for " + tag);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for yt-dlp", e);
        }
        if (process.exitValue() != 0) {
            throw new IOException("yt-dlp exited " + process.exitValue() + ": " + stderr.toString().trim());
        }
        long tDone = System.nanoTime();
        // LoggingManager.info(String.format(
        //        "[YtDlp] %s — binary=%dms, process=%dms, total=%dms (stdout %d bytes)",
        //        tag,
        //        0,
        //        (tDone - tProcStarted) / 1_000_000L,
        //        (tDone - tStart) / 1_000_000L,
        //        stdout.length()
        //));
        return stdout.toString();
    }

    private static List<YtVideoInfo> parseEntries(String json) throws IOException {
        List<YtVideoInfo> out = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("entries") || !root.get("entries").isJsonArray()) return out;
            for (JsonElement el : root.getAsJsonArray("entries")) {
                if (!el.isJsonObject()) continue;
                JsonObject e = el.getAsJsonObject();
                String id = optString(e, "id");
                String title = optString(e, "title");
                if (id == null || title == null) continue;
                Double dur = optDouble(e, "duration");
                Long durSec = dur == null ? null : (long) Math.floor(dur);
                Long views = optLong(e);
                String uploader = optString(e, "uploader");
                if (uploader == null) uploader = optString(e, "channel");
                out.add(new YtVideoInfo(id, title, uploader, durSec, views));
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Failed to parse search JSON", e);
        }
    }

    private static @Nullable Long optLong(JsonObject obj) {
        if (!obj.has("view_count") || obj.get("view_count").isJsonNull()) return null;
        try {
            return obj.get("view_count").getAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<YtVideoInfo> waitForInfoFuture(
            CompletableFuture<List<YtVideoInfo>> future,
            String tag,
            Runnable cleanup
    ) throws IOException {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("yt-dlp " + tag + " failed", cause != null ? cause : e);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("yt-dlp " + tag + " failed", cause != null ? cause : e);
        } finally {
            cleanup.run();
        }
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
                Integer h = optInt(f);
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
        if (optBoolean(obj)) return true;
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

    private static @Nullable Integer optInt(JsonObject obj) {
        if (!obj.has("height") || obj.get("height").isJsonNull()) return null;
        try {
            return obj.get("height").getAsInt();
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

    private static boolean optBoolean(JsonObject obj) {
        if (!obj.has("is_live") || obj.get("is_live").isJsonNull()) return false;
        try {
            return obj.get("is_live").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static @Nullable String resolveBinary() throws IOException {
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

            // TODO: write an explanation about it
            candidates.addAll(Arrays.asList(CANDIDATE_PATHS));
            Path bundled = BUNDLED_DIR.resolve(bundledBinaryName());
            candidates.add(bundled.toString());

            for (String c : candidates) {
                if (canExecute(c)) {
                    resolvedBinary = c;
                    return c;
                }
            }

            // LoggingManager.info("yt-dlp not found, downloading bundled copy...");
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

    private record InfoCacheEntry(List<YtVideoInfo> results, long createdAtMs) {
    }

    private record TitleCacheEntry(@Nullable String title, long createdAtMs) {
    }

    private record CacheEntry(List<YtStream> streams, long createdAtMs) {
    }
}
