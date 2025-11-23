package com.dreamdisplays.managers;

import com.github.zafarkhaja.semver.Version;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {
    private static final Map<UUID, Version> versions = new HashMap<>();
    private static final Map<UUID, Boolean> modUpdateNotified = new HashMap<>();
    private static final Map<UUID, Boolean> pluginUpdateNotified = new HashMap<>();

    public static Version getVersion(final Player player) {
        return versions.get(player.getUniqueId());
    }

    public static void setVersion(final Player player, final Version version) {
        versions.put(player.getUniqueId(), version);
    }

    public static void removeVersion(final Player player) {
        versions.remove(player.getUniqueId());
        modUpdateNotified.remove(player.getUniqueId());
        pluginUpdateNotified.remove(player.getUniqueId());
    }

    public static Collection<Version> getVersions() {
        return versions.values();
    }

    public static boolean hasBeenNotifiedAboutModUpdate(final Player player) {
        return modUpdateNotified.getOrDefault(player.getUniqueId(), false);
    }

    public static void setModUpdateNotified(final Player player, boolean notified) {
        modUpdateNotified.put(player.getUniqueId(), notified);
    }

    public static boolean hasBeenNotifiedAboutPluginUpdate(final Player player) {
        return pluginUpdateNotified.getOrDefault(player.getUniqueId(), false);
    }

    public static void setPluginUpdateNotified(final Player player, boolean notified) {
        pluginUpdateNotified.put(player.getUniqueId(), notified);
    }
}
