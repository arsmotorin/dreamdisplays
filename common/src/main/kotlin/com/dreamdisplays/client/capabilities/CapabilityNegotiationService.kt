package com.dreamdisplays.client.capabilities

import com.dreamdisplays.protocol.ClientCapabilities
import com.dreamdisplays.protocol.ServerCapabilities

interface CapabilityNegotiationService {
    val localCapabilities: ClientCapabilities
    val serverCapabilities: ServerCapabilities?
    val isNegotiated: Boolean

    fun advertise()
    fun onServerCapabilities(capabilities: ServerCapabilities)
    fun isFeatureEnabled(feature: String): Boolean
}
