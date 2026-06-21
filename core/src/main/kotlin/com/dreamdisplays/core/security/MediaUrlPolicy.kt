package com.dreamdisplays.core.security

/**
 * Trust-boundary policy for client-supplied media URLs.
 */
object MediaUrlPolicy {
    private val BARE_YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

    /**
     * Maximum accepted URL length. Comfortably above any legitimate media URL while capping the
     * amplification a single client can trigger: the server stores, persists and rebroadcasts the
     * URL to every viewer, each of whom then attempts to resolve it.
     */
    const val MAX_URL_LENGTH = 2048

    /**
     * A bare 11-character YouTube id (which may legitimately start with `-`), or a trimmed
     * `http://` / `https://` URL with no whitespace or control characters, within [MAX_URL_LENGTH].
     */
    fun isAllowed(url: String): Boolean {
        if (url.isEmpty()) return true
        if (url.length > MAX_URL_LENGTH) return false
        val s = url.trim()
        if (s.isEmpty()) return false
        if (s.any { it.isWhitespace() || it.isISOControl() }) return false
        if (s.length == 11 && BARE_YOUTUBE_ID.matches(s)) return true
        val lower = s.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
