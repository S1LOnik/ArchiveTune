/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import kotlin.math.PI
import kotlin.math.cos

internal fun hannWindow(size: Int): FloatArray =
    FloatArray(size) { i ->
        0.5f * (1f - cos(2.0 * PI * i / size).toFloat())
    }

internal fun robustMean(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val from = sorted.size / 4
    val to = sorted.size - sorted.size / 4
    if (to <= from) return sorted[sorted.size / 2]
    var sum = 0f
    for (i in from until to) sum += sorted[i]
    return sum / (to - from)
}
