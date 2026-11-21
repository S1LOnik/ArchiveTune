/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import androidx.annotation.Keep

/** Owns one native FFT plan and its reusable scratch buffers. */
@Keep
internal class AutoMixSpectrum(private val size: Int) : AutoCloseable {
    private var handle: Long

    init {
        require(size == 512 || size == 1024) { "Unsupported AutoMix FFT size: $size" }
        handle = nativeCreate(size)
        check(handle != 0L) { "Could not initialize the AutoMix spectrum calculator" }
    }

    @Synchronized
    fun magnitudeSpectrum(real: FloatArray, magnitudes: FloatArray) {
        check(handle != 0L) { "AutoMix spectrum calculator is closed" }
        require(real.size == size) { "Expected $size FFT samples" }
        require(magnitudes.size == size / 2) { "Expected ${size / 2} FFT bins" }
        check(nativeCalculate(handle, real, magnitudes)) { "AutoMix spectrum calculation failed" }
    }

    @Synchronized
    override fun close() {
        val nativeHandle = handle
        handle = 0
        if (nativeHandle != 0L) nativeRelease(nativeHandle)
    }

    private external fun nativeCreate(size: Int): Long
    private external fun nativeCalculate(handle: Long, input: FloatArray, magnitudes: FloatArray): Boolean
    private external fun nativeRelease(handle: Long)

    private companion object {
        init {
            System.loadLibrary("archivetune_automix")
        }
    }
}
