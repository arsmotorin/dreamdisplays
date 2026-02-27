package com.dreamdisplays.commands.subcommands

import org.bukkit.command.CommandSender

/**
 * Sub-command that can be executed by a command sender.
 * Each sub-command has a name, an optional permission requirement,
 * and an execute method that defines the command's behavior.
 *
 * @property name The name of the sub-command.
 * @property permission The permission required to execute the sub-command, or null if no permission is
 * required.
 * @function execute Executes the sub-command with the given command sender and arguments.
 * @param sender The command sender executing the sub-command.
 * @param args The arguments passed to the sub-command.
 * @see CommandSender
 */
interface SubCommand {
    val name: String
    val permission: String?
    val playerOnly: Boolean get() = false
    fun execute(sender: CommandSender, args: Array<String?>)
    fun complete(sender: CommandSender, args: Array<String?>): List<String> = emptyList()
}
