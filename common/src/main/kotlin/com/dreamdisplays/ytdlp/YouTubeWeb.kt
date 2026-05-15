package com.dreamdisplays.ytdlp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.inotsleep.utils.logging.LoggingManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/** `YouTube` web. **/
object YouTubeWeb {

    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Safari/537.36"
    private val AGE_PATTERN: Pattern = Pattern.compile(
        "(\\d+)\\s+(second|minute|hour|day|week|month|year)s?\\s+ago",
        Pattern.CASE_INSENSITIVE
    )


    @Throws(IOException::class)
    fun search(query: String, limit: Int): List<YtVideoInfo> {
        val url = "https://www.youtube.com/results?search_query=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8) + "&hl=en"
        return extractSearchVideos(fetchInitialData(url), limit)
    }


    @Throws(IOException::class)
    fun related(videoId: String, limit: Int): List<YtVideoInfo> {
        val url = "https://www.youtube.com/watch?v=$videoId&hl=en"
        return extractRelatedVideos(fetchInitialData(url), videoId, limit)
    }


    @Throws(IOException::class)
    fun metadata(videoId: String): YtVideoInfo? {
        val url = "https://www.youtube.com/watch?v=$videoId&hl=en"
        return extractWatchMetadata(fetchInitialData(url), videoId)
    }

