package com.dreamdisplays.utils;

import com.dreamdisplays.datatypes.DisplayData;
import org.bukkit.Location;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static boolean isInBoundaries(Location pos1, Location pos2, Location location) {
        if (location.getWorld() != pos1.getWorld()) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());

        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        if (minX > location.getBlockX() || location.getBlockX() > maxX) return false;
        if (minY > location.getBlockY() || location.getBlockY() > maxY) return false;
        return minZ <= location.getBlockZ() && location.getBlockZ() <= maxZ;
    }

    public static double getDistanceToScreen(Location location, DisplayData displayData) {
        int minX = Math.min(displayData.getPos1().getBlockX(), displayData.getPos2().getBlockX());
        int minY = Math.min(displayData.getPos1().getBlockY(), displayData.getPos2().getBlockY());
        int minZ = Math.min(displayData.getPos1().getBlockZ(), displayData.getPos2().getBlockZ());

        int maxX = Math.max(displayData.getPos1().getBlockX(), displayData.getPos2().getBlockX());
        int maxY = Math.max(displayData.getPos1().getBlockY(), displayData.getPos2().getBlockY());
        int maxZ = Math.max(displayData.getPos1().getBlockZ(), displayData.getPos2().getBlockZ());

        int clampedX = Math.min(Math.max(location.getBlockX(), minX), maxX);
        int clampedY = Math.min(Math.max(location.getBlockY(), minY), maxY);
        int clampedZ = Math.min(Math.max(location.getBlockZ(), minZ), maxZ);

        Location closestPoint = new Location(location.getWorld(), clampedX, clampedY, clampedZ);

        return closestPoint.distance(location);
    }

    public static String extractVideoId(String youtubeUrl) {
        try {
            URI uri = new URI(youtubeUrl);
            String query = uri.getQuery(); // Takes part after "?"
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && pair[0].equals("v")) {
                        return pair[1];
                    }
                }
            }
            // If youtu.be/ID
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

    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().replaceAll("[^0-9A-Za-z+.-]", "");
    }
}
