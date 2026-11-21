/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AutoMixSpectrumTest {
    @Test
    fun nativeSpectrumMatchesBaselineAcrossConsecutiveFrames() {
        val expectedBins = requireNotNull(javaClass.getResourceAsStream("/automix-spectrum-golden.txt"))
            .bufferedReader()
            .useLines { lines ->
                lines.filterNot { it.startsWith("#") }.flatMap { it.split(' ').asSequence() }
                    .map(String::toFloat).toList()
            }
        var expectedIndex = 0
        for (size in listOf(512, 1024)) {
            val random = Random(size)
            val frames = listOf(
                FloatArray(size),
                FloatArray(size) { if (it == 7) 1f else 0f },
                FloatArray(size) { 1f },
                FloatArray(size) { if (it % 2 == 0) 1f else -1f },
                FloatArray(size) { sin(2.0 * PI * it * 19 / size).toFloat() },
                FloatArray(size) { random.nextFloat() * 2f - 1f },
            )
            val actual = FloatArray(size / 2)
            AutoMixSpectrum(size).use { spectrum ->
                for ((frameIndex, frame) in frames.withIndex()) {
                    val input = frame.copyOf()
                    spectrum.magnitudeSpectrum(input, actual)
                    assertArrayEquals(frame, input, 0f)
                    for (bin in actual.indices) {
                        assertEquals(
                            "FFT $size, frame $frameIndex, bin $bin",
                            expectedBins[expectedIndex++],
                            actual[bin],
                            0.0002f,
                        )
                    }
                }
            }
        }
        assertEquals(expectedBins.size, expectedIndex)
    }

    @Test
    fun rejectsInvalidBuffersAndUseAfterClose() {
        assertThrows(IllegalArgumentException::class.java) { AutoMixSpectrum(256) }
        val spectrum = AutoMixSpectrum(512)
        spectrum.use {
            assertThrows(IllegalArgumentException::class.java) {
                it.magnitudeSpectrum(FloatArray(511), FloatArray(256))
            }
            assertThrows(IllegalArgumentException::class.java) {
                it.magnitudeSpectrum(FloatArray(512), FloatArray(257))
            }
        }
        spectrum.close()
        assertThrows(IllegalStateException::class.java) {
            spectrum.magnitudeSpectrum(FloatArray(512), FloatArray(256))
        }
    }

    @Test
    fun concurrentCallsKeepSharedPlanScratchIsolated() {
        AutoMixSpectrum(512).use { spectrum ->
            val workers = Executors.newFixedThreadPool(4)
            try {
                val tasks = (1..32).map { amplitude ->
                    Callable {
                        val input = FloatArray(512) { if (it == 7) amplitude.toFloat() else 0f }
                        val bins = FloatArray(256)
                        spectrum.magnitudeSpectrum(input, bins)
                        assertArrayEquals(FloatArray(256) { amplitude.toFloat() }, bins, 0.0002f)
                    }
                }
                workers.invokeAll(tasks).forEach { it.get() }
            } finally {
                workers.shutdownNow()
            }
        }
    }
}
