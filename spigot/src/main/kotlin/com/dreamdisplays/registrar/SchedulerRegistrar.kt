package com.dreamdisplays.registrar

import com.dreamdisplays.Main
import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.managers.DisplayManager.updateAllDisplays
import com.dreamdisplays.scheduler.ProviderScheduler
import com.dreamdisplays.scheduler.ProviderScheduler.adapter
import com.dreamdisplays.utils.Updater.checkForUpdates

/**
 * Manages the registration of scheduled tasks.
 */
object SchedulerRegistrar {

    fun runRepeatingTasks(plugin: Main) {
        // Update displays every second
        adapter.runRepeatingAsync(plugin, 50L, 1000L) {
            updateAllDisplays()
        }

        // Check for updates every hour
        val settings = config.settings
        if (settings.updatesEnabled) {
            adapter.runRepeatingAsync(plugin, 20L, 20L * 3600L) {
                checkForUpdates(
                    settings.repoOwner,
                    settings.repoName
                )
            }
        }
    }
}
