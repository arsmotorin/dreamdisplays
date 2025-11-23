package com.dreamdisplays.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.inotsleep.utils.logging.LoggingManager;
import org.jspecify.annotations.NullMarked;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NullMarked
public class ClientDisplaySettings {

    // TODO: move to adequate path
    private static final File SETTINGS_FILE = new File("./config/dreamdisplays/client-display-settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, DisplaySettings> displaySettings = new HashMap<>();

    public static class DisplaySettings {
        public float volume = 0.5f;
        public String quality = "720";
        public boolean muted = false;

        public DisplaySettings() {}

        public DisplaySettings(float volume, String quality, boolean muted) {
            this.volume = volume;
            this.quality = quality;
            this.muted = muted;
        }
    }

    // Load settings from disk
    public static void load() {

        // Ensure directory exists
        File dir = SETTINGS_FILE.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            LoggingManager.error("Failed to create settings directory.");
            return;
        }

        try (Reader reader = new FileReader(SETTINGS_FILE)) {
            Type type = new TypeToken<Map<String, DisplaySettings>>(){}.getType();
            Map<String, DisplaySettings> loadedSettings = GSON.fromJson(reader, type);

            if (loadedSettings != null) {
                displaySettings.clear();
                for (Map.Entry<String, DisplaySettings> entry : loadedSettings.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        displaySettings.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        LoggingManager.error("Invalid UUID in client display settings: " + entry.getKey());
                    }
                }
            }
        } catch (IOException e) {
            LoggingManager.error("Failed to load client display settings", e);
        }
    }

    // Save settings to disk
    public static void save() {
        try {
            File dir = SETTINGS_FILE.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                LoggingManager.error("Failed to create settings directory.");
                return;
            }

            Map<String, DisplaySettings> toSave = new HashMap<>();
            for (Map.Entry<UUID, DisplaySettings> entry : displaySettings.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }

            try (Writer writer = new FileWriter(SETTINGS_FILE)) {
                GSON.toJson(toSave, writer);
            }
        } catch (IOException e) {
            LoggingManager.error("Failed to save client display settings", e);
        }
    }

    // Get settings for a display
    public static DisplaySettings getSettings(UUID displayId) {
        return displaySettings.computeIfAbsent(displayId, k -> new DisplaySettings());
    }

    // Update settings for a display
    public static void updateSettings(UUID displayId, float volume, String quality, boolean muted) {
        DisplaySettings settings = getSettings(displayId);
        settings.volume = volume;
        settings.quality = quality;
        settings.muted = muted;

        save();
    }
}
