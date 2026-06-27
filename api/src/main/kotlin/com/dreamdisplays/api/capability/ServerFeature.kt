package com.dreamdisplays.api.capability

import com.dreamdisplays.api.DreamDisplaysUnstableApi
import com.dreamdisplays.api.util.WireEnum
import com.dreamdisplays.api.util.wireEnumValueOfOrNull

/**
 * Server capabilities advertised to clients during negotiation.
 *
 * Wire protocols may keep these as strings for compatibility; runtime code should build and query
 * features through this enum so feature tokens remain centralized.
 *
 * @since 1.8.4
 */
@DreamDisplaysUnstableApi
enum class ServerFeature(override val wire: String) : WireEnum {
    /** Server supports selecting playback modes. */
    MODES("modes"),

    /** Server supports watch-party sessions. */
    WATCH_PARTY("watch_party"),

    /** Server supports broadcast playback. */
    BROADCAST("broadcast");

    companion object {
        /** Playback-related features enabled by the current server implementation. */
        val playbackFeatures: List<ServerFeature> = listOf(MODES, WATCH_PARTY, BROADCAST)

        /** Playback-related feature tokens for string-based wire protocols. */
        val playbackFeatureWires: List<String> = playbackFeatures.toWire()

        /** Decodes a feature token, or returns `null` when unknown. */
        fun fromWire(raw: String): ServerFeature? = wireEnumValueOfOrNull(raw)
    }
}

/** Converts feature enums to their wire tokens. */
@DreamDisplaysUnstableApi
fun Iterable<ServerFeature>.toWire(): List<String> = map { it.wire }
