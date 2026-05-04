package com.dreamdisplays.scheduler

import org.bukkit.plugin.Plugin
import org.jspecify.annotations.NullMarked

@NullMarked
object BukkitScheduler : AdapterScheduler {
    override fun runRepeatingAsync(plugin: Plugin, delayTicks: Long, intervalTicks: Long, task: Runnable) {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, intervalTicks)
    }
}
