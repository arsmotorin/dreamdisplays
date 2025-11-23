package com.dreamdisplays.commands

import com.dreamdisplays.DreamDisplaysPlugin
import com.dreamdisplays.listeners.SelectionListener
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.utils.MessageUtil
import com.dreamdisplays.utils.Utils
import me.inotsleep.utils.AbstractCommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DisplayCommand : AbstractCommand(DreamDisplaysPlugin.getInstance().name, "display") {

    override fun toExecute(sender: CommandSender, s: String?, args: Array<String?>) {
        when (args.size) {
            0 -> sendHelp(sender)
            1 -> handleSingle(sender, args[0])
            2, 3 -> handleVideo(sender, args)
        }
    }

    private fun handleVideo(sender: CommandSender, args: Array<String?>) {
        val player = sender as? Player ?: return
        if (args[0] != "video") return

        val block = player.getTargetBlock(null, 32)
        if (block.type != DreamDisplaysPlugin.config.settings.baseMaterial) {
            msg(player, "noDisplay")
            return
        }

        val data = DisplayManager.isContains(block.location)
        if (data == null || data.ownerId != player.uniqueId) {
            msg(player, "noDisplay")
            return
        }

        val code = Utils.extractVideoId(args[1] ?: "")
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

    private fun handleSingle(sender: CommandSender, arg: String?) {
        when (arg) {
            "create" -> handleCreate(sender)
            "delete" -> handleDelete(sender)
            "reload" -> handleReload(sender)
            "list"   -> handleList(sender)
        }
    }

    private fun handleCreate(sender: CommandSender) {
        val player = sender as? Player ?: return

        val sel = SelectionListener.selectionPoints[player.uniqueId]
            ?: return msg(player, "noDisplayTerritories")

        val valid = SelectionListener.isValidDisplay(sel)
        if (valid != 6) {
            SelectionListener.sendErrorMessage(player, valid)
            return
        }

        if (DisplayManager.isOverlaps(sel)) {
            msg(player, "displayOverlap")
            return
        }

        val displayData = sel.generateDisplayData()
        SelectionListener.selectionPoints.remove(player.uniqueId)

        DisplayManager.register(displayData)
        msg(player, "successfulCreation")
    }

    private fun handleDelete(sender: CommandSender) {
        val player = sender as? Player ?: return
        if (!player.hasPermission(DreamDisplaysPlugin.config.permissions.delete)) return

        val block = player.getTargetBlock(null, 32)
        if (block.type != DreamDisplaysPlugin.config.settings.baseMaterial) {
            msg(player, "noDisplay")
            return
        }

        val data = DisplayManager.isContains(block.location)
            ?: return msg(player, "noDisplay")

        DisplayManager.delete(data)
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission(DreamDisplaysPlugin.config.permissions.reload)) {
            sendHelp(sender)
            return
        }

        DreamDisplaysPlugin.config.reload()
        msg(sender, "configReloaded")
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission(DreamDisplaysPlugin.config.permissions.list)) {
            sendHelp(sender)
            return
        }

        val displays = DisplayManager.getDisplays()
        if (displays.isEmpty()) {
            msg(sender, "noDisplaysFound")
            return
        }

        msg(sender, "displayListHeader")

        val entry = DreamDisplaysPlugin.config.messages["displayListEntry"] as String?

        displays.forEach { d ->
            val owner = Bukkit.getOfflinePlayer(d.ownerId).name ?: "Unknown"
            val formatted = me.inotsleep.utils.MessageUtil.parsePlaceholders(
                entry,
                d.id.toString(),
                owner,
                d.pos1.blockX.toString(),
                d.pos1.blockY.toString(),
                d.pos1.blockZ.toString(),
                d.url ?: "None"
            )
            MessageUtil.sendColoredMessage(sender, formatted)
        }
    }

    private fun msg(sender: CommandSender?, key: String) {
        MessageUtil.sendColoredMessage(sender, DreamDisplaysPlugin.config.messages[key] as String?)
    }

    private fun sendHelp(sender: CommandSender?) {
        @Suppress("UNCHECKED_CAST")
        MessageUtil.sendColoredMessages(
            sender,
            DreamDisplaysPlugin.config.messages["displayCommandHelp"] as MutableList<String?>?
        )
    }

    override fun complete(sender: CommandSender, args: Array<String?>): MutableList<String?> {
        if (args.size != 1) return mutableListOf()

        val list = mutableListOf<String?>("create", "video")
        val perms = DreamDisplaysPlugin.config.permissions

        if (sender.hasPermission(perms.delete)) list += "delete"
        if (sender.hasPermission(perms.list))   list += "list"
        if (sender.hasPermission(perms.reload)) list += "reload"

        return list
    }
}
