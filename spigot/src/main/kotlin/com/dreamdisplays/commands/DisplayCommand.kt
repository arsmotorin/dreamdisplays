package com.dreamdisplays.commands

import com.dreamdisplays.commands.subcommands.*
import com.dreamdisplays.utils.Message.sendMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.jspecify.annotations.NullMarked

/**
 * Main command handler for the `/display` command.
 * Handles sub-commands and tab completion.
 * @constructor Creates a new DisplayCommand instance.
 * @see CommandExecutor
 * @see TabCompleter
 */
@NullMarked
class DisplayCommand :
    CommandExecutor,
    TabCompleter {

    // All sub-commands
    private val subCommands = listOf(
        CreateCommand(),
        DeleteCommand(),
        VideoCommand(),
        ListCommand(),
        StatsCommand(),
        ReloadCommand(),
        OnCommand(),
        OffCommand(),
        HelpCommand()
    ).associateBy { it.name }

    // Command execution logic
    fun toExecute(sender: CommandSender, label: String?, args: Array<String?>) {
        if (args.isEmpty()) {
            subCommands["help"]?.execute(sender, args)
            return
        }

        val sub = subCommands[args[0]]
            ?: return sendMessage(sender, "displayWrongCommand")

        sub.permission?.let { perm ->
            if (!sender.hasPermission(perm)) {
                sendMessage(sender, "displayCommandMissingPermission")
                return
            }
        }

        sub.execute(sender, args)
    }

    // Tab completer logic
    fun complete(sender: CommandSender, args: Array<String?>): MutableList<String?> {
        if (args.size != 1) return mutableListOf()

        return subCommands.values
            .filter { (it.permission == null) || sender.hasPermission(it.permission ?: "") }
            .map { it.name }
            .toMutableList()
    }

    // Command executor
    @Suppress("UNCHECKED_CAST")
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        toExecute(sender, label, args as Array<String?>)
        return true
    }

    // Tab completer
    @Suppress("UNCHECKED_CAST")
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): MutableList<String?> {
        return complete(sender, args as Array<String?>)
    }
}
