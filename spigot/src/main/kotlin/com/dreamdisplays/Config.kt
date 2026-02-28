package com.dreamdisplays

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.moandjiezana.toml.Toml
import me.inotsleep.utils.logging.LoggingManager
import me.inotsleep.utils.storage.StorageSettings
import org.bukkit.Material
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages the configuration of the plugin.
 */
@NullMarked
class Config(private val plugin: Main) {
    private val configFile = File(plugin.dataFolder, "config.toml")
    private var toml = Toml()

    lateinit var language: LanguageSection
        private set
    lateinit var settings: SettingsSection
        private set
    lateinit var storage: StorageSection
        private set
    lateinit var permissions: PermissionsSection
        private set
    val messages = mutableMapOf<String, Any>()
    val languages = mutableMapOf<String, Map<String, Any>>()

    init {
        createDefaultConfig()
        extractLangFiles(true)
        load()
        loadMessages()
    }

    private fun createDefaultConfig() {
        if (!configFile.exists()) {
            plugin.dataFolder.mkdirs()
            plugin.getResource("config.toml")?.use {
                Files.copy(it, configFile.toPath())
            } ?: plugin.logger.severe("Could not create default config.toml")
        }
    }

    // Load configuration
    private fun load() {
        toml = try {
            Toml().read(configFile)
        } catch (e: Exception) {
            LoggingManager.error("Failed to parse config.toml", e)
            Toml()
        }

        language = toml.to(LanguageSection::class.java) ?: LanguageSection()
        settings = toml.to(SettingsSection::class.java)?.apply { initMaterials() } ?: SettingsSection()
        storage = toml.to(StorageSection::class.java) ?: StorageSection()
        permissions = toml.to(PermissionsSection::class.java) ?: PermissionsSection()
    }

    // Reload configuration
    fun reload() {
        load()
        extractLangFiles(false)
        loadMessages()
    }

    // Extract language files
    private fun extractLangFiles(overwrite: Boolean) {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.logger.warning("Could not create lang folder")
            return
        }

