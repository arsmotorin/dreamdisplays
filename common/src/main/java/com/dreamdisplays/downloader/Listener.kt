package com.dreamdisplays.downloader

import org.jspecify.annotations.NullMarked

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
object Listener {
    var task: String = ""
        set(value) {
            field = value
            progress = 0f
        }

    var progress: Float = 0f
        set(value) {
            field = ((value * 100).toInt() % 100) / 100f
        }

    var isDone: Boolean = false
    var isFailed: Boolean = false
}
