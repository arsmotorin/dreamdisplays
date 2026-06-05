package com.dreamdisplays.server.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import io.github.arsmotorin.ofrat.FabricOnly
import io.github.arsmotorin.ofrat.PaperOnly
import net.minecraft.commands.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Handles the `/display on` command. Enables display rendering for the sender or for another
 * player when the caller holds the `toggleOthers` permission.
 */
@Deprecated("This command is being replaced by UI interface. Will be removed in a future update.")
@PaperOnly class OnCommand : SubCommand {
    override val name = "on"
    override val permission: String? = null
    override val playerOnly = false

    /** Enables displays for the sender or for another player when permitted. */
    override fun execute(sender: CommandSender, args: Array<String?>) {
        ToggleDisplayCommand.execute(sender, args, enabled = true)
    }

    /** Provides auto-completion suggestions for the target player name. */
    override fun complete(sender: CommandSender, args: Array<String?>): List<String> {
        if (args.size != 2) return emptyList()
        return Bukkit.getOnlinePlayers().map { it.name }.sorted()
    }
}

/**
 * `Fabric`-specific implementation of the `/display on` command.
 */
@Deprecated("This command is being replaced by UI interface. Will be removed in a future update.")
@FabricOnly object FabricOnCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>, targetName: String? = null): Int {
        val args: Array<String?> = if (targetName == null) arrayOf("on") else arrayOf("on", targetName)
        return if (ToggleDisplayCommand.execute(ctx.source, args, enabled = true)) 1 else 0
    }
}