        LANGUAGE_FILES.forEach { fileName ->
            runCatching {
                plugin.getResource("assets/dreamdisplays/lang/$fileName")?.use { input ->
                    val target = File(langFolder, fileName)
                    if (overwrite || !target.exists()) {
                        Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }.onFailure {
                plugin.logger.warning("Could not extract $fileName: ${it.message}")
            }
        }
    }

    // Load messages from language file
    private fun loadMessages() {
        languages.clear()
        LANGUAGE_FILES.forEach { fileName ->
            val langCode = fileName.removeSuffix(".json")
            val langFile = File(plugin.dataFolder, "lang/$fileName")
            if (langFile.exists()) {
                runCatching {
                    val msgs = GSON.fromJson<Map<String, Any>>(
                        langFile.readText(),
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    languages[langCode] = msgs
                }.onFailure {
                    LoggingManager.error("Error loading language file: $fileName", it)
                }
            }
        }
        // Set default messages to en
        messages.clear()
        messages.putAll(languages["en"] ?: emptyMap())
    }

    // Configuration sections
    data class LanguageSection(
        val default_language: String = "en",
    )

    data class SettingsSection(
        val reports: ReportsConfig = ReportsConfig(),
        val updates: UpdatesConfig = UpdatesConfig(),
        val display: DisplayConfig = DisplayConfig(),
    ) {
        // Reports
        val webhookUrl get() = reports.webhook_url
        val reportCooldown get() = reports.cooldown * 1000

        // Updates
        val repoName get() = updates.repo_name
        val repoOwner get() = updates.repo_owner
        val updatesEnabled get() = display.updates && updates.enabled

        // Materials
        lateinit var selectionMaterial: Material
        lateinit var baseMaterial: Material

        // Particles
        val particlesEnabled get() = display.particles
        val particleRenderDelay = 2

        // Mod detection
        val modDetectionEnabled get() = display.mod_detection_enabled

        // Display
        val minWidth get() = display.min_width
        val minHeight get() = display.min_height
        val maxWidth get() = display.max_width
        val maxHeight get() = display.max_height
        val maxRenderDistance get() = display.max_render_distance

        internal fun initMaterials() {
            selectionMaterial = Material.matchMaterial(display.selection_material) ?: Material.DIAMOND_AXE
            baseMaterial = Material.matchMaterial(display.base_material) ?: Material.BLACK_CONCRETE
        }

        data class ReportsConfig(
            val webhook_url: String = "",
            val cooldown: Int = 15,
        )

        data class UpdatesConfig(
            val enabled: Boolean = true,
            val repo_name: String = "dreamdisplays",
            val repo_owner: String = "arsmotorin",
        )

        data class DisplayConfig(
            val selection_material: String = "DIAMOND_AXE",
            val base_material: String = "BLACK_CONCRETE",
            val updates: Boolean = true,
            val particles: Boolean = true,
            val particles_color: String = "#00FFFF",
            val mod_detection_enabled: Boolean = true,
            val min_width: Int = 1,
            val min_height: Int = 1,
            val max_width: Int = 32,
            val max_height: Int = 24,
            val max_render_distance: Double = 96.0,
        )
    }

    // Storage configuration
    data class StorageSection(
        val storage: StorageConfig = StorageConfig(),
    ) : StorageSettings() {
        init {
            this.host = storage.host
            this.port = storage.port
            this.database = storage.database
            this.password = storage.password
            this.username = storage.username
            this.options = "autoReconnect=true&useSSL=false;"
            this.tablePrefix = storage.table_prefix
        }

        data class StorageConfig(
            val type: String = "SQLITE",
            val host: String = "localhost",
            val port: String = "3306",
            val database: String = "my_database",
            val password: String = "veryStrongPassword",
            val username: String = "username",
            val table_prefix: String = "",
        )
    }

    // Permissions configuration
    data class PermissionsSection(
        val permissions: PermissionsConfig = PermissionsConfig(),
    ) {
        val create get() = permissions.create
        val video get() = permissions.video
        val info get() = permissions.info
        val premium get() = permissions.premium
        val delete get() = permissions.delete
        val list get() = permissions.list
        val reload get() = permissions.reload
        val updates get() = permissions.updates
        val help get() = permissions.help
        val stats get() = permissions.stats

        data class PermissionsConfig(
            val create: String = "dreamdisplays.create",
            val video: String = "dreamdisplays.video",
            val info: String = "dreamdisplays.info",
            val premium: String = "group.premium",
            val delete: String = "dreamdisplays.delete",
            val list: String = "dreamdisplays.list",
            val reload: String = "dreamdisplays.reload",
            val updates: String = "dreamdisplays.updates",
            val help: String = "dreamdisplays.help",
            val stats: String = "dreamdisplays.stats",
        )
    }

    fun getMessageForPlayer(player: Player?, key: String): Any? {
        val locale = player?.locale ?: "en_us"
        val langCode = mapLocaleToLang(locale)
        val defaultLangCode = mapLocaleToLang(language.default_language)
        return languages[langCode]?.get(key)
            ?: languages[defaultLangCode]?.get(key)
            ?: messages[key]
    }

    private fun mapLocaleToLang(locale: String): String {
        return when (val normalized = locale.lowercase()) {
            "ru_ru" -> "ru"
            "uk_ua" -> "uk"
            "pl_pl" -> "pl"
            "de_de" -> "de"
            "cs_cz" -> "cs"
            "be_by" -> "be"
            "he_il" -> "he"
            else -> normalized.substringBefore('_').substringBefore('-').ifEmpty { "en" }
        }
    }

    companion object {
        private val GSON = Gson()
        private val LANGUAGE_FILES =
            listOf("en.json", "pl.json", "ru.json", "uk.json", "de.json", "cs.json", "be.json", "he.json")
    }
}
