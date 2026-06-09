package com.dreamdisplays.media.api

interface MediaResolverChain {
    fun register(resolver: MediaResolver)
    fun unregister(resolver: MediaResolver)
    suspend fun resolve(source: MediaSource): ResolvedMedia
    val resolvers: List<MediaResolver>
}
