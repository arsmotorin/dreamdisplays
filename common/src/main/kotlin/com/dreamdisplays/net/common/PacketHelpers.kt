package com.dreamdisplays.net.common

import com.dreamdisplays.Initializer
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.*
import net.minecraft.resources.Identifier.fromNamespaceAndPath

internal fun <T : CustomPacketPayload> createType(path: String): Type<T> {
    return Type(
        fromNamespaceAndPath(Initializer.MOD_ID, path)
    )
}
