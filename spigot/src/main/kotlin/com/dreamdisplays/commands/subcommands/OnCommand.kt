package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.managers.PlayerManager.isDisplaysEnabled
import com.dreamdisplays.managers.PlayerManager.setDisplaysEnabled
import com.dreamdisplays.utils.Message.sendMessage
import com.dreamdisplays.utils.net.Utils.sendDisplayEnabled
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class OnCommand : SubCommand {

    override val name = "on"
    override val permission: String? = null

    override fun execute(sender: CommandSender, args: Array<String?>) {
        val player = (sender as? Player) ?: return

        if (isDisplaysEnabled(player)) {
            sendMessage(player, "display.already-enabled")
            return
        }

        setDisplaysEnabled(player, true)
        sendDisplayEnabled(player, true)
        sendMessage(player, "display.enabled")
    }
}
