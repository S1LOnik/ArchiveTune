/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import moe.rukamori.archivetune.models.ItemsPage
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

enum class OnlineSearchSort {
    DEFAULT,
    VIEWS,
}

sealed interface OnlineSearchFilter {
    data object Spotify : OnlineSearchFilter
    data object SoundCloud : OnlineSearchFilter
    data object All : OnlineSearchFilter
    data class YouTube(val filter: moe.rukamori.archivetune.innertube.YouTube.SearchFilter) : OnlineSearchFilter
}

@HiltViewModel
class OnlineSearchViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
        val spotifyLibraryRepository: SpotifyLibraryRepository,
    ) : ViewModel() {
        val query =
            decodeOnlineSearchQuery(
                savedStateHandle.get<String>(OnlineSearchResultArgument).orEmpty(),
            )
        val searchFilter = MutableStateFlow<OnlineSearchFilter>(OnlineSearchFilter.All)
        val filter: StateFlow<YouTube.SearchFilter?> =
            searchFilter
                .map { (it as? OnlineSearchFilter.YouTube)?.filter }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val spotifyTracks = MutableStateFlow<List<SpotifyTrack>?>(null)
        val spotifyAlbums = MutableStateFlow<List<SpotifyAlbum>?>(null)
        val spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>?>(null)
        var isSpotifyLoading by mutableStateOf(false)
        var isSpotifyError by mutableStateOf(false)

        val soundCloudTracks = MutableStateFlow<List<SoundCloudAudioProvider.SoundCloudTrackMetadata>?>(null)
        val soundCloudPlaylists = MutableStateFlow<List<SoundCloudAudioProvider.SoundCloudPlaylistResult>?>(null)
        val soundCloudUsers = MutableStateFlow<List<SoundCloudAudioProvider.SoundCloudUserResult>?>(null)
        var isSoundCloudLoading by mutableStateOf(false)
        var isSoundCloudError by mutableStateOf(false)

        var isYouTubeBlocked by mutableStateOf(false)

        var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

        private val allModeFilters =
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_PODCAST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            )
        private var isSummaryLoading = false
        private val loadingFilters = mutableSetOf<String>()

        init {
            viewModelScope.launch {
                searchFilter.collect { selectedFilter ->
                    when (selectedFilter) {
                        OnlineSearchFilter.Spotify -> {
                            loadSpotifyIfNeeded()
                        }
                        OnlineSearchFilter.SoundCloud -> {
                            loadSoundCloudIfNeeded()
                        }
                        OnlineSearchFilter.All -> {
                            viewModelScope.launch {
                                loadSummaryIfNeeded()
                            }
                            allModeFilters.forEach { allModeFilter ->
                                viewModelScope.launch {
                                    loadFilterIfNeeded(allModeFilter)
                                }
                            }
                        }
                        is OnlineSearchFilter.YouTube -> {
                            loadFilterIfNeeded(selectedFilter.filter)
                        }
                    }
                }
            }
        }

        private suspend fun loadSummaryIfNeeded() {
            if (summaryPage != null || isSummaryLoading) return

            isSummaryLoading = true
            try {
                YouTube
                    .searchSummary(query)
                    .onSuccess {
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val contentFilteredPage =
                            it
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(context.dataStore.get(HideVideoKey, false))
                        summaryPage =
                            contentFilteredPage.copy(
                                summaries =
                                    contentFilteredPage.summaries.mapNotNull { summary ->
                                        summary
                                            .copy(items = filterAiContent(summary.items, aiContentFilterPolicy))
                                            .takeIf { filteredSummary -> filteredSummary.items.isNotEmpty() }
                                    },
                            )
                    }.onFailure {
                        reportException(it)
                        if (it.message?.contains("403") == true || it.message?.contains("bot", ignoreCase = true) == true) {
                            isYouTubeBlocked = true
                        }
                    }
            } finally {
                isSummaryLoading = false
            }
        }

        private suspend fun loadFilterIfNeeded(filter: YouTube.SearchFilter) {
            val filterKey = filter.value
            if (viewStateMap.containsKey(filterKey) || !loadingFilters.add(filterKey)) return

            try {
                if (filter == FILTER_SONG) {
                    val songsResult = YouTube.search(query, FILTER_SONG)
                    val videosResult = YouTube.search(query, FILTER_VIDEO)
                    val combinedItems = mutableListOf<YTItem>()
                    songsResult.getOrNull()?.items?.let { combinedItems.addAll(it) }
                    videosResult.getOrNull()?.items?.let { combinedItems.addAll(it) }
                    val continuation = songsResult.getOrNull()?.continuation ?: videosResult.getOrNull()?.continuation

                    if (combinedItems.isNotEmpty()) {
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        viewStateMap[filterKey] =
                            ItemsPage(
                                filterAiContent(
                                    combinedItems
                                        .distinctBy { it.id }
                                        .filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                                    aiContentFilterPolicy,
                                ),
                                continuation,
                            )
                    } else {
                        songsResult.onFailure {
                            reportException(it)
                            if (it.message?.contains("403") == true || it.message?.contains("bot", ignoreCase = true) == true) {
                                isYouTubeBlocked = true
                            }
                        }
                    }
                } else {
                    YouTube
                        .search(query, filter)
                        .onSuccess { result ->
                            val aiContentFilterPolicy = loadAiContentFilterPolicy()
                            viewStateMap[filterKey] =
                                ItemsPage(
                                    filterAiContent(
                                        result.items
                                            .distinctBy { it.id }
                                            .filterExplicit(
                                                context.dataStore.get(
                                                    HideExplicitKey,
                                                    false,
                                                ),
                                            ),
                                        aiContentFilterPolicy,
                                    ),
                                    result.continuation,
                                )
                        }.onFailure {
                            reportException(it)
                            if (it.message?.contains("403") == true || it.message?.contains("bot", ignoreCase = true) == true) {
                                isYouTubeBlocked = true
                            }
                        }
                }
            } finally {
                loadingFilters.remove(filterKey)
            }
        }

        fun loadSpotifyIfNeeded() {
            if (spotifyTracks.value != null || isSpotifyLoading) return
            viewModelScope.launch {
                if (query.isBlank()) {
                    spotifyTracks.value = emptyList()
                    spotifyAlbums.value = emptyList()
                    spotifyPlaylists.value = emptyList()
                    return@launch
                }
                isSpotifyLoading = true
                isSpotifyError = false
                try {
                    val result = spotifyLibraryRepository.searchAll(query = query, limit = 20, offset = 0)
                    spotifyTracks.value = result.tracks?.items.orEmpty()
                    spotifyAlbums.value = result.albums?.items.orEmpty()
                    spotifyPlaylists.value = result.playlists?.items.orEmpty()
                } catch (e: Throwable) {
                    reportException(e)
                    isSpotifyError = true
                    spotifyTracks.value = emptyList()
                    spotifyAlbums.value = emptyList()
                    spotifyPlaylists.value = emptyList()
                } finally {
                    isSpotifyLoading = false
                }
            }
        }

        fun loadSoundCloudIfNeeded() {
            if (soundCloudTracks.value != null || isSoundCloudLoading) return
            viewModelScope.launch {
                if (query.isBlank()) {
                    soundCloudTracks.value = emptyList()
                    soundCloudPlaylists.value = emptyList()
                    soundCloudUsers.value = emptyList()
                    return@launch
                }
                isSoundCloudLoading = true
                isSoundCloudError = false
                try {
                    val tracksDeferred = async { SoundCloudAudioProvider.searchMetadata(term = query, limit = 20) }
                    val playlistsDeferred = async { SoundCloudAudioProvider.searchPlaylists(term = query, limit = 20) }
                    val usersDeferred = async { SoundCloudAudioProvider.searchUsers(term = query, limit = 20) }
                    soundCloudTracks.value = tracksDeferred.await()
                    soundCloudPlaylists.value = playlistsDeferred.await()
                    soundCloudUsers.value = usersDeferred.await()
                } catch (e: Throwable) {
                    reportException(e)
                    isSoundCloudError = true
                    soundCloudTracks.value = emptyList()
                    soundCloudPlaylists.value = emptyList()
                    soundCloudUsers.value = emptyList()
                } finally {
                    isSoundCloudLoading = false
                }
            }
        }

        fun loadMore() {
            when (val currentFilter = searchFilter.value) {
                OnlineSearchFilter.Spotify -> {
                    val currentTracks = spotifyTracks.value.orEmpty()
                    if (currentTracks.isEmpty() || isSpotifyLoading) return
                    viewModelScope.launch {
                        try {
                            val more = spotifyLibraryRepository.searchTracks(
                                query = query,
                                limit = 50,
                                offset = currentTracks.size,
                            )
                            if (more.isNotEmpty()) {
                                spotifyTracks.value = (currentTracks + more).distinctBy { it.id }
                            }
                        } catch (e: Throwable) {
                            reportException(e)
                        }
                    }
                }
                OnlineSearchFilter.SoundCloud -> {
                    val currentTracks = soundCloudTracks.value.orEmpty()
                    if (currentTracks.isEmpty() || isSoundCloudLoading) return
                    viewModelScope.launch {
                        try {
                            val more = SoundCloudAudioProvider.searchMetadata(
                                term = query,
                                limit = currentTracks.size + 20,
                            )
                            if (more.isNotEmpty()) {
                                soundCloudTracks.value = (currentTracks + more).distinctBy { it.trackId }
                            }
                        } catch (e: Throwable) {
                            reportException(e)
                        }
                    }
                }
                OnlineSearchFilter.All -> return
                is OnlineSearchFilter.YouTube -> {
                    val filterKey = currentFilter.filter.value
                    viewModelScope.launch {
                        val viewState = viewStateMap[filterKey] ?: return@launch
                        val continuation = viewState.continuation ?: return@launch
                        val searchResult =
                            YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val continuedItems =
                            filterAiContent(
                                searchResult.items
                                    .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                    .filterVideo(context.dataStore.get(HideVideoKey, false)),
                                aiContentFilterPolicy,
                            )
                        viewStateMap[filterKey] =
                            ItemsPage(
                                (viewState.items + continuedItems).distinctBy { it.id },
                                searchResult.continuation,
                            )
                    }
                }
            }
        }

        fun sortedItems(
            items: List<YTItem>,
            sort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
        ): List<YTItem> =
            when (sort) {
                OnlineSearchSort.DEFAULT -> {
                    items
                }

                OnlineSearchSort.VIEWS -> {
                    items
                        .withIndex()
                        .sortedWith(
                            compareByDescending<IndexedValue<YTItem>> {
                                (it.value as? SongItem)?.viewCount ?: Long.MIN_VALUE
                            }.thenBy { it.index },
                        ).map { it.value }
                }
            }
    }
