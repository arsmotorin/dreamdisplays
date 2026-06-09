package com.dreamdisplays.client.capabilities

import com.dreamdisplays.protocol.ClientCapabilities

interface ClientCapabilityDetector {
    fun detect(): ClientCapabilities
    val supportsPopout: Boolean
    val supportsHardwareDecode: Boolean
    val maxTextureSize: Int
    val supportedCodecs: List<String>
}
