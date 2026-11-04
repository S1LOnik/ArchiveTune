/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.fallback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.SoundCloudAudioQuality
import moe.rukamori.archivetune.constants.SoundCloudAuthTokenKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.session.SpotifySessionManager
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioSourceKind {
    SPOTIFY,
    YOUTUBE,
    SOUNDCLOUD,
}

data class FallbackResult(
    val newMediaId: String,
    val source: AudioSourceKind,
    val preservedMetadata: MediaMetadata,
    val statusMessage: String,
)

@Singleton
class LatencyServerMonitor @Inject constructor() {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    private val spotifyLatencyMs = AtomicLong(60L)
    private val youtubeLatencyMs = AtomicLong(40L)
    private val soundCloudLatencyMs = AtomicLong(70L)

    companion object {
        private const val TAG = "LatencyServerMonitor"
        const val LATENCY_DEGRADED_THRESHOLD_MS = 800L
    }

    fun isSpotifyHealthy(): Boolean = spotifyLatencyMs.get() < LATENCY_DEGRADED_THRESHOLD_MS
    fun isYouTubeHealthy(): Boolean = youtubeLatencyMs.get() < LATENCY_DEGRADED_THRESHOLD_MS
    fun isSoundCloudHealthy(): Boolean = soundCloudLatencyMs.get() < LATENCY_DEGRADED_THRESHOLD_MS

    fun getSpotifyLatency(): Long = spotifyLatencyMs.get()
    fun getYouTubeLatency(): Long = youtubeLatencyMs.get()

    suspend fun checkLatencies() = withContext(Dispatchers.IO) {
        measurePing("https://api.spotify.com/v1/")?.let { spotifyLatencyMs.set(it) }
        measurePing("https://music.youtube.com/generate_204")?.let { youtubeLatencyMs.set(it) }
        measurePing("https://api-v2.soundcloud.com/")?.let { soundCloudLatencyMs.set(it) }
        Timber.tag(TAG).d(
            "Latency check: Spotify=%dms, YouTube=%dms, SoundCloud=%dms",
            spotifyLatencyMs.get(),
            youtubeLatencyMs.get(),
            soundCloudLatencyMs.get(),
        )
    }

    private fun measurePing(url: String): Long? {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                if (response.isSuccessful || response.code in 200..499) elapsed else null
            }
        } catch (_: IOException) {
            System.currentTimeMillis() - start + 1000L
        } catch (_: Exception) {
            null
        }
    }
}

