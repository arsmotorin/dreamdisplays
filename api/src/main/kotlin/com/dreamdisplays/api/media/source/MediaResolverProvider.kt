package com.dreamdisplays.api.media.source

import com.dreamdisplays.api.DreamDisplaysUnstableApi

/**
 * Supplies the ordered set of [MediaResolver]s a [MediaResolverRegistry] is assembled from.
 *
 * @since 1.8.4
 */
@DreamDisplaysUnstableApi
fun interface MediaResolverProvider {
    /** The resolvers to register, in any order; [MediaResolverRegistry] sorts by [MediaResolver.priority]. */
    fun resolvers(): List<MediaResolver>
}
