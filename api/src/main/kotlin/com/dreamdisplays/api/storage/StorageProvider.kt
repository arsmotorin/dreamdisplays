package com.dreamdisplays.api.storage

import com.dreamdisplays.api.DreamDisplaysUnstableApi

/**
 * Supplies the storage backends.
 *
 * @since 1.8.4
 */
@DreamDisplaysUnstableApi
interface StorageProvider {
    /** The server-authoritative display snapshot registry. */
    fun displayStorage(): DisplayStorage

    /** The client-local per-display settings store. */
    fun clientSettingsStorage(): ClientSettingsStorage
}
