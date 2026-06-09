package com.dreamdisplays.client.core

import com.dreamdisplays.api.DefaultDisplayService
import com.dreamdisplays.api.DefaultPlaybackService
import com.dreamdisplays.api.DisplayService
import com.dreamdisplays.api.PlaybackService
import com.dreamdisplays.client.capabilities.CapabilityNegotiationService
import com.dreamdisplays.client.capabilities.ClientCapabilityDetector
import com.dreamdisplays.client.capabilities.DefaultCapabilityNegotiationService
import com.dreamdisplays.client.capabilities.MinecraftClientCapabilityDetector
import com.dreamdisplays.client.input.DisplayInteractionService
import com.dreamdisplays.client.input.MinecraftDisplayInteractionService
import com.dreamdisplays.client.overlay.OverlayManager
import com.dreamdisplays.client.popout.DefaultPopoutManager
import com.dreamdisplays.client.popout.PopoutManager
import com.dreamdisplays.client.render.ClientRenderService
import com.dreamdisplays.client.ui.PipOverlayManager
import com.dreamdisplays.media.DefaultMediaResolverChain
import com.dreamdisplays.media.DefaultMediaSessionManager
import com.dreamdisplays.media.DefaultStreamSelector
import com.dreamdisplays.media.YtDlpSearchService
import com.dreamdisplays.media.api.MediaResolverChain
import com.dreamdisplays.media.api.MediaSearchService
import com.dreamdisplays.media.api.MediaSessionManager
import com.dreamdisplays.media.api.StreamSelector
import com.dreamdisplays.render.ScreenRenderer
import com.dreamdisplays.ytdlp.NewPipeResolver
import com.dreamdisplays.ytdlp.YtDlpResolver

/**
 * Process-wide [ServiceRegistry] holder and bootstrap entry point.
 *
 * Replaces scattered `object` singletons accessed by name with contract-typed lookups:
 * call [registry].`get<MediaResolverChain>()` instead of touching the concrete resolver objects.
 * [bootstrap] wires the default service graph and is idempotent, so it is safe to call from each
 * platform's startup path.
 */
object DreamServices {

    /** The shared registry. Services are populated by [bootstrap]. */
    val registry: ServiceRegistry = DefaultServiceRegistry()

    @Volatile
    private var bootstrapped = false

    /**
     * Registers the default service graph exactly once.
     *
     * Media pipeline: [MediaResolverChain] ([NewPipeResolver] fast path at priority 10,
     * [YtDlpResolver] subprocess fallback at 0), [MediaSearchService], [MediaSessionManager],
     * and [StreamSelector].
     *
     * Displays and playback: [DisplayService], [PlaybackService], [OverlayManager],
     * [PopoutManager], and [DisplayInteractionService].
     *
     * Client integration: [ClientRenderService] ([ScreenRenderer]), [ClientCapabilityDetector],
     * and [CapabilityNegotiationService].
     */
    @Synchronized
    fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true

        val resolverChain = DefaultMediaResolverChain().apply {
            register(NewPipeResolver)
            register(YtDlpResolver)
        }
        registry.register<MediaResolverChain>(resolverChain)
        registry.register<MediaSearchService>(YtDlpSearchService())
        registry.register<MediaSessionManager>(DefaultMediaSessionManager())
        registry.register<StreamSelector>(DefaultStreamSelector())
        registry.register<OverlayManager>(PipOverlayManager)
        registry.register<DisplayInteractionService>(MinecraftDisplayInteractionService)
        registry.register<ClientRenderService>(ScreenRenderer)
        registry.register<PopoutManager>(DefaultPopoutManager())
        registry.register<DisplayService>(DefaultDisplayService())
        registry.register<PlaybackService>(DefaultPlaybackService())
        registry.register<ClientCapabilityDetector>(MinecraftClientCapabilityDetector)
        registry.register<CapabilityNegotiationService>(
            DefaultCapabilityNegotiationService(MinecraftClientCapabilityDetector)
        )
    }
}
