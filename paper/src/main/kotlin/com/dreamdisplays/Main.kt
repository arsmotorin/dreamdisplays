package com.dreamdisplays

import com.dreamdisplays.managers.StorageManager
import com.dreamdisplays.registrar.ChannelRegistrar.registerChannels
import com.dreamdisplays.registrar.CommandRegistrar
import com.dreamdisplays.registrar.ListenerRegistrar.registerListeners
import com.dreamdisplays.registrar.SchedulerRegistrar.runRepeatingTasks
import com.github.zafarkhaja.semver.Version
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.inotsleep.utils.logging.LoggingManager.log
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.java.JavaPlugin
import org.jspecify.annotations.NullMarked

@NullMarked
@Suppress("UnstableApiUsage")
class Main : JavaPlugin() {
    lateinit var storage: StorageManager

    override fun onLoad() {
        instance = this
        Companion.config = Config(this)
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            CommandRegistrar.register(event.registrar())
        }
    }

    override fun onEnable() {
        doEnable()
    }

    override fun onDisable() {
        doDisable()
    }

    fun doEnable() {
        log("Enabling DreamDisplays ${description.version}...")

        com.dreamdisplays.utils.Scheduler.init(this)

        storage = StorageManager(this)

        registerListeners(this)
        registerChannels(this)
        runRepeatingTasks(this)

        Metrics(this, 26488)
    }

    fun doDisable() {
        log("Disabling Dream Displays ${description.version}...")
        storage.onDisable()
    }

    companion object {
        lateinit var config: Config

        var modVersion: Version? = null
        var pluginLatestVersion: String? = null

        fun getInstance(): Main = instance

        fun disablePlugin() {
            instance.server.pluginManager.disablePlugin(instance)
        }

        private lateinit var instance: Main
    }
}
