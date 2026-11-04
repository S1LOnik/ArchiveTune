/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortDescendingKey
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortType
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortTypeKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.spotify.GetSpotifyLikedSongsUseCase
import moe.rukamori.archivetune.spotify.SpotifyLikedSong
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
        private val syncUtils: SyncUtils,
        private val getSpotifyLikedSongs: GetSpotifyLikedSongsUseCase,
    ) : ViewModel() {
        val playlist = savedStateHandle.get<String>("playlist")!!

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        private val _spotifyLikedSongs = MutableStateFlow<List<Song>>(emptyList())

        private fun AutoPlaylistSongSortType.toSongSortType(): SongSortType =
            when (this) {
                AutoPlaylistSongSortType.CREATE_DATE -> SongSortType.CREATE_DATE
                AutoPlaylistSongSortType.NAME -> SongSortType.NAME
                AutoPlaylistSongSortType.ARTIST -> SongSortType.ARTIST
                AutoPlaylistSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
            }

        private fun SpotifyLikedSong.toSong(index: Int = 0): Song {
            val cleanId = id.removePrefix("spotify:track:").removePrefix("spotify://")
            val songId = "spotify:track:$cleanId"
            val fallbackDate = java.time.LocalDateTime.now().minusSeconds(index.toLong())
            return Song(
                song = SongEntity(
                    id = songId,
                    title = title,
                    duration = (durationMs / 1000).toInt(),
                    thumbnailUrl = thumbnailUrl,
                    liked = true,
                    likedDate = fallbackDate,
                    inLibrary = fallbackDate,
                    explicit = isExplicit,
                ),
                artists = artistNames.map { name ->
                    ArtistEntity(
                        id = "spotify_artist_${name.hashCode()}",
                        name = name,
                    )
                },
                album = null,
            )
        }

        init {
            // Load Spotify liked songs when playlist is "liked"
            if (playlist == "liked") {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // Fetch and persist Spotify liked songs to Room database
                        val spotifySongs = getSpotifyLikedSongs()
                        persistSpotifyLikedSongsToDatabase(spotifySongs)
                        _spotifyLikedSongs.value = spotifySongs.mapIndexed { index, song -> song.toSong(index) }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
        }

        private suspend fun persistSpotifyLikedSongsToDatabase(spotifySongs: List<SpotifyLikedSong>) {
            // [FIX-ZCODE] B3 + suspend fix: существующие песни читаем ДО транзакции
            // (fork's database.transaction is not a suspend context), далее транзакция только пишет.
            val baseTime = java.time.LocalDateTime.now()
            val existingLikedDates: Map<String, java.time.LocalDateTime> =
                spotifySongs.mapIndexed { index, spotifySong ->
                    val cleanId = spotifySong.id.removePrefix("spotify:track:").removePrefix("spotify://")
                    val songId = "spotify:track:$cleanId"
                    val likedDate =
                        runCatching { database.getSongById(songId) }.getOrNull()
                            ?.song?.likedDate
                    songId to (likedDate ?: baseTime.minusSeconds(index.toLong()))
                }.toMap()

            database.transaction {
                spotifySongs.forEachIndexed { index, spotifySong ->
                    val cleanId = spotifySong.id.removePrefix("spotify:track:").removePrefix("spotify://")
                    val songId = "spotify:track:$cleanId"
                    val targetLikedDate = existingLikedDates[songId] ?: baseTime.minusSeconds(index.toLong())

                    val songEntity = SongEntity(
                        id = songId,
                        title = spotifySong.title,
                        duration = (spotifySong.durationMs / 1000).toInt(),
                        thumbnailUrl = spotifySong.thumbnailUrl,
                        liked = true,
                        likedDate = targetLikedDate,
                        inLibrary = targetLikedDate,
                        explicit = spotifySong.isExplicit,
                    )
                    upsert(songEntity)

                    spotifySong.artistNames.forEachIndexed { aIndex, name ->
                        val artistId = "spotify_artist_${name.hashCode()}"
                        upsert(ArtistEntity(id = artistId, name = name))
                        insert(SongArtistMap(songId = songId, artistId = artistId, position = aIndex))
                    }
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        val likedSongs =
            context.dataStore.data
                .map {
                    Triple(
                        it[AutoPlaylistSongSortTypeKey].toEnum(AutoPlaylistSongSortType.CREATE_DATE) to (
                            it[AutoPlaylistSongSortDescendingKey]
                                ?: true
                        ),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (sortDesc, hideExplicit, hideVideo) ->
                    val (sortType, descending) = sortDesc
                    val songSortType = sortType.toSongSortType()
                    when (playlist) {
                        "liked" -> {
                            // All liked songs (YouTube Music + Spotify) are now in the database
                            database.likedSongs(songSortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                        }

                        "liked_ytm" -> {
                            // [FIX-ZCODE] W10: YouTube Music liked only — also exclude the
                            // legacy "spotify//" id variant and SoundCloud tracks
                            database.likedSongs(songSortType, descending, hideVideo).map { songs ->
                                songs
                                    .filter {
                                        !it.song.id.startsWith("spotify:") &&
                                            !it.song.id.startsWith("spotify//") &&
                                            !it.song.id.startsWith("soundcloud:")
                                    }.filterExplicit(hideExplicit)
                            }
                        }

                        else -> {
                            flow { emit(emptyList()) }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh() {
            if (_isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    when (playlist) {
                        "liked" -> {
                            syncUtils.syncLikedSongs(authoritative = true, propagateFailures = true)
                            // Reload and persist Spotify liked songs
                            val spotifySongs = getSpotifyLikedSongs()
                            persistSpotifyLikedSongsToDatabase(spotifySongs)
                            _spotifyLikedSongs.value = spotifySongs.map { it.toSong() }
                        }
                        "liked_ytm" -> {
                            syncUtils.syncLikedSongs(authoritative = true, propagateFailures = true)
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun syncLikedSongs() {
            refresh()
        }
    }