@Singleton
class UniversalFallbackMatrix
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val spotifyLibraryRepository: SpotifyLibraryRepository,
        private val spotifySessionManager: SpotifySessionManager,
        val latencyMonitor: LatencyServerMonitor,
    ) {
        companion object {
            private const val TAG = "UniversalFallback"

            // [FIX-ZCODE] Wave 9: pure source detection so the routing rules are unit-testable.
            fun detectSourceOf(mediaId: String): AudioSourceKind =
                when {
                    mediaId.startsWith("spotify:") || mediaId.startsWith("spotify//") -> AudioSourceKind.SPOTIFY
                    mediaId.startsWith("soundcloud:") || mediaId.startsWith(SoundCloudAudioProvider.HLS_SCHEME) -> AudioSourceKind.SOUNDCLOUD
                    else -> AudioSourceKind.YOUTUBE
                }

            internal val SPECIFIC_MODIFIERS = listOf(
                "hardstyle", "nightcore", "daycore", "slowed", "reverb",
                "speed up", "sped up", "fast", "flip", "mashup",
                "cover", "acoustic", "instrumental", "vip", "bass boosted",
                "8d", "phonk", "drill", "bootleg", "tribute", "parody", "tiktok", "larp"
            )

            internal val GENERIC_REMIX_KEYWORDS = listOf(
                "remix", "mix", "edit", "club mix", "extended", "dub"
            )

            internal val ALL_REMIX_KEYWORDS = SPECIFIC_MODIFIERS + GENERIC_REMIX_KEYWORDS

            internal val NON_REMIX_PHRASES = listOf(
                "original mix", "original version", "album version", "radio edit", "radio mix", "single version"
            )

            internal fun isCandidateAcceptable(
                original: MediaMetadata,
                candidate: moe.rukamori.archivetune.spotify.models.SpotifyTrack,
                isMaxQuality: Boolean = false,
            ): Boolean {
                val origTitleLower = original.title.lowercase()
                val candidateTitleLower = candidate.name.lowercase()

                // 1. Specific style modifier check:
                // If original contains specific modifiers (e.g. hardstyle, slowed, sped up, phonk),
                // the candidate MUST contain that same specific modifier!
                for (modifier in SPECIFIC_MODIFIERS) {
                    if (origTitleLower.contains(modifier)) {
                        val candidateMatches = when (modifier) {
                            "sped up", "speed up" -> candidateTitleLower.contains("sped up") || candidateTitleLower.contains("speed up")
                            "slowed", "reverb" -> candidateTitleLower.contains("slowed") || candidateTitleLower.contains("reverb")
                            else -> candidateTitleLower.contains(modifier)
                        }
                        if (!candidateMatches) {
                            Timber.tag(TAG).d("Rejecting candidate '%s': missing specific modifier '%s' from original '%s'", candidate.name, modifier, original.title)
                            return false
                        }
                    }
                }

                // 2. Original remix vs candidate original/non-remix guard:
                val origHasRemix = ALL_REMIX_KEYWORDS.any { origTitleLower.contains(it) }
                val candidateHasRemix = ALL_REMIX_KEYWORDS.any { candidateTitleLower.contains(it) }

                if (origHasRemix) {
                    // If original is a remix/edit, candidate MUST also have remix keywords and MUST NOT be an original/radio mix
                    if (!candidateHasRemix) {
                        Timber.tag(TAG).d("Rejecting candidate '%s': candidate lacks remix keyword while original '%s' is a remix", candidate.name, original.title)
                        return false
                    }
                    if (NON_REMIX_PHRASES.any { candidateTitleLower.contains(it) } && !SPECIFIC_MODIFIERS.any { candidateTitleLower.contains(it) }) {
                        Timber.tag(TAG).d("Rejecting candidate '%s': candidate is an original/radio version, not the remix", candidate.name)
                        return false
                    }
                } else {
                    // If original is standard track, candidate MUST NOT be a remix or instrumental
                    if (candidateHasRemix) {
                        Timber.tag(TAG).d("Rejecting candidate '%s': candidate is a remix/edit but original '%s' is standard track", candidate.name, original.title)
                        return false
                    }
                }

                // 3. Strict Duration check (applies to BOTH auto-mode and max quality):
                if (original.duration > 0 && candidate.durationMs > 0) {
                    val candidateDurationSec = candidate.durationMs / 1000
                    val diffSec = kotlin.math.abs(original.duration - candidateDurationSec)
                    val maxAllowedDiff = 25
                    if (diffSec > maxAllowedDiff || (diffSec.toFloat() / original.duration.toFloat()) > 0.15f) {
                        Timber.tag(TAG).d("Rejecting candidate '%s': duration mismatch (orig: %ds, cand: %ds, diff: %ds)", candidate.name, original.duration, candidateDurationSec, diffSec)
                        return false
                    }
                }

                // 4. Word token similarity (applies to BOTH auto-mode and max quality):
                fun cleanWords(s: String) =
                    s.lowercase()
                        .replace(Regex("\\[.*?\\]|\\(.*?\\)"), " ")
                        .replace(Regex("[^a-z0-9а-яё ]"), " ")
                        .split(" ")
                        .filter { it.length > 1 }
                        .toSet()

                val origWords = cleanWords(original.title)
                val candidateWords = cleanWords(candidate.name)
                if (origWords.isNotEmpty() && candidateWords.isNotEmpty()) {
                    val intersection = origWords.intersect(candidateWords).size
                    val minWords = minOf(origWords.size, candidateWords.size)
                    if (intersection < minOf(2, minWords)) {
                        Timber.tag(TAG).d("Rejecting candidate '%s': low token similarity with '%s' (intersection: %d, minWords: %d)", candidate.name, original.title, intersection, minWords)
                        return false
                    }
                }

                return true
            }
        }

        fun detectSource(mediaId: String): AudioSourceKind = detectSourceOf(mediaId)

        suspend fun resolveFallback(
            originalMetadata: MediaMetadata,
            failedMediaId: String,
        ): FallbackResult? = withContext(Dispatchers.IO) {
            val failedSource = detectSource(failedMediaId)
            Timber.tag(TAG).i(
                "Triggering fallback matrix for track '%s' (failed source: %s)",
                originalMetadata.title,
                failedSource.name,
            )

            val title = originalMetadata.title.ifBlank { null } ?: return@withContext null
            val artistStr = originalMetadata.artists.joinToString(" ") { it.name }.ifBlank { null }
            val query = if (!artistStr.isNullOrBlank()) "$title $artistStr" else title

            val fallbackOrder = when (failedSource) {
                AudioSourceKind.SPOTIFY -> {
                    if (latencyMonitor.isYouTubeHealthy()) {
                        listOf(AudioSourceKind.YOUTUBE, AudioSourceKind.SOUNDCLOUD)
                    } else {
                        listOf(AudioSourceKind.SOUNDCLOUD, AudioSourceKind.YOUTUBE)
                    }
                }
                AudioSourceKind.YOUTUBE -> {
                    if (spotifySessionManager.hasActiveSession() && latencyMonitor.isSpotifyHealthy()) {
                        listOf(AudioSourceKind.SPOTIFY, AudioSourceKind.SOUNDCLOUD)
                    } else {
                        listOf(AudioSourceKind.SOUNDCLOUD, AudioSourceKind.SPOTIFY)
                    }
                }
                AudioSourceKind.SOUNDCLOUD -> {
                    if (spotifySessionManager.hasActiveSession() && latencyMonitor.isSpotifyHealthy()) {
                        listOf(AudioSourceKind.SPOTIFY, AudioSourceKind.YOUTUBE)
                    } else {
                        listOf(AudioSourceKind.YOUTUBE, AudioSourceKind.SPOTIFY)
                    }
                }
            }

            for (targetSource in fallbackOrder) {
                val result = when (targetSource) {
                    AudioSourceKind.SPOTIFY -> tryResolveSpotify(originalMetadata, query)
                    AudioSourceKind.YOUTUBE -> tryResolveYouTube(originalMetadata, query)
                    AudioSourceKind.SOUNDCLOUD -> tryResolveSoundCloud(originalMetadata, title, originalMetadata.artists.map { it.name })
                }
                if (result != null) {
                    database.query {
                        insert(result.preservedMetadata)
                    }
                    Timber.tag(TAG).i(
                        "Fallback succeeded: switched from %s to %s (%s)",
                        failedSource.name,
                        targetSource.name,
                        result.newMediaId,
                    )
                    return@withContext result
                }
            }

            Timber.tag(TAG).w("All fallback sources exhausted for '%s'", query)
            null
        }

        suspend fun resolveMaxQuality(originalMetadata: MediaMetadata): FallbackResult? = withContext(Dispatchers.IO) {
            val currentSource = detectSource(originalMetadata.id)
            if (currentSource == AudioSourceKind.SPOTIFY) return@withContext null

            if (!spotifySessionManager.hasActiveSession() || !latencyMonitor.isSpotifyHealthy()) {
                return@withContext null
            }

            val title = originalMetadata.title.ifBlank { null } ?: return@withContext null
            val artistStr = originalMetadata.artists.joinToString(" ") { it.name }.ifBlank { null }
            val query = if (!artistStr.isNullOrBlank()) "$title $artistStr" else title

            val result = tryResolveSpotify(originalMetadata, query, isMaxQuality = true)
            if (result != null) {
                database.query {
                    insert(result.preservedMetadata)
                }
            }
            result
        }

        private suspend fun tryResolveSpotify(
            originalMetadata: MediaMetadata,
            query: String,
            isMaxQuality: Boolean = false,
        ): FallbackResult? {
            if (!spotifySessionManager.hasActiveSession()) return null
            return runCatching {
                val tracks = spotifyLibraryRepository.searchTracks(query = query, limit = 5)
                if (tracks.isEmpty()) return null
                val bestTrack = tracks.firstOrNull { isCandidateAcceptable(originalMetadata, it, isMaxQuality) } ?: return null
                val cleanId = bestTrack.id
                    .removePrefix("spotify:track:")
                    .removePrefix("spotify://track/")
                    .removePrefix("spotify://")
                    .substringBefore("?")
                val spotifyMediaId = "spotify:track:$cleanId"

                // PRESERVE ORIGINAL METADATA, COVER ART, AND LYRICS
                val preserved = originalMetadata.copy(
                    id = spotifyMediaId,
                    spotifyTrackId = cleanId,
                    duration = if (bestTrack.durationMs > 0) (bestTrack.durationMs / 1000).toInt() else originalMetadata.duration,
                )

                val msg = if (isMaxQuality) {
                    "Включено максимальное качество (Spotify 320 kbps)"
                } else {
                    "Стрим переключен на Spotify"
                }

                FallbackResult(
                    newMediaId = spotifyMediaId,
                    source = AudioSourceKind.SPOTIFY,
                    preservedMetadata = preserved,
                    statusMessage = msg,
                )
            }.getOrNull()
        }

        private suspend fun tryResolveYouTube(
            originalMetadata: MediaMetadata,
            query: String,
        ): FallbackResult? {
            return runCatching {
                val searchResult = YouTube.search(
                    query = query,
                    filter = YouTube.SearchFilter.FILTER_SONG,
                    useAccountContext = false,
                ).getOrNull()

                val bestSong = searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: return null
                val ytMediaId = bestSong.id

                // PRESERVE ORIGINAL METADATA, COVER ART, AND LYRICS
                val preserved = originalMetadata.copy(
                    id = ytMediaId,
                    duration = bestSong.duration?.toInt() ?: originalMetadata.duration,
                )

                FallbackResult(
                    newMediaId = ytMediaId,
                    source = AudioSourceKind.YOUTUBE,
                    preservedMetadata = preserved,
                    statusMessage = "Стрим переключен на YouTube Music",
                )
            }.getOrNull()
        }

        private suspend fun tryResolveSoundCloud(
            originalMetadata: MediaMetadata,
            title: String,
            artists: List<String>,
        ): FallbackResult? {
            return runCatching {
                val authToken = context.dataStore.get(SoundCloudAuthTokenKey, "")
                val durationMs = if (originalMetadata.duration > 0) originalMetadata.duration * 1000L else null
                val stream = SoundCloudAudioProvider.resolve(
                    query = SoundCloudAudioProvider.Query(
                        mediaId = originalMetadata.id,
                        title = title,
                        artists = artists,
                        album = originalMetadata.album?.title,
                        durationMs = durationMs,
                    ),
                    authToken = authToken,
                    quality = SoundCloudAudioQuality.OPUS_64,
                ) ?: return null

                val scMediaId = stream.uri
                // PRESERVE ORIGINAL METADATA, COVER ART, AND LYRICS
                val preserved = originalMetadata.copy(
                    id = scMediaId,
                )

                FallbackResult(
                    newMediaId = scMediaId,
                    source = AudioSourceKind.SOUNDCLOUD,
                    preservedMetadata = preserved,
                    statusMessage = "Стрим переключен на SoundCloud",
                )
            }.getOrNull()
        }
    }
