/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class AutoMixTempoTest {

    // Numerical tolerance explanation:
    // Floating-point calculations between JVM HotSpot (x86_64) and Rust native code
    // use IEEE 754 32-bit floats for intermediate centered buffers and 64-bit doubles
    // for accumulations. Bounded difference <= 1e-6 accounts for compiler instruction reordering.
    private val floatTolerance = 1e-6

    @Test
    fun matchesBaselineGoldenOutputs() {
        // 1. Silence
        assertNull("silence should return null", AutoMixTempo.estimate(FloatArray(128) { 0f }, 100.0))

        // 2. Fewer than 64 samples
        assertNull("fewer than 64 samples should return null", AutoMixTempo.estimate(FloatArray(63) { 1f }, 100.0))

        // 3. Regular pulses
        val regularPulses = FloatArray(500) { if (it % 50 == 0) 1.0f else 0.0f }
        val regularResult = AutoMixTempo.estimate(regularPulses, 100.0)
        assertNotNull("regular pulses result should not be null", regularResult)
        assertEquals(120.0, regularResult!!.bpm, floatTolerance)
        assertEquals(500.0, regularResult.intervalMs, floatTolerance)
        assertEquals(1.0, regularResult.confidence, floatTolerance)
        assertEquals(0.0, regularResult.phaseMs, floatTolerance)

        // 4. Pure noise
        val noise = FloatArray(500) { Random(12345).nextFloat() }
        assertNull("pure noise should return null", AutoMixTempo.estimate(noise, 100.0))

        // 5. Phase shift
        val phaseShift = FloatArray(500) { if ((it - 15 + 500) % 50 == 0) 1.0f else 0.0f }
        val phaseResult = AutoMixTempo.estimate(phaseShift, 100.0)
        assertNotNull("phase shift result should not be null", phaseResult)
        assertEquals(120.0, phaseResult!!.bpm, floatTolerance)
        assertEquals(500.0, phaseResult.intervalMs, floatTolerance)
        assertEquals(1.0, phaseResult.confidence, floatTolerance)
        assertEquals(150.0, phaseResult.phaseMs, floatTolerance)

        // 6. Half-double tempo ambiguity
        val ambiguous = FloatArray(600) { if (it % 40 == 0) 1.0f else if (it % 20 == 0) 0.5f else 0.0f }
        val ambResult = AutoMixTempo.estimate(ambiguous, 100.0)
        assertNotNull("ambiguous result should not be null", ambResult)
        assertEquals(150.0, ambResult!!.bpm, floatTolerance)
        assertEquals(400.0, ambResult.intervalMs, floatTolerance)
        assertEquals(1.0, ambResult.confidence, floatTolerance)
        assertEquals(0.0, ambResult.phaseMs, floatTolerance)

        // 7. Long array
        val longArray = FloatArray(2500) { (if (it % 45 == 0) 1.0f else 0.05f) + (it % 7) * 0.01f }
        val longResult = AutoMixTempo.estimate(longArray, 100.0)
        assertNotNull("long array result should not be null", longResult)
        assertEquals(133.33333333333334, longResult!!.bpm, floatTolerance)
        assertEquals(449.99999999999994, longResult.intervalMs, floatTolerance)
        assertEquals(1.0, longResult.confidence, floatTolerance)
        assertEquals(0.0, longResult.phaseMs, floatTolerance)

        // 8. Noisy pulses
        val noisyPulses = FloatArray(500) { (if (it % 50 == 0) 1.0f else 0.0f) + Random(it).nextFloat() * 1.5f }
        val noisyResult = AutoMixTempo.estimate(noisyPulses, 100.0)
        assertNotNull("noisy pulses result should not be null", noisyResult)
        assertEquals(90.9090909090909, noisyResult!!.bpm, floatTolerance)
        assertEquals(660.0, noisyResult.intervalMs, floatTolerance)
        assertEquals(1.0, noisyResult.confidence, floatTolerance)
        assertEquals(860.0, noisyResult.phaseMs, floatTolerance)

        // 9. Decimation rate (fluxRate = 400.0 -> factor = 2)
        val decimation = FloatArray(800) { if (it % 80 == 0) 1.0f else 0.0f }
        val decResult = AutoMixTempo.estimate(decimation, 400.0)
        assertNotNull("decimation result should not be null", decResult)
        assertEquals(150.0, decResult!!.bpm, floatTolerance)
        assertEquals(400.0, decResult.intervalMs, floatTolerance)
        assertEquals(1.0, decResult.confidence, floatTolerance)
        assertEquals(0.0, decResult.phaseMs, floatTolerance)
    }

    @Test
    fun rejectsInvalidInputsSafelyWithoutPanic() {
        assertNull(AutoMixTempo.estimate(FloatArray(0), 100.0))
        assertNull(AutoMixTempo.estimate(FloatArray(100) { 1f }, 0.0))
        assertNull(AutoMixTempo.estimate(FloatArray(100) { 1f }, -50.0))
        assertNull(AutoMixTempo.estimate(FloatArray(100) { 1f }, Double.NaN))
        assertNull(AutoMixTempo.estimate(FloatArray(100) { 1f }, Double.POSITIVE_INFINITY))

        val withNan = FloatArray(100) { 1f }
        withNan[10] = Float.NaN
        assertNull(AutoMixTempo.estimate(withNan, 100.0))

        val withInf = FloatArray(100) { 1f }
        withInf[20] = Float.POSITIVE_INFINITY
        assertNull(AutoMixTempo.estimate(withInf, 100.0))
    }
}
