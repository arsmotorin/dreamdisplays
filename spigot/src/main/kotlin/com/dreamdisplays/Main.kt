package com.dreamdisplays

import com.dreamdisplays.commands.DisplayCommand
import com.dreamdisplays.managers.StorageManager
import com.dreamdisplays.registrar.ChannelRegistrar.registerChannels
import com.dreamdisplays.registrar.ListenerRegistrar.registerListeners
import com.dreamdisplays.registrar.SchedulerRegistrar.runRepeatingTasks
import com.github.zafarkhaja.semver.Version
import me.inotsleep.utils.logging.LoggingManager.log
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.java.JavaPlugin
import org.jspecify.annotations.NullMarked

@NullMarked
class Main : JavaPlugin() {
    lateinit var storage: StorageManager
    var audiences: BukkitAudiences? = null

    override fun onEnable() {
        instance = this
        doEnable()
    }

    override fun onDisable() {
        doDisable()
    }

    fun doEnable() {
        log("Enabling DreamDisplays ${description.version}...")

        // Initialize Scheduler
        com.dreamdisplays.utils.Scheduler.init(this)

        // Adventure API
        audiences = runCatching { BukkitAudiences.create(this) }.getOrElse {
            logger.warning("Adventure API not supported on this server.")
            null
        }

        // Configuration
        Companion.config = Config(this)

        // Storage
        storage = StorageManager(this)

        // Register commands
        val displayCommand = DisplayCommand()
        getCommand("display")?.setExecutor(displayCommand)
        getCommand("display")?.tabCompleter = displayCommand

        // Registrars
        registerListeners(this)
        registerChannels(this)
        runRepeatingTasks(this)

        // bStats
        Metrics(this, 26488)
    }

    fun doDisable() {
        log("Disabling Dream Displays ${description.version}...")
        audiences?.close()
        storage.onDisable()
    }

    companion object {
        lateinit var config: Config

        var modVersion: Version? = null
        var pluginLatestVersion: String? = null

        fun getInstance(): Main =
            instance

        fun disablePlugin() {
            instance.server.pluginManager.disablePlugin(instance)
        }

        private lateinit var instance: Main
    }
}
