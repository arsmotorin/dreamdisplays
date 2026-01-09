package com.dreamdisplays.registrars

import com.dreamdisplays.Main
import com.dreamdisplays.utils.net.PacketReceiver

/**
 * Manages the registration of plugin channels for incoming and outgoing messages.
 */
object ChannelRegistrar {

    private val incomingChannels = listOf(
        "dreamdisplays:sync",
        "dreamdisplays:req_sync",
        "dreamdisplays:delete",
        "dreamdisplays:report",
        "dreamdisplays:version",
        "dreamdisplays:display_enabled"
    )

    private val outgoingChannels = listOf(
        "dreamdisplays:premium",
        "dreamdisplays:display_info",
        "dreamdisplays:sync",
        "dreamdisplays:delete",
        "dreamdisplays:report_enabled",
        "dreamdisplays:ceiling_floor_support"
    )

    fun registerChannels(plugin: Main) {
        val messenger = plugin.server.messenger
        val receiver = PacketReceiver(plugin)

        incomingChannels.forEach { messenger.registerIncomingPluginChannel(plugin, it, receiver) }
        outgoingChannels.forEach { messenger.registerOutgoingPluginChannel(plugin, it) }
    }
}
