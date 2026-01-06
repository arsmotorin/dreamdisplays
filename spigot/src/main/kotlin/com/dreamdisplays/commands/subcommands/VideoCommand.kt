package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.Main
import com.dreamdisplays.managers.DisplayManager.getReceivers
import com.dreamdisplays.managers.DisplayManager.isContains
import com.dreamdisplays.managers.DisplayManager.sendUpdate
import com.dreamdisplays.utils.Message
import com.dreamdisplays.utils.Message.sendMessage
import com.dreamdisplays.utils.YouTubeUtils
import com.dreamdisplays.utils.YouTubeUtils.extractVideoIdFromUri
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class VideoCommand : SubCommand {

    override val name = "video"
    override val permission = Main.config.permissions.video

    override fun execute(sender: CommandSender, args: Array<String?>) {
        val player = (sender as? Player) ?: return
        if (args.size < 2) {
            sendMessage(player, "invalidURL")
            return
        }

        val block = player.getTargetBlock(null, 32)
        val data = isContains(block.location)

        if (
            block.type != Main.config.settings.baseMaterial ||
            data == null ||
            data.ownerId != player.uniqueId
        ) {
            sendMessage(player, "noDisplay")
            return
        }

        val code = extractVideoIdFromUri(args[1] ?: "")
            ?: return sendMessage(player, "invalidURL")

        data.apply {
            url = "https://youtube.com/watch?v=$code"
            lang = args.getOrNull(2).orEmpty()
            isSync = false
        }

        sendUpdate(data, getReceivers(data))

        sendMessage(player, "settedURL")
    }
}
