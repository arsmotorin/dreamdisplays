package com.dreamdisplays.utils

/**
 * Platform-specific checks. Right now it includes Folia detection.
 */
object PlatformUtils {

    val isFolia: Boolean by lazy {
        runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess
    }
}
