package com.dreamdisplays.core.protocol

/**
 * Server capabilities advertised in [ServerHello.allowedFeatures].
 *
 * The protobuf field stays `List<String>` for wire compatibility; code should build and query it
 * through this enum so feature tokens remain centralized.
 */
enum class ServerFeature(val wire: String) {
    MODES("modes"),
    WATCH_PARTY("watch_party"),
    BROADCAST("broadcast");

    companion object {
        val playbackFeatures: List<ServerFeature> = listOf(MODES, WATCH_PARTY, BROADCAST)
        val playbackFeatureWires: List<String> = playbackFeatures.toWire()

        fun fromWire(raw: String): ServerFeature? =
            entries.firstOrNull { it.wire == raw }
    }
}

/** Converts feature enums to their protobuf-compatible wire tokens. */
fun Iterable<ServerFeature>.toWire(): List<String> = map { it.wire }

/** True if this server snapshot advertises [feature]. */
fun ServerHello.hasFeature(feature: ServerFeature): Boolean =
    feature.wire in allowedFeatures
