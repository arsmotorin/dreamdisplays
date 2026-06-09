package com.dreamdisplays.client.core

interface ClientBootstrap {
    fun bootstrap(context: ClientContext)
    fun teardown(context: ClientContext)
}
