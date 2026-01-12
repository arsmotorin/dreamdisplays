package com.dreamdisplays.utils

import com.dreamdisplays.utils.Platform.isFolia
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.jspecify.annotations.NullMarked
import java.lang.reflect.Proxy

/**
 * Scheduler utility to run tasks synchronously or asynchronously,
 * supporting both standard Bukkit and Folia server implementations.
 */
@NullMarked
object Scheduler {

    private lateinit var plugin: Plugin

    // Initialize the scheduler with the plugin instance
    fun init(plugin: Plugin) {
        this.plugin = plugin
    }

    // Run async
    fun runAsync(task: Runnable) {
        if (isFolia) {
            runFoliaAsync(task)
        } else {
            BukkitTask(task).runTaskAsynchronously(plugin)
        }
    }

    // Run sync
    fun runSync(task: Runnable) {
        if (isFolia) {
            runFoliaSync(task)
        } else {
            BukkitTask(task).runTask(plugin)
        }
    }

    // Folia async
    private fun runFoliaAsync(task: Runnable) {
        runCatching {
            val scheduler = Class.forName("org.bukkit.Bukkit")
                .getMethod("getAsyncScheduler")
                .invoke(null)

            scheduler.javaClass
                .getMethod("runNow", Plugin::class.java, consumerClass)
                .invoke(scheduler, plugin, consumer(task))
        }.getOrElse {
            task.run()
        }
    }

    // Folia sync
    private fun runFoliaSync(task: Runnable) {
        runCatching {
            val scheduler = Class.forName("org.bukkit.Bukkit")
                .getMethod("getGlobalRegionScheduler")
                .invoke(null)

            scheduler.javaClass
                .getMethod("run", Plugin::class.java, consumerClass)
                .invoke(scheduler, plugin, consumer(task))
        }.getOrElse {
            task.run()
        }
    }

    // Create Consumer proxy (it's needed to pass Runnable to Folia scheduler)
    private val consumerClass = Class.forName("java.util.function.Consumer")

    private fun consumer(task: Runnable): Any =
        Proxy.newProxyInstance(
            consumerClass.classLoader,
            arrayOf(consumerClass)
        ) { _, _, _ ->
            task.run()
            null
        }

    // Bukkit task wrapper
    private class BukkitTask(private val task: Runnable) : BukkitRunnable() {
        override fun run() = task.run()
    }
}
