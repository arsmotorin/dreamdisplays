package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.Main
import com.dreamdisplays.managers.DisplayManager.getDisplays
import com.dreamdisplays.utils.Message.sendComponent
import com.dreamdisplays.utils.Message.sendMessage
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.command.CommandSender

class ListCommand : SubCommand {

    override val name = "list"
    override val permission = Main.config.permissions.list

    override fun execute(sender: CommandSender, args: Array<String?>) {
        val displays = getDisplays()
        if (displays.isEmpty()) {
            sendMessage(sender, "noDisplaysFound")
            return
        }

        sendMessage(sender, "displayListHeader")

        displays.forEachIndexed { index, d ->
            val owner = getOfflinePlayer(d.ownerId).name ?: "Unknown"

            val component = text("${index + 1}. Owner: $owner. Location: ")
                .append(
                    text("[${d.pos1.blockX}, ${d.pos1.blockY}, ${d.pos1.blockZ}]")
                        .color(NamedTextColor.GOLD)
                        .clickEvent(
                            ClickEvent.runCommand(
                                "/tp ${d.pos1.blockX} ${d.pos1.blockY} ${d.pos1.blockZ}"
                            )
                        )
                )
                .append(
                    text(" [YouTube]").color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.openUrl(d.url))
                )

            sendComponent(sender, component)
        }
    }
}
