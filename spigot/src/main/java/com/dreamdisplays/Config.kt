package com.dreamdisplays

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.moandjiezana.toml.Toml
import me.inotsleep.utils.logging.LoggingManager
import me.inotsleep.utils.storage.StorageSettings
import org.bukkit.Material
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class Config(private val plugin: Main) {
    private val configFile: File = File(plugin.dataFolder, "config.toml")
    private var toml: Toml = Toml()

    lateinit var language: LanguageSection
        private set
    lateinit var settings: SettingsSection
        private set
    lateinit var storage: StorageSection
        private set
    lateinit var permissions: PermissionsSection
        private set
    val messages: MutableMap<String, Any> = mutableMapOf()

    init {
        if (!configFile.exists()) {
            plugin.dataFolder.mkdirs()
            plugin.getResource("config.toml")?.use { input ->
                Files.copy(input, configFile.toPath())
            } ?: plugin.logger.severe("Could not create default config.toml")
        }

        extractLangFiles(true)
        load()
        initializeSections()

        LoggingManager.log("Loading messages for language: ${language.messageLanguage}")
        setMessages(language.messageLanguage)
    }

    private fun initializeSections() {
        language = LanguageSection(toml)
        settings = SettingsSection(toml).apply { cache() }
        storage = StorageSection(toml).apply { cache() }
        permissions = PermissionsSection(toml).apply { cache() }
    }

    // Load configuration
    private fun load() {
        try {
            toml = Toml().read(configFile)
        } catch (_: Exception) {
            toml = Toml() // Empty TOML with defaults
        }
    }

    fun reload() {
        load()
        initializeSections()
        extractLangFiles(false)
        setMessages(language.messageLanguage)
    }

    private fun extractLangFiles(overwrite: Boolean) {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.logger.warning("Could not create lang folder")
            return
        }

        listOf("en.json", "pl.json", "ru.json", "uk.json").forEach { fileName ->
            try {
                plugin.getResource("assets/dreamdisplays/lang/$fileName")?.use { input ->
                    val targetFile = File(langFolder, fileName)
                    if (overwrite || !targetFile.exists()) {
                        Files.copy(input, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not extract language file $fileName: ${e.message}")
            }
        }
    }

    private fun setMessages(lang: String) {
        val langFile = File(configFile.parentFile, "lang/$lang.json").let {
            if (it.exists()) it else {
                LoggingManager.warn("Language file not found: ${it.path}, falling back to en.json")
                File(configFile.parentFile, "lang/en.json")
            }
        }

        if (!langFile.exists()) {
            LoggingManager.error("Could not load any language file for: $lang")
            return
        }

        try {
            val json = langFile.readText(StandardCharsets.UTF_8)
            val msgs = gson.fromJson<Map<String, Any>>(
                json,
                object : TypeToken<Map<String, Any>>() {}.type
            )

            if (msgs.isNotEmpty()) {
                messages.clear()
                messages.putAll(msgs)
            } else {
                LoggingManager.warn("No messages found in language file: $lang")
            }
        } catch (e: Exception) {
            LoggingManager.error("Error loading language file: $lang", e)
        }
    }

    class LanguageSection(private val toml: Toml) {
        val messageLanguage: String
            get() = toml.getString("language.message_language") ?: "en"
    }

    // Settings section
    class SettingsSection(private val toml: Toml) {
        private fun readWebhookUrl(): String {
            val url = toml.getString("reports.webhook_url")
            return url ?: ""
        }

        private fun readReportCooldown(): Int {
            val cooldown = toml.getLong("reports.cooldown")
            return if (cooldown != null) Math.toIntExact(cooldown) * 1000 else 15000
        }

        private fun readRepoName(): String = toml.getString("updates.repo_name") ?: "dreamdisplays"

        private fun readRepoOwner(): String = toml.getString("updates.repo_owner") ?: "arsmotorin"

        private fun readUpdatesEnabled(): Boolean {
            val enabled = toml.getBoolean("updates")
            return enabled ?: true
        }

        private fun readSelectionMaterial(): Material {
            val mat = toml.getString("display.selection_material") ?: return Material.DIAMOND_AXE
            val m = Material.matchMaterial(mat)
            return m ?: Material.DIAMOND_AXE
        }

        private fun readBaseMaterial(): Material {
            val mat = toml.getString("display.base_material") ?: return Material.BLACK_CONCRETE
            val m = Material.matchMaterial(mat)
            return m ?: Material.BLACK_CONCRETE
        }

        private fun readParticlesEnabled(): Boolean {
            val enabled = toml.getBoolean("particles")
            return enabled ?: true
        }

        val cUIParticleRenderDelay: Int
            get() = 2
        val cUIParticlesPerBlock: Int
            get() = 3
        val cUIParticlesColor: Int
            get() {
                val hex = toml.getString("particles_color")
                if (hex != null && hex.startsWith("#")) {
                    return try {
                        hex.substring(1).toInt(16)
                    } catch (_: NumberFormatException) {
                        0x00FFFF
                    }
                }
                return 0x00FFFF
            }

        private fun readMinWidth(): Int {
            val width = toml.getLong("display.min_width")
            return if (width != null) Math.toIntExact(width) else 1
        }

        private fun readMinHeight(): Int {
            val height = toml.getLong("display.min_height")
            return if (height != null) Math.toIntExact(height) else 1
        }

        private fun readMaxWidth(): Int {
            val width = toml.getLong("display.max_width")
            return if (width != null) Math.toIntExact(width) else 32
        }

        private fun readMaxHeight(): Int {
            val height = toml.getLong("display.max_height")
            return if (height != null) Math.toIntExact(height) else 24
        }

        private fun readMaxRenderDistance(): Double {
            try {
                val distance = toml.getDouble("display.max_render_distance")
                if (distance != null) {
                    return distance
                }
            } catch (_: ClassCastException) {
                val distanceLong = toml.getLong("display.max_render_distance")
                if (distanceLong != null) {
                    return distanceLong.toDouble()
                }
            }
            return 96.0
        }

        // Cached properties
        lateinit var webhookUrl: String
        var reportCooldown: Int = 0
        lateinit var repoName: String
        lateinit var repoOwner: String
        var updatesEnabled: Boolean = false
        lateinit var selectionMaterial: Material
        lateinit var baseMaterial: Material
        var particlesEnabled: Boolean = false
        var particleRenderDelay: Int = 0
        var particlesPerBlock: Int = 0
        var particlesColor: Int = 0
        var minWidth: Int = 0
        var minHeight: Int = 0
        var maxWidth: Int = 0
        var maxHeight: Int = 0
        var maxRenderDistance: Double = 0.0

        internal fun cache() {
            webhookUrl = readWebhookUrl()
            reportCooldown = readReportCooldown()
            repoName = readRepoName()
            repoOwner = readRepoOwner()
            updatesEnabled = readUpdatesEnabled()
            selectionMaterial = readSelectionMaterial()
            baseMaterial = readBaseMaterial()
            particlesEnabled = readParticlesEnabled()
            particleRenderDelay = cUIParticleRenderDelay
            particlesPerBlock = cUIParticlesPerBlock
            particlesColor = cUIParticlesColor
            minWidth = readMinWidth()
            minHeight = readMinHeight()
            maxWidth = readMaxWidth()
            maxHeight = readMaxHeight()
            maxRenderDistance = readMaxRenderDistance()
        }
    }

    class StorageSection(private val toml: Toml) : StorageSettings() {
        private lateinit var _storageType: String
        private lateinit var _sqliteFile: String

        internal fun cache() {
            _storageType = toml.getString("storage.type") ?: "SQLITE"
            _sqliteFile = "database.db"
            host = toml.getString("storage.host") ?: "localhost"
            port = toml.getString("storage.port") ?: "3306"
            database = toml.getString("storage.database") ?: "my_database"
            password = toml.getString("storage.password") ?: "veryStrongPassword"
            username = toml.getString("storage.username") ?: "username"
            options = "autoReconnect=true&useSSL=false;"
            tablePrefix = toml.getString("storage.table_prefix") ?: ""
        }
    }

    class PermissionsSection(private val toml: Toml) {
        lateinit var premium: String
        lateinit var delete: String
        lateinit var list: String
        lateinit var reload: String
        lateinit var updates: String

        internal fun cache() {
            premium = toml.getString("permissions.premium") ?: "group.premium"
            delete = toml.getString("permissions.delete") ?: "dreamdisplays.delete"
            list = toml.getString("permissions.list") ?: "dreamdisplays.list"
            reload = toml.getString("permissions.reload") ?: "dreamdisplays.reload"
            updates = toml.getString("permissions.updates") ?: "dreamdisplays.updates"
        }
    }

    companion object {
        private val gson = Gson()
    }
}
