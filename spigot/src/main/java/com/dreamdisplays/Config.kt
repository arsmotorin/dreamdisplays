package com.dreamdisplays;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.moandjiezana.toml.Toml;
import org.bukkit.Material;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    private static final Gson gson = new Gson();
    private final File configFile;
    private Toml toml;

    public LanguageSection language;
    public SettingsSection settings;
    public StorageSection storage;
    public PermissionsSection permissions;
    public final Map<String, Object> messages = new HashMap<>();

    // Constructor
    public Config(DreamDisplaysPlugin plugin) {
        this.configFile = new File(plugin.getDataFolder(), "config.toml");

        if (!configFile.exists()) {
            if (!plugin.getDataFolder().mkdirs() && !plugin.getDataFolder().exists()) {
                plugin.getLogger().severe("Could not create plugin data folder");
            }
            try (InputStream in = plugin.getResource("config.toml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create default config.toml: " + e.getMessage());
            }
        }

        extractLangFiles(plugin, true); // Force overwrite on first run
        load();

        this.language = new LanguageSection(toml);
        this.settings = new SettingsSection(toml);
        this.storage = new StorageSection(toml);
        this.permissions = new PermissionsSection(toml);

        // Cache values
        settings.cache();
        storage.cache();
        permissions.cache();

        String selectedLang = language.getMessageLanguage();
        me.inotsleep.utils.logging.LoggingManager.log("Loading messages for language: " + selectedLang);
        setMessages(selectedLang);
    }

    // Load configuration
    private void load() {
        try {
            toml = new Toml().read(configFile);
        } catch (Exception e) {
            toml = new Toml(); // Empty toml with defaults
        }
    }

    // Reload configuration
    public void reload(DreamDisplaysPlugin plugin) {
        load();
        this.language = new LanguageSection(toml);
        this.settings = new SettingsSection(toml);
        this.storage = new StorageSection(toml);
        this.permissions = new PermissionsSection(toml);

        // Cache values
        settings.cache();
        storage.cache();
        permissions.cache();
        extractLangFiles(plugin, false); // Don't overwrite user changes
        setMessages(language.getMessageLanguage());
    }

    // Extract language files
    private void extractLangFiles(DreamDisplaysPlugin plugin, boolean overwrite) {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            if (!langFolder.mkdirs()) {
                plugin.getLogger().warning("Could not create lang folder");
                return;
            }
        }

        List<String> langFiles = List.of("en.json", "pl.json", "ru.json", "uk.json");

        for (String fileName : langFiles) {
            String resourcePath = "assets/dreamdisplays/lang/" + fileName;
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    File targetFile = new File(langFolder, fileName);
                    if (overwrite || !targetFile.exists()) {
                        Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not extract language file " + fileName + ": " + e.getMessage());
            }
        }
    }

    // Load messages from language file
    private void setMessages(String lang) {
        File langFile = new File(configFile.getParentFile(), "lang/" + lang + ".json");
        if (!langFile.exists()) {
            me.inotsleep.utils.logging.LoggingManager.warn("Language file not found: " + langFile.getPath() + ", falling back to en.json");
            langFile = new File(configFile.getParentFile(), "lang/en.json");
        }

        if (langFile.exists()) {
            try (InputStream is = new FileInputStream(langFile)) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> msgs = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                if (msgs != null && !msgs.isEmpty()) {
                    messages.clear();
                    messages.putAll(msgs);
                } else {
                    me.inotsleep.utils.logging.LoggingManager.warn("No messages found in language file: " + lang);
                }
            } catch (IOException e) {
                me.inotsleep.utils.logging.LoggingManager.error("Error loading language file: " + lang, e);
            }
        } else {
            me.inotsleep.utils.logging.LoggingManager.error("Could not load any language file for: " + lang);
        }
    }

    // Language section
    public static class LanguageSection {
        private final Toml toml;
        public LanguageSection(Toml toml) { this.toml = toml; }
        public String getMessageLanguage() {
            String lang = toml.getString("language.message_language");
            return lang != null ? lang : "en";
        }
    }

    // Settings section
    public static class SettingsSection {
        private final Toml toml;
        public SettingsSection(Toml toml) { this.toml = toml; }

        public String getWebhookUrl() {
            String url = toml.getString("reports.webhook_url");
            return url != null ? url : "";
        }
        public int getReportCooldown() {
            Long cooldown = toml.getLong("reports.cooldown");
            return cooldown != null ? Math.toIntExact(cooldown) * 1000 : 15000;
        }
        public String getRepoName() {
            return "com/dreamdisplays";
        }
        public String getRepoOwner() {
            return "arsmotorin";
        }
        public boolean isUpdatesEnabled() {
            Boolean enabled = toml.getBoolean("updates");
            return enabled != null ? enabled : true;
        }

        public Material getSelectionMaterial() {
            String mat = toml.getString("display.selection_material");
            if (mat == null) return Material.DIAMOND_AXE;
            Material m = Material.matchMaterial(mat);
            return m != null ? m : Material.DIAMOND_AXE;
        }

        public Material getBaseMaterial() {
            String mat = toml.getString("display.base_material");
            if (mat == null) return Material.BLACK_CONCRETE;
            Material m = Material.matchMaterial(mat);
            return m != null ? m : Material.BLACK_CONCRETE;
        }

        public boolean isParticlesEnabled() {
            Boolean enabled = toml.getBoolean("particles");
            return enabled != null ? enabled : true;
        }
        public int getCUIParticleRenderDelay() {
            return 2;
        }
        public int getCUIParticlesPerBlock() {
            return 3;
        }
        public int getCUIParticlesColor() {
            String hex = toml.getString("particles_color");
            if (hex != null && hex.startsWith("#")) {
                try {
                    return Integer.parseInt(hex.substring(1), 16);
                } catch (NumberFormatException e) {
                    return 0x00FF00;
                }
            }
            return 0x00FF00;
        }
        public int getMinWidth() {
            Long width = toml.getLong("display.min_width");
            return width != null ? Math.toIntExact(width) : 1;
        }
        public int getMinHeight() {
            Long height = toml.getLong("display.min_height");
            return height != null ? Math.toIntExact(height) : 1;
        }
        public int getMaxWidth() {
            Long width = toml.getLong("display.max_width");
            return width != null ? Math.toIntExact(width) : 32;
        }
        public int getMaxHeight() {
            Long height = toml.getLong("display.max_height");
            return height != null ? Math.toIntExact(height) : 24;
        }
        public double getMaxRenderDistance() {
            try {
                Double distance = toml.getDouble("display.max_render_distance");
                if (distance != null) {
                    return distance;
                }
            } catch (ClassCastException e) {
                Long distanceLong = toml.getLong("display.max_render_distance");
                if (distanceLong != null) {
                    return distanceLong.doubleValue();
                }
            }
            return 96.0;
        }

        // Cached properties
        public String webhookUrl;
        public int reportCooldown;
        public String repoName;
        public String repoOwner;
        public boolean updatesEnabled;
        public Material selectionMaterial;
        public Material baseMaterial;
        public boolean particlesEnabled;
        public int particleRenderDelay;
        public int particlesPerBlock;
        public int particlesColor;
        public int minWidth;
        public int minHeight;
        public int maxWidth;
        public int maxHeight;
        public double maxRenderDistance;

        private void cache() {
            webhookUrl = getWebhookUrl();
            reportCooldown = getReportCooldown();
            repoName = getRepoName();
            repoOwner = getRepoOwner();
            updatesEnabled = isUpdatesEnabled();
            selectionMaterial = getSelectionMaterial();
            baseMaterial = getBaseMaterial();
            particlesEnabled = isParticlesEnabled();
            particleRenderDelay = getCUIParticleRenderDelay();
            particlesPerBlock = getCUIParticlesPerBlock();
            particlesColor = getCUIParticlesColor();
            minWidth = getMinWidth();
            minHeight = getMinHeight();
            maxWidth = getMaxWidth();
            maxHeight = getMaxHeight();
            maxRenderDistance = getMaxRenderDistance();
        }
    }

    // Storage section
    public static class StorageSection extends me.inotsleep.utils.storage.StorageSettings {
        private final Toml toml;

        // Public fields that shadow parent's private fields
        public String storageType;
        public String sqliteFile;
        public String host;
        public String port;
        public String database;
        public String password;
        public String username;
        public String options;
        public String tablePrefix;

        public StorageSection(Toml toml) {
            this.toml = toml;
        }

        private void cache() {
            String type = toml.getString("storage.type");
            this.storageType = type != null ? type : "SQLITE";

            this.sqliteFile = "database.db";

            String hostVal = toml.getString("storage.host");
            this.host = hostVal != null ? hostVal : "localhost";

            String portVal = toml.getString("storage.port");
            this.port = portVal != null ? portVal : "3306";

            String dbVal = toml.getString("storage.database");
            this.database = dbVal != null ? dbVal : "my_database";

            String passVal = toml.getString("storage.password");
            this.password = passVal != null ? passVal : "veryStrongPassword";

            String userVal = toml.getString("storage.username");
            this.username = userVal != null ? userVal : "username";

            this.options = "autoReconnect=true&useSSL=false;";

            String prefixVal = toml.getString("storage.table_prefix");
            this.tablePrefix = prefixVal != null ? prefixVal : "";
        }
    }

    // Permissions section
    public static class PermissionsSection {
        private final Toml toml;
        public PermissionsSection(Toml toml) { this.toml = toml; }

        // Cached properties
        public String premium;
        public String delete;
        public String list;
        public String reload;
        public String updates;

        private void cache() {
            String prem = toml.getString("permissions.premium");
            premium = prem != null ? prem : "group.premium";

            String del = toml.getString("permissions.delete");
            delete = del != null ? del : "dreamdisplays.delete";

            String listVal = toml.getString("permissions.list");
            list = listVal != null ? listVal : "dreamdisplays.list";

            String reloadVal = toml.getString("permissions.reload");
            reload = reloadVal != null ? reloadVal : "dreamdisplays.reload";

            String updatesVal = toml.getString("permissions.updates");
            updates = updatesVal != null ? updatesVal : "dreamdisplays.updates";
        }
    }
}
