package com.dreamdisplays.utils.net

import com.dreamdisplays.Main
import com.dreamdisplays.datatypes.Sync
import com.dreamdisplays.managers.Display.delete
import com.dreamdisplays.managers.Display.report
import com.dreamdisplays.managers.Player.hasBeenNotifiedAboutModUpdate
import com.dreamdisplays.managers.Player.hasBeenNotifiedAboutPluginUpdate
import com.dreamdisplays.managers.Player.setModUpdateNotified
import com.dreamdisplays.managers.Player.setPluginUpdateNotified
import com.dreamdisplays.managers.Player.setVersion
import com.dreamdisplays.managers.State.processSyncPacket
import com.dreamdisplays.managers.State.sendSyncPacket
import com.dreamdisplays.utils.Utils
import com.github.zafarkhaja.semver.Version
import me.inotsleep.utils.logging.LoggingManager
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.jspecify.annotations.NullMarked
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.*
import com.dreamdisplays.utils.net.Utils as Net

@NullMarked
class Receiver(var plugin: Main?) : PluginMessageListener {
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        when (channel) {
            "dreamdisplays:sync" -> {
                processSyncPacket(player, message)
            }

            "dreamdisplays:req_sync", "dreamdisplays:delete", "dreamdisplays:report" -> {
                val id = processUUIDPacketWithException(message) ?: return

                when (channel.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]) {
                    "req_sync" -> {
                        sendSyncPacket(id, player)
                    }

                    "delete" -> {
                        run {
                            delete(id, player)
                        }
                        run {
                            report(id, player)
                        }
                    }

                    "report" -> {
                        report(id, player)
                    }
                }
            }

            "dreamdisplays:version" -> {
                processVersionPacket(player, message)
            }
        }
    }

    private fun processVersionPacket(player: Player, message: ByteArray) {
        if (Main.modVersion == null) return
        try {
            val `in` = DataInputStream(ByteArrayInputStream(message))
            val len = Net.readVarInt(`in`)

            val data = ByteArray(len)

            `in`.read(data, 0, len)

            Net.sendPremiumPacket(
                player,
                player.hasPermission(Main.config.permissions.premium)
            )

            val version = Utils.sanitize(String(data, 0, len))

            LoggingManager.log(
                player.name + " has Dream Displays with version: " + version + ". Premium: " + player.hasPermission(
                    Main.config.permissions.premium
                )
            )

            val userVersion = Version.parse(version)

            setVersion(player, userVersion)

            // Check for mod updates and notify all users with the mod
            val result = userVersion.compareTo(Main.modVersion)
            if (result < 0 && !hasBeenNotifiedAboutModUpdate(player)) {
                val message = Main.config.messages["newVersion"] as? String
                    ?: "&7D |&f New version of Dream Displays (%s) is available! Please update your mod!"
                player.sendMessage(
                    ChatColor.translateAlternateColorCodes(
                        '&', String.format(message, Main.modVersion.toString())
                    )
                )
                setModUpdateNotified(player, true)
            }

            // Check for plugin updates and notify admins only
            if (Main.config.settings.updatesEnabled &&
                player.hasPermission(Main.config.permissions.updates) && !hasBeenNotifiedAboutPluginUpdate(
                    player
                )
            ) {
                val pluginVersion: String = Main.getInstance().description.version
                if (Main.pluginLatestVersion != null) {
                    val currentPluginVersion = Version.parse(pluginVersion)
                    val latestPluginVersion = Version.parse(Main.pluginLatestVersion)

                    if (currentPluginVersion < latestPluginVersion) {
                        val message = Main.config.messages["newPluginVersion"] as? String
                            ?: "&7D |&f New version of Dream Displays plugin (%s) is available! Please update the server plugin!"
                        player.sendMessage(
                            ChatColor.translateAlternateColorCodes(
                                '&', String.format(message, Main.pluginLatestVersion)
                            )
                        )
                        setPluginUpdateNotified(player, true)
                    }
                }
            }
        } catch (e: IOException) {
            LoggingManager.warn("Unable to decode VersionPacket", e)
        }
    }

    private fun processSyncPacket(player: Player, message: ByteArray) {
        try {
            val `in` = DataInputStream(ByteArrayInputStream(message))
            val id = Net.readUUID(`in`)

            val isSync = `in`.readBoolean()
            val currentState = `in`.readBoolean()

            val currentTime = Net.readVarLong(`in`)
            val limitTime = Net.readVarLong(`in`)

            val packet = Sync(id, isSync, currentState, currentTime, limitTime)
            processSyncPacket(packet, player)
        } catch (e: IOException) {
            LoggingManager.warn("Unable to decode SyncPacket", e)
        }
    }

    private fun processUUIDPacketWithException(message: ByteArray): UUID? {
        try {
            val `in` = DataInputStream(ByteArrayInputStream(message))
            return Net.readUUID(`in`)
        } catch (e: IOException) {
            LoggingManager.error("Unable to decode RequestSyncPacket", e)
        }
        return null
    }
}
