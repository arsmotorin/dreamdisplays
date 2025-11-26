package com.dreamdisplays

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.moandjiezana.toml.Toml
import me.inotsleep.utils.logging.LoggingManager
import me.inotsleep.utils.storage.StorageSettings
import org.bukkit.Material
import org.jspecify.annotations.NullMarked
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Configuration manager for the Dream Displays plugin.
 */
@NullMarked
class Config(private val plugin: Main) {

    private val folder = plugin.dataFolder
    private val configFile = File(folder, "config.toml")
    private var toml = Toml()

    lateinit var language: LanguageSection; private set
    lateinit var settings: SettingsSection; private set
    lateinit var storage: StorageSection; private set
    lateinit var permissions: PermissionsSection; private set

    val messages = mutableMapOf<String, Any>()

    init {
        createDefaultConfig()
        extractLangFiles(overwrite = true)
        reload()
    }

    fun reload() {
        loadToml()
        parseSections()
        extractLangFiles(overwrite = false)
        loadMessages()
    }

    private fun createDefaultConfig() {
        if (!configFile.exists()) {
            folder.mkdirs()
            plugin.getResource("config.toml")?.use {
                Files.copy(it, configFile.toPath())
            } ?: plugin.logger.severe("Could not create default config.toml")
        }
    }

    private fun loadToml() {
        toml = runCatching { Toml().read(configFile) }
            .getOrElse {
                LoggingManager.error("Failed to parse config.toml", it)
                Toml()
            }
    }

    private fun parseSections() {
        language = toml.to(LanguageSection::class.java) ?: LanguageSection()
        settings = toml.to(SettingsSection::class.java)?.apply { initMaterials() } ?: SettingsSection()
        storage = toml.to(StorageSection::class.java) ?: StorageSection()
        permissions = toml.to(PermissionsSection::class.java) ?: PermissionsSection()
    }

    private fun extractLangFiles(overwrite: Boolean) {
        val langFolder = File(folder, "lang")
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.logger.warning("Could not create lang folder")
            return
        }

        LANGUAGE_FILES.forEach { fileName ->
            runCatching {
                val input = plugin.getResource("assets/dreamdisplays/lang/$fileName") ?: return@runCatching
                val target = File(langFolder, fileName)
                if (overwrite || !target.exists()) {
                    Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }.onFailure {
                plugin.logger.warning("Could not extract $fileName: ${it.message}")
            }
        }
    }

    private fun loadMessages() {
        val lang = language.messageLanguage
        val langFile = File(folder, "lang/$lang.json")
            .takeIf { it.exists() }
            ?: File(folder, "lang/en.json").also {
                LoggingManager.warn("Language '$lang' not found, using 'en'")
            }

        runCatching {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map = GSON.fromJson<Map<String, Any>>(langFile.readText(), type)

            messages.clear()
            messages.putAll(map)

            LoggingManager.log("Loaded ${map.size} messages ($lang)")
        }.onFailure {
            LoggingManager.error("Error loading language file: $lang", it)
        }
    }

    data class LanguageSection(val message_language: String = "en") {
        val messageLanguage get() = message_language
    }

    data class SettingsSection(
        val reports: ReportsConfig = ReportsConfig(),
        val updates: UpdatesConfig = UpdatesConfig(),
        val display: DisplayConfig = DisplayConfig(),
        val particles: Boolean = true,
        val particles_color: String = "#00FFFF"
    ) {
        lateinit var selectionMaterial: Material
        lateinit var baseMaterial: Material

        internal fun initMaterials() {
            selectionMaterial = Material.matchMaterial(display.selection_material) ?: Material.DIAMOND_AXE
            baseMaterial = Material.matchMaterial(display.base_material) ?: Material.BLACK_CONCRETE
        }

        data class ReportsConfig(val webhook_url: String = "", val cooldown: Int = 15)
        data class UpdatesConfig(
            val enabled: Boolean = true,
            val repo_name: String = "dreamdisplays",
            val repo_owner: String = "arsmotorin"
        )

        data class DisplayConfig(
            val selection_material: String = "DIAMOND_AXE",
            val base_material: String = "BLACK_CONCRETE",
            val min_width: Int = 1,
            val min_height: Int = 1,
            val max_width: Int = 32,
            val max_height: Int = 24,
            val max_render_distance: Double = 96.0
        )
    }

    data class StorageSection(val storage: StorageConfig = StorageConfig()) : StorageSettings() {
        init {
            host = storage.host
            port = storage.port
            database = storage.database
            password = storage.password
            username = storage.username
            tablePrefix = storage.table_prefix
            options = "autoReconnect=true&useSSL=false;"
        }

        data class StorageConfig(
            val type: String = "SQLITE",
            val host: String = "localhost",
            val port: String = "3306",
            val database: String = "my_database",
            val password: String = "veryStrongPassword",
            val username: String = "username",
            val table_prefix: String = ""
        )
    }

    data class PermissionsSection(val permissions: PermissionsConfig = PermissionsConfig()) {
        val premium get() = permissions.premium
        val delete get() = permissions.delete
        val list get() = permissions.list
        val reload get() = permissions.reload
        val updates get() = permissions.updates

        data class PermissionsConfig(
            val premium: String = "group.premium",
            val delete: String = "dreamdisplays.delete",
            val list: String = "dreamdisplays.list",
            val reload: String = "dreamdisplays.reload",
            val updates: String = "dreamdisplays.updates"
        )
    }

    companion object {
        private val GSON = Gson()
        private val LANGUAGE_FILES = listOf("en.json", "pl.json", "ru.json", "uk.json")
    }
}
