package com.dreamdisplays

import com.dreamdisplays.scheduler.Provider
import com.dreamdisplays.storage.Storage
import com.dreamdisplays.listeners.PlayerListener
import com.dreamdisplays.listeners.SelectionListener
import com.dreamdisplays.commands.DisplayCommand
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.utils.GitHubUpdater
import me.inotsleep.utils.AbstractPlugin
import org.bukkit.Bukkit
import com.github.zafarkhaja.semver.Version

class DreamDisplaysPlugin : AbstractPlugin<DreamDisplaysPlugin>() {

    lateinit var storage: Storage

    override fun onEnable() = try {
        super.onEnable()
    } catch (e: NoSuchMethodError) {
        if (!e.message.orEmpty().contains("getMinecraftVersion")) throw e
        doEnable()
    }

    override fun doEnable() {
        Companion.config = Config(this)
        storage = Storage(this)

        registerChannels()
        registerCommands()

        Bukkit.getPluginManager().registerEvents(SelectionListener(this), this)
        Bukkit.getPluginManager().registerEvents(PlayerListener(), this)

        // Updating displays
        Provider.adapter.runRepeatingAsync(
            this, 50L, 1000L
        ) { DisplayManager.updateAllDisplays() }

        // GitHub update checks
        if (Companion.config.settings.updatesEnabled) {
            Provider.adapter.runRepeatingAsync(
                this, 20L, 20L * 3600L
            ) { GitHubUpdater.checkForUpdates() }
        }
    }

    override fun doDisable() {
        storage.onDisable()
    }

    fun registerCommands() = DisplayCommand()

    fun registerChannels() {
        val messenger = server.messenger
        val receiver = com.dreamdisplays.utils.net.PacketReceiver(this)

        listOf(
            "dreamdisplays:display_info",
            "dreamdisplays:sync",
            "dreamdisplays:delete",
            "dreamdisplays:premium"
        ).forEach { messenger.registerOutgoingPluginChannel(this, it) }

        listOf(
            "dreamdisplays:sync",
            "dreamdisplays:req_sync",
            "dreamdisplays:delete",
            "dreamdisplays:report",
            "dreamdisplays:version"
        ).forEach { messenger.registerIncomingPluginChannel(this, it, receiver) }
    }

    companion object {
        lateinit var config: Config
        var modVersion: Version? = null
        var pluginLatestVersion: String? = null

        fun getInstance(): DreamDisplaysPlugin = getInstanceByClazz(DreamDisplaysPlugin::class.java)

        fun getIsFolia(): Boolean = runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess

        fun disablePlugin() {
            getInstance().server.pluginManager.disablePlugin(getInstance())
        }
    }
}
