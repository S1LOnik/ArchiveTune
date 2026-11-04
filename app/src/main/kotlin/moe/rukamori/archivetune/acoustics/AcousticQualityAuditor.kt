/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.acoustics

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

enum class AcousticVerdict {
    TRUE_LOSSLESS,
    UPSCALED_LOSSY,
    SUSPECTED_FAKE,
}

data class AcousticAuditReport(
    val verdict: AcousticVerdict,
    val cutoffHz: Float,
    val rollOffHz: Float,
    val nyquistHz: Float,
    val sampleRate: Int,
    val highBandEnergyRatio: Double,
    val isTrueLossless: Boolean,
)

object AcousticQualityAuditor {
    private const val TAG = "AcousticAuditor"
    private const val FFT_SIZE = 4096
    private const val TARGET_SECONDS = 30
    private const val ACTIVE_THRESHOLD_DB = -70.0f
    private const val ROLLOFF_ENERGY_PERCENT = 0.99

    /**
     * Audits a media file (FLAC, OGG, M4A, etc.) for authentic lossless quality.
     */
    suspend fun audit(file: File): AcousticAuditReport? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 1024) return@withContext null
        val pcm = decodeSamples(file = file, context = null, uri = null) ?: return@withContext null
        analyzePcm(pcm)
    }

    suspend fun audit(context: Context, uri: Uri): AcousticAuditReport? = withContext(Dispatchers.IO) {
        val pcm = decodeSamples(file = null, context = context, uri = uri) ?: return@withContext null
        analyzePcm(pcm)
    }

    internal class DecodedPcm(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    internal fun analyzePcm(pcm: DecodedPcm): AcousticAuditReport {
        val samples = pcm.samples
        val sampleRate = pcm.sampleRate
        val step = FFT_SIZE / 2
        val numFrames = (samples.size - FFT_SIZE) / step
        if (numFrames <= 0) {
            val nyquist = sampleRate / 2.0f
            return AcousticAuditReport(
                verdict = AcousticVerdict.SUSPECTED_FAKE,
                cutoffHz = 0f,
                rollOffHz = 0f,
                nyquistHz = nyquist,
                sampleRate = sampleRate,
                highBandEnergyRatio = 0.0,
                isTrueLossless = false,
            )
        }

        val numBins = FFT_SIZE / 2
        val nyquistHz = sampleRate / 2.0f
        val binHz = nyquistHz / numBins

        val hann = FloatArray(FFT_SIZE) { i ->
            (0.5 * (1.0 - cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
        }

        val sumMagnitudes = FloatArray(numBins)
        val sumEnergy = DoubleArray(numBins)

        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)

        for (frame in 0 until numFrames) {
            val offset = frame * step
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * hann[i]
                im[i] = 0f
            }
            fft(re, im)
            for (bin in 0 until numBins) {
                val mag = kotlin.math.sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                sumMagnitudes[bin] += mag
                sumEnergy[bin] += (mag * mag).toDouble()
            }
        }

        var maxMag = 1e-8f
        for (bin in 0 until numBins) {
            val avgMag = sumMagnitudes[bin] / numFrames
            if (avgMag > maxMag) maxMag = avgMag
        }
        val maxDb = 20.0f * log10(maxMag)
        val dynamicThresholdDb = maxDb - 42.0f

        var highestActiveBin = -1
        for (bin in 0 until numBins) {
            val avgMag = sumMagnitudes[bin] / numFrames
            val avgDb = 20.0f * log10(avgMag + 1e-8f)
            if (avgDb > dynamicThresholdDb) {
                highestActiveBin = bin
            }
        }

        val cutoffHz = if (highestActiveBin != -1) highestActiveBin * binHz else 0f

        val totalEnergy = sumEnergy.sum()
        var accumulatedEnergy = 0.0
        var rollOffBin = 0
        val targetEnergy = totalEnergy * ROLLOFF_ENERGY_PERCENT

        for (bin in 0 until numBins) {
            accumulatedEnergy += sumEnergy[bin]
            if (accumulatedEnergy >= targetEnergy) {
                rollOffBin = bin
                break
            }
        }
        val rollOffHz = rollOffBin * binHz

        val k1k = (1_000f / binHz).toInt().coerceIn(0, numBins - 1)
        val k15k = (15_000f / binHz).toInt().coerceIn(0, numBins - 1)
        val k20_5k = (20_500f / binHz).toInt().coerceIn(0, numBins - 1)
        val k22k = (22_050f / binHz).toInt().coerceIn(0, numBins - 1)

        var midBandEnergy = 0.0
        for (bin in k1k..k15k) {
            midBandEnergy += sumEnergy[bin]
        }

        var highBandEnergy = 0.0
        for (bin in k20_5k..k22k) {
            highBandEnergy += sumEnergy[bin]
        }

        val highBandRatio = if (midBandEnergy > 0.0) highBandEnergy / midBandEnergy else 0.0

        val verdict = when {
            cutoffHz >= 20_000f && (highBandRatio > 1e-5 || rollOffHz >= 19_500f) -> AcousticVerdict.TRUE_LOSSLESS
            cutoffHz >= 18_000f -> AcousticVerdict.UPSCALED_LOSSY
            else -> AcousticVerdict.SUSPECTED_FAKE
        }

        Timber.tag(TAG).i(
            "Spectral Audit result: verdict=%s cutoff=%.1f Hz rollOff=%.1f Hz ratio=%.2e",
            verdict.name,
            cutoffHz,
            rollOffHz,
            highBandRatio,
        )

        return AcousticAuditReport(
            verdict = verdict,
            cutoffHz = cutoffHz,
            rollOffHz = rollOffHz,
            nyquistHz = nyquistHz,
            sampleRate = sampleRate,
            highBandEnergyRatio = highBandRatio,
            isTrueLossless = verdict == AcousticVerdict.TRUE_LOSSLESS,
        )
    }

    private suspend fun decodeSamples(
        file: File?,
        context: Context?,
        uri: Uri?,
    ): DecodedPcm? {
        val extractor = MediaExtractor()
        val opened = runCatching {
            when {
                file != null -> extractor.setDataSource(file.absolutePath)
                context != null && uri != null -> extractor.setDataSource(context, uri, null)
                else -> false
            }
        }.isSuccess
        if (!opened) {
            runCatching { extractor.release() }
            return null
        }

        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                break
            }
        }
        if (trackIndex == -1) {
            runCatching { extractor.release() }
            return null
        }

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
            runCatching { extractor.release() }
            return null
        }
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            44_100
        }
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            2
        }
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }

        val targetSamples = sampleRate * TARGET_SECONDS
        val sampleData = FloatArray(targetSamples)

        val seekUs = if (durationUs > 0L) (durationUs / 3L).coerceAtLeast(0L) else 0L
        runCatching { extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC) }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (_: Exception) {
            runCatching { extractor.release() }
            return null
        }

        var decodedSamples = 0
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val deadlineMs = android.os.SystemClock.elapsedRealtime() + 15_000L

            while (!isOutputEOS && decodedSamples < targetSamples) {
                currentCoroutineContext().ensureActive()
                if (android.os.SystemClock.elapsedRealtime() > deadlineMs) break

                if (!isInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEOS = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.asShortBuffer()
                        val chunkSamples = shortBuffer.remaining()
                        var i = 0
                        while (i < chunkSamples && decodedSamples < targetSamples) {
                            var sum = 0f
                            var count = 0
                            for (c in 0 until channelCount) {
                                if (shortBuffer.hasRemaining()) {
                                    sum += shortBuffer.get().toFloat() / 32768.0f
                                    count++
                                }
                            }
                            if (count > 0) {
                                sampleData[decodedSamples] = sum / count
                                decodedSamples++
                            }
                            i += channelCount
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error decoding PCM samples")
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }

        if (decodedSamples < FFT_SIZE * 2) return null
        return DecodedPcm(sampleData.copyOf(decodedSamples), sampleRate)
    }

    internal fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        if (n == 0) return
        var i = 0
        var j = 0
        while (i < n) {
            if (i < j) {
                val tempRe = re[i]
                re[i] = re[j]
                re[j] = tempRe
                val tempIm = im[i]
                im[i] = im[j]
                im[j] = tempIm
            }
            var m = n shr 1
            while (m >= 2 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
            i++
        }

        var len = 2
        while (len <= n) {
            val ang = 2.0 * Math.PI / len
            val wpr = cos(ang).toFloat()
            val wpi = -sin(ang).toFloat()
            var iVal = 0
            while (iVal < n) {
                var wr = 1.0f
                var wi = 0.0f
                val halfLen = len / 2
                for (k in 0 until halfLen) {
                    val idx1 = iVal + k
                    val idx2 = idx1 + halfLen
                    val tr = re[idx2] * wr - im[idx2] * wi
                    val ti = re[idx2] * wi + im[idx2] * wr
                    re[idx2] = re[idx1] - tr
                    im[idx2] = im[idx1] - ti
                    re[idx1] += tr
                    im[idx1] += ti
                    val nextWr = wr * wpr - wi * wpi
                    wi = wr * wpi + wi * wpr
                    wr = nextWr
                }
                iVal += len
            }
            len = len shl 1
        }
    }
}
