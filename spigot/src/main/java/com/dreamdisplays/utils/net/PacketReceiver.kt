package com.dreamdisplays.utils.net

import com.dreamdisplays.DreamDisplaysPlugin
import com.dreamdisplays.datatypes.SyncPacket
import com.dreamdisplays.managers.DisplayManager.delete
import com.dreamdisplays.managers.DisplayManager.report
import com.dreamdisplays.managers.PlayStateManager.processSyncPacket
import com.dreamdisplays.managers.PlayStateManager.sendSyncPacket
import com.dreamdisplays.managers.PlayerManager.hasBeenNotifiedAboutModUpdate
import com.dreamdisplays.managers.PlayerManager.hasBeenNotifiedAboutPluginUpdate
import com.dreamdisplays.managers.PlayerManager.setModUpdateNotified
import com.dreamdisplays.managers.PlayerManager.setPluginUpdateNotified
import com.dreamdisplays.managers.PlayerManager.setVersion
import com.dreamdisplays.utils.Utils
import com.github.zafarkhaja.semver.Version
import me.inotsleep.utils.logging.LoggingManager
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.*

class PacketReceiver(var plugin: DreamDisplaysPlugin?) : PluginMessageListener {
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
        if (DreamDisplaysPlugin.modVersion == null) return
        try {
            val `in` = DataInputStream(ByteArrayInputStream(message))
            val len = PacketUtils.readVarInt(`in`)

            val data = ByteArray(len)

            `in`.read(data, 0, len)

            PacketUtils.sendPremiumPacket(
                player,
                player.hasPermission(DreamDisplaysPlugin.config.permissions.premium)
            )

            val version = Utils.sanitize(String(data, 0, len))

            LoggingManager.log(
                player.name + " has Dream Displays with version: " + version + ". Premium: " + player.hasPermission(
                    DreamDisplaysPlugin.config.permissions.premium
                )
            )

            val userVersion = Version.parse(version)

            setVersion(player, userVersion)

            // Check for mod updates and notify all users with the mod
            val result = userVersion.compareTo(DreamDisplaysPlugin.modVersion)
            if (result < 0 && !hasBeenNotifiedAboutModUpdate(player)) {
                player.sendMessage(
                    ChatColor.translateAlternateColorCodes(
                        '&', String.format(
                            (DreamDisplaysPlugin.config.messages["newVersion"] as String?)!!,
                            DreamDisplaysPlugin.modVersion.toString()
                        )
                    )
                )
                setModUpdateNotified(player, true)
            }

            // Check for plugin updates and notify admins only
            if (DreamDisplaysPlugin.config.settings.updatesEnabled &&
                player.hasPermission(DreamDisplaysPlugin.config.permissions.updates) && !hasBeenNotifiedAboutPluginUpdate(
                    player
                )
            ) {
                val pluginVersion: String = DreamDisplaysPlugin.getInstance().description.version
                if (DreamDisplaysPlugin.pluginLatestVersion != null) {
                    val currentPluginVersion = Version.parse(pluginVersion)
                    val latestPluginVersion = Version.parse(DreamDisplaysPlugin.pluginLatestVersion)

                    if (currentPluginVersion < latestPluginVersion) {
                        player.sendMessage(
                            ChatColor.translateAlternateColorCodes(
                                '&', String.format(
                                    (DreamDisplaysPlugin.config.messages["newPluginVersion"] as String?)!!,
                                    DreamDisplaysPlugin.pluginLatestVersion
                                )
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
            val id = PacketUtils.readUUID(`in`)

            val isSync = `in`.readBoolean()
            val currentState = `in`.readBoolean()

            val currentTime = PacketUtils.readVarLong(`in`)
            val limitTime = PacketUtils.readVarLong(`in`)

            val packet = SyncPacket(id, isSync, currentState, currentTime, limitTime)
            processSyncPacket(packet, player)
        } catch (e: IOException) {
            LoggingManager.warn("Unable to decode SyncPacket", e)
        }
    }

    private fun processUUIDPacketWithException(message: ByteArray): UUID? {
        try {
            val `in` = DataInputStream(ByteArrayInputStream(message))
            return PacketUtils.readUUID(`in`)
        } catch (e: IOException) {
            LoggingManager.error("Unable to decode RequestSyncPacket", e)
        }
        return null
    }
}
