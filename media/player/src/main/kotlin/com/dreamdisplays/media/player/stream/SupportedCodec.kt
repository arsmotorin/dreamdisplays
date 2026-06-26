package com.dreamdisplays.media.player.stream

import com.dreamdisplays.api.util.WireEnum
import com.dreamdisplays.api.util.wireEnumValueOf

/** Video codecs the player pipeline recognizes and can advertise to the server. */
enum class SupportedCodec(override val wire: String, private vararg val prefixes: String) : WireEnum {
    H264("h264", "avc", "h264"),
    HEVC("hevc", "hvc", "hev", "h265"),
    VP9("vp9", "vp9", "vp09"),
    AV1("av1", "av01", "av1"),
    UNKNOWN("unknown");

    companion object {
        /** Codecs advertised by the default client capability detector. */
        val advertised: List<SupportedCodec> = listOf(H264, HEVC, VP9, AV1)

        /** Returns the enum value corresponding to the given wire value, or [UNKNOWN] if not found. */
        fun fromWire(raw: String?): SupportedCodec = wireEnumValueOf(raw, UNKNOWN)

        /** Returns the enum value corresponding to the codec name, or [UNKNOWN] if not found. */
        fun fromCodecName(raw: String?): SupportedCodec {
            val codec = raw?.trim()?.lowercase() ?: return UNKNOWN
            return entries.firstOrNull { candidate ->
                candidate !== UNKNOWN && candidate.prefixes.any { codec.startsWith(it) }
            } ?: UNKNOWN
        }
    }
}
