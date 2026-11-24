/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

internal fun hannWindow(size: Int): FloatArray = AutoMixNativeDsp.hannWindow(size)

internal fun robustMean(values: List<Float>): Float =
    AutoMixNativeDsp.robustMean(values.toFloatArray())
