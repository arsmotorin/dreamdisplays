package com.dreamdisplays.scheduler

import com.dreamdisplays.Main
import org.jspecify.annotations.NullMarked

/**
 * Provider object to select the appropriate scheduler implementation.
 */
@NullMarked
object Provider {
    val adapter: Adapter =
        if (Main.getIsFolia()) FoliaScheduler else BukkitScheduler
}
