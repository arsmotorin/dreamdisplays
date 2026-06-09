package com.dreamdisplays.client.input

interface InputHandler {
    /** @return true if the action was consumed and should not propagate */
    fun handle(action: InputAction): Boolean

    val priority: Int get() = 0
}
