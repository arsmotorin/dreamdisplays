package com.dreamdisplays.client.core

import com.dreamdisplays.platform.api.Platform

interface ClientContext {
    val state: ClientMutableState
    val services: ServiceRegistry
    val platform: Platform
}
