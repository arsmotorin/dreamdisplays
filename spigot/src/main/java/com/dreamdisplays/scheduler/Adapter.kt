package com.dreamdisplays.scheduler

import org.bukkit.plugin.Plugin
import org.jspecify.annotations.NullMarked

@NullMarked
interface Adapter {
    fun runRepeatingAsync(plugin: Plugin, delayTicks: Long, intervalTicks: Long, task: Runnable)
}
