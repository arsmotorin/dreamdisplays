package com.dreamdisplays.ytdlp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NullMarked
public final class YouTubeWeb {

    private static final String UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Safari/537.36";
    private static final Pattern AGE_PATTERN = Pattern.compile(
            "(\\d+)\\s+(second|minute|hour|day|week|month|year)s?\\s+ago",
            Pattern.CASE_INSENSITIVE);

    private YouTubeWeb() {
    }

    public static List<YtVideoInfo> search(String query, int limit) throws IOException {
        String url = "https://www.youtube.com/results?search_query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&hl=en";
        JsonObject root = fetchInitialData(url);
        return extractSearchVideos(root, limit);
    }

    public static List<YtVideoInfo> related(String videoId, int limit) throws IOException {
        String url = "https://www.youtube.com/watch?v=" + videoId + "&hl=en";
        JsonObject root = fetchInitialData(url);
        return extractRelatedVideos(root, videoId, limit);
    }

    public static @Nullable YtVideoInfo metadata(String videoId) throws IOException {
        String url = "https://www.youtube.com/watch?v=" + videoId + "&hl=en";
        JsonObject root = fetchInitialData(url);
        return extractWatchMetadata(root, videoId);
    }

    // HTTP
    private static JsonObject fetchInitialData(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        String realCookies = YtDlp.getPublicCookieHeader();
        conn.setRequestProperty("Cookie",
                realCookies != null ? realCookies : "CONSENT=YES+cb; SOCS=CAI; PREF=hl=en");
        try (InputStream in = conn.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Use brace-counting extraction (regex .*? cuts off at first '};')
            String json = YtDlp.extractJsonObject(body, "var ytInitialData");
            if (json == null) {
                json = YtDlp.extractJsonObject(body, "window[\"ytInitialData\"]");
            }
            if (json == null) {
                throw new IOException("ytInitialData not found");
            }
            try {
                return JsonParser.parseString(json).getAsJsonObject();
            } catch (Exception e) {
                throw new IOException("Failed to parse ytInitialData JSON", e);
            }
        } finally {
            conn.disconnect();
        }
    }

    // Search
    private static List<YtVideoInfo> extractSearchVideos(JsonObject root, int limit) {
        List<YtVideoInfo> out = new ArrayList<>();
        try {
            JsonArray sections = path(root,
                    "contents", "twoColumnSearchResultsRenderer", "primaryContents",
                    "sectionListRenderer", "contents").getAsJsonArray();
            for (JsonElement sec : sections) {
                JsonElement isr = sec.getAsJsonObject().get("itemSectionRenderer");
                if (isr == null || !isr.isJsonObject()) continue;
                JsonArray contents = isr.getAsJsonObject().getAsJsonArray("contents");
                if (contents == null) continue;
                for (JsonElement el : contents) {
                    JsonObject obj = el.getAsJsonObject();
                    JsonElement vr = obj.get("videoRenderer");
                    if (vr == null || !vr.isJsonObject()) continue;
                    YtVideoInfo info = parseVideoRenderer(vr.getAsJsonObject());
                    if (info != null) {
                        out.add(info);
                        if (out.size() >= limit) return out;
                    }
                }
            }
        } catch (Exception e) {
            LoggingManager.warn("Search parse failed: " + e.getMessage());
        }
        return out;
    }

    private static @Nullable YtVideoInfo parseVideoRenderer(JsonObject vr) {
        String id = optString(vr, "videoId");
        if (id == null) return null;
        // Filter stupid shorts. YouTube has been pushing them aggressively, so remove them!
        // Three signals:
        // 1. navigationEndpoint URL starts with /shorts/
        // 2. duration < 65 s (most shorts are 60 s exactly)
        // 3. presence of a "SHORTS" badge / shortsLockupViewModel
        if (looksLikeShorts(vr)) return null;
        String title = runsText(vr.getAsJsonObject("title"));
        if (title == null) title = simpleText(vr.getAsJsonObject("title"));
        if (title == null) title = id;
        String uploader = runsText(vr.getAsJsonObject("ownerText"));
        if (uploader == null) uploader = runsText(vr.getAsJsonObject("longBylineText"));
        Long duration = parseDuration(simpleText(vr.getAsJsonObject("lengthText")));
        if (duration != null && duration < 65) return null;
        Long views = parseViews(simpleText(vr.getAsJsonObject("viewCountText")));
        if (views == null) views = parseViews(simpleText(vr.getAsJsonObject("shortViewCountText")));
        String publishedText = simpleText(vr.getAsJsonObject("publishedTimeText"));
        Integer daysAgo = parseDaysAgo(publishedText);
        return new YtVideoInfo(id, title, uploader, duration, views, null,
                publishedText, daysAgo);
    }

