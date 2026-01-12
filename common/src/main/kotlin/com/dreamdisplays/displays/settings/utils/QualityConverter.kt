package com.dreamdisplays.displays.settings.utils

import com.dreamdisplays.displays.DisplayScreen
import org.jspecify.annotations.NullMarked
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converting between quality resolution indices and quality strings.
 */
@NullMarked
object QualityConverter {

    fun toQuality(resolution: Int, display: DisplayScreen?): String {
        var list = emptyList<Int>()
        if (display != null) {
            list = display.getQualityList()
        }

        if (list.isEmpty()) return "144"

        val i = max(min(resolution, list.size - 1), 0)
        return list[i].toString()
    }

    fun fromQuality(quality: String, display: DisplayScreen?): Int {
        if (display == null) return 0

        val list: List<Int> = display.getQualityList()
        if (list.isEmpty()) return 0

        val cQ = quality.replace("p", "").toInt()

        var closest: Int = list.first()
        var minDiff = abs(cQ - closest)
        for (q in list) {
            val diff = abs(q - cQ)
            if (diff < minDiff) {
                minDiff = diff
                closest = q
            }
        }

        return list.indexOf(closest)
    }
}