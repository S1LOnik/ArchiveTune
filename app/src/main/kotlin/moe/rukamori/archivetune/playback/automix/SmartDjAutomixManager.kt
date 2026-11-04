/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.spotify.Spotify
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

data class TrackRhythm(
    val bpm: Float,
    val firstDownbeatMs: Long = 0L,
    val timeSignature: Int = 4,
)

data class AutomixGains(
    val outgoingGain: Float,
    val incomingGain: Float,
    val outgoingBassCut: Boolean,
    val incomingBassCut: Boolean,
)

data class PlaybackWarpGains(
    val outgoingSpeed: Float,
    val incomingSpeed: Float,
)

@Singleton
class SmartDjAutomixManager
    @Inject
    constructor() {
        private val rhythmCache = ConcurrentHashMap<String, TrackRhythm>()
        private val silenceBoundaryCache = ConcurrentHashMap<String, Pair<Long, Long>>()

        companion object {
            private const val TAG = "SmartDjAutomix"
            const val DEFAULT_BPM = 120.0f
            const val DEFAULT_MUSIC_ONSET_MS = 200L
            const val DEFAULT_TRAILING_SILENCE_MS = 1200L
            const val MIN_AUTOMIX_DURATION_MS = 2500L
            const val MAX_AUTOMIX_DURATION_MS = 16000L
            const val MAX_TEMPO_WARP_RATIO_DELTA = 0.15f
        }

        fun getMusicOnsetMs(mediaId: String): Long {
            val cached = silenceBoundaryCache[mediaId]
            return cached?.first ?: DEFAULT_MUSIC_ONSET_MS
        }

        fun getAudibleEndMs(mediaId: String, durationMs: Long): Long {
            if (durationMs <= 0L) return durationMs
            val cached = silenceBoundaryCache[mediaId]
            val trailingSilence = cached?.second ?: DEFAULT_TRAILING_SILENCE_MS
            return (durationMs - trailingSilence).coerceAtLeast(durationMs / 2)
        }

        fun recordAudioBoundaries(mediaId: String, onsetMs: Long, trailingSilenceMs: Long) {
            silenceBoundaryCache[mediaId] = Pair(
                onsetMs.coerceAtLeast(0L),
                trailingSilenceMs.coerceAtLeast(0L),
            )
        }

        suspend fun resolveRhythm(
            mediaId: String,
            title: String? = null,
            artists: String? = null,
        ): TrackRhythm =
            withContext(Dispatchers.IO) {
                rhythmCache[mediaId]?.let { return@withContext it }

                if (mediaId.startsWith("spotify:") || mediaId.startsWith("spotify//")) {
                    val cleanId = mediaId.removePrefix("spotify:track:").removePrefix("spotify://")
                    val features = runCatching { Spotify.audioFeatures(cleanId).getOrNull() }.getOrNull()
                    if (features != null && features.tempo > 40f && features.tempo < 240f) {
                        val rhythm = TrackRhythm(
                            bpm = features.tempo,
                            timeSignature = features.timeSignature.takeIf { it > 0 } ?: 4,
                        )
                        rhythmCache[mediaId] = rhythm
                        Timber.tag(TAG).d("Resolved Spotify rhythm for %s: %s BPM", mediaId, features.tempo)
                        return@withContext rhythm
                    }
                }

                // Deterministic rhythm hash fallback centered around musical tempi (80-140 BPM)
                val hash = abs((mediaId + (title.orEmpty()) + (artists.orEmpty())).hashCode())
                val estimatedBpm = 85.0f + (hash % 55) // 85 to 140 BPM
                val fallbackRhythm = TrackRhythm(bpm = estimatedBpm, timeSignature = 4)
                rhythmCache[mediaId] = fallbackRhythm
                fallbackRhythm
            }

        fun alignDurationToPhrase(bpm: Float, targetDurationMs: Long): Long {
            val safeBpm = if (bpm in 40f..240f) bpm else DEFAULT_BPM
            val beatMs = (60_000.0f / safeBpm)
            val barMs = beatMs * 4f // 4 beats per bar

            // Candidate phrase lengths: 2 bars (8 beats), 4 bars (16 beats), or 8 bars (32 beats)
            val candidates = listOf(
                (barMs * 2f).roundToLong(),
                (barMs * 4f).roundToLong(),
                (barMs * 8f).roundToLong(),
            ).filter { it in MIN_AUTOMIX_DURATION_MS..MAX_AUTOMIX_DURATION_MS }

            if (candidates.isEmpty()) {
                return targetDurationMs.coerceIn(MIN_AUTOMIX_DURATION_MS, MAX_AUTOMIX_DURATION_MS)
            }

            return candidates.minByOrNull { abs(it - targetDurationMs) } ?: targetDurationMs
        }

        fun calculateDownbeatSyncSeek(
            incomingOnsetMs: Long,
            outgoingPositionMs: Long,
            outgoingRhythm: TrackRhythm,
            incomingRhythm: TrackRhythm,
        ): Long {
            val safeOutgoingBpm = if (outgoingRhythm.bpm in 40f..240f) outgoingRhythm.bpm else DEFAULT_BPM
            val beatMs = 60_000.0f / safeOutgoingBpm
            val outgoingPhaseMs = ((outgoingPositionMs - outgoingRhythm.firstDownbeatMs) % beatMs).coerceAtLeast(0f)

            // Offset incoming track start so the incoming downbeat lands on outgoing beat grid
            val alignedStart = (incomingOnsetMs + outgoingPhaseMs).toLong()
            return alignedStart.coerceAtLeast(incomingOnsetMs)
        }

        fun calculateTempoWarpParameters(
            progress: Float,
            outgoingBpm: Float,
            incomingBpm: Float,
        ): PlaybackWarpGains {
            if (outgoingBpm !in 40f..240f || incomingBpm !in 40f..240f) {
                return PlaybackWarpGains(1.0f, 1.0f)
            }

            val ratio = incomingBpm / outgoingBpm
            if (abs(ratio - 1.0f) > MAX_TEMPO_WARP_RATIO_DELTA) {
                return PlaybackWarpGains(1.0f, 1.0f)
            }

            val clampedProgress = progress.coerceIn(0f, 1f)
            // Outgoing transitions smoothly from 1.0 towards target ratio
            val outgoingSpeed = 1.0f + (ratio - 1.0f) * clampedProgress * 0.5f
            // Incoming transitions from initial warped speed towards 1.0
            val initialIncomingSpeed = 1.0f / ratio
            val incomingSpeed = initialIncomingSpeed + (1.0f - initialIncomingSpeed) * clampedProgress

            return PlaybackWarpGains(
                outgoingSpeed = outgoingSpeed.coerceIn(0.85f, 1.15f),
                incomingSpeed = incomingSpeed.coerceIn(0.85f, 1.15f),
            )
        }

        fun calculateAutomixGains(progress: Float): AutomixGains {
            val p = progress.coerceIn(0f, 1f)

            // DJ S-curve: Outgoing volume stays energetic then drops quickly;
            // Incoming rises smoothly with an S-curve profile
            val outgoingGain = cos(p.toDouble() * (PI / 2.0).toDouble().times(1.2).coerceAtMost(PI / 2.0)).toFloat()
            val incomingGain = sin(p.toDouble() * (PI / 2.0).toDouble().times(0.85)).toFloat()

            // Bass Swap crossover at phrase midpoint (p = 0.5)
            // Outgoing track retains full bass until p >= 0.5f, where bass is swapped to incoming track
            val outgoingBassCut = p >= 0.5f
            val incomingBassCut = p < 0.5f

            return AutomixGains(
                outgoingGain = outgoingGain.coerceIn(0f, 1.2f),
                incomingGain = incomingGain.coerceIn(0f, 1.2f),
                outgoingBassCut = outgoingBassCut,
                incomingBassCut = incomingBassCut,
            )
        }
    }
