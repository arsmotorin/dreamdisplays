package com.dreamdisplays.client.popout

data class WindowConfig(
    val initialWidth: Int = 640,
    val initialHeight: Int = 360,
    val title: String = "DreamDisplays",
    val alwaysOnTop: Boolean = false,
    val backend: WindowBackend = WindowBackend.detectDefault(),
    val resizable: Boolean = true,
)
