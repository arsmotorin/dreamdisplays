package com.dreamdisplays.platform.client.platform

import com.dreamdisplays.api.platform.Platform
import com.dreamdisplays.api.platform.PlatformIntegrationProvider

/** Supplies the `NeoForge` [Platform] adapter. */
object NeoForgePlatformIntegrationProvider : PlatformIntegrationProvider {
    override fun create(): Platform = NeoForgePlatform
}
