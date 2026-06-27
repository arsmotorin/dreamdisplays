package com.dreamdisplays.api.protocol

import com.dreamdisplays.api.DreamDisplaysUnstableApi

/**
 * The direction a packet is allowed to travel. Registry metadata used for send-side
 * validation; never serialized.
 *
 * @since 1.8.4
 */
@DreamDisplaysUnstableApi
enum class PacketDirection {
    /** Sent by the client, handled on the server. */
    CLIENT_TO_SERVER,

    /** Sent by the server, handled on the client. */
    SERVER_TO_CLIENT,

    /** Valid in both directions. */
    BIDIRECTIONAL,
}
