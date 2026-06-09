package com.dreamdisplays.client.input

interface DisplayInteractionService {
    fun raycast(): RaycastResult
    fun getCurrentTarget(): RaycastResult.Hit?
    fun emit(interaction: DisplayInteraction)
    fun on(listener: (DisplayInteraction) -> Unit): AutoCloseable
}
