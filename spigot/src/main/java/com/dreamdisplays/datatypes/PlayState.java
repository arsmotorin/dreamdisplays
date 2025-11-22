package com.dreamdisplays.datatypes;

import com.dreamdisplays.managers.DisplayManager;

import java.util.UUID;

public class PlayState {
    private final UUID id;
    private boolean paused = false;
    private long lastReportedTime = 0;
    private long lastReportedTimeTimestamp = 0;
    private long limitTime = 0;
    public DisplayData displayData;

    public PlayState (UUID id) {
        this.id = id;
        displayData = DisplayManager.getDisplayData(id);
    }

    public void update(SyncPacket packet) {
        this.paused = packet.currentState();
        this.lastReportedTime = packet.currentTime();
        this.lastReportedTimeTimestamp = System.nanoTime();
        limitTime = packet.limitTime();
    }

    public SyncPacket createPacket() {
        long nanos = System.nanoTime();
        long currentTime;

        if (paused) {
            currentTime = lastReportedTime;
        } else {
            long elapsed = nanos - lastReportedTimeTimestamp;
            currentTime = lastReportedTime + elapsed;
        }

        if (limitTime == 0 && displayData.getDuration() != null) {
            limitTime = displayData.getDuration();
        }

        if (limitTime > 0) {
            currentTime %= limitTime;
        }

        return new SyncPacket(id, true, paused, currentTime, limitTime);
    }
}