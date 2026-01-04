package com.dreamdisplays.downloader;

import me.inotsleep.utils.logging.LoggingManager;
import org.freedesktop.gstreamer.Gst;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static com.dreamdisplays.util.Utils.detectPlatform;

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
public class Init {

    // Pattern to match Linux/Unix shared object files
    private static final Pattern SO_PATTERN = Pattern.compile(
            ".*\\.so(\\.\\d+)*$",
            Pattern.CASE_INSENSITIVE
    );
    // Pattern to match macOS dynamic libraries
    private static final Pattern DYLIB_PATTERN = Pattern.compile(
            ".*\\.(dylib|jnilib)$",
            Pattern.CASE_INSENSITIVE
    );

    // Sets up the library path for GStreamer and loads the libraries
    private static void setupLibraryPath() throws IOException {
        final File gStreamerLibrariesDir = new File("./libs/gstreamer");

        List<File> files = List.of(
                Objects.requireNonNull(
                        new File(gStreamerLibrariesDir, "bin").listFiles()
                )
        );

        Listener.INSTANCE.setProgress(0f);
        Listener.INSTANCE.setTask("Loading libraries for Dream Launcher 0/0");
        loadLibraries(recursiveLoadLibs(files));

        System.setProperty(
                "jna.library.path",
                String.join(
                        File.pathSeparator,
                        new File(gStreamerLibrariesDir, "bin").getCanonicalPath(),
                        new File(gStreamerLibrariesDir, "lib").getCanonicalPath()
                )
        );
        try {
            Gst.init("MediaPlayer");
        } catch (Throwable e) {
            LoggingManager.error(
                    "Failed to initialize GStreamer after loading libraries",
                    e
            );
            Listener.INSTANCE.setFailed(true);
            throw new RuntimeException(e);
        }
    }

    // Loads the specified libraries, handling dependencies
    public static void loadLibraries(Collection<String> libraries) {
        Deque<String> toLoad = new ArrayDeque<>(libraries);
        int total = libraries.size();
        int loadedCount = 0;

        Listener.INSTANCE.setTask(
                String.format(
                        "Loading libraries for Dream Displays %d/%d",
                        loadedCount,
                        total
                )
        );
        while (!toLoad.isEmpty()) {
            int passSize = toLoad.size();
            int loadedThisPass = 0;

            // Try to load other libraries.
            for (int i = 0; i < passSize; i++) {
                String path = toLoad.removeFirst();
                try {
                    System.load(path);
                    loadedCount++;
                    loadedThisPass++;

                    // Update progress and task message
                    Listener.INSTANCE.setProgress(
                            ((float) loadedCount) / total
                    );

                    Listener.INSTANCE.setTask(
                            String.format(
                                    "Loading libraries for Dream Displays %d/%d (%d/%d)",
                                    loadedCount,
                                    total,
                                    loadedThisPass,
                                    passSize
                            )
                    );
                } catch (LinkageError e) {
                    toLoad.addLast(path);
                }
            }

            if (loadedThisPass == 0) {
                LoggingManager.error(
                        "Dream Displays can't load some libraries:"
                );
                toLoad.forEach(p -> LoggingManager.error("  " + p));

                Listener.INSTANCE.setFailed(true);
                return;
            }
        }

        Listener.INSTANCE.setDone(true);
    }

    // Checks if a file name corresponds to a library file
    private static boolean isLib(@Nullable String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        // Windows
        if (lower.endsWith(".dll")) {
            return true;
        }
        // macOS
        if (DYLIB_PATTERN.matcher(name).matches()) {
            return true;
        }
        // Linux/Unix (including .so.1, .so.1.2, etc.)
        return SO_PATTERN.matcher(name).matches();
    }

    private static List<String> recursiveLoadLibs(List<File> files) {
        List<String> libs = new ArrayList<>();

        for (File file : files) {
            if (isLib(file.getName())) libs.add(file.getAbsolutePath());
            else if (file.isDirectory()) libs.addAll(
                    recursiveLoadLibs(
                            List.of(Objects.requireNonNull(file.listFiles()))
                    )
            );
        }

        return libs;
    }

    public static void init() {
        String platform = detectPlatform();
        if (!platform.equals("windows")) {
            Listener.INSTANCE.setFailed(true);
            Listener.INSTANCE.setDone(false);
            return;
        }

        final File gStreamerLibrariesDir = new File("./libs/gstreamer");
        if (
                !gStreamerLibrariesDir.exists() && gStreamerLibrariesDir.mkdirs()
        ) LoggingManager.error("Unable to mk directory");

        Thread downloadThread = new Thread(() -> {
            Downloader downloader = new Downloader();
            boolean downloadGStreamer;

            try {
                downloadGStreamer = !downloader.downloadGstreamerChecksum();
            } catch (IOException e) {
                LoggingManager.error(
                        "Failed to download GStreamer checksum.",
                        e
                );
                Listener.INSTANCE.setFailed(true);
                return;
            }

            File gStreamerBinLibrariesDir = new File("./libs/gstreamer/bin");
            downloadGStreamer |= !gStreamerBinLibrariesDir.exists();

            if (downloadGStreamer) {
                try {
                    downloader.downloadGstreamerBuild();
                } catch (IOException e) {
                    LoggingManager.error("Failed to download GStreamer.", e);
                    Listener.INSTANCE.setFailed(true);
                    return;
                }

                downloader.extractGstreamer(true);
            }

            try {
                setupLibraryPath();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        downloadThread.start();
    }
}
