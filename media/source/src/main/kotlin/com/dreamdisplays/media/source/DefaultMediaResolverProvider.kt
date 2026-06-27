package com.dreamdisplays.media.source

import com.dreamdisplays.api.media.source.MediaResolver
import com.dreamdisplays.api.media.source.MediaResolverProvider
import com.dreamdisplays.media.source.ytdlp.NewPipeResolver
import com.dreamdisplays.media.source.ytdlp.YtDlpResolver

/** Supplies the built-in resolver chain: the fast in-process `NewPipeExtractor` path, then the `yt-dlp` fallback. */
object DefaultMediaResolverProvider : MediaResolverProvider {
    override fun resolvers(): List<MediaResolver> = listOf(NewPipeResolver, YtDlpResolver)
}
