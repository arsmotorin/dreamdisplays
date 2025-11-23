package com.dreamdisplays.scheduler

import com.dreamdisplays.Main

object Provider {
    val adapter: Adapter =
        if (Main.getIsFolia()) Folia else Bukkit
}
