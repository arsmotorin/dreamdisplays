package com.dreamdisplays.platform.client.core.modules

import com.dreamdisplays.api.runtime.DreamDisplaysModule
import com.dreamdisplays.api.runtime.ModuleContext
import com.dreamdisplays.api.storage.StorageServices
import com.dreamdisplays.platform.client.storage.DefaultStorageProvider

/** Installs the display snapshot registry and client settings store behind the storage service keys. */
object ClientStorageModule : DreamDisplaysModule {
    /** The ID of this module. */
    override val id: String = "dreamdisplays:client_storage"

    /** Installs the storage service providers. */
    override fun install(context: ModuleContext) {
        val services = context.services
        val provider = DefaultStorageProvider
        services.register(StorageServices.DISPLAY_STORAGE, provider.displayStorage())
        services.register(StorageServices.CLIENT_SETTINGS, provider.clientSettingsStorage())
    }
}
