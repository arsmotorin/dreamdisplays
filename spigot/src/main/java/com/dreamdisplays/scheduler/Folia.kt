package com.dreamdisplays.scheduler

import org.bukkit.plugin.Plugin
import java.lang.reflect.Proxy

object Folia : Adapter {

    private val asyncScheduler = Class.forName("org.bukkit.Bukkit")
        .getMethod("getAsyncScheduler").invoke(null)

    private val pluginClass = Class.forName("org.bukkit.plugin.Plugin")
    private val consumerClass = Class.forName("java.util.function.Consumer")
    private val timeUnitClass = Class.forName("java.util.concurrent.TimeUnit")
    private val milliseconds = timeUnitClass.getDeclaredField("MILLISECONDS").get(null)

    override fun runRepeatingAsync(
        plugin: Plugin,
        delayTicks: Long,
        intervalTicks: Long,
        task: Runnable
    ) {
        val consumer = Proxy.newProxyInstance(
            consumerClass.classLoader,
            arrayOf(consumerClass)
        ) { _, method, _ ->
            if (method?.name == "accept") task.run()
            null
        }

        asyncScheduler.javaClass.getMethod(
            "runAtFixedRate",
            pluginClass,
            consumerClass,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            timeUnitClass
        ).invoke(
            asyncScheduler,
            plugin,
            consumer,
            delayTicks,
            intervalTicks,
            milliseconds
        )
    }
}
