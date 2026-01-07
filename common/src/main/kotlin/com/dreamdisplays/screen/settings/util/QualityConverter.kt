package com.dreamdisplays.screen.settings.util

import com.dreamdisplays.screen.DisplayScreen
import org.jspecify.annotations.NullMarked
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converting between quality resolution indices and quality strings.
 */
@NullMarked
object QualityConverter {

    fun toQuality(resolution: Int, screen: DisplayScreen?): String {
        var list = emptyList<Int>()
        if (screen != null) {
            list = screen.getQualityList()
        }

        if (list.isEmpty()) return "144"

        val i = max(min(resolution, list.size - 1), 0)
        return list[i].toString()
    }

    fun fromQuality(quality: String, screen: DisplayScreen?): Int {
        if (screen == null) return 0

        val list: List<Int> = screen.getQualityList()
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