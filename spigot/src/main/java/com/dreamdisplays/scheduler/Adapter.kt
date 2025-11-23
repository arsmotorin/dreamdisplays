package com.dreamdisplays.scheduler

import org.bukkit.plugin.Plugin

interface Adapter {
    fun runRepeatingAsync(plugin: Plugin, delayTicks: Long, intervalTicks: Long, task: Runnable)
}
