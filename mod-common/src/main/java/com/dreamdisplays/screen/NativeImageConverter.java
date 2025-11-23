package com.dreamdisplays.screen;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

// Native image format converter for high-performance pixel operations.
// Converts RGBA/ABGR formats using native code
public class NativeImageConverter {

    private static boolean nativeAvailable = false;

    static {
        try {
            // Determine OS and architecture
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch").toLowerCase();
            String libName;
            String libExtension;

            if (osName.contains("win")) {
                libName = "dreamdisplays_native";
                libExtension = ".dll";
            } else if (osName.contains("mac")) {
                libName = "libdreamdisplays_native";
                libExtension = ".dylib";
            } else {
                libName = "libdreamdisplays_native";
                libExtension = ".so";
            }

            String fullLibName = libName + libExtension;
            String resourcePath = "/natives/" + fullLibName;

            // Load the library from resources
            InputStream libStream = NativeImageConverter.class.getResourceAsStream(resourcePath);

            if (libStream != null) {
                File tempLib = File.createTempFile("dreamdisplays_native_", libExtension);
                tempLib.deleteOnExit();

                // Copy the library to a temporary file
                Files.copy(libStream, tempLib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                libStream.close();

                // Load the native library
                System.load(tempLib.getAbsolutePath());
                nativeAvailable = true;
                System.out.println("Dream Displays: Native library loaded (" + osName + "/" + osArch + ")");
            } else {
                System.out.println("Dream Displays: Native library not found, using Java fallback");
                nativeAvailable = false;
            }
        } catch (Exception e) {
            System.err.println("Dream Displays: Failed to load native library: " + e.getMessage());
            nativeAvailable = false;
        }
    }

    // Convert RGBA to ARGB format
    private static native void convertRGBAtoARGB(byte[] src, byte[] dst, int length);

    // Convert ABGR to RGBA format and write to direct buffer
    private static native void convertABGRtoRGBA(byte[] src, ByteBuffer dst, int length);

    // Convert RGBA to ARGB format
    public static void rgbaToArgb(byte[] src, byte[] dst, int length) {
        if (nativeAvailable) {
            convertRGBAtoARGB(src, dst, length);
        } else {
            // Java fallback
            for (int i = 0; i < length; i += 4) {
                byte r = src[i], g = src[i + 1], b = src[i + 2], a = src[i + 3];
                dst[i] = a;
                dst[i + 1] = b;
                dst[i + 2] = g;
                dst[i + 3] = r;
            }
        }
    }

    // Convert ABGR to RGBA format and write to direct buffer
    public static void abgrToRgbaDirect(byte[] src, ByteBuffer dst, int length) {
        if (nativeAvailable) {
            convertABGRtoRGBA(src, dst, length);
        } else {
            // Java fallback
            for (int i = 0; i < length; i += 4) {
                byte a = src[i], b = src[i + 1], g = src[i + 2], r = src[i + 3];
                dst.put(r).put(g).put(b).put(a);
            }
        }
    }
}
