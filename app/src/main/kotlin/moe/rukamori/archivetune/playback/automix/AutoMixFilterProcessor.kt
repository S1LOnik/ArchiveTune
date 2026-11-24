/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import android.util.Log
import androidx.annotation.Keep
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Keep
@UnstableApi
class AutoMixFilterProcessor : BaseAudioProcessor() {
    @Volatile
    private var targetLowPassHz = OPEN_HZ

    @Volatile
    private var targetHighPassHz = OFF_HZ

    private var channelCount = 0
    private var sampleRate = 0
    private var isFloat = false
    private var nativeHandle = 0L

    private var shortScratch = ShortArray(0)
    private var floatScratch = FloatArray(0)

    fun setCutoffs(
        lowPassHz: Double,
        highPassHz: Double,
    ) {
        val low = lowPassHz.toFloat().coerceIn(MIN_HZ, OPEN_HZ)
        val high = highPassHz.toFloat().coerceIn(OFF_HZ, MAX_HIGH_PASS_HZ)
        targetLowPassHz = low
        targetHighPassHz = high
        val h = nativeHandle
        if (h != 0L) {
            nativeSetFilterCutoffs(h, low, high)
        }
    }

    fun open() = setCutoffs(OPEN_HZ.toDouble(), OFF_HZ.toDouble())

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (moe.rukamori.archivetune.playback.dsp.BitPerfectRuntime.chainBypassActive) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount < 1 ||
            (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
        ) {
            Log.w(
                TAG,
                "automix filter inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit/float PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        releaseNative()
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        nativeHandle = if (nativeAvailable) {
            nativeCreateFilter(channelCount, sampleRate)
        } else {
            0L
        }
        if (nativeHandle != 0L) {
            nativeSetFilterCutoffs(nativeHandle, targetLowPassHz, targetHighPassHz)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        val h = nativeHandle
        if (h != 0L) {
            nativeFlushFilter(h)
        }
    }

    override fun onReset() {
        targetLowPassHz = OPEN_HZ
        targetHighPassHz = OFF_HZ
        releaseNative()
        shortScratch = ShortArray(0)
        floatScratch = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (isFloat) FLOAT_BYTES else SHORT_BYTES
        val bytesPerFrame = bytesPerSample * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val h = nativeHandle
        if (h == 0L || nativeIsFilterParked(h)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val sampleCount = frameCount * channelCount
        if (isFloat) {
            if (floatScratch.size < sampleCount) {
                floatScratch = FloatArray(sampleCount)
            }
            inputBuffer.asFloatBuffer().get(floatScratch, 0, sampleCount)
            inputBuffer.position(inputBuffer.position() + sampleCount * FLOAT_BYTES)
            nativeProcessFilterFloat(h, floatScratch, sampleCount)
            outputBuffer.asFloatBuffer().put(floatScratch, 0, sampleCount)
            outputBuffer.position(sampleCount * FLOAT_BYTES)
        } else {
            if (shortScratch.size < sampleCount) {
                shortScratch = ShortArray(sampleCount)
            }
            inputBuffer.asShortBuffer().get(shortScratch, 0, sampleCount)
            inputBuffer.position(inputBuffer.position() + sampleCount * SHORT_BYTES)
            nativeProcessFilterShort(h, shortScratch, sampleCount)
            outputBuffer.asShortBuffer().put(shortScratch, 0, sampleCount)
            outputBuffer.position(sampleCount * SHORT_BYTES)
        }

        outputBuffer.flip()
    }

    private fun releaseNative() {
        val h = nativeHandle
        nativeHandle = 0L
        if (h != 0L) {
            nativeReleaseFilter(h)
        }
    }

    private external fun nativeCreateFilter(
        channels: Int,
        sampleRate: Int,
    ): Long

    private external fun nativeSetFilterCutoffs(
        handle: Long,
        lowPassHz: Float,
        highPassHz: Float,
    )

    private external fun nativeFlushFilter(handle: Long)

    private external fun nativeIsFilterParked(handle: Long): Boolean

    private external fun nativeProcessFilterShort(
        handle: Long,
        samples: ShortArray,
        count: Int,
    ): Boolean

    private external fun nativeProcessFilterFloat(
        handle: Long,
        samples: FloatArray,
        count: Int,
    ): Boolean

    private external fun nativeReleaseFilter(handle: Long)

    companion object {
        private const val TAG = "AutoMixFilter"

        private val nativeAvailable: Boolean =
            runCatching {
                System.loadLibrary("archivetune_automix")
            }.isSuccess

        const val OPEN_HZ = 20_000f
        const val OFF_HZ = 10f
        const val MIN_HZ = 20f
        const val MAX_HIGH_PASS_HZ = 7_000f

        private const val SHORT_BYTES = 2
        private const val FLOAT_BYTES = 4
    }
}
