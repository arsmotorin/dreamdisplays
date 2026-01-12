package com.dreamdisplays.displays.mediaplayer.utils

import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.State

class GStreamer {
    companion object {
        fun safeStopAndDispose(e: Element?) {
            if (e == null) return
            try {
                e.state = State.NULL
            } catch (_: Exception) {
            }
            try {
                e.dispose()
            } catch (_: Exception) {
            }
        }
    }
}
