/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

/** A spectrum calculator confined to one decoder invocation. */
internal class AutoMixSpectrum(private val size: Int) : AutoCloseable {
    private var handle = if (nativeAvailable) {
        try {
            nativeCreate(size)
        } catch (_: UnsatisfiedLinkError) {
            0L
        }
    } else {
        0L
    }
    private val fallback by lazy(LazyThreadSafetyMode.NONE) { Fft(size) }
    private val imaginary by lazy(LazyThreadSafetyMode.NONE) { FloatArray(size) }

    fun magnitudeSpectrum(real: FloatArray, magnitudes: FloatArray) {
        if (handle != 0L && nativeCalculate(handle, real, magnitudes)) return

        imaginary.fill(0f)
        fallback.magnitudeSpectrum(real, imaginary, magnitudes)
    }

    override fun close() {
        val nativeHandle = handle
        handle = 0
        if (nativeHandle != 0L) nativeRelease(nativeHandle)
    }

    private external fun nativeCreate(size: Int): Long
    private external fun nativeCalculate(handle: Long, input: FloatArray, magnitudes: FloatArray): Boolean
    private external fun nativeRelease(handle: Long)

    private companion object {
        val nativeAvailable = try {
            System.loadLibrary("archivetune_automix")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
}
