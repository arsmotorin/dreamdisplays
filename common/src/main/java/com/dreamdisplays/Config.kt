package com.dreamdisplays;

import me.inotsleep.utils.config.AbstractConfig;
import me.inotsleep.utils.config.Path;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Client configuration settings.
 */
@NullMarked
public class Config extends AbstractConfig {

    static {
        System.setProperty("file.encoding", "UTF-8");
    }

    @Path("mute-on-alt-tab")
    public boolean muteOnAltTab = false;

    @Path("default-render-distance")
    public int defaultDistance = 64;

    @Path("default-sync-display-volume")
    public double syncDisplayVolume = 0.25;

    @Path("default-default-display-volume")
    public double defaultDisplayVolume = 0.5;

    @Path("displays-enabled")
    public boolean displaysEnabled = true;

    public Config(File baseDir) {
        super(baseDir, "config.yml");
    }
}