    private static boolean looksLikeShorts(JsonObject vr) {
        try {
            JsonObject nav = vr.getAsJsonObject("navigationEndpoint");
            if (nav != null) {
                JsonObject cmd = nav.getAsJsonObject("commandMetadata");
                if (cmd != null) {
                    JsonObject web = cmd.getAsJsonObject("webCommandMetadata");
                    if (web != null) {
                        String webUrl = optString(web, "url");
                        if (webUrl != null && webUrl.startsWith("/shorts/")) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return vr.toString().contains("\"label\":\"Shorts\"")
                || vr.toString().contains("shortsLockupViewModel");
    }

    // Watch page
    private static List<YtVideoInfo> extractRelatedVideos(JsonObject root, String selfId, int limit) {
        List<YtVideoInfo> out = new ArrayList<>();
        try {
            JsonArray results = path(root,
                    "contents", "twoColumnWatchNextResults", "secondaryResults",
                    "secondaryResults", "results").getAsJsonArray();
            for (JsonElement el : results) {
                if (!el.isJsonObject()) continue;
                JsonElement cvr = el.getAsJsonObject().get("compactVideoRenderer");
                if (cvr == null || !cvr.isJsonObject()) continue;
                YtVideoInfo info = parseCompactVideoRenderer(cvr.getAsJsonObject());
                if (info != null && !info.getId().equals(selfId)) {
                    out.add(info);
                    if (out.size() >= limit) return out;
                }
            }
        } catch (Exception e) {
            LoggingManager.warn("Related parse failed: " + e.getMessage());
        }
        return out;
    }

    private static @Nullable YtVideoInfo parseCompactVideoRenderer(JsonObject cvr) {
        String id = optString(cvr, "videoId");
        if (id == null) return null;
        if (looksLikeShorts(cvr)) return null;
        String title = simpleText(cvr.getAsJsonObject("title"));
        if (title == null) title = id;
        String uploader = simpleText(cvr.getAsJsonObject("longBylineText"));
        if (uploader == null) uploader = simpleText(cvr.getAsJsonObject("shortBylineText"));
        Long duration = parseDuration(simpleText(cvr.getAsJsonObject("lengthText")));
        if (duration != null && duration < 65) return null;
        Long views = parseViews(simpleText(cvr.getAsJsonObject("viewCountText")));
        if (views == null) views = parseViews(simpleText(cvr.getAsJsonObject("shortViewCountText")));
        String publishedText = simpleText(cvr.getAsJsonObject("publishedTimeText"));
        Integer daysAgo = parseDaysAgo(publishedText);
        return new YtVideoInfo(id, title, uploader, duration, views, null,
                publishedText, daysAgo);
    }

    // Metadata
    private static @Nullable YtVideoInfo extractWatchMetadata(JsonObject root, String videoId) {
        try {
            JsonArray contents = path(root,
                    "contents", "twoColumnWatchNextResults", "results",
                    "results", "contents").getAsJsonArray();
            String title = null;
            String channel = null;
            Long views = null;
            Long likes = null;
            String publishedText = null;
            Integer daysAgo = null;
            for (JsonElement el : contents) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("videoPrimaryInfoRenderer")) {
                    JsonObject vp = obj.getAsJsonObject("videoPrimaryInfoRenderer");
                    if (title == null) title = runsText(vp.getAsJsonObject("title"));
                    String dateText = simpleText(vp.getAsJsonObject("dateText"));
                    if (publishedText == null) publishedText = dateText;
                    if (daysAgo == null) daysAgo = parseDaysAgo(dateText);
                    Long v = parseViews(runsText(vp.getAsJsonObject("viewCount")
                            == null ? null : vp.getAsJsonObject("viewCount").getAsJsonObject("videoViewCountRenderer")
                                             .getAsJsonObject("viewCount")));
                    if (v == null) v = parseViews(simpleText(maybeViewCountText(vp)));
                    if (v != null) views = v;
                    likes = extractLikeCount(vp);
                }
                if (obj.has("videoSecondaryInfoRenderer")) {
                    JsonObject vs = obj.getAsJsonObject("videoSecondaryInfoRenderer");
                    if (channel == null) {
                        JsonObject owner = vs.getAsJsonObject("owner");
                        if (owner != null) {
                            JsonObject vor = owner.getAsJsonObject("videoOwnerRenderer");
                            if (vor != null) channel = runsText(vor.getAsJsonObject("title"));
                        }
                    }
                }
            }
            if (title == null) return null;
            return new YtVideoInfo(videoId, title, channel, null, views, likes,
                    publishedText, daysAgo);
        } catch (Exception e) {
            LoggingManager.warn("Watch metadata parse failed: " + e.getMessage());
            return null;
        }
    }

    private static @Nullable JsonObject maybeViewCountText(JsonObject vp) {
        if (!vp.has("viewCount")) return null;
        JsonObject vc = vp.getAsJsonObject("viewCount");
        if (vc.has("videoViewCountRenderer")) {
            JsonObject vvcr = vc.getAsJsonObject("videoViewCountRenderer");
            if (vvcr.has("viewCount")) return vvcr.getAsJsonObject("viewCount");
        }
        return null;
    }

    // I started writing this code on the left side of the screen, and by the time I finished, I was somewhere near the kitchen
    private static @Nullable Long extractLikeCount(JsonObject vp) {
        JsonObject menu = vp.getAsJsonObject("videoActions");
        if (menu == null) return null;
        JsonObject mr = menu.getAsJsonObject("menuRenderer");
        if (mr == null) return null;
        JsonElement topLevel = mr.get("topLevelButtons");
        if (topLevel == null || !topLevel.isJsonArray()) return null;
        for (JsonElement btn : topLevel.getAsJsonArray()) {
            if (!btn.isJsonObject()) continue;
            JsonObject bo = btn.getAsJsonObject();
            JsonObject sbvr = bo.getAsJsonObject("segmentedLikeDislikeButtonViewModel");
            if (sbvr != null) {
                JsonObject lb = sbvr.getAsJsonObject("likeButtonViewModel");
                if (lb != null) {
                    JsonObject inner = lb.getAsJsonObject("likeButtonViewModel");
                    if (inner != null) {
                        JsonObject toggleButton = inner.getAsJsonObject("toggleButtonViewModel");
                        if (toggleButton != null) {
                            JsonObject tbvm = toggleButton.getAsJsonObject("toggleButtonViewModel");
                            if (tbvm != null) {
                                JsonObject defaultButton = tbvm.getAsJsonObject("defaultButtonViewModel");
                                if (defaultButton != null) {
                                    JsonObject buttonViewModel = defaultButton.getAsJsonObject("buttonViewModel");
                                    if (buttonViewModel != null) {
                                        Long parsed = parseViews(optString(buttonViewModel, "title"));
                                        if (parsed != null) return parsed;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static JsonElement path(JsonObject obj, String... keys) {
        JsonElement cur = obj;
        for (String k : keys) {
            if (cur == null || !cur.isJsonObject()) return new JsonObject();
            cur = cur.getAsJsonObject().get(k);
        }
        return cur == null ? new JsonObject() : cur;
    }

    private static @Nullable String optString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable String runsText(@Nullable JsonObject obj) {
        if (obj == null || !obj.has("runs") || !obj.get("runs").isJsonArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : obj.getAsJsonArray("runs")) {
            if (!el.isJsonObject()) continue;
            String t = optString(el.getAsJsonObject(), "text");
            if (t != null) sb.append(t);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static @Nullable String simpleText(@Nullable JsonObject obj) {
        if (obj == null) return null;
        String s = optString(obj, "simpleText");
        if (s != null) return s;
        return runsText(obj);
    }

    private static @Nullable Long parseDuration(@Nullable String s) {
        if (s == null) return null;
        String[] parts = s.split(":");
        try {
            long total = 0;
            for (String p : parts) total = total * 60 + Integer.parseInt(p.trim());
            return total;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable Long parseViews(@Nullable String s) {
        if (s == null) return null;
        String t = s.toLowerCase().replace(",", "").replace("views", "").trim();
        if (t.isEmpty()) return null;
        char suffix = t.charAt(t.length() - 1);
        double mult = 1;
        if (suffix == 'k') {
            mult = 1_000;
            t = t.substring(0, t.length() - 1);
        } else if (suffix == 'm') {
            mult = 1_000_000;
            t = t.substring(0, t.length() - 1);
        } else if (suffix == 'b') {
            mult = 1_000_000_000;
            t = t.substring(0, t.length() - 1);
        }
        try {
            return (long) (Double.parseDouble(t.trim()) * mult);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable Integer parseDaysAgo(@Nullable String s) {
        if (s == null) return null;
        Matcher m = AGE_PATTERN.matcher(s);
        if (!m.find()) return null;
        int n;
        try {
            n = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        String unit = m.group(2).toLowerCase();
        return switch (unit) {
            case "second", "minute", "hour" -> 0;
            case "day" -> n;
            case "week" -> n * 7;
            case "month" -> n * 30;
            case "year" -> n * 365;
            default -> null;
        };
    }
}
