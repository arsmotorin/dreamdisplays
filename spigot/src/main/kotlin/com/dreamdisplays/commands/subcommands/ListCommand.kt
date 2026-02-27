package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.Main
import com.dreamdisplays.datatypes.DisplayData
import com.dreamdisplays.managers.DisplayManager.getDisplays
import com.dreamdisplays.utils.Message.sendComponent
import com.dreamdisplays.utils.Message.sendColoredMessage
import com.dreamdisplays.utils.Message.sendMessage
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.max

class ListCommand : SubCommand {
    private companion object {
        const val PAGE_SIZE = 10
        const val DEFAULT_LIST_ENTRY_TEMPLATE = "&7D |&f {0}. Owner: {1}, Location: [{2}, {3}, {4}], URL: {5}"
        val LEGACY_SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.legacyAmpersand()
    }

    override val name = "list"
    override val permission = Main.config.permissions.list

    override fun execute(sender: CommandSender, args: Array<String?>) {
        val displays = getDisplays()
            .sortedWith(
                compareBy<DisplayData>(
                    { it.pos1.world?.name ?: "~unknown" },
                    { it.pos1.blockX },
                    { it.pos1.blockY },
                    { it.pos1.blockZ },
                    { it.id.toString() }
                )
            )
        if (displays.isEmpty()) {
            sendMessage(sender, "noDisplaysFound")
            return
        }

        val totalPages = max(1, (displays.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val requestedPage = args.getOrNull(1)?.toIntOrNull()
        if (args.size >= 2 && requestedPage == null) {
            sendMessage(sender, "displayWrongCommand")
            return
        }
        val page = (requestedPage ?: 1).coerceIn(1, totalPages)
        val startIndex = (page - 1) * PAGE_SIZE
        val endExclusive = minOf(startIndex + PAGE_SIZE, displays.size)
        val pageDisplays = displays.subList(startIndex, endExclusive)

        sendMessage(sender, "displayListHeader")
        sendColoredMessage(
            sender,
            "&7D |&7 Page &f$page&7/&f$totalPages &7| Total: &f${displays.size}"
        )

        pageDisplays.forEachIndexed { localIndex, d ->
            val index = startIndex + localIndex + 1
            val owner = Bukkit.getOfflinePlayer(d.ownerId).name ?: "Unknown"
            val worldName = d.pos1.world?.name ?: "unknown"
            val idShort = d.id.toString().substring(0, 8)
            val url = d.url.ifBlank { "N/A" }
            val template = Main.config.getMessageForPlayer(sender as? Player, "displayListEntry") as? String
                ?: DEFAULT_LIST_ENTRY_TEMPLATE
            val baseLine = applyPlaceholders(
                template,
                index.toString(),
                owner,
                d.pos1.blockX.toString(),
                d.pos1.blockY.toString(),
                d.pos1.blockZ.toString(),
                url
            )
            val details = " &8| &7world=&f$worldName &8| &7size=&f${d.width}x${d.height} &8| &7sync=&f${d.isSync} &8| &7id=&f$idShort"
            val fullLine = baseLine + details

            if (sender !is Player) {
                sendColoredMessage(sender, fullLine)
                return@forEachIndexed
            }

            val component = LEGACY_SERIALIZER.deserialize(fullLine)
                .append(
                    text(" [TP]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(
                            ClickEvent.runCommand(
                                "/tp ${d.pos1.blockX} ${d.pos1.blockY} ${d.pos1.blockZ}"
                            )
                        )
                )

            val finalComponent = if (d.url.isBlank()) {
                component
            } else {
                component.append(
                    text(" [URL]").color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.openUrl(d.url))
                )
            }

            sendComponent(sender, finalComponent)
        }
    }

    override fun complete(sender: CommandSender, args: Array<String?>): List<String> {
        if (args.size != 2) return emptyList()
        val totalPages = max(1, (getDisplays().size + PAGE_SIZE - 1) / PAGE_SIZE)
        return (1..totalPages).map { it.toString() }
    }

    private fun applyPlaceholders(template: String, vararg values: String): String {
        var result = template
        values.forEachIndexed { index, value ->
            result = result.replace("{$index}", value)
        }
        return result
    }
}
