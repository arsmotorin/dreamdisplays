package com.dreamdisplays.downloader;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Unique;

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
public class Listener {

    // TODO: I kinda would like to keep other mods from accessing this, but mixin complicates stuff

    @Unique
    public static final Listener INSTANCE = new Listener();

    private String task = "";
    private float percent;
    private boolean done;
    private boolean failed;

    public String getTask() {
        return task;
    }

    public void setTask(String name) {
        this.task = name;
        this.percent = 0;
    }

    public float getProgress() {
        return percent;
    }

    public void setProgress(float percent) {
        this.percent = ((float) (((int) (percent * 100)) % 100)) / 100;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }
}
