package com.dreamdisplays.client.overlay

fun interface CrosshairPolicy {
    fun shouldSuppressCrosshair(): Boolean

    companion object {
        val ALWAYS_SHOW: CrosshairPolicy = CrosshairPolicy { false }
        val ALWAYS_HIDE: CrosshairPolicy = CrosshairPolicy { true }
    }
}
