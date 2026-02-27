package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.managers.PlayerManager.isDisplaysEnabled
import com.dreamdisplays.managers.PlayerManager.setDisplaysEnabled
import com.dreamdisplays.utils.Message.sendMessage
import com.dreamdisplays.utils.net.PacketUtils.sendDisplayEnabled
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class OffCommand : SubCommand {

    override val name = "off"
    override val permission: String? = null
    override val playerOnly = true

    override fun execute(sender: CommandSender, args: Array<String?>) {
        val player = (sender as? Player) ?: return

        if (!isDisplaysEnabled(player)) {
            sendMessage(player, "display.already-disabled")
            return
        }

        setDisplaysEnabled(player, false)
        sendDisplayEnabled(player, false)
        sendMessage(player, "display.disabled")
    }
}
