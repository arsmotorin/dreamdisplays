package com.dreamdisplays.client.input

interface KeyBindingRegistry {
    fun register(binding: KeyBinding): KeyBinding
    fun getAll(): List<KeyBinding>
    fun findById(id: String): KeyBinding?
    fun reset()
}
