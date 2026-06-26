package com.dreamdisplays.api.display.model

import com.dreamdisplays.api.DreamDisplaysUnstableApi

/**
 * Content texture rotation in quarter turns.
 *
 * Packet and storage boundaries persist [quarterTurns]; runtime code should use this enum so the
 * `0..3` contract stays centralized.
 *
 * @since 1.8.4
 */
@DreamDisplaysUnstableApi
enum class ContentRotation(val quarterTurns: Int) {
    /** No content rotation. */
    NONE(0),

    /** Rotate content one quarter turn to the right. */
    RIGHT(1),

    /** Rotate content by half a turn. */
    HALF_TURN(2),

    /** Rotate content one quarter turn to the left. */
    LEFT(3);

    companion object {
        private val byQuarterTurns = entries.associateBy { it.quarterTurns }

        /** Decodes a rotation from persisted quarter turns, wrapping out-of-range values. */
        fun fromQuarterTurns(raw: Int): ContentRotation =
            byQuarterTurns[Math.floorMod(raw, entries.size)] ?: NONE
    }
}
