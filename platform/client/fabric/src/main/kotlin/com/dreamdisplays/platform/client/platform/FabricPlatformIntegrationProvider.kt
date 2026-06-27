package com.dreamdisplays.platform.client.platform

import com.dreamdisplays.api.platform.Platform
import com.dreamdisplays.api.platform.PlatformIntegrationProvider

/** Supplies the `Fabric` [Platform] adapter. */
object FabricPlatformIntegrationProvider : PlatformIntegrationProvider {
    override fun create(): Platform = FabricPlatform
}
