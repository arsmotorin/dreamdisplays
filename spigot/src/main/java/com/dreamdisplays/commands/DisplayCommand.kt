package com.dreamdisplays.commands;

import com.dreamdisplays.DreamDisplaysPlugin;
import com.dreamdisplays.datatypes.DisplayData;
import com.dreamdisplays.datatypes.SelectionData;
import com.dreamdisplays.listeners.SelectionListener;
import com.dreamdisplays.managers.DisplayManager;
import com.dreamdisplays.utils.MessageUtil;
import com.dreamdisplays.utils.Utils;
import me.inotsleep.utils.AbstractCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DisplayCommand extends AbstractCommand {
    public DisplayCommand() {
        super(DreamDisplaysPlugin.getInstance().getName(), "display");
    }

    // Executes the /display command with the given arguments
    @Override
    public void toExecute(CommandSender sender, String s, String[] args) {
        switch (args.length) {
            case 0 -> sendHelp(sender);
            case 1 -> handle1Args(sender, args);
            case 2, 3 -> handleVideoCommand(sender, args);
        }
    }

    // Handles the /display video command to set a video URL on a display
    private void handleVideoCommand(CommandSender sender, String[] args) {
        if (args[0].equals("video")) {
            if (!(sender instanceof Player player)) return;

            Block block = player.getTargetBlock(null, 32);

            if (block.getType() != DreamDisplaysPlugin.config.settings.baseMaterial) {
                MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("noDisplay"));
                return;
            }

            Location location = block.getLocation();

            DisplayData data = DisplayManager.isContains(location);
            if (data == null || !(data.getOwnerId() + "").equals(player.getUniqueId() + "")) {
                MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("noDisplay"));
                return;
            }

            String code = Utils.extractVideoId(args[1]);

            String lang = args.length == 3 ? args[2] : "";

            if (code == null) {
                MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("invalidURL"));
                return;
            }

            data.setUrl("https://youtube.com/watch?v=" + code);
            data.setLang(lang);
            data.setSync(false);
            data.sendUpdatePacket(data.getReceivers());

            MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("settedURL"));
        }
    }

    // Handles commands with a single argument
    private void handle1Args(CommandSender sender, String[] args) {
        switch (args[0]) {
            case "create" -> {
                if (!(sender instanceof Player player)) return;

                SelectionData data = SelectionListener.selectionPoints.get(player.getUniqueId());
                if (data == null) {
                    MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("noDisplayTerritories"));
                    return;
                }

                int validation = SelectionListener.isValidDisplay(data);

                if (validation != 6) {
                    SelectionListener.sendErrorMessage(player, validation);
                    return;
                }

                if (DisplayManager.isOverlaps(data)) {
                    MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("displayOverlap"));
                    return;
                }

                DisplayData displayData = data.generateDisplayData();
                SelectionListener.selectionPoints.remove(player.getUniqueId());

                DisplayManager.register(displayData);

                MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("successfulCreation"));
            }
            case "delete" -> {
                if (!(sender instanceof Player player)) return;
                if (!player.hasPermission(DreamDisplaysPlugin.config.permissions.delete)) {
                    return;
                }

                Block block = player.getTargetBlock(null, 32);
                if (block.getType() != DreamDisplaysPlugin.config.settings.baseMaterial) {
                    MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("noDisplay"));
                    return;
                }

                Location location = block.getLocation();

                DisplayData data = DisplayManager.isContains(location);
                if (data == null) {
                    MessageUtil.sendColoredMessage(player, (String) DreamDisplaysPlugin.config.messages.get("noDisplay"));
                    return;
                }

                DisplayManager.delete(data);
            }
            case "reload" -> {
                if (!sender.hasPermission(DreamDisplaysPlugin.config.permissions.reload)) {
                    sendHelp(sender);
                    return;
                }

                DreamDisplaysPlugin.config.reload(DreamDisplaysPlugin.getInstance());
                MessageUtil.sendColoredMessage(sender, (String) DreamDisplaysPlugin.config.messages.get("configReloaded"));
            }
            case "list" -> {
                if (!sender.hasPermission(DreamDisplaysPlugin.config.permissions.list)) {
                    sendHelp(sender);
                    return;
                }

                List<DisplayData> displays = DisplayManager.getDisplays();
                if (displays.isEmpty()) {
                    MessageUtil.sendColoredMessage(sender, (String) DreamDisplaysPlugin.config.messages.get("noDisplaysFound"));
                    return;
                }

                MessageUtil.sendColoredMessage(sender, (String) DreamDisplaysPlugin.config.messages.get("displayListHeader"));
                for (DisplayData data : displays) {
                    String ownerName = Bukkit.getOfflinePlayer(data.getOwnerId()).getName();
                    if (ownerName == null) ownerName = "Unknown";
                    MessageUtil.sendColoredMessage(sender, me.inotsleep.utils.MessageUtil.parsePlaceholders(
                            (String) DreamDisplaysPlugin.config.messages.get("displayListEntry"),
                            data.getId().toString(),
                            ownerName,
                            String.valueOf(data.getPos1().getBlockX()),
                            String.valueOf(data.getPos1().getBlockY()),
                            String.valueOf(data.getPos1().getBlockZ()),
                            data.getUrl() != null ? data.getUrl() : "None"
                    ));
                }
            }
        }
    }

    // Sends help messages to the command sender
    private void sendHelp(CommandSender sender) {
        MessageUtil.sendColoredMessages(sender, (List<String>) DreamDisplaysPlugin.config.messages.get("displayCommandHelp"));
    }

    // Provides tab completion for the /display command
    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(List.of("create", "video"));
            if (sender.hasPermission(DreamDisplaysPlugin.config.permissions.delete)) completions.add("delete");
            if (sender.hasPermission(DreamDisplaysPlugin.config.permissions.list)) completions.add("list");
            if (sender.hasPermission(DreamDisplaysPlugin.config.permissions.reload)) completions.add("reload");
            return completions;
        }
        return List.of();
    }
}
