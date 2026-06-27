package com.dreamdisplays.core.protocol

/**
 * Marker for every protocol-v2 packet. The sealed hierarchy exists for exhaustive `when` dispatch
 * in handlers; it is never serialized polymorphically — the wire envelope carries an explicit
 * type id resolved through [PacketRegistry].
 */
sealed interface DreamPacket
