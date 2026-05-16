package com.dreamdisplays.player.events

/**
 * Callbacks emitted by [MediaPlayer] to decouple playback logic from the rendering / UI layer.
 * Lambdas are invoked from the control executor unless noted otherwise.
 */
internal data class PlayerEvents(
    /** Called on an unrecoverable playback error. */
    val onError: () -> Unit,

    /** Called after a seek completes so the screen can reset its overlay state. */
    val onSeek: () -> Unit,

    /**
     * Posted to the `Minecraft` render queue after each frame swap.
     * Called from the video reader thread, not the control executor.
     */
    val onFitTexture: Runnable,
)
