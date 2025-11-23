package com.dreamdisplays.scheduler

import com.dreamdisplays.Main
import org.jspecify.annotations.NullMarked

@NullMarked
object Provider {
    val adapter: Adapter =
        if (Main.getIsFolia()) Folia else Bukkit
}
