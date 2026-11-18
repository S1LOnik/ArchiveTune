/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AutoMixSpectrumTest {
    @Test
    fun spectrumMatchesKotlinAcrossConsecutiveFrames() {
        for (size in listOf(512, 1024)) {
            val reference = Fft(size)
            val random = Random(size)
            val frames = listOf(
                FloatArray(size),
                FloatArray(size) { if (it == 7) 1f else 0f },
                FloatArray(size) { 1f },
                FloatArray(size) { if (it % 2 == 0) 1f else -1f },
                FloatArray(size) { sin(2.0 * PI * it * 19 / size).toFloat() },
                FloatArray(size) { random.nextFloat() * 2f - 1f },
            )
            val expected = FloatArray(size / 2)
            val actual = FloatArray(size / 2)
            AutoMixSpectrum(size).use { spectrum ->
                for (frame in frames) {
                    reference.magnitudeSpectrum(frame.copyOf(), FloatArray(size), expected)
                    spectrum.magnitudeSpectrum(frame.copyOf(), actual)
                    for (bin in expected.indices) {
                        assertEquals("FFT $size, bin $bin", expected[bin], actual[bin], 0.0002f)
                    }
                }
            }
        }
    }
}
