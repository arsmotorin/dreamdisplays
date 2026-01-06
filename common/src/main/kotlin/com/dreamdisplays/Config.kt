package com.dreamdisplays

import me.inotsleep.utils.config.AbstractConfig
import me.inotsleep.utils.config.Path
import org.jspecify.annotations.NullMarked
import java.io.File
import java.lang.System.setProperty

/**
 * Client configuration settings.
 */
@NullMarked
class Config(baseDir: File) : AbstractConfig(baseDir, "config.yml") {

    @JvmField
    @Path("mute-on-alt-tab")
    var muteOnAltTab: Boolean = false

    @JvmField
    @Path("default-render-distance")
    var defaultDistance: Int = 64

    @JvmField
    @Path("default-sync-display-volume")
    var syncDisplayVolume: Double = 0.25

    @JvmField
    @Path("default-default-display-volume")
    var defaultDisplayVolume: Double = 0.5

    @JvmField
    @Path("displays-enabled")
    var displaysEnabled: Boolean = true

    companion object {
        init {
            setProperty("file.encoding", "UTF-8")
        }
    }
}
