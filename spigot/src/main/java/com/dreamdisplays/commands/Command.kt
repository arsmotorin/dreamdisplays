package com.dreamdisplays.commands

import com.dreamdisplays.Main
import com.dreamdisplays.listeners.Selection
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.utils.Message
import com.dreamdisplays.utils.Utils
import me.inotsleep.utils.AbstractCommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked

/**
 * Main command class for handling display-related commands.
 */
@NullMarked
class Command : AbstractCommand(Main.getInstance().name, "display") {

    // Execute the command based on the provided arguments
    override fun toExecute(sender: CommandSender, s: String?, args: Array<String?>) {
        when (args.size) {
            0 -> sendHelp(sender)
            1 -> when (args[0]) {
                "create" -> handleCreate(sender)
                "delete" -> handleDelete(sender)
                "reload" -> handleReload(sender)
                "list" -> handleList(sender)
                else -> sendHelp(sender)
            }

            2, 3 -> if (args[0] == "video") handleVideo(sender, args) else sendHelp(sender)
        }
    }

    // Handle the /video command to set a YouTube video URL for a display
    private fun handleVideo(sender: CommandSender, args: Array<String?>) {
        val player = sender as? Player ?: return

        val block = player.getTargetBlock(null, 32)
        if (block.type != Main.config.settings.baseMaterial) {
            msg(player, "noDisplay")
            return
        }

        val data = DisplayManager.isContains(block.location)
        if (data == null || data.ownerId != player.uniqueId) {
            msg(player, "noDisplay")
            return
        }

        val code = Utils.extractVideo(args[1] ?: "")
        if (code == null) {
            msg(player, "invalidURL")
            return
        }

        data.url = "https://youtube.com/watch?v=$code"
        data.lang = args.getOrNull(2) ?: ""
        data.isSync = false
        data.sendUpdatePacket(data.receivers)

        msg(player, "settedURL")
    }

    // Handle the /create command to create a new display
    private fun handleCreate(sender: CommandSender) {
        val player = sender as? Player ?: return

        val sel = Selection.selectionPoints[player.uniqueId]
            ?: return msg(player, "noDisplayTerritories")

        val valid = Selection.isValidDisplay(sel)
        if (valid != 6) {
            Selection.sendErrorMessage(player, valid)
            return
        }

        if (DisplayManager.isOverlaps(sel)) {
            msg(player, "displayOverlap")
            return
        }

        val displayData = sel.generateDisplayData()
        Selection.selectionPoints.remove(player.uniqueId)

        DisplayManager.register(displayData)
        msg(player, "successfulCreation")
    }

    // Handle the /delete command to delete an existing display
    private fun handleDelete(sender: CommandSender) {
        val player = sender as? Player ?: return
        if (!player.hasPermission(Main.config.permissions.delete)) return

        val block = player.getTargetBlock(null, 32)
        if (block.type != Main.config.settings.baseMaterial) {
            msg(player, "noDisplay")
            return
        }

        val data = DisplayManager.isContains(block.location)
            ?: return msg(player, "noDisplay")

        DisplayManager.delete(data)
        msg(player, "displayDeleted")
    }

    // Handle the /reload command to reload the plugin configuration
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission(Main.config.permissions.reload)) {
            sendHelp(sender)
            return
        }

        Main.config.reload()
        msg(sender, "configReloaded")
    }

    // Handle the /list command to list all existing displays
    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission(Main.config.permissions.list)) {
            sendHelp(sender)
            return
        }

        val displays = DisplayManager.getDisplays()
        if (displays.isEmpty()) {
            msg(sender, "noDisplaysFound")
            return
        }

        msg(sender, "displayListHeader")

        val entry = Main.config.messages["displayListEntry"] as String?

        displays.forEach { d ->
            val owner = Bukkit.getOfflinePlayer(d.ownerId).name ?: "Unknown"
            val formatted = me.inotsleep.utils.MessageUtil.parsePlaceholders(
                entry,
                d.id.toString(),
                owner,
                d.pos1.blockX.toString(),
                d.pos1.blockY.toString(),
                d.pos1.blockZ.toString(),
                d.url
            )
            Message.sendColoredMessage(sender, formatted)
        }
    }

    // Send a message to the command sender
    private fun msg(sender: CommandSender?, key: String) {
        Message.sendMessage(sender, key)
    }

    // Send help messages to the command sender
    private fun sendHelp(sender: CommandSender?) {
        Message.sendColoredMessages(sender, Message.getMessages("displayCommandHelp"))
    }

    // Provide tab completion for the command
    override fun complete(sender: CommandSender, args: Array<String?>): MutableList<String?> {
        if (args.size != 1) return mutableListOf()

        val list = mutableListOf<String?>("create", "video")
        val perms = Main.config.permissions

        if (sender.hasPermission(perms.delete)) list += "delete"
        if (sender.hasPermission(perms.list)) list += "list"
        if (sender.hasPermission(perms.reload)) list += "reload"

        return list
    }
}
