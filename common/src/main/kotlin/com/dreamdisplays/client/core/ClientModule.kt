package com.dreamdisplays.client.core

interface ClientModule {
    val id: String
    val dependencies: List<String> get() = emptyList()

    fun install(context: ClientContext)
    fun onEvent(event: ClientLifecycleEvent) {}
}
