package com.dreamdisplays.client.core

sealed interface ClientLifecycleEvent {
    data object Initializing : ClientLifecycleEvent
    data object Ready : ClientLifecycleEvent
    data class ServerJoined(val serverId: String) : ClientLifecycleEvent
    data class ServerLeft(val serverId: String) : ClientLifecycleEvent
    data object ShuttingDown : ClientLifecycleEvent
    data class LevelLoaded(val levelId: String) : ClientLifecycleEvent
    data class LevelUnloaded(val levelId: String) : ClientLifecycleEvent
    data class Tick(val tickCount: Long) : ClientLifecycleEvent
    data class FocusChanged(val focused: Boolean) : ClientLifecycleEvent
}
