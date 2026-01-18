package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.utils.Message.sendColoredMessage
import com.dreamdisplays.utils.Message.sendMessage
import org.bukkit.command.CommandSender

class ReloadCommand : SubCommand {

    override val name = "reload"
    override val permission = config.permissions.reload

    override fun execute(sender: CommandSender, args: Array<String?>) {
        try {
            config.reload()
            sendMessage(sender, "configReloaded")
        } catch (_: Exception) {
            sendColoredMessage(sender, "configReloadFailed")
        }
    }
}
