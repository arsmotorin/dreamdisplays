package com.dreamdisplays.util;

import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NullMarked
public class Utils {

    // Detects the current operating system platform
    public static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (os.contains("win")) {
            return "windows";
        } else if (os.contains("mac")) {
            return "macos";
        } else if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            return "linux";
        }
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }

    // Extracts video ID from various YouTube URL formats
    @Nullable
    public static String extractVideoId(String youtubeUrl) {
        try {
            URI uri = new URI(youtubeUrl);
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && pair[0].equals("v")) {
                        return pair[1];
                    }
                }
            }

            // If the URL is a shortened version or a YouTube Shorts link
            String host = uri.getHost();
            if (host != null && host.contains("youtu.be")) {
                String path = uri.getPath();
                if (path != null && path.length() > 1) {
                    return path.substring(1);
                }
            } else if (host != null && host.contains("youtube.com")) {
                String path = uri.getPath();
                if (path != null && path.contains("shorts")) {
                    return List.of(path.split("/")).getLast();
                }
            }
        } catch (URISyntaxException ignored) {
        }

        String regex = "(?<=([?&]v=))[^#&?]*";
        Matcher m = Pattern.compile(regex).matcher(youtubeUrl);
        return m.find() ? m.group() : null;
    }

    public static String readResource(String resourcePath) throws IOException {
        try (InputStream in = Utils.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Can't find the resource: " + resourcePath);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    // Reads the mod version from the appropriate metadata file
    public static String getModVersion() {
        // Fabric
        try {
            String fabricJson = readResource("/fabric.mod.json");
            Pattern pattern = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(fabricJson);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException ignored) {
        }

        // NeoForge/Forge
        try {
            String neoforgeToml = readResource("/META-INF/neoforge.mods.toml");
            Pattern pattern = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(neoforgeToml);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException ignored) {
        }

        return "unknown";
    }

    // Check if a certificate is already installed in the CurrentUser\Root store
    private static boolean isInstalled(File certFile) throws Exception {

        String subject;
        try (InputStream is = Files.newInputStream(certFile.toPath())) {
            X509Certificate cert = (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(is);
            subject = cert.getSubjectX500Principal().getName();
        }

        ProcessBuilder pb = new ProcessBuilder("certutil", "-store", "-user", "Root");
        Process p = pb.start();
        String all = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return all.contains(subject);
    }

    // Install all .cer files from certDir to the CurrentUser\Root store
    public static void installToCurrentUserRoot(File certDir) throws Exception {
        if (!certDir.exists() || !certDir.isDirectory()) {
            throw new IllegalArgumentException("Error while installing certificates: " +
                    certDir.getAbsolutePath());
        }

        File[] cerFiles = certDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".cer"));
        if (cerFiles == null || cerFiles.length == 0) {
            LoggingManager.info("No .cer files found in " + certDir.getAbsolutePath());
            return;
        }

        for (File cert : cerFiles) {
            LoggingManager.info("Installing " + cert.getAbsolutePath());

            if (isInstalled(cert)) {
                LoggingManager.info("Cert already installed: " + cert.getAbsolutePath());
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(
                    "certutil",
                    "-addstore",
                    "-user",
                    "Root",
                    cert.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            try (InputStream is = proc.getInputStream()) {
                is.transferTo(System.out);
            }

            int code = proc.waitFor();
            if (code != 0) {
                throw new RuntimeException(
                        "Certificate error " +
                                cert.getName() + ", error code: " + code
                );
            }
        }

        LoggingManager.info("Certificates installed successfully from ");
    }
}
