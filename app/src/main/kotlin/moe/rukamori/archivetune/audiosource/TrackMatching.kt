/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audiosource

import androidx.annotation.Keep

@Keep
internal object TrackMatching {
    const val MIN_MATCH_SCORE = 60

    init {
        System.loadLibrary("archivetune_automix")
    }

    data class Target(
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    )

    data class Candidate(
        val id: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    )

    fun score(
        wantedTitle: String,
        wantedArtists: List<String>,
        candidateTitle: String,
        candidateArtist: String,
        wantedDurationMs: Long?,
        candidateDurationMs: Long?,
    ): Int =
        nativeScore(
            wantedTitle = wantedTitle,
            wantedArtists = wantedArtists.toTypedArray(),
            candidateTitle = candidateTitle,
            candidateArtist = candidateArtist,
            wantedDurationMs = wantedDurationMs ?: -1L,
            candidateDurationMs = candidateDurationMs ?: -1L,
        )

    fun best(
        target: Target,
        candidates: List<Candidate>,
    ): Candidate? {
        if (candidates.isEmpty()) return null
        val candidateTitles = candidates.map { it.title }.toTypedArray()
        val candidateArtists = candidates.map { it.artists.firstOrNull().orEmpty() }.toTypedArray()
        val candidateDurations = LongArray(candidates.size) { i -> candidates[i].durationMs ?: -1L }

        val bestIdx = nativeBestMatchIndex(
            targetTitle = target.title,
            targetArtists = target.artists.toTypedArray(),
            targetDurationMs = target.durationMs ?: -1L,
            candidateTitles = candidateTitles,
            candidatePrimaryArtists = candidateArtists,
            candidateDurationsMs = candidateDurations,
        )

        return if (bestIdx >= 0 && bestIdx < candidates.size) candidates[bestIdx] else null
    }

    fun durationMatches(
        wantedDurationMs: Long?,
        candidateDurationMs: Long?,
    ): Boolean =
        nativeDurationMatches(
            wantedDurationMs ?: -1L,
            candidateDurationMs ?: -1L,
        )

    fun normalize(value: String?): String =
        if (value == null) "" else nativeNormalize(value)

    fun normalizeTitle(value: String): String =
        nativeNormalizeTitle(value)

    fun searchTitle(value: String): String =
        nativeSearchTitle(value)

    fun searchArtist(value: String): String =
        nativeSearchArtist(value)

    @JvmStatic
    private external fun nativeDurationMatches(wantedDurationMs: Long, candidateDurationMs: Long): Boolean

    @JvmStatic
    private external fun nativeNormalize(value: String): String

    @JvmStatic
    private external fun nativeNormalizeTitle(value: String): String

    @JvmStatic
    private external fun nativeSearchTitle(value: String): String

    @JvmStatic
    private external fun nativeSearchArtist(value: String): String

    @JvmStatic
    private external fun nativeScore(
        wantedTitle: String,
        wantedArtists: Array<String>,
        candidateTitle: String,
        candidateArtist: String,
        wantedDurationMs: Long,
        candidateDurationMs: Long,
    ): Int

    @JvmStatic
    private external fun nativeBestMatchIndex(
        targetTitle: String,
        targetArtists: Array<String>,
        targetDurationMs: Long,
        candidateTitles: Array<String>,
        candidatePrimaryArtists: Array<String>,
        candidateDurationsMs: LongArray,
    ): Int
}
