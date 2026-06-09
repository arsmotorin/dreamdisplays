package com.dreamdisplays.media.api

interface MediaResolver {
    val priority: Int get() = 0
    fun canResolve(source: MediaSource): Boolean
    suspend fun resolve(source: MediaSource): ResolvedMedia
}
