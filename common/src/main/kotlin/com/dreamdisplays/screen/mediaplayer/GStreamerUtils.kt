package com.dreamdisplays.screen.mediaplayer

import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.State

class GStreamerUtils {
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
