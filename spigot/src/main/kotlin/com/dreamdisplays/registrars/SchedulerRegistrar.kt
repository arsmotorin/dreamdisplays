package com.dreamdisplays.registrars

import com.dreamdisplays.Main
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.schedulers.ProviderScheduler
import com.dreamdisplays.utils.Updater.checkForUpdates

/**
 * Manages the registration of scheduled tasks.
 */
object SchedulerRegistrar {

    fun runRepeatingTasks(plugin: Main) {
        // Update displays every second
        ProviderScheduler.adapter.runRepeatingAsync(plugin, 50L, 1000L) {
            DisplayManager.updateAllDisplays()
        }

        // Check for updates every hour
        val settings = Main.config.settings
        if (settings.updatesEnabled) {
            ProviderScheduler.adapter.runRepeatingAsync(plugin, 20L, 20L * 3600L) {
                checkForUpdates(
                    settings.repoOwner,
                    settings.repoName
                )
            }
        }
    }
}
