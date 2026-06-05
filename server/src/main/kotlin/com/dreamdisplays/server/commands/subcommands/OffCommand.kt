package com.dreamdisplays.server.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import io.github.arsmotorin.ofrat.FabricOnly
import io.github.arsmotorin.ofrat.PaperOnly
import net.minecraft.commands.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Handles the `/display off` command.
 */
@Deprecated("This command is being replaced by UI interface. Will be removed in a future update.")
@PaperOnly
class OffCommand : SubCommand {
    override val name = "off"
    override val permission: String? = null
    override val playerOnly = false

    override fun execute(sender: CommandSender, args: Array<String?>) {
        ToggleDisplayCommand.execute(sender, args, enabled = false)
    }

    override fun complete(sender: CommandSender, args: Array<String?>): List<String> {
        if (args.size != 2) return emptyList()
        return Bukkit.getOnlinePlayers().map { it.name }.sorted()
    }
}

/**
 * `Fabric`-specific entrypoint for `/display off`.
 */
@Deprecated("This command is being replaced by UI interface. Will be removed in a future update.")
@FabricOnly
object FabricOffCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>, targetName: String? = null): Int {
        val args: Array<String?> = if (targetName == null) arrayOf("off") else arrayOf("off", targetName)
        return if (ToggleDisplayCommand.execute(ctx.source, args, enabled = false)) 1 else 0
    }
}
