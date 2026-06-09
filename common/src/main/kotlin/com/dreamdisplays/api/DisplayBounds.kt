package com.dreamdisplays.api

data class DisplayBounds(
    val x: Double,
    val y: Double,
    val z: Double,
    val width: Int,
    val height: Int,
    val facing: DisplayFacing,
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    fun distanceSqTo(px: Double, py: Double, pz: Double): Double {
        val dx = x - px
        val dy = y - py
        val dz = z - pz
        return dx * dx + dy * dy + dz * dz
    }
}
