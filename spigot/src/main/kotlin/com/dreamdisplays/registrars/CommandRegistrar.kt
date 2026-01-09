package com.dreamdisplays.registrars

import com.dreamdisplays.Main
import com.dreamdisplays.commands.DisplayCommand

/**
 * Registers commands for the plugin.
 */
object CommandRegistrar {

    fun registerCommands(plugin: Main) {
        val command = DisplayCommand()

        plugin.getCommand("display")?.apply {
            setExecutor(command)
            tabCompleter = command
        }
    }
}
