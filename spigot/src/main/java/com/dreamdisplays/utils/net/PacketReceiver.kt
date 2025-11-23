package com.dreamdisplays.utils.net;

import com.github.zafarkhaja.semver.Version;
import com.dreamdisplays.DreamDisplaysPlugin;
import com.dreamdisplays.datatypes.SyncPacket;
import com.dreamdisplays.managers.DisplayManager;
import com.dreamdisplays.managers.PlayStateManager;
import com.dreamdisplays.managers.PlayerManager;
import com.dreamdisplays.utils.Utils;
import me.inotsleep.utils.logging.LoggingManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

public class PacketReceiver implements PluginMessageListener {
    DreamDisplaysPlugin plugin;

    public PacketReceiver(DreamDisplaysPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        switch (channel) {
            case "dreamdisplays:sync": {
                processSyncPacket(player, message);
                break;
            }
            case "dreamdisplays:req_sync":
            case "dreamdisplays:delete":
            case "dreamdisplays:report": {
                UUID id = processUUIDPacketWithException(message);

                if (id == null) return;

                switch (channel.split(":")[1]) {
                    case "req_sync": {
                        PlayStateManager.sendSyncPacket(id, player);
                        break;
                    }
                    case "delete": {
                        DisplayManager.delete(id, player);
                    }
                    case "report": {
                        DisplayManager.report(id, player);
                    }
                }
                break;
            }
            case "dreamdisplays:version": {
                processVersionPacket(player, message);
            }
        }
    }

    private void processVersionPacket(Player player, byte[] message) {
        if (DreamDisplaysPlugin.modVersion == null) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            int len = PacketUtils.readVarInt(in);

            byte[] data = new byte[len];

            in.read(data, 0, len);

            PacketUtils.sendPremiumPacket(player, player.hasPermission(DreamDisplaysPlugin.config.permissions.premium));

            String version = Utils.sanitize(new String(data, 0, len));

            LoggingManager.log(player.getName() + " has Dream Displays with version: " + version +". Premium: " + player.hasPermission(DreamDisplaysPlugin.config.permissions.premium));

            Version userVersion = Version.parse(version);

            PlayerManager.setVersion(player, userVersion);

            // Check for mod updates and notify all users with the mod
            int result = userVersion.compareTo(DreamDisplaysPlugin.modVersion);
            if (result < 0 && !PlayerManager.hasBeenNotifiedAboutModUpdate(player)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format(
                        (String) DreamDisplaysPlugin.config.messages.get("newVersion"),
                        DreamDisplaysPlugin.modVersion.toString()
                )));
                PlayerManager.setModUpdateNotified(player, true);
            }

            // Check for plugin updates and notify admins only
            if (DreamDisplaysPlugin.config.settings.updatesEnabled &&
                player.hasPermission(DreamDisplaysPlugin.config.permissions.updates) &&
                !PlayerManager.hasBeenNotifiedAboutPluginUpdate(player)) {

                String pluginVersion = DreamDisplaysPlugin.getInstance().getDescription().getVersion();
                if (DreamDisplaysPlugin.pluginLatestVersion != null) {
                    Version currentPluginVersion = Version.parse(pluginVersion);
                    Version latestPluginVersion = Version.parse(DreamDisplaysPlugin.pluginLatestVersion);

                    if (currentPluginVersion.compareTo(latestPluginVersion) < 0) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format(
                                (String) DreamDisplaysPlugin.config.messages.get("newPluginVersion"),
                                DreamDisplaysPlugin.pluginLatestVersion
                        )));
                        PlayerManager.setPluginUpdateNotified(player, true);
                    }
                }
            }

        } catch (IOException e) {
            LoggingManager.warn("Unable to decode VersionPacket", e);
        }
    }

    private void processSyncPacket(Player player, byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            UUID id = PacketUtils.readUUID(in);

            boolean isSync = in.readBoolean();
            boolean currentState = in.readBoolean();

            long currentTime = PacketUtils.readVarLong(in);
            long limitTime = PacketUtils.readVarLong(in);

            SyncPacket packet = new SyncPacket(id, isSync, currentState, currentTime, limitTime);
            PlayStateManager.processSyncPacket(packet, player);
        } catch (IOException e) {
            LoggingManager.warn("Unable to decode SyncPacket", e);
        }
    }

    private UUID processUUIDPacketWithException(byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            return PacketUtils.readUUID(in);
        } catch (IOException e) {
            LoggingManager.error("Unable to decode RequestSyncPacket", e);
        }
        return null;
    }
}
