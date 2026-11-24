/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class AutoMixCurveAnalysisDto(
    val audibleStartMs: Long = 0L,
    val introEndMs: Long = 0L,
    val outroStartMs: Long = 0L,
    val contentEndMs: Long = 0L,
    val finalFadeOnsetMs: Long = 0L,
    val mixInCandidatesMs: List<Long> = emptyList(),
    val mixOutCandidatesMs: List<Long> = emptyList(),
    val normalizedVocal: List<Float> = emptyList(),
)

/** Native Rust DSP, FFT curve analysis, EQ, dynamics, and LUFS loudness bridge. */
@Keep
internal object AutoMixNativeDsp {
    init {
        System.loadLibrary("archivetune_automix")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun analyzeCurves(
        energy: FloatArray,
        vocal: FloatArray,
        durationMs: Long,
    ): AutoMixCurveAnalysisDto? {
        val raw = nativeAnalyzeCurvesJson(energy, vocal, durationMs) ?: return null
        return runCatching { json.decodeFromString<AutoMixCurveAnalysisDto>(raw) }.getOrNull()
    }

    fun hannWindow(size: Int): FloatArray =
        if (size <= 0) FloatArray(0) else nativeHannWindow(size)

    fun robustMean(values: FloatArray): Float =
        if (values.isEmpty()) 0f else nativeRobustMean(values)

    fun equalPowerGains(progress: Float): Pair<Float, Float> {
        val pair = nativeEqualPowerGains(progress)
        return if (pair.size >= 2) pair[0] to pair[1] else (1f - progress) to progress
    }

    fun sCurveGains(progress: Float): Pair<Float, Float> {
        val pair = nativeSCurveGains(progress)
        return if (pair.size >= 2) pair[0] to pair[1] else (1f - progress) to progress
    }

    fun resampleCubicMono(
        input: FloatArray,
        srcRate: Int,
        dstRate: Int,
    ): FloatArray =
        if (input.isEmpty() || srcRate <= 0 || dstRate <= 0) {
            FloatArray(0)
        } else {
            nativeResampleCubicMono(input, srcRate, dstRate)
        }

    fun estimateLufs(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
    ): Double =
        if (samples.isEmpty() || sampleRate <= 0 || channels <= 0) {
            -70.0
        } else {
            nativeEstimateLufs(samples, sampleRate, channels)
        }

    fun replayGainDb(
        measuredLufs: Double,
        targetLufs: Double = -14.0,
    ): Double = nativeReplayGainDb(measuredLufs, targetLufs)

    fun waveformPeaksShort(
        samples: ShortArray,
        channels: Int,
        buckets: Int,
    ): FloatArray =
        if (samples.isEmpty() || channels <= 0 || buckets <= 0) {
            FloatArray(0)
        } else {
            nativeWaveformPeaksShort(samples, channels, buckets)
        }

    fun waveformPeaksFloat(
        samples: FloatArray,
        channels: Int,
        buckets: Int,
    ): FloatArray =
        if (samples.isEmpty() || channels <= 0 || buckets <= 0) {
            FloatArray(0)
        } else {
            nativeWaveformPeaksFloat(samples, channels, buckets)
        }

    fun threeBandEnergy(
        mono: FloatArray,
        sampleRate: Int,
    ): FloatArray = nativeThreeBandEnergy(mono, sampleRate)

    fun truePeak4x(samples: FloatArray): Pair<Float, Float> {
        val res = nativeTruePeak4x(samples)
        return if (res.size >= 2) res[0] to res[1] else 0f to -120f
    }

    fun pcmS24LeToFloat(bytes: ByteArray): FloatArray = nativePcmS24LeToFloat(bytes)

    fun quantizeFloatToShortTpdf(
        samples: FloatArray,
        seed: Long = 1L,
    ): ShortArray = nativeQuantizeFloatToShortTpdf(samples, seed)

    fun stretchWsola(
        mono: FloatArray,
        sampleRate: Int,
        tempo: Float,
    ): FloatArray = nativeStretchWsola(mono, sampleRate, tempo)

    class NativeParametricEq(
        sampleRate: Int,
        channels: Int,
        bandCount: Int = 10,
    ) : AutoCloseable {
        private var handle: Long = nativeCreateEq(sampleRate, channels, bandCount)

        fun isValid(): Boolean = handle != 0L

        fun setPreampDb(preampDb: Float) {
            if (handle != 0L) nativeSetEqPreamp(handle, preampDb)
        }

        fun setBand(
            index: Int,
            kind: Int,
            freqHz: Double,
            q: Double,
            gainDb: Double,
            enabled: Boolean,
        ): Boolean =
            handle != 0L && nativeSetEqBand(handle, index, kind, freqHz, q, gainDb, enabled)

        fun processFloatInPlace(samples: FloatArray): Boolean =
            handle != 0L && nativeProcessEqFloat(handle, samples)

        override fun close() {
            val h = handle
            handle = 0L
            if (h != 0L) nativeReleaseEq(h)
        }
    }

    class NativeDynamicsCompressor(
        sampleRate: Int,
        thresholdDb: Float = -12f,
        ratio: Float = 4f,
        kneeDb: Float = 4f,
        attackMs: Float = 2f,
        releaseMs: Float = 80f,
        makeupDb: Float = 0f,
    ) : AutoCloseable {
        private var handle: Long =
            nativeCreateCompressor(sampleRate, thresholdDb, ratio, kneeDb, attackMs, releaseMs, makeupDb)

        fun processFloatInPlace(
            samples: FloatArray,
            channels: Int,
        ): Boolean = handle != 0L && nativeProcessCompressorFloat(handle, samples, channels)

        override fun close() {
            val h = handle
            handle = 0L
            if (h != 0L) nativeReleaseCompressor(h)
        }
    }

    @JvmStatic
    private external fun nativeHannWindow(size: Int): FloatArray

    @JvmStatic
    private external fun nativeRobustMean(values: FloatArray): Float

    @JvmStatic
    private external fun nativeAnalyzeCurvesJson(
        energy: FloatArray,
        vocal: FloatArray,
        durationMs: Long,
    ): String?

    @JvmStatic
    private external fun nativeEqualPowerGains(progress: Float): FloatArray

    @JvmStatic
    private external fun nativeSCurveGains(progress: Float): FloatArray

    @JvmStatic
    private external fun nativeResampleCubicMono(
        input: FloatArray,
        srcRate: Int,
        dstRate: Int,
    ): FloatArray

    @JvmStatic
    private external fun nativeEstimateLufs(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
    ): Double

    @JvmStatic
    private external fun nativeReplayGainDb(
        measuredLufs: Double,
        targetLufs: Double,
    ): Double

    @JvmStatic
    private external fun nativeWaveformPeaksShort(
        samples: ShortArray,
        channels: Int,
        buckets: Int,
    ): FloatArray

    @JvmStatic
    private external fun nativeWaveformPeaksFloat(
        samples: FloatArray,
        channels: Int,
        buckets: Int,
    ): FloatArray

    @JvmStatic
    private external fun nativeThreeBandEnergy(
        mono: FloatArray,
        sampleRate: Int,
    ): FloatArray

    @JvmStatic
    private external fun nativeTruePeak4x(samples: FloatArray): FloatArray

    @JvmStatic
    private external fun nativePcmS24LeToFloat(bytes: ByteArray): FloatArray

    @JvmStatic
    private external fun nativeQuantizeFloatToShortTpdf(
        samples: FloatArray,
        seed: Long,
    ): ShortArray

    @JvmStatic
    private external fun nativeStretchWsola(
        mono: FloatArray,
        sampleRate: Int,
        tempo: Float,
    ): FloatArray

    @JvmStatic
    private external fun nativeCreateEq(
        sampleRate: Int,
        channels: Int,
        bands: Int,
    ): Long

    @JvmStatic
    private external fun nativeSetEqPreamp(
        handle: Long,
        preampDb: Float,
    )

    @JvmStatic
    private external fun nativeSetEqBand(
        handle: Long,
        index: Int,
        kind: Int,
        freqHz: Double,
        q: Double,
        gainDb: Double,
        enabled: Boolean,
    ): Boolean

    @JvmStatic
    private external fun nativeProcessEqFloat(
        handle: Long,
        samples: FloatArray,
    ): Boolean

    @JvmStatic
    private external fun nativeReleaseEq(handle: Long)

    @JvmStatic
    private external fun nativeCreateCompressor(
        sampleRate: Int,
        thresholdDb: Float,
        ratio: Float,
        kneeDb: Float,
        attackMs: Float,
        releaseMs: Float,
        makeupDb: Float,
    ): Long

    @JvmStatic
    private external fun nativeProcessCompressorFloat(
        handle: Long,
        samples: FloatArray,
        channels: Int,
    ): Boolean

    @JvmStatic
    private external fun nativeReleaseCompressor(handle: Long)
}
