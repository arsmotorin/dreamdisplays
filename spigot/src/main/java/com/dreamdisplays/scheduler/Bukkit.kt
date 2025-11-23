package com.dreamdisplays.scheduler

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.jspecify.annotations.NullMarked

@NullMarked
object Bukkit : Adapter {
    override fun runRepeatingAsync(
        plugin: Plugin,
        delayTicks: Long,
        intervalTicks: Long,
        task: Runnable
    ) {
        object : BukkitRunnable() {
            override fun run() = task.run()
        }.runTaskTimerAsynchronously(plugin, delayTicks, intervalTicks)
    }
}
