package com.dreamdisplays.protocol

import com.dreamdisplays.core.playback.PlaybackMode
import com.dreamdisplays.core.playback.Timeline

/** Builds the wire [DisplaySync] for [id] in [mode], stamped with the current [nowMs] anchor. */
fun Timeline.toSync(id: ProtoUuid, mode: PlaybackMode, nowMs: Long): DisplaySync {
    val anchored = anchoredAt(nowMs)
    return DisplaySync(
        id = id,
        isSync = mode == PlaybackMode.SYNCED,
        isPaused = anchored.paused,
        currentTimeMs = anchored.positionMs,
        durationMs = anchored.durationMs,
        serverTimeMs = anchored.serverTimeMs,
        loop = anchored.loop,
        mode = mode.wire,
    )
}

/** Reconstructs a core [Timeline] from an incoming wire [DisplaySync] packet. */
fun DisplaySync.toTimeline(): Timeline = Timeline(
    positionMs = currentTimeMs,
    serverTimeMs = serverTimeMs,
    paused = isPaused,
    durationMs = durationMs,
    loop = loop,
)
