/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.library

import android.content.Context
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.ai.AiTextService
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiRecommendationsApiKeyKey
import moe.rukamori.archivetune.constants.AiRecommendationsCustomEndpointKey
import moe.rukamori.archivetune.constants.AiRecommendationsModelKey
import moe.rukamori.archivetune.constants.AiRecommendationsProviderKey
import moe.rukamori.archivetune.constants.AiRecommendationsSeparateApiKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.repository.LibraryTopMixRepository
import moe.rukamori.archivetune.utils.dataStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

private const val TopMixCountLimit = 5
private const val TopMixSongsPerMix = 40
private const val TopMixPromptCandidateLimit = 90

class RefreshLibraryTopMixesUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: LibraryTopMixRepository,
    ) {
        suspend operator fun invoke(): RefreshLibraryTopMixesResult {
            val config = readAiConfig()
            if (!config.canCallApi || isAiValidationFailed()) {
                return RefreshLibraryTopMixesResult.Failure(TopMixGenerationFailure.AI_NOT_CONFIGURED)
            }

            val likedSongs = repository.likedSongsForTopMixes(40)
            val mostPlayedSongs = repository.mostPlayedSongsForTopMixes(40)
            val recentSongs = repository.recentSongsForTopMixes(50)

            val likedIds = likedSongs.mapTo(HashSet()) { it.id }
            val recentRankById = recentSongs.mapIndexed { idx, s -> s.id to (idx + 1) }.toMap()

            val candidates =
                (likedSongs + mostPlayedSongs + recentSongs)
                    .distinctBy { it.id }
                    .filter { it.song.title.isNotBlank() }
                    .take(TopMixPromptCandidateLimit)
                    .map { song ->
                        ValidatedTopMixSong(
                            id = song.id,
                            title = song.song.title,
                            artists = song.artists.map { it.name }.filter { it.isNotBlank() },
                            albumName = song.album?.title ?: song.song.albumName,
                            mediaMetadata = song.toMediaMetadata(),
                            isLiked = song.id in likedIds || song.song.liked,
                            playMinutes = (song.song.totalPlayTime / 60_000L).toInt().coerceAtLeast(0),
                            recentRank = recentRankById[song.id],
                        )
                    }

            if (candidates.isEmpty()) {
                return RefreshLibraryTopMixesResult.Failure(TopMixGenerationFailure.NO_RECENT_HISTORY)
            }

            return runCatching {
                val mixes = requestAiMixes(config = config, candidates = candidates)
                if (mixes.isEmpty()) {
                    RefreshLibraryTopMixesResult.Failure(TopMixGenerationFailure.NO_VALID_MIXES)
                } else {
                    repository.replaceTopMixes(mixes)
                    RefreshLibraryTopMixesResult.Success
                }
            }.getOrElse { throwable ->
                RefreshLibraryTopMixesResult.Failure(
                    reason = TopMixGenerationFailure.AI_REQUEST_FAILED,
                    cause = throwable,
                )
            }
        }

        private suspend fun readAiConfig(): AiServiceConfig {
            val prefs = context.dataStore.data.first()
            val useSeparate = prefs[AiRecommendationsSeparateApiKey] == true
            val provider =
                if (useSeparate) {
                    prefs[AiRecommendationsProviderKey].toEnum(AiProvider.NONE)
                } else {
                    prefs[AiProviderKey].toEnum(AiProvider.NONE)
                }
            val apiKey =
                if (useSeparate) {
                    prefs[AiRecommendationsApiKeyKey].orEmpty()
                } else {
                    prefs[AiApiKeyKey].orEmpty()
                }
            val endpoint =
                if (useSeparate) {
                    prefs[AiRecommendationsCustomEndpointKey].orEmpty()
                } else {
                    prefs[AiCustomEndpointKey].orEmpty()
                }
            val mainModel =
                if (provider == AiProvider.CUSTOM) {
                    prefs[AiCustomModelKey].orEmpty()
                } else {
                    prefs[AiSelectedModelKey].orEmpty()
                }
            val recModel = prefs[AiRecommendationsModelKey].orEmpty().trim()
            return AiServiceConfig(
                provider = provider,
                apiKey = apiKey,
                customEndpoint = endpoint,
                model = recModel.ifBlank { mainModel },
            )
        }

        private suspend fun isAiValidationFailed(): Boolean {
            val prefs = context.dataStore.data.first()
            if (prefs[AiRecommendationsSeparateApiKey] == true) return false
            return prefs[AiApiValidationStatusKey]
                .toEnum(AiApiValidationStatus.UNKNOWN) == AiApiValidationStatus.FAILED
        }

        private suspend fun requestAiMixes(
            config: AiServiceConfig,
            candidates: List<ValidatedTopMixSong>,
        ): List<GeneratedLibraryTopMix> {
            val candidateById = candidates.associateBy { it.id }
            val candidatePayload =
                JSONArray().apply {
                    candidates.forEach { candidate ->
                        put(
                            JSONObject()
                                .put("id", candidate.id)
                                .put("title", candidate.title)
                                .put("artists", candidate.artists.joinToString(", "))
                                .put("album", candidate.albumName.orEmpty())
                                .put("liked", candidate.isLiked)
                                .put("playMinutes", candidate.playMinutes)
                                .apply {
                                    if (candidate.recentRank != null) {
                                        put("recentRank", candidate.recentRank)
                                    }
                                },
                        )
                    }
                }
            val response =
                AiTextService.complete(
                    config = config,
                    systemPrompt =
                        """
                        You are an elite personal music curator and A&R specialist for ArchiveTune.
                        You receive the user's multi-signal taste profile:
                        - `liked`: tracks the user explicitly marked as favorites
                        - `playMinutes`: total listening time showing long-term obsession
                        - `recentRank`: what they have been playing most recently (1 = most recent)

                        Build $TopMixCountLimit distinct, deeply cohesive personal mixes:
                        1. Core Favorites & Deep Cuts Mix (anchored by their most-loved/most-played tracks + hidden gems from the same scene/subgenre)
                        2. Current Obsession Mix (built around their recent listening vibe + fresh tracks that match the exact tempo, production style, and language/scene)
                        3. High-Energy / Flow Mix (tracks with driving rhythm, bass, or momentum from their taste profile + matching recommendations)
                        4. Late-Night / Atmospheric Mix (melodic, immersive, emotional, or chill tracks from their profile + matching recommendations)
                        5. Pure Discovery Radar Mix (mostly NEW recommended tracks — 75%+ recommendations — from kindred artists, underground/indie peers, and adjacent subgenres the user hasn't heard yet but will love based on their liked & most-played tracks).

                        Rules:
                        - Never pad mixes with generic top-40 pop unless the user's history is top-40 pop. Respect niche genres (phonk, hyperpop, post-punk, synthwave, anime/vocaloid, hip-hop, metal, electronic, CIS/Russian indie, etc.).
                        - In mixes 1-4, blend the user's matching candidate `songIds` with 8 to 15 new `recommendations` (`{"title":"...","artist":"..."}`) not already in `candidates`.
                        - Order tracks for smooth DJ-like energy and mood transitions.
                        - Return valid JSON only matching this exact schema:
                          {"mixes":[{"title":"Descriptive Mix Title","description":"Vivid 1-sentence description","songIds":["id"],"recommendations":[{"title":"Song Title","artist":"Artist Name"}]}]}
                        - Every title must end with "Mix".
                        """.trimIndent(),
                    userPrompt =
                        JSONObject()
                            .put("basis", "liked songs, most played songs, and recent listening history")
                            .put("maxMixes", TopMixCountLimit)
                            .put("maxSongsPerMix", TopMixSongsPerMix)
                            .put("candidates", candidatePayload)
                            .toString(),
                    temperature = 0.45,
                    maxTokens = 4096,
                )
            return parseGeneratedMixes(
                response = response,
                candidateById = candidateById,
            )
        }

        private suspend fun parseGeneratedMixes(
            response: String,
            candidateById: Map<String, ValidatedTopMixSong>,
        ): List<GeneratedLibraryTopMix> {
            val json = JSONObject(response.substringAfter('{').substringBeforeLast('}').let { "{$it}" })
            val mixes = json.optJSONArray("mixes") ?: JSONArray()

            val recQueries =
                buildList {
                    for (index in 0 until mixes.length()) {
                        val mixJson = mixes.optJSONObject(index) ?: continue
                        val recs = mixJson.optJSONArray("recommendations") ?: JSONArray()
                        for (r in 0 until recs.length()) {
                            val recJson = recs.optJSONObject(r) ?: continue
                            val title = recJson.optString("title").trim()
                            val artist = recJson.optString("artist").trim()
                            if (title.isNotEmpty() && artist.isNotEmpty()) {
                                add(title to artist)
                            }
                        }
                    }
                }.distinct().take(48)

            val searchSemaphore = Semaphore(6)
            val resolvedRecommendations =
                coroutineScope {
                    recQueries
                        .map { (title, artist) ->
                            async(Dispatchers.IO) {
                                searchSemaphore.withPermit {
                                    val query = "$artist $title"
                                    val songs =
                                        YouTube
                                            .search(query, YouTube.SearchFilter.FILTER_SONG)
                                            .getOrNull()
                                            ?.items
                                            .orEmpty()
                                            .filterIsInstance<SongItem>()

                                    val match =
                                        songs.firstOrNull { song ->
                                            song.title.contains(title, ignoreCase = true) &&
                                                song.artists.any { it.name.contains(artist, ignoreCase = true) }
                                        } ?: songs.firstOrNull()

                                    if (match != null) {
                                        (title to artist) to ValidatedTopMixSong.fromYtm(match)
                                    } else {
                                        null
                                    }
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                        .toMap()
                }

            return buildList {
                for (index in 0 until mixes.length()) {
                    if (size >= TopMixCountLimit) break
                    val mixJson = mixes.optJSONObject(index) ?: continue

                    val candidatesList =
                        mixJson
                            .optJSONArray("songIds")
                            .toStringList()
                            .mapNotNull(candidateById::get)

                    val recommendedList =
                        buildList {
                            val recs = mixJson.optJSONArray("recommendations") ?: JSONArray()
                            for (r in 0 until recs.length()) {
                                val recJson = recs.optJSONObject(r) ?: continue
                                val title = recJson.optString("title").trim()
                                val artist = recJson.optString("artist").trim()
                                val resolved = resolvedRecommendations[title to artist]
                                if (resolved != null) {
                                    add(resolved)
                                }
                            }
                        }

                    val selected =
                        (candidatesList + recommendedList)
                            .distinctBy { it.id }
                            .take(TopMixSongsPerMix)

                    if (selected.isEmpty()) continue

                    add(
                        GeneratedLibraryTopMix(
                            id = "ai_top_mix_${System.currentTimeMillis()}_$index",
                            title = mixJson.optString("title").sanitizeMixTitle(),
                            description = mixJson.optString("description").sanitizeMixDescription(),
                            tracks = ImmutableList.copyOf(selected.map { it.mediaMetadata }),
                        ),
                    )
                }
            }
        }
    }

sealed interface RefreshLibraryTopMixesResult {
    data object Success : RefreshLibraryTopMixesResult

    data class Failure(
        val reason: TopMixGenerationFailure,
        val cause: Throwable? = null,
    ) : RefreshLibraryTopMixesResult
}

enum class TopMixGenerationFailure {
    AI_NOT_CONFIGURED,
    NO_RECENT_HISTORY,
    NO_VALID_MIXES,
    AI_REQUEST_FAILED,
}

private data class ValidatedTopMixSong(
    val id: String,
    val title: String,
    val artists: List<String>,
    val albumName: String?,
    val mediaMetadata: MediaMetadata,
    val isLiked: Boolean = false,
    val playMinutes: Int = 0,
    val recentRank: Int? = null,
) {
    companion object {
        fun fromYtm(ytmSong: SongItem): ValidatedTopMixSong =
            ValidatedTopMixSong(
                id = ytmSong.id,
                title = ytmSong.title,
                artists = ytmSong.artists.map { it.name },
                albumName = ytmSong.album?.name,
                mediaMetadata = ytmSong.toMediaMetadata(),
            )
    }
}

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) {
        emptyList()
    } else {
        List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
    }

private fun String.sanitizeMixTitle(): String {
    val title =
        lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .take(80)
            .ifBlank { "Recent Listening Mix" }
    return if (title.contains("mix", ignoreCase = true)) title else "$title Mix"
}

private fun String.sanitizeMixDescription(): String =
    lineSequence()
        .firstOrNull()
        .orEmpty()
        .trim()
        .take(120)
        .ifBlank { "A genre-focused mix based on your listening profile." }
