package com.dreamdisplays.client.input

sealed interface InputAction {
    data class KeyPressed(val keyCode: Int, val scanCode: Int, val modifiers: Int) : InputAction
    data class KeyReleased(val keyCode: Int, val scanCode: Int) : InputAction
    data class MouseClicked(val x: Double, val y: Double, val button: Int) : InputAction
    data class MouseReleased(val x: Double, val y: Double, val button: Int) : InputAction
    data class MouseScrolled(val x: Double, val y: Double, val deltaX: Double, val deltaY: Double) : InputAction
    data class CharTyped(val char: Char) : InputAction
}
