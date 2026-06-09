package com.dreamdisplays.client.core

interface ClientApplication {
    val context: ClientContext

    fun registerModule(module: ClientModule)
    fun start()
    fun stop()
    fun emit(event: ClientLifecycleEvent)
    fun onEvent(listener: (ClientLifecycleEvent) -> Unit): AutoCloseable
}
