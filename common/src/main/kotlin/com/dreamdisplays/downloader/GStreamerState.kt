package com.dreamdisplays.downloader

import java.util.concurrent.atomic.AtomicBoolean

object GStreamerState {
    val recursionDetector = AtomicBoolean(false)
    var downloaded = false
}
