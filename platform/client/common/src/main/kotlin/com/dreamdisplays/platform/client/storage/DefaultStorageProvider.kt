package com.dreamdisplays.platform.client.storage

import com.dreamdisplays.api.storage.ClientSettingsStorage
import com.dreamdisplays.api.storage.DisplayStorage
import com.dreamdisplays.api.storage.StorageProvider
import com.dreamdisplays.core.storage.DisplayStorage as CoreDisplayStorage

/** Supplies the client's storage backends: the core display snapshot registry and the JSON settings store. */
object DefaultStorageProvider : StorageProvider {
    override fun displayStorage(): DisplayStorage = CoreDisplayStorage
    override fun clientSettingsStorage(): ClientSettingsStorage = ClientSettingsStore
}
