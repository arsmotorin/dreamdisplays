package com.dreamdisplays.client.popout

enum class WindowBackend {
    GLFW,
    AWT;

    companion object {
        fun detectDefault(): WindowBackend {
            val os = System.getProperty("os.name", "").lowercase()
            return if ("mac" in os) GLFW else AWT
        }
    }
}
