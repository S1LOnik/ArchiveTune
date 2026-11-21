/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoMixSpectrumDeviceTest {
    @Test
    fun packagedNativeLibraryProcessesBothAutoMixWindowSizes() {
        for (size in listOf(512, 1024)) {
            val input = FloatArray(size) { if (it == 7) 1f else 0f }
            val bins = FloatArray(size / 2)
            AutoMixSpectrum(size).use { spectrum ->
                spectrum.magnitudeSpectrum(input, bins)
                assertArrayEquals(FloatArray(size / 2) { 1f }, bins, 0.0002f)

                input.fill(1f)
                spectrum.magnitudeSpectrum(input, bins)
                assertEquals(size.toFloat(), bins[0], 0.0002f)
                assertArrayEquals(FloatArray(size / 2 - 1), bins.copyOfRange(1, bins.size), 0.0002f)
            }
        }
    }
}
