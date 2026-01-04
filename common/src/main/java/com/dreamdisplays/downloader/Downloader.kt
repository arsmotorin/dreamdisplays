package com.dreamdisplays.downloader

import com.dreamdisplays.util.Utils
import me.inotsleep.utils.logging.LoggingManager
import org.apache.commons.io.FileUtils
import org.jspecify.annotations.NullMarked
import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipFile

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
class Downloader {
    val gStreamerDownloadUrl: String
        get() = formatURL(GSTREAMER_DOWNLOAD_URL)

    val gStreamerChecksumDownloadUrl: String
        get() = formatURL(GSTREAMER_CHECKSUM_DOWNLOAD_URL)

    private fun formatURL(url: String): String {
        return url.replace("\${platform}", Utils.detectPlatform())
    }

    @Throws(IOException::class)
    fun downloadGstreamerBuild() {
        val gStreamerLibrariesPath = File("./libs/gstreamer")
        Listener.task = "Downloading GStreamer"
        downloadFile(
            this.gStreamerDownloadUrl,
            File(gStreamerLibrariesPath, "gstreamer.zip")
        )
    }

    @Throws(IOException::class)
    fun downloadGstreamerChecksum(): Boolean {
        val gStreamerLibrariesPath = File("./libs/gstreamer")
        val gStreamerHashFileTemp = File(
            gStreamerLibrariesPath,
            "gstreamer.zip.sha256.temp"
        )
        val gStreamerHashFile = File(
            gStreamerLibrariesPath,
            "gstreamer.zip.sha256"
        )

        Listener.task = "Downloading Checksum"
        downloadFile(this.gStreamerChecksumDownloadUrl, gStreamerHashFileTemp)

        if (gStreamerHashFile.exists()) {
            val sameContent = FileUtils.contentEquals(
                gStreamerHashFile,
                gStreamerHashFileTemp
            )
            if (sameContent) {
                if (!gStreamerHashFileTemp.delete()) LoggingManager.warn(
                    "Unable to delete directory"
                )
                return true
            } else {
                LoggingManager.warn("GStreamer Hash does not match.")
            }
        } else {
            LoggingManager.warn("Failed to download GStreamer hash.")
        }

        if (!gStreamerHashFileTemp.renameTo(gStreamerHashFile)) {
            LoggingManager.warn("Unable to rename directory")
        }

        return false
    }

    fun extractGstreamer(delete: Boolean) {
        val gStreamerLibrariesPath = File("./libs/gstreamer")
        val tarGzArchive = File(gStreamerLibrariesPath, "gstreamer.zip")
        extractZip(tarGzArchive, gStreamerLibrariesPath)
        if (delete && tarGzArchive.exists()) {
            if (!tarGzArchive.delete()) LoggingManager.warn(
                "Unable to delete file"
            )
        }
    }

    companion object {
        // URLs for downloading GStreamer builds and checksums
        private const val GSTREAMER_DOWNLOAD_URL =
            "https://github.com/arsmotorin/dreamdisplays/releases/download/gstreamer/gstreamer-\${platform}.zip"
        private const val GSTREAMER_CHECKSUM_DOWNLOAD_URL = "$GSTREAMER_DOWNLOAD_URL.sha256"

        @Throws(IOException::class)
        private fun downloadFile(urlString: String, outputFile: File) {
            LoggingManager.info("$urlString -> ${outputFile.canonicalPath}")

            val url = URI(urlString).toURL()
            var conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "Java/" + System.getProperty("java.version")
            )
            conn.setRequestProperty("Accept", "application/octet-stream")

            var status = conn.responseCode
            if (status / 100 == 3) {
                val redirectUrl = conn.getHeaderField("Location")
                conn.disconnect()
                conn = URI(redirectUrl).toURL().openConnection() as HttpURLConnection
                conn.setRequestProperty(
                    "User-Agent",
                    "Java/" + System.getProperty("java.version")
                )
                status = conn.responseCode
            }

            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP $status for $urlString")
            }

            val fileSize = conn.contentLengthLong

            conn.inputStream.buffered().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead: Long = 0
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead.toLong()
                        if (fileSize > 0) {
                            Listener.progress = totalRead.toFloat() / fileSize
                        }
                    }
                }
            }
            conn.disconnect()
        }

        // Extracts a zip file to the specified output directory
        private fun extractZip(zipFile: File, outputDirectory: File) {
            Listener.task = "Extracting"
            if (!outputDirectory.parentFile.exists() &&
                !outputDirectory.parentFile.mkdirs()
            ) LoggingManager.warn("Unable to mk directory")

            val fileSize = zipFile.length()
            var totalBytesRead: Long = 0

            try {
                ZipFile(zipFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val outputFile = File(outputDirectory, entry.name)

                        if (!outputFile.parentFile.exists() &&
                            !outputFile.parentFile.mkdirs()
                        ) LoggingManager.warn("Unable to mk directory")

                        if (entry.isDirectory) {
                            continue
                        }

                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outputFile).use { output ->
                                val buffer = ByteArray(4096)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead.toLong()

                                    Listener.progress = totalBytesRead.toFloat() / fileSize
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                LoggingManager.error(
                    "Failed to extract zip file to $outputDirectory",
                    e
                )
            }

            Listener.progress = 1.0f
        }
    }
}
