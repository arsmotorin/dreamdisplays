package com.dreamdisplays.downloader

import com.dreamdisplays.util.Utils
import me.inotsleep.utils.logging.LoggingManager
import org.freedesktop.gstreamer.Gst
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.regex.Pattern
import kotlin.concurrent.thread

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
object Init {

    // Pattern to match Linux/Unix shared object files
    private val SO_PATTERN = Pattern.compile(
        ".*\\.so(\\.\\d+)*$",
        Pattern.CASE_INSENSITIVE
    )
    // Pattern to match macOS dynamic libraries
    private val DYLIB_PATTERN = Pattern.compile(
        ".*\\.(dylib|jnilib)$",
        Pattern.CASE_INSENSITIVE
    )

    // Sets up the library path for GStreamer and loads the libraries
    @Throws(IOException::class)
    private fun setupLibraryPath() {
        val gStreamerLibrariesDir = File("./libs/gstreamer")

        val files = File(gStreamerLibrariesDir, "bin").listFiles()?.toList() ?: emptyList()

        Listener.progress = 0f
        Listener.task = "Loading libraries for Dream Launcher 0/0"
        loadLibraries(recursiveLoadLibs(files))

        System.setProperty(
            "jna.library.path",
            listOf(
                File(gStreamerLibrariesDir, "bin").canonicalPath,
                File(gStreamerLibrariesDir, "lib").canonicalPath
            ).joinToString(File.pathSeparator)
        )
        try {
            Gst.init("MediaPlayer")
        } catch (e: Throwable) {
            LoggingManager.error(
                "Failed to initialize GStreamer after loading libraries",
                e
            )
            Listener.isFailed = true
            throw RuntimeException(e)
        }
    }

    // Loads the specified libraries, handling dependencies
    fun loadLibraries(libraries: Collection<String>) {
        val toLoad = ArrayDeque(libraries)
        val total = libraries.size
        var loadedCount = 0

        Listener.task = "Loading libraries for Dream Displays ${0}/$total"

        while (!toLoad.isEmpty()) {
            val passSize = toLoad.size
            var loadedThisPass = 0

            // Try to load other libraries.
            repeat(passSize) {
                val path = toLoad.removeFirst()
                try {
                    System.load(path)
                    loadedCount++
                    loadedThisPass++

                    // Update progress and task message
                    Listener.progress = loadedCount.toFloat() / total

                    Listener.task = "Loading libraries for Dream Displays $loadedCount/$total ($loadedThisPass/$passSize)"
                } catch (_: LinkageError) {
                    toLoad.addLast(path)
                }
            }

            if (loadedThisPass == 0) {
                LoggingManager.error("Dream Displays can't load some libraries:")
                toLoad.forEach { p -> LoggingManager.error("  $p") }

                Listener.isFailed = true
                return
            }
        }

        Listener.isDone = true
    }

    // Checks if a file name corresponds to a library file
    private fun isLib(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        // Windows
        if (lower.endsWith(".dll")) {
            return true
        }
        // macOS
        if (DYLIB_PATTERN.matcher(name).matches()) {
            return true
        }
        // Linux/Unix (including .so.1, .so.1.2, etc.)
        return SO_PATTERN.matcher(name).matches()
    }

    private fun recursiveLoadLibs(files: List<File>): List<String> {
        val libs = ArrayList<String>()

        for (file in files) {
            if (isLib(file.name)) {
                libs.add(file.absolutePath)
            } else if (file.isDirectory) {
                val subFiles = file.listFiles()
                if (subFiles != null) {
                    libs.addAll(recursiveLoadLibs(subFiles.toList()))
                }
            }
        }

        return libs
    }

    fun init() {
        val platform = Utils.detectPlatform()
        if (platform != "windows") {
            Listener.isFailed = true
            Listener.isDone = false
            return
        }

        val gStreamerLibrariesDir = File("./libs/gstreamer")
        if (!gStreamerLibrariesDir.exists() && !gStreamerLibrariesDir.mkdirs()) {
            LoggingManager.error("Unable to mk directory")
        }

        thread {
            val downloader = Downloader()
            var downloadGStreamer: Boolean

            try {
                downloadGStreamer = !downloader.downloadGstreamerChecksum()
            } catch (e: IOException) {
                LoggingManager.error(
                    "Failed to download GStreamer checksum.",
                    e
                )
                Listener.isFailed = true
                return@thread
            }

            val gStreamerBinLibrariesDir = File("./libs/gstreamer/bin")
            downloadGStreamer = downloadGStreamer || !gStreamerBinLibrariesDir.exists()

            if (downloadGStreamer) {
                try {
                    downloader.downloadGstreamerBuild()
                } catch (e: IOException) {
                    LoggingManager.error("Failed to download GStreamer.", e)
                    Listener.isFailed = true
                    return@thread
                }

                downloader.extractGstreamer(true)
            }

            try {
                setupLibraryPath()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
