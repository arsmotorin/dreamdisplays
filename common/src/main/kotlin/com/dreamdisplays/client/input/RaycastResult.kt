package com.dreamdisplays.client.input

import com.dreamdisplays.api.DisplayId

sealed interface RaycastResult {
    data object Miss : RaycastResult

    data class Hit(
        val displayId: DisplayId,
        val hitX: Double,
        val hitY: Double,
        val hitZ: Double,
        val distanceSq: Double,
    ) : RaycastResult
}
