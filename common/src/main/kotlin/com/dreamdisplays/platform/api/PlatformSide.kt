package com.dreamdisplays.platform.api

enum class PlatformSide {
    CLIENT,
    SERVER,
    BOTH;

    val isClient: Boolean get() = this == CLIENT || this == BOTH
    val isServer: Boolean get() = this == SERVER || this == BOTH
}