    @Throws(IOException::class)
    private fun fetchInitialData(url: String): JsonObject {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        val realCookies = YtDlp.getPublicCookieHeader()
        conn.setRequestProperty("Cookie", realCookies ?: "CONSENT=YES+cb; SOCS=CAI; PREF=hl=en")
        try {
            conn.inputStream.use { input ->
                val body = String(input.readAllBytes(), StandardCharsets.UTF_8)
                val json = YtDlp.extractJsonObject(body, "var ytInitialData")
                    ?: YtDlp.extractJsonObject(body, "window[\"ytInitialData\"]")
                    ?: throw IOException("ytInitialData not found")
                return try {
                    JsonParser.parseString(json).asJsonObject
                } catch (e: Exception) {
                    throw IOException("Failed to parse ytInitialData JSON", e)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractSearchVideos(root: JsonObject, limit: Int): List<YtVideoInfo> {
        val out = ArrayList<YtVideoInfo>()
        try {
            val sections = path(root, "contents", "twoColumnSearchResultsRenderer", "primaryContents",
                "sectionListRenderer", "contents").asJsonArray
            for (sec in sections) {
                val isr = sec.asJsonObject.get("itemSectionRenderer")
                if (isr == null || !isr.isJsonObject) continue
                val contents = isr.asJsonObject.getAsJsonArray("contents") ?: continue
                for (el in contents) {
                    val obj = el.asJsonObject
                    val vr = obj.get("videoRenderer")
                    if (vr == null || !vr.isJsonObject) continue
                    parseVideoRenderer(vr.asJsonObject)?.let {
                        out.add(it)
                        if (out.size >= limit) return out
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.warn("Search parse failed: ${e.message}")
        }
        return out
    }

    private fun parseVideoRenderer(vr: JsonObject): YtVideoInfo? {
        val id = optString(vr, "videoId") ?: return null
        if (looksLikeShorts(vr)) return null
        val title = runsText(vr.getAsJsonObject("title"))
            ?: simpleText(vr.getAsJsonObject("title")) ?: id
        val uploader = runsText(vr.getAsJsonObject("ownerText"))
            ?: runsText(vr.getAsJsonObject("longBylineText"))
        val duration = parseDuration(simpleText(vr.getAsJsonObject("lengthText")))
        if (duration != null && duration < 65) return null
        val views = parseViews(simpleText(vr.getAsJsonObject("viewCountText")))
            ?: parseViews(simpleText(vr.getAsJsonObject("shortViewCountText")))
        val publishedText = simpleText(vr.getAsJsonObject("publishedTimeText"))
        val daysAgo = parseDaysAgo(publishedText)
        return YtVideoInfo(id, title, uploader, duration, views, null, publishedText, daysAgo)
    }

    private fun looksLikeShorts(vr: JsonObject): Boolean {
        try {
            val nav = vr.getAsJsonObject("navigationEndpoint")
            if (nav != null) {
                val cmd = nav.getAsJsonObject("commandMetadata")
                if (cmd != null) {
                    val web = cmd.getAsJsonObject("webCommandMetadata")
                    if (web != null) {
                        val webUrl = optString(web, "url")
                        if (webUrl != null && webUrl.startsWith("/shorts/")) return true
                    }
                }
            }
        } catch (_: Exception) {
        }
        val s = vr.toString()
        return "\"label\":\"Shorts\"" in s || "shortsLockupViewModel" in s
    }

    private fun extractRelatedVideos(root: JsonObject, selfId: String, limit: Int): List<YtVideoInfo> {
        val out = ArrayList<YtVideoInfo>()
        try {
            val results = path(root, "contents", "twoColumnWatchNextResults", "secondaryResults",
                "secondaryResults", "results").asJsonArray
            for (el in results) {
                if (!el.isJsonObject) continue
                val cvr = el.asJsonObject.get("compactVideoRenderer")
                if (cvr == null || !cvr.isJsonObject) continue
                val info = parseCompactVideoRenderer(cvr.asJsonObject)
                if (info != null && info.id != selfId) {
                    out.add(info)
                    if (out.size >= limit) return out
                }
            }
        } catch (e: Exception) {
            LoggingManager.warn("Related parse failed: ${e.message}")
        }
        return out
    }

    private fun parseCompactVideoRenderer(cvr: JsonObject): YtVideoInfo? {
        val id = optString(cvr, "videoId") ?: return null
        if (looksLikeShorts(cvr)) return null
        val title = simpleText(cvr.getAsJsonObject("title")) ?: id
        val uploader = simpleText(cvr.getAsJsonObject("longBylineText"))
            ?: simpleText(cvr.getAsJsonObject("shortBylineText"))
        val duration = parseDuration(simpleText(cvr.getAsJsonObject("lengthText")))
        if (duration != null && duration < 65) return null
        val views = parseViews(simpleText(cvr.getAsJsonObject("viewCountText")))
            ?: parseViews(simpleText(cvr.getAsJsonObject("shortViewCountText")))
        val publishedText = simpleText(cvr.getAsJsonObject("publishedTimeText"))
        val daysAgo = parseDaysAgo(publishedText)
        return YtVideoInfo(id, title, uploader, duration, views, null, publishedText, daysAgo)
    }

    private fun extractWatchMetadata(root: JsonObject, videoId: String): YtVideoInfo? {
        try {
            val contents = path(root, "contents", "twoColumnWatchNextResults", "results",
                "results", "contents").asJsonArray
            var title: String? = null
            var channel: String? = null
            var views: Long? = null
            var likes: Long? = null
            var publishedText: String? = null
            var daysAgo: Int? = null
            for (el in contents) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                if (obj.has("videoPrimaryInfoRenderer")) {
                    val vp = obj.getAsJsonObject("videoPrimaryInfoRenderer")
                    if (title == null) title = runsText(vp.getAsJsonObject("title"))
                    val dateText = simpleText(vp.getAsJsonObject("dateText"))
                    if (publishedText == null) publishedText = dateText
                    if (daysAgo == null) daysAgo = parseDaysAgo(dateText)
                    val vc = vp.getAsJsonObject("viewCount")
                    val viewCountObj = vc?.getAsJsonObject("videoViewCountRenderer")?.getAsJsonObject("viewCount")
                    var v = parseViews(runsText(viewCountObj))
                    if (v == null) v = parseViews(simpleText(maybeViewCountText(vp)))
                    if (v != null) views = v
                    likes = extractLikeCount(vp)
                }
                if (obj.has("videoSecondaryInfoRenderer")) {
                    val vs = obj.getAsJsonObject("videoSecondaryInfoRenderer")
                    if (channel == null) {
                        val owner = vs.getAsJsonObject("owner")
                        if (owner != null) {
                            val vor = owner.getAsJsonObject("videoOwnerRenderer")
                            if (vor != null) channel = runsText(vor.getAsJsonObject("title"))
                        }
                    }
                }
            }
            if (title == null) return null
            return YtVideoInfo(videoId, title, channel, null, views, likes, publishedText, daysAgo)
        } catch (e: Exception) {
            LoggingManager.warn("Watch metadata parse failed: ${e.message}")
            return null
        }
    }

    private fun maybeViewCountText(vp: JsonObject): JsonObject? {
        if (!vp.has("viewCount")) return null
        val vc = vp.getAsJsonObject("viewCount")
        if (vc.has("videoViewCountRenderer")) {
            val vvcr = vc.getAsJsonObject("videoViewCountRenderer")
            if (vvcr.has("viewCount")) return vvcr.getAsJsonObject("viewCount")
        }
        return null
    }

    private fun extractLikeCount(vp: JsonObject): Long? {
        val menu = vp.getAsJsonObject("videoActions") ?: return null
        val mr = menu.getAsJsonObject("menuRenderer") ?: return null
        val topLevel = mr.get("topLevelButtons")
        if (topLevel == null || !topLevel.isJsonArray) return null
        for (btn in topLevel.asJsonArray) {
            if (!btn.isJsonObject) continue
            val bo = btn.asJsonObject
            val sbvr = bo.getAsJsonObject("segmentedLikeDislikeButtonViewModel") ?: continue
            val lb = sbvr.getAsJsonObject("likeButtonViewModel") ?: continue
            val inner = lb.getAsJsonObject("likeButtonViewModel") ?: continue
            val toggleButton = inner.getAsJsonObject("toggleButtonViewModel") ?: continue
            val tbvm = toggleButton.getAsJsonObject("toggleButtonViewModel") ?: continue
            val defaultButton = tbvm.getAsJsonObject("defaultButtonViewModel") ?: continue
            val buttonViewModel = defaultButton.getAsJsonObject("buttonViewModel") ?: continue
            val parsed = parseViews(optString(buttonViewModel, "title"))
            if (parsed != null) return parsed
        }
        return null
    }

    private fun path(obj: JsonObject, vararg keys: String): JsonElement {
        var cur: JsonElement? = obj
        for (k in keys) {
            if (cur == null || !cur.isJsonObject) return JsonObject()
            cur = cur.asJsonObject.get(k)
        }
        return cur ?: JsonObject()
    }

    private fun optString(obj: JsonObject, key: String): String? {
        if (!obj.has(key) || obj.get(key).isJsonNull) return null
        return try { obj.get(key).asString } catch (_: Exception) { null }
    }

    private fun runsText(obj: JsonObject?): String? {
        if (obj == null || !obj.has("runs") || !obj.get("runs").isJsonArray) return null
        val sb = StringBuilder()
        for (el in obj.getAsJsonArray("runs")) {
            if (!el.isJsonObject) continue
            optString(el.asJsonObject, "text")?.let { sb.append(it) }
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun simpleText(obj: JsonObject?): String? {
        if (obj == null) return null
        return optString(obj, "simpleText") ?: runsText(obj)
    }

    private fun parseDuration(s: String?): Long? {
        if (s == null) return null
        val parts = s.split(":")
        return try {
            var total = 0L
            for (p in parts) total = total * 60 + p.trim().toInt()
            total
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseViews(s: String?): Long? {
        if (s == null) return null
        var t = s.lowercase().replace(",", "").replace("views", "").trim()
        if (t.isEmpty()) return null
        var mult = 1.0
        when (t.last()) {
            'k' -> { mult = 1_000.0; t = t.dropLast(1) }
            'm' -> { mult = 1_000_000.0; t = t.dropLast(1) }
            'b' -> { mult = 1_000_000_000.0; t = t.dropLast(1) }
        }
        return try { (t.trim().toDouble() * mult).toLong() } catch (_: NumberFormatException) { null }
    }

    private fun parseDaysAgo(s: String?): Int? {
        if (s == null) return null
        val m = AGE_PATTERN.matcher(s)
        if (!m.find()) return null
        val n = try { m.group(1).toInt() } catch (_: NumberFormatException) { return null }
        return when (m.group(2).lowercase()) {
            "second", "minute", "hour" -> 0
            "day" -> n
            "week" -> n * 7
            "month" -> n * 30
            "year" -> n * 365
            else -> null
        }
    }
}
