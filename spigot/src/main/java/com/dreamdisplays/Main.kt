package com.dreamdisplays

import com.dreamdisplays.commands.Command
import com.dreamdisplays.listeners.Player
import com.dreamdisplays.listeners.Selection
import com.dreamdisplays.managers.Display
import com.dreamdisplays.scheduler.Provider
import com.dreamdisplays.storage.Storage
import com.dreamdisplays.utils.Updater
import com.github.zafarkhaja.semver.Version
import me.inotsleep.utils.AbstractPlugin
import org.bukkit.Bukkit
import org.jspecify.annotations.NullMarked

@NullMarked
class Main : AbstractPlugin<Main>() {

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

        Bukkit.getPluginManager().registerEvents(Selection(this), this)
        Bukkit.getPluginManager().registerEvents(Player(), this)

        // Updating displays
        Provider.adapter.runRepeatingAsync(
            this, 50L, 1000L
        ) { Display.updateAllDisplays() }

        // GitHub update checks
        if (Companion.config.settings.updatesEnabled) {
            Provider.adapter.runRepeatingAsync(
                this, 20L, 20L * 3600L
            ) { Updater.checkForUpdates() }
        }
    }

    override fun doDisable() {
        storage.onDisable()
    }

    fun registerCommands() = Command()

    fun registerChannels() {
        val messenger = server.messenger
        val receiver = com.dreamdisplays.utils.net.Receiver(this)

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

        fun getInstance(): Main = getInstanceByClazz(Main::class.java)

        fun getIsFolia(): Boolean = runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess

        fun disablePlugin() {
            getInstance().server.pluginManager.disablePlugin(getInstance())
        }
    }
}
