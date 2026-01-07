package com.dreamdisplays.net.common.helpers

import com.dreamdisplays.ModInitializer
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.*
import net.minecraft.resources.Identifier.fromNamespaceAndPath

internal fun <T : CustomPacketPayload> createType(path: String): Type<T> {
    return Type(
        fromNamespaceAndPath(ModInitializer.MOD_ID, path)
    )
}
