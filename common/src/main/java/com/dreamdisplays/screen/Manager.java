package com.dreamdisplays.screen;

import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for all screen displays.
 */
@NullMarked
public class Manager {

    public static final ConcurrentHashMap<UUID, Screen> screens =
            new ConcurrentHashMap<>();

    // Cache of unloaded displays
    public static final ConcurrentHashMap<UUID, Settings.FullDisplayData> unloadedScreens =
            new ConcurrentHashMap<>();

    public Manager() {
    }

    public static Collection<Screen> getScreens() {
        return screens.values();
    }

    public static void registerScreen(Screen screen) {
        if (screens.containsKey(screen.getUUID())) {
            Screen old = screens.get(screen.getUUID());
            old.unregister();
        }

        Settings.DisplaySettings clientSettings = Settings.getSettings(
                screen.getUUID()
        );
        screen.setVolume(clientSettings.volume);
        screen.setQuality(clientSettings.quality);
        screen.muted = clientSettings.muted;

        Settings.FullDisplayData savedData = Settings.getDisplayData(
                screen.getUUID()
        );
        if (savedData != null) {
            screen.setRenderDistance(savedData.renderDistance);
            screen.setSavedTimeNanos(savedData.currentTimeNanos);
        }

        screens.put(screen.getUUID(), screen);

        // Save the display data for persistence
        saveScreenData(screen);
    }

    public static void unregisterScreen(Screen screen) {
        // Cache the display data before unregistering
        String videoUrl = screen.getVideoUrl();
        String lang = screen.getLang();
        UUID ownerUuid = screen.getOwnerUuid();

        Settings.FullDisplayData data = new Settings.FullDisplayData(
                screen.getUUID(),
                screen.getPos().getX(),
                screen.getPos().getY(),
                screen.getPos().getZ(),
                screen.getFacing(),
                (int) screen.getWidth(),
                (int) screen.getHeight(),
                videoUrl != null ? videoUrl : "",
                lang != null ? lang : "",
                (float) screen.getVolume(),
                screen.getQuality(),
                screen.muted,
                screen.isSync,
                ownerUuid != null ? ownerUuid : screen.getUUID(),
                screen.getRenderDistance(),
                screen.getCurrentTimeNanos()
        );
        unloadedScreens.put(screen.getUUID(), data);

        screens.remove(screen.getUUID());
        screen.unregister();
    }

    public static void unloadAll() {
        for (Screen screen : screens.values()) {
            screen.unregister();
        }

        screens.clear();
        unloadedScreens.clear(); // Clear cache when changing servers
    }

    // Save screen data to persistent storage
    public static void saveScreenData(Screen screen) {
        Settings.FullDisplayData data = new Settings.FullDisplayData(
                screen.getUUID(),
                screen.getPos().getX(),
                screen.getPos().getY(),
                screen.getPos().getZ(),
                screen.getFacing(),
                (int) screen.getWidth(),
                (int) screen.getHeight(),
                screen.getVideoUrl(),
                screen.getLang(),
                (float) screen.getVolume(),
                screen.getQuality(),
                screen.muted,
                screen.isSync,
                screen.getOwnerUuid(),
                screen.getRenderDistance(),
                screen.getCurrentTimeNanos()
        );

        Settings.saveDisplayData(screen.getUUID(), data);
    }

    // Load displays from persistent storage for a server
    // Actual display data comes from the server via Info packets.
    // Local cache is used only for client preferences (volume, quality, muted).
    public static void loadScreensForServer(String serverId) {
        Settings.loadServerDisplays(serverId);
        // Displays will be received from server via Info packets
    }

    // Save all screens to persistent storage for current server
    public static void saveAllScreens() {
        for (Screen screen : screens.values()) {
            saveScreenData(screen);
        }
    }
}
