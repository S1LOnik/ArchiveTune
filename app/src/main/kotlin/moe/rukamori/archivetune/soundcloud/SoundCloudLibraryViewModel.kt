/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.soundcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SoundCloudLibraryViewModel
    @Inject
    constructor() : ViewModel() {

        val genres = listOf("Trending", "Phonk", "Electronic", "Hip-Hop", "Rock", "Pop", "Ambient")

        private val _selectedGenre = MutableStateFlow("Trending")
        val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        private val _tracks = MutableStateFlow<List<SoundCloudAudioProvider.SoundCloudTrackMetadata>>(emptyList())
        val tracks: StateFlow<List<SoundCloudAudioProvider.SoundCloudTrackMetadata>> = _tracks.asStateFlow()

        private val _playlists = MutableStateFlow<List<SoundCloudAudioProvider.SoundCloudPlaylistResult>>(emptyList())
        val playlists: StateFlow<List<SoundCloudAudioProvider.SoundCloudPlaylistResult>> = _playlists.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        init {
            loadGenreContent("Trending")
        }

        fun onGenreSelected(genre: String) {
            _selectedGenre.value = genre
            _query.value = ""
            loadGenreContent(genre)
        }

        fun onQueryChange(newQuery: String) {
            _query.value = newQuery
            if (newQuery.isBlank()) {
                loadGenreContent(_selectedGenre.value)
            } else {
                performSearch(newQuery)
            }
        }

        fun refresh() {
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    SoundCloudAudioProvider.invalidateClientId()
                    if (_query.value.isNotBlank()) {
                        loadSearchContentInternal(_query.value)
                    } else {
                        loadGenreContentInternal(_selectedGenre.value)
                    }
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        private fun loadGenreContent(genre: String) {
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    loadGenreContentInternal(genre)
                } catch (e: Exception) {
                    Timber.tag("SoundCloudLibVM").e(e, "Failed to load SoundCloud content for %s", genre)
                    _errorMessage.value = e.localizedMessage
                } finally {
                    _isLoading.value = false
                }
            }
        }

        private fun performSearch(searchQuery: String) {
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    loadSearchContentInternal(searchQuery)
                } catch (e: Exception) {
                    Timber.tag("SoundCloudLibVM").e(e, "SoundCloud search failed for %s", searchQuery)
                    _errorMessage.value = e.localizedMessage
                } finally {
                    _isLoading.value = false
                }
            }
        }

        private suspend fun loadGenreContentInternal(genre: String) {
            val searchTerm = if (genre == "Trending") "top hits" else genre
            val fetchedTracks = SoundCloudAudioProvider.searchMetadata(term = searchTerm, limit = 30)
            val fetchedPlaylists = SoundCloudAudioProvider.searchPlaylists(term = searchTerm, limit = 15)
            _tracks.value = fetchedTracks
            _playlists.value = fetchedPlaylists
        }

        private suspend fun loadSearchContentInternal(searchQuery: String) {
            val fetchedTracks = SoundCloudAudioProvider.searchMetadata(term = searchQuery, limit = 30)
            val fetchedPlaylists = SoundCloudAudioProvider.searchPlaylists(term = searchQuery, limit = 15)
            _tracks.value = fetchedTracks
            _playlists.value = fetchedPlaylists
        }
    }
