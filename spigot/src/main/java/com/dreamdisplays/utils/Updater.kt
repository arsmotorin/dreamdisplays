package com.dreamdisplays.utils

import com.dreamdisplays.Main
import com.github.zafarkhaja.semver.Version
import me.inotsleep.utils.logging.LoggingManager
import org.jspecify.annotations.NullMarked
import java.util.regex.Pattern

@NullMarked
object Updater {

    private val tailPattern = Pattern.compile("\\d[\\s\\S]*")

    fun checkForUpdates() {
        try {
            val settings = Main.config.settings

            val releases = Fetcher.fetchReleases(
                settings.repoOwner,
                settings.repoName
            )

            if (releases.isEmpty()) return

            Main.modVersion = releases
                .mapNotNull { parseVersion(it.tagName) }
                .maxOrNull()

            Main.pluginLatestVersion = releases
                .filter { it.tagName.contains("spigot", ignoreCase = true) || it.tagName.contains("plugin", ignoreCase = true) }
                .mapNotNull { parseVersion(it.tagName)?.toString() }
                .maxOrNull() ?: Main.modVersion?.toString()

        } catch (e: Exception) {
            LoggingManager.warn("Unable to load versions from GitHub", e)
        }
    }

    private fun parseVersion(tag: String): Version? {
        val extracted = extractTail(tag).takeIf { it.isNotBlank() } ?: return null
        return runCatching { Version.parse(extracted) }.getOrNull()
    }

    private fun extractTail(input: String): String {
        val matcher = tailPattern.matcher(input)
        return if (matcher.find()) matcher.group() else ""
    }
}
