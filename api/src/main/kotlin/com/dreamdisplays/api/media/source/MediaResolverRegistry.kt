package com.dreamdisplays.api.media.source


interface MediaResolverRegistry {
    fun register(resolver: MediaResolver)
    fun unregister(resolver: MediaResolver)
    fun resolve(source: MediaSource): ResolvedMedia

    /** Delegates [MediaResolver.prefetch] to all capable resolvers for [source]. */
    fun prefetch(source: MediaSource)
    val resolvers: List<MediaResolver>
}
