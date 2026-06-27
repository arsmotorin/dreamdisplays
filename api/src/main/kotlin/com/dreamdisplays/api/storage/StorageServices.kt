package com.dreamdisplays.api.storage

import com.dreamdisplays.api.DreamDisplaysUnstableApi
import com.dreamdisplays.api.runtime.ServiceKey
import com.dreamdisplays.api.runtime.serviceKey

/**
 * Storage service keys.
 *
 * @since 1.8.4
 */
@DreamDisplaysUnstableApi
object StorageServices {
    /** Server-authoritative display snapshot registry. */
    val DISPLAY_STORAGE: ServiceKey<DisplayStorage> = serviceKey("dreamdisplays:display_storage")

    /** Client-local per-display settings store. */
    val CLIENT_SETTINGS: ServiceKey<ClientSettingsStorage> = serviceKey("dreamdisplays:client_settings_storage")
}
