/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import androidx.annotation.Keep

/** JNI bridge to the Rust AutoMix tempo estimation engine. */
@Keep
internal object AutoMixTempo {
    init {
        System.loadLibrary("archivetune_automix")
    }

    data class Estimate(
        val bpm: Double,
        val intervalMs: Double,
        val confidence: Double,
        val phaseMs: Double,
    )

    fun estimate(flux: FloatArray, fluxRate: Double): Estimate? {
        if (flux.size < 64 || !fluxRate.isFinite() || fluxRate <= 0.0) return null
        val raw = nativeEstimateTempo(flux, fluxRate) ?: return null
        if (raw.size < 4) return null
        return Estimate(
            bpm = raw[0],
            intervalMs = raw[1],
            confidence = raw[2],
            phaseMs = raw[3],
        )
    }

    private external fun nativeEstimateTempo(flux: FloatArray, fluxRate: Double): DoubleArray?
}
