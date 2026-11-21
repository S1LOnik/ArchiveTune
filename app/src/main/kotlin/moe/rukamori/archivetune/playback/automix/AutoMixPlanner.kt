/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import androidx.annotation.Keep

@Keep
object AutoMixPlanner {
    init {
        System.loadLibrary("archivetune_automix")
    }

    fun plan(
        current: AutoMixAnalysis?,
        next: AutoMixAnalysis?,
        currentTrack: AutoMixTrackInfo,
        nextTrack: AutoMixTrackInfo,
        positionMs: Long,
        fallbackFadeMs: Long,
        minFadeMs: Long,
        gaplessAlbum: Boolean,
    ): AutoMixPlan {
        val curScalars = current?.let {
            doubleArrayOf(
                it.durationMs.toDouble(),
                it.bpm,
                it.beatIntervalMs,
                it.beatConfidence,
                it.downbeatPhaseMs,
                it.audibleStartMs.toDouble(),
                it.introEndMs.toDouble(),
                it.outroStartMs.toDouble(),
                it.contentEndMs.toDouble(),
                it.finalFadeOnsetMs.toDouble(),
            )
        }
        val curMixIn = current?.mixInCandidatesMs?.toLongArray()
        val curMixOut = current?.mixOutCandidatesMs?.toLongArray()
        val curEnergy = current?.energyCurve?.toFloatArray()
        val curVocal = current?.vocalActivity?.toFloatArray()

        val nxtScalars = next?.let {
            doubleArrayOf(
                it.durationMs.toDouble(),
                it.bpm,
                it.beatIntervalMs,
                it.beatConfidence,
                it.downbeatPhaseMs,
                it.audibleStartMs.toDouble(),
                it.introEndMs.toDouble(),
                it.outroStartMs.toDouble(),
                it.contentEndMs.toDouble(),
                it.finalFadeOnsetMs.toDouble(),
            )
        }
        val nxtMixIn = next?.mixInCandidatesMs?.toLongArray()
        val nxtMixOut = next?.mixOutCandidatesMs?.toLongArray()
        val nxtEnergy = next?.energyCurve?.toFloatArray()
        val nxtVocal = next?.vocalActivity?.toFloatArray()

        val raw = nativePlan(
            curScalars = curScalars,
            curMixIn = curMixIn,
            curMixOut = curMixOut,
            curEnergy = curEnergy,
            curVocal = curVocal,
            nxtScalars = nxtScalars,
            nxtMixIn = nxtMixIn,
            nxtMixOut = nxtMixOut,
            nxtEnergy = nxtEnergy,
            nxtVocal = nxtVocal,
            currentDurationMs = currentTrack.durationMs,
            nextDurationMs = nextTrack.durationMs,
            positionMs = positionMs,
            fallbackFadeMs = fallbackFadeMs,
            minFadeMs = minFadeMs,
            gaplessAlbum = gaplessAlbum,
        ) ?: return AutoMixPlan.fallback(
            fallbackFadeMs,
            (currentTrack.durationMs - fallbackFadeMs).coerceAtLeast(0L),
            "native plan failed",
        )

        val nums = raw[0] as DoubleArray
        val reason = raw[1] as String

        val styleIndex = nums[0].toInt().coerceIn(0, AutoMixStyle.entries.size - 1)
        val style = AutoMixStyle.entries[styleIndex]

        return AutoMixPlan(
            blocked = nums[11] > 0.5,
            reason = reason,
            style = style,
            transitionStartMs = nums[1].toLong(),
            transitionEndMs = nums[2].toLong(),
            fadeMs = nums[3].toLong(),
            incomingCueMs = nums[4].toLong(),
            incomingPlaybackRate = nums[5],
            bassSwap = nums[6] > 0.5,
            bassSwapFraction = nums[7],
            filterSweep = nums[8],
            vocalOverlap = nums[9],
            markerVisible = nums[10] > 0.5,
        )
    }

    @JvmStatic
    private external fun nativePlan(
        curScalars: DoubleArray?,
        curMixIn: LongArray?,
        curMixOut: LongArray?,
        curEnergy: FloatArray?,
        curVocal: FloatArray?,
        nxtScalars: DoubleArray?,
        nxtMixIn: LongArray?,
        nxtMixOut: LongArray?,
        nxtEnergy: FloatArray?,
        nxtVocal: FloatArray?,
        currentDurationMs: Long,
        nextDurationMs: Long,
        positionMs: Long,
        fallbackFadeMs: Long,
        minFadeMs: Long,
        gaplessAlbum: Boolean,
    ): Array<Any>?
}
