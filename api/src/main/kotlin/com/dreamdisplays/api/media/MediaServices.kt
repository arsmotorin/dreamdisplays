package com.dreamdisplays.api.media

import com.dreamdisplays.api.DreamDisplaysUnstableApi
import com.dreamdisplays.api.media.search.MediaSearchService
import com.dreamdisplays.api.media.source.MediaResolverRegistry
import com.dreamdisplays.api.runtime.ServiceKey
import com.dreamdisplays.api.runtime.serviceKey

/**
 * Media service keys.
 *
 * @since 1.8.4
 */
@DreamDisplaysUnstableApi
object MediaServices {
    /** Ordered resolver chain for media sources. */
    val RESOLVER_REGISTRY: ServiceKey<MediaResolverRegistry> = serviceKey("dreamdisplays:media_resolver_registry")

    /** Search and related-video lookup service. */
    val SEARCH: ServiceKey<MediaSearchService> = serviceKey("dreamdisplays:media_search")
}
