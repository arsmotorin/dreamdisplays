package com.dreamdisplays.net.common

import com.dreamdisplays.Initializer
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

internal fun <T : CustomPacketPayload> createType(path: String): CustomPacketPayload.Type<T> {
    return CustomPacketPayload.Type(
        Identifier.fromNamespaceAndPath(Initializer.MOD_ID, path)
    )
}
