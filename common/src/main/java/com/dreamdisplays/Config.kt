package com.dreamdisplays

import me.inotsleep.utils.config.AbstractConfig
import me.inotsleep.utils.config.Path
import org.jspecify.annotations.NullMarked
import java.io.File

@NullMarked
class Config(baseDir: File) : AbstractConfig(baseDir, "config.yml") {
    @Path("mute-on-alt-tab")
    var muteOnAltTab: Boolean = true

    @JvmField
    @Path("default-render-distance")
    var defaultDistance: Int = 64

    @JvmField
    @Path("default-sync-display-volume")
    var syncDisplayVolume: Double = 0.25

    @JvmField
    @Path("default-default-display-volume")
    var defaultDisplayVolume: Double = 0.5

    companion object {
        init {
            System.setProperty("file.encoding", "UTF-8")
        }
    }
}
