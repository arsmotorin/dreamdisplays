package com.dreamdisplays

import com.dreamdisplays.managers.StorageManager
import com.dreamdisplays.registrar.ChannelRegistrar.registerChannels
import com.dreamdisplays.registrar.CommandRegistrar.registerCommands
import com.dreamdisplays.registrar.ListenerRegistrar.registerListeners
import com.dreamdisplays.registrar.SchedulerRegistrar.runRepeatingTasks
import com.github.zafarkhaja.semver.Version
import me.inotsleep.utils.AbstractPlugin
import me.inotsleep.utils.logging.LoggingManager.log
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bstats.bukkit.Metrics
import org.jspecify.annotations.NullMarked

@NullMarked
class Main : AbstractPlugin<Main>() {
    lateinit var storage: StorageManager
    var audiences: BukkitAudiences? = null

    override fun doEnable() {
        log("Enabling DreamDisplays v${description.version}...")

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

        // Registrars
        registerCommands(this)
        registerListeners(this)
        registerChannels(this)
        runRepeatingTasks(this)

        // bStats
        Metrics(this, 26488)
    }

    override fun doDisable() {
        audiences?.close()
        storage.onDisable()
    }

    companion object {
        lateinit var config: Config

        var modVersion: Version? = null
        var pluginLatestVersion: String? = null

        fun getInstance(): Main =
            getInstanceByClazz(Main::class.java)


        fun disablePlugin() {
            log("Disabling DreamDisplays plugin...")
            getInstance().server.pluginManager.disablePlugin(getInstance())
        }
    }
}
