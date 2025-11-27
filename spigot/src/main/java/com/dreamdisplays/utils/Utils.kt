package com.dreamdisplays.utils

/**
 * YouTube URL extraction and string sanitization.
 */
object Utils {

    // List of regex patterns to match different YouTube URL formats
    private val patterns = listOf(
        // https://www.youtube.com/watch?v=ID
        Regex("""[?&]v=([A-Za-z0-9_-]{6,})"""),

        // https://youtu.be/ID
        Regex("""youtu\.be/([A-Za-z0-9_-]{6,})"""),

        // https://youtube.com/shorts/ID
        Regex("""/shorts/([A-Za-z0-9_-]{6,})""")
    )

    // Extract YouTube video ID from a given URL
    fun extractVideo(url: String): String? {
        val u = url.trim()

        for (pattern in patterns) {
            val match = pattern.find(u)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    // Sanitize a string by removing unwanted characters
    fun sanitize(s: String?) =
        s?.trim()?.replace(Regex("[^0-9A-Za-z+.-]"), "")
}
