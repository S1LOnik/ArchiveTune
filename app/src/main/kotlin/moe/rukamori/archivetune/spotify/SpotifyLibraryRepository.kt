/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpotifyAccessTokenExpiresAtKey
import moe.rukamori.archivetune.constants.SpotifyAccessTokenKey
import moe.rukamori.archivetune.constants.SpotifyAccountAvatarUrlKey
import moe.rukamori.archivetune.constants.SpotifyAccountNameKey
import moe.rukamori.archivetune.constants.SpotifyLibraryAlbumsCacheKey
import moe.rukamori.archivetune.constants.SpotifyLibraryPlaylistsCacheKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifySearchResult
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.session.SpotifySessionManager
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.utils.clearWebAuthSession
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLibraryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
        val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists.asStateFlow()

        // [FIX-ZCODE] T8: saved Spotify albums shown in the library like the stock client
        private val _savedAlbums = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
        val savedAlbums: StateFlow<List<SpotifyAlbum>> = _savedAlbums.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val tokenRefreshMutex = Mutex()

        suspend fun restoreCachedPlaylists() {
            withContext(Dispatchers.IO) {
                if (_playlists.value.isNotEmpty()) return@withContext
                val cached =
                    context.dataStore.data
                        .first()[SpotifyLibraryPlaylistsCacheKey]
                        .orEmpty()
                if (cached.isBlank()) return@withContext
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyPlaylist.serializer()),
                        cached,
                    )
                }.onSuccess { playlists ->
                    _playlists.value = playlists
                }.onFailure { error ->
                    reportException(error)
                    context.dataStore.edit { prefs ->
                        prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    }
                }
            }
        }

        suspend fun restoreCachedSavedAlbums() {
            withContext(Dispatchers.IO) {
                if (_savedAlbums.value.isNotEmpty()) return@withContext
                val cached =
                    context.dataStore.data
                        .first()[SpotifyLibraryAlbumsCacheKey]
                        .orEmpty()
                if (cached.isBlank()) return@withContext
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyAlbum.serializer()),
                        cached,
                    )
                }.onSuccess { albums ->
                    _savedAlbums.value = albums
                }.onFailure { error ->
                    reportException(error)
                    context.dataStore.edit { prefs ->
                        prefs.remove(SpotifyLibraryAlbumsCacheKey)
                    }
                }
            }
        }

        fun isAuthenticated(): Boolean =
            !Spotify.accessToken.isNullOrBlank() || (SpotifySessionManager.get()?.hasActiveSession() == true)

        suspend fun syncLikedSongsToDatabase(targetDatabase: MusicDatabase = database) =
            withContext(Dispatchers.IO) {
                runCatching {
                    ensureAuthenticated()
                    var offset = 0
                    val limit = 50
                    val baseSyncTime = java.time.LocalDateTime.now()

                    while (true) {
                        val page =
                            spotifyCallWithTokenRetry {
                                Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                            }
                        if (page.items.isEmpty()) break
                        val pageItems = page.items.filterNot { it.track.isLocal }
                        if (pageItems.isNotEmpty()) {
                            targetDatabase.transaction {
                                pageItems.forEachIndexed { itemIndex, savedTrack ->
                                    val track = savedTrack.track
                                    val cleanId = track.id.removePrefix("spotify:track:").removePrefix("spotify://")
                                    val songId = "spotify:track:$cleanId"
                                    val durationSec = (track.durationMs / 1000).toInt()
                                    val thumbUrl = SpotifyMapper.getTrackThumbnail(track)

                                    val parsedAddedAt = savedTrack.addedAt?.let { iso ->
                                        runCatching {
                                            java.time.OffsetDateTime.parse(iso).toLocalDateTime()
                                        }.getOrNull()
                                    }
                                    val targetTimestamp = parsedAddedAt
                                        ?: baseSyncTime.minusSeconds((offset + itemIndex).toLong())

                                    val songEntity = SongEntity(
                                        id = songId,
                                        title = track.name,
                                        duration = durationSec,
                                        thumbnailUrl = thumbUrl,
                                        liked = true,
                                        likedDate = targetTimestamp,
                                        inLibrary = targetTimestamp,
                                        explicit = track.explicit,
                                    )
                                    upsert(songEntity)

                                    track.artists.forEachIndexed { index, artist ->
                                        val artistId = "spotify_artist_${artist.name.hashCode()}"
                                        upsert(ArtistEntity(id = artistId, name = artist.name))
                                        insert(SongArtistMap(songId = songId, artistId = artistId, position = index))
                                    }
                                }
                            }
                        }
                        offset += page.items.size
                        if (offset >= page.total || page.items.size < limit) break
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                }
            }

        suspend fun restoreSession(): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                val prefs = context.dataStore.data.first()
                val token = prefs[SpotifyAccessTokenKey].orEmpty()
                val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                val accountName = prefs[SpotifyAccountNameKey].orEmpty()
                val avatarUrl = prefs[SpotifyAccountAvatarUrlKey]

                if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                    Spotify.accessToken = token
                    CoroutineScope(Dispatchers.IO).launch {
                        syncLikedSongsToDatabase()
                    }
                    return@withContext SpotifyAccountSession(
                        isAuthenticated = true,
                        accountName = accountName,
                        accountAvatarUrl = avatarUrl,
                    )
                }

                val spDc = prefs[SpotifySpDcKey].orEmpty()
                if (spDc.isBlank()) return@withContext SpotifyAccountSession()

                refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty())
                    .fold(
                        onSuccess = {
                            CoroutineScope(Dispatchers.IO).launch {
                                syncLikedSongsToDatabase()
                            }
                            val refreshed = context.dataStore.data.first()
                            SpotifyAccountSession(
                                isAuthenticated = true,
                                accountName = refreshed[SpotifyAccountNameKey].orEmpty(),
                                accountAvatarUrl = refreshed[SpotifyAccountAvatarUrlKey],
                            )
                        },
                        onFailure = {
                            if (it is CancellationException) throw it
                            reportException(it)
                            SpotifyAccountSession()
                        },
                    )
            }

        suspend fun connectWithCookies(
            spDc: String,
            spKey: String,
        ): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                var credentialsChanged = false
                context.dataStore.edit { prefs ->
                    credentialsChanged =
                        prefs[SpotifySpDcKey] != spDc || prefs[SpotifySpKeyKey].orEmpty() != spKey
                    prefs[SpotifySpDcKey] = spDc
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    if (spKey.isNotBlank()) {
                        prefs[SpotifySpKeyKey] = spKey
                    } else {
                        prefs.remove(SpotifySpKeyKey)
                    }
                    if (credentialsChanged) {
                        prefs.remove(SpotifyAccessTokenKey)
                        prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    }
                }
                if (credentialsChanged) Spotify.accessToken = null
                _playlists.value = emptyList()
                _savedAlbums.value = emptyList()
                _errorMessage.value = null
                refreshAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                CoroutineScope(Dispatchers.IO).launch {
                    syncLikedSongsToDatabase()
                }
                val prefs = context.dataStore.data.first()
                SpotifyAccountSession(
                    isAuthenticated = true,
                    accountName = prefs[SpotifyAccountNameKey].orEmpty(),
                    accountAvatarUrl = prefs[SpotifyAccountAvatarUrlKey],
                )
            }

        suspend fun logout() {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    prefs.remove(SpotifySpDcKey)
                    prefs.remove(SpotifySpKeyKey)
                    prefs.remove(SpotifyAccessTokenKey)
                    prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    prefs.remove(SpotifyAccountNameKey)
                    prefs.remove(SpotifyAccountAvatarUrlKey)
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    prefs.remove(SpotifyLibraryAlbumsCacheKey)
                }
                _playlists.value = emptyList()
                _savedAlbums.value = emptyList()
                _errorMessage.value = null
                Spotify.accessToken = null
                runCatching { clearWebAuthSession(context) }
                    .onFailure(::reportException)
            }
        }

        suspend fun refreshPlaylists(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                _isRefreshing.value = true
                _errorMessage.value = null
                try {
                    ensureAuthenticated()
                    refreshProfile()
                    val loaded = fetchAllPlaylists()
                    _playlists.value = loaded
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyLibraryPlaylistsCacheKey] =
                            spotifyCacheJson.encodeToString(
                                ListSerializer(SpotifyPlaylist.serializer()),
                                loaded,
                            )
                    }
                    // [FIX-ZCODE] T8: refresh saved albums alongside playlists; failure here
                    // must not fail the playlist refresh
                    runCatching { refreshSavedAlbums() }.onFailure(::reportException)
                    loaded
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportException(error)
                    _errorMessage.value = error.message
                    _playlists.value
                } finally {
                    _isRefreshing.value = false
                }
            }

        // [FIX-ZCODE] T8/W11: fetch all saved albums (GraphQL myAlbums with REST fallback) and cache them
        suspend fun refreshSavedAlbums(): List<SpotifyAlbum> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                val albums = ArrayList<SpotifyAlbum>()
                var offset = 0
                val limit = 50

                // 1. Try GraphQL libraryV3(filters: ["Albums"]) first — works with standard web player token
                val gqlResult =
                    runCatching {
                        while (true) {
                            val page =
                                spotifyCallWithTokenRetry {
                                    Spotify.myAlbums(limit = limit, offset = offset).getOrThrow()
                                }
                            if (page.items.isEmpty()) break
                            albums += page.items.filter { it.id.isNotBlank() }
                            offset += page.items.size
                            if (offset >= page.total || page.items.size < limit) break
                        }
                        albums
                    }

                // 2. If GraphQL failed or returned empty, try REST /v1/me/albums fallback
                if (gqlResult.isFailure || albums.isEmpty()) {
                    Timber.tag("SpotifyLibraryRepository").w(
                        gqlResult.exceptionOrNull(),
                        "GraphQL myAlbums failed or returned empty; attempting REST savedAlbums fallback",
                    )
                    runCatching {
                        offset = 0
                        while (true) {
                            val page =
                                spotifyCallWithTokenRetry {
                                    Spotify.savedAlbums(limit = limit, offset = offset).getOrThrow()
                                }
                            if (page.items.isEmpty()) break
                            albums += page.items.map { it.album }.filter { it.id.isNotBlank() }
                            offset += page.items.size
                            if (offset >= page.total || page.items.size < limit) break
                        }
                    }.onFailure { restErr ->
                        Timber.tag("SpotifyLibraryRepository").e(restErr, "REST savedAlbums also failed")
                    }
                }

                if (albums.isNotEmpty()) {
                    _savedAlbums.value = albums
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyLibraryAlbumsCacheKey] =
                            spotifyCacheJson.encodeToString(
                                ListSerializer(SpotifyAlbum.serializer()),
                                albums,
                            )
                    }
                } else if (_savedAlbums.value.isEmpty()) {
                    restoreCachedSavedAlbums()
                }
                _savedAlbums.value
            }

        suspend fun playlist(playlistId: String): SpotifyPlaylist =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.playlist(playlistId).getOrThrow()
                }
            }

        suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                val tracks = ArrayList<SpotifyTrack>()
                var offset = 0
                val limit = 50

                while (true) {
                    val page =
                        spotifyCallWithTokenRetry {
                            Spotify
                                .playlistTracks(
                                    playlistId = playlistId,
                                    limit = limit,
                                    offset = offset,
                                ).getOrThrow()
                        }
                    if (page.items.isEmpty()) break
                    val pageTracks = page.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
                    tracks += pageTracks
                    offset += page.items.size
                    if (offset >= page.total || page.items.size < limit) break
                }

                tracks
            }

        suspend fun likedSongs(): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                val tracks = ArrayList<SpotifyTrack>()
                var offset = 0
                val limit = 50

                while (true) {
                    val page =
                        spotifyCallWithTokenRetry {
                            Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                        }
                    if (page.items.isEmpty()) break
                    tracks += page.items.map { savedTrack -> savedTrack.track }.filterNot(SpotifyTrack::isLocal)
                    offset += page.items.size
                    if (offset >= page.total || page.items.size < limit) break
                }

                tracks
            }

        suspend fun searchTracks(
            query: String,
            limit: Int = 50,
            offset: Int = 0,
        ): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.search(
                        query = query,
                        types = listOf("track"),
                        limit = limit,
                        offset = offset,
                    ).getOrThrow().tracks?.items.orEmpty()
                }
            }

        suspend fun searchAll(
            query: String,
            limit: Int = 20,
            offset: Int = 0,
        ): SpotifySearchResult =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.search(
                        query = query,
                        types = listOf("track", "album", "playlist"),
                        limit = limit,
                        offset = offset,
                    ).getOrThrow()
                }
            }

        suspend fun album(albumId: String): SpotifyAlbum =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.album(albumId).getOrThrow()
                }
            }

        suspend fun addTracksToPlaylist(
            playlistId: String,
            trackUris: List<String>,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    ensureAuthenticated()
                    spotifyCallWithTokenRetry {
                        Spotify.addTracksToPlaylist(playlistId = playlistId, trackUris = trackUris).getOrThrow()
                    }
                }
            }

        suspend fun addToLibrary(
            uris: List<String>,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    ensureAuthenticated()
                    spotifyCallWithTokenRetry {
                        Spotify.addToLibrary(uris = uris).getOrThrow()
                    }
                }
            }

        suspend fun removeFromLibrary(
            uris: List<String>,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    ensureAuthenticated()
                    spotifyCallWithTokenRetry {
                        Spotify.removeFromLibrary(uris = uris).getOrThrow()
                    }
                }
            }

        suspend fun refresh(): List<SpotifyPlaylist> = refreshPlaylists()

        suspend fun getUserPlaylists(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                if (_playlists.value.isNotEmpty()) return@withContext _playlists.value
                restoreCachedPlaylists()
                if (_playlists.value.isNotEmpty()) return@withContext _playlists.value
                refreshPlaylists()
            }

        private suspend fun ensureAuthenticated() {
            if (!Spotify.accessToken.isNullOrBlank()) {
                val prefs = context.dataStore.data.first()
                val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                if (expiresAt == 0L || expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                    return
                }
            }

            val sessionManager = SpotifySessionManager.get()
            if (sessionManager?.hasActiveSession() == true) {
                val webToken = runCatching {
                    sessionManager.getWebToken()
                }.getOrNull()
                if (!webToken.isNullOrBlank()) {
                    Spotify.accessToken = webToken
                    return
                }
            }

            val prefs = context.dataStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) {
                val anonResult = SpotifyAuth.fetchAccessToken(allowAnonymous = true)
                if (anonResult.isSuccess) {
                    val anonToken = anonResult.getOrThrow()
                    Spotify.accessToken = anonToken.accessToken
                    return
                }
                throw IllegalStateException(context.getString(R.string.spotify_not_connected))
            }
            refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
        }

        private suspend fun refreshAccessToken(
            spDc: String,
            spKey: String,
            rejectedAccessToken: String? = null,
        ): Result<Unit> =
            try {
                tokenRefreshMutex.withLock {
                    val prefs = context.dataStore.data.first()
                    val storedAccessToken = prefs[SpotifyAccessTokenKey].orEmpty()
                    val storedExpiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                    val credentialsMatch =
                        prefs[SpotifySpDcKey].orEmpty() == spDc &&
                            prefs[SpotifySpKeyKey].orEmpty() == spKey
                    val canReuseStoredToken =
                        credentialsMatch &&
                            storedAccessToken.isNotBlank() &&
                            storedExpiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS &&
                            (rejectedAccessToken == null || storedAccessToken != rejectedAccessToken)

                    if (canReuseStoredToken) {
                        Spotify.accessToken = storedAccessToken
                        return@withLock Result.success(Unit)
                    }

                    val token = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                    Spotify.accessToken = token.accessToken
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyAccessTokenKey] = token.accessToken
                        prefs[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
                    }
                    refreshProfile()
                    Result.success(Unit)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }

        private suspend fun refreshProfile() {
            Spotify
                .me()
                .onSuccess { user ->
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyAccountNameKey] = user.displayName.orEmpty()
                        user.images
                            .firstOrNull()
                            ?.url
                            ?.let { prefs[SpotifyAccountAvatarUrlKey] = it }
                            ?: prefs.remove(SpotifyAccountAvatarUrlKey)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }

        private suspend fun fetchAllPlaylists(): List<SpotifyPlaylist> {
            val playlists = ArrayList<SpotifyPlaylist>()
            var offset = 0
            val limit = 50

            while (true) {
                val page =
                    spotifyCallWithTokenRetry {
                        Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
                    }
                if (page.items.isEmpty()) break
                playlists +=
                    page.items.map { playlist ->
                        if (playlist.tracks?.total != null) {
                            playlist
                        } else {
                            playlistTrackCount(playlist.id)
                                ?.let { playlist.copy(tracks = SpotifyPlaylistTracksRef(total = it)) }
                                ?: playlist
                        }
                    }
                offset += page.items.size
                if (offset >= page.total || page.items.size < limit) break
            }

            return playlists
        }

        private suspend fun playlistTrackCount(playlistId: String): Int? =
            try {
                spotifyCallWithTokenRetry {
                    Spotify
                        .playlistTracks(
                            playlistId = playlistId,
                            limit = 1,
                            offset = 0,
                        ).getOrThrow()
                }.total
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportException(error)
                null
            }

        private suspend fun <T> spotifyCallWithTokenRetry(block: suspend () -> T): T =
            Spotify.accessToken.let { rejectedAccessToken ->
                runCatching { block() }
                    .getOrElse { error ->
                        if ((error as? Spotify.SpotifyException)?.statusCode != 401) throw error
                        val prefs = context.dataStore.data.first()
                        val spDc = prefs[SpotifySpDcKey].orEmpty()
                        if (spDc.isBlank()) throw error
                        refreshAccessToken(
                            spDc = spDc,
                            spKey = prefs[SpotifySpKeyKey].orEmpty(),
                            rejectedAccessToken = rejectedAccessToken,
                        ).getOrThrow()
                        block()
                    }
            }

        companion object {
            private const val TOKEN_EXPIRY_GRACE_MS = 60_000L
            private val spotifyCacheJson =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
        }
    }

data class SpotifyAccountSession(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
)
