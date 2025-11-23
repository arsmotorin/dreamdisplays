package com.dreamdisplays.scheduler

import com.dreamdisplays.DreamDisplaysPlugin

object Provider {
    val adapter: Adapter =
        if (DreamDisplaysPlugin.getIsFolia()) Folia else Bukkit
}
