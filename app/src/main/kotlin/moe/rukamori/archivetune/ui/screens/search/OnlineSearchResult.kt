/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.ui.component.ListItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.applemusic.AppleMusicCatalog
import moe.rukamori.archivetune.applemusic.AppleMusicPlaybackResolver
import moe.rukamori.archivetune.applemusic.AppleMusicSearchItem
import moe.rukamori.archivetune.constants.DefaultSearchSourceKey
import moe.rukamori.archivetune.constants.SearchProvider
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifyPlaylistQueue
import moe.rukamori.archivetune.ui.component.ChipsRow
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.SearchSourceCycler
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.menu.SpotifyTrackMenu
import moe.rukamori.archivetune.ui.menu.SoundCloudTrackMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.viewmodels.OnlineSearchFilter
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    searchSort: OnlineSearchSort,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val searchFilter by viewModel.searchFilter.collectAsStateWithLifecycle()
    // [FIX-ZCODE] Wave 6 (owner request): donor-style provider switching. When the
    // search-bar picker selects a provider, the "All" results view focuses on that
    // provider instead of the merged YouTube-centric list.
    // [FIX-ZCODE] Wave 8 (owner request): the provider preference, writable — the
    // results-header cycler advances it and the search-bar picker stays in sync.
    var searchProvider by rememberEnumPreference(DefaultSearchSourceKey, SearchProvider.YOUTUBE)
    val effectiveSearchFilter =
        if (searchFilter == OnlineSearchFilter.All) {
            when (searchProvider) {
                SearchProvider.SPOTIFY -> OnlineSearchFilter.Spotify
                SearchProvider.SOUNDCLOUD -> OnlineSearchFilter.SoundCloud
                else -> OnlineSearchFilter.All
            }
        } else {
            searchFilter
        }

    val appleMusicProviderActive =
        searchProvider == SearchProvider.APPLE_MUSIC && effectiveSearchFilter == OnlineSearchFilter.All
    // [FIX-ZCODE] Wave 9: multi-source album search — with the Spotify provider active the
    // dedicated Albums filter shows Spotify Web API albums instead of YouTube Music only.
    val spotifyAlbumsFilterActive =
        (searchFilter as? OnlineSearchFilter.YouTube)?.filter == FILTER_ALBUM &&
            searchProvider == SearchProvider.SPOTIFY
    LaunchedEffect(spotifyAlbumsFilterActive, viewModel.query) {
        if (spotifyAlbumsFilterActive) viewModel.loadSpotifyIfNeeded()
    }
    var appleMusicItems by remember(viewModel.query) { mutableStateOf<List<AppleMusicSearchItem>?>(null) }
    var appleMusicFailed by remember(viewModel.query) { mutableStateOf(false) }
    LaunchedEffect(viewModel.query, appleMusicProviderActive) {
        if (!appleMusicProviderActive) return@LaunchedEffect
        val currentQuery = viewModel.query
        if (currentQuery.isBlank()) {
            appleMusicItems = emptyList()
            return@LaunchedEffect
        }
        appleMusicItems = null
        appleMusicFailed = false
        val page =
            withContext(Dispatchers.IO) {
                runCatching { AppleMusicCatalog.searchPage(currentQuery, limit = 30, offset = 0) }.getOrNull()
            }
        if (page == null) {
            appleMusicFailed = true
            appleMusicItems = emptyList()
        } else {
            appleMusicItems = page.items
        }
    }
    val spotifyTracks by viewModel.spotifyTracks.collectAsStateWithLifecycle()
    val spotifyAlbums by viewModel.spotifyAlbums.collectAsStateWithLifecycle()
    val spotifyPlaylists by viewModel.spotifyPlaylists.collectAsStateWithLifecycle()
    val soundCloudTracks by viewModel.soundCloudTracks.collectAsStateWithLifecycle()
    val soundCloudPlaylists by viewModel.soundCloudPlaylists.collectAsStateWithLifecycle()
    val soundCloudUsers by viewModel.soundCloudUsers.collectAsStateWithLifecycle()
    val database = LocalDatabase.current
    val uriHandler = LocalUriHandler.current
    val searchSummary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            (searchFilter as? OnlineSearchFilter.YouTube)?.filter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }
    val allModeSections =
        buildList<SearchSummary> {
            searchSummary
                ?.summaries
                ?.forEach { summary ->
                    if (summary.items.isNotEmpty()) {
                        add(SearchSummary(title = summary.title, items = viewModel.sortedItems(summary.items, searchSort)))
                    }
                }

            val existingTitles = map { it.title.lowercase() }.toSet()
            listOf(
                FILTER_SONG to stringResource(R.string.filter_songs),
                FILTER_VIDEO to stringResource(R.string.filter_videos),
                FILTER_ALBUM to stringResource(R.string.filter_albums),
                FILTER_ARTIST to stringResource(R.string.filter_artists),
                FILTER_PODCAST to stringResource(R.string.podcast),
                FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
            ).forEach { (sectionFilter, sectionTitle) ->
                if (!existingTitles.contains(sectionTitle.lowercase())) {
                    viewModel.viewStateMap[sectionFilter.value]
                        ?.items
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { items ->
                            add(SearchSummary(title = sectionTitle, items = viewModel.sortedItems(items, searchSort)))
                        }
                }
            }
        }
    val isAllModeLoaded =
        searchSummary != null ||
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_PODCAST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            ).all { viewModel.viewStateMap.containsKey(it.value) }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val longClick: (() -> Unit)? =
            if (item is PodcastItem || item is EpisodeItem) {
                null
            } else {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is SongItem -> {
                                YouTubeSongMenu(
                                    song = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is AlbumItem -> {
                                YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is ArtistItem -> {
                                YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is PlaylistItem -> {
                                YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = coroutineScope,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is PodcastItem, is EpisodeItem -> Unit
                        }
                    }
                }
            }
        YouTubeListItem(
            item = item,
            viewCountText = (item as? SongItem)?.viewCountText,
            isActive =
                when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
            isPlaying = isPlaying,
            trailingContent = {
                if (longClick != null) {
                    IconButton(onClick = longClick) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                }
            },
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                }

                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                }

                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                }

                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                }

                                is PodcastItem -> {
                                    navController.navigate("podcast/${android.net.Uri.encode(item.browseId)}")
                                }

                                is EpisodeItem -> {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = item.podcast?.name ?: item.title,
                                            items = listOf(item.toMediaItem()),
                                        ),
                                    )
                                }
                            }
                        },
                        onLongClick = longClick,
                    ).animateItem(),
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top).add(WindowInsets(top = AppBarHeight)))
                    .fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipsRow(
                    chips =
                        listOf(
                            OnlineSearchFilter.Spotify to stringResource(R.string.filter_spotify),
                            OnlineSearchFilter.SoundCloud to stringResource(R.string.filter_soundcloud),
                            OnlineSearchFilter.All to stringResource(R.string.filter_all),
                            OnlineSearchFilter.YouTube(FILTER_SONG) to stringResource(R.string.filter_songs),
                            OnlineSearchFilter.YouTube(FILTER_VIDEO) to stringResource(R.string.filter_videos),
                            OnlineSearchFilter.YouTube(FILTER_ALBUM) to stringResource(R.string.filter_albums),
                            OnlineSearchFilter.YouTube(FILTER_ARTIST) to stringResource(R.string.filter_artists),
                            OnlineSearchFilter.YouTube(FILTER_PODCAST) to stringResource(R.string.podcast),
                            OnlineSearchFilter.YouTube(FILTER_COMMUNITY_PLAYLIST) to stringResource(R.string.filter_community_playlists),
                            OnlineSearchFilter.YouTube(FILTER_FEATURED_PLAYLIST) to stringResource(R.string.filter_featured_playlists),
                        ),
                    currentValue = searchFilter,
                    onValueUpdate = {
                        if (viewModel.searchFilter.value != it) {
                            viewModel.searchFilter.value = it
                        }
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    },
                    icons =
                        mapOf(
                            OnlineSearchFilter.Spotify to R.drawable.spotify_icon,
                            OnlineSearchFilter.SoundCloud to R.drawable.soundcloud_icon,
                            OnlineSearchFilter.All to R.drawable.search,
                            OnlineSearchFilter.YouTube(FILTER_SONG) to R.drawable.music_note,
                            OnlineSearchFilter.YouTube(FILTER_VIDEO) to R.drawable.slow_motion_video,
                            OnlineSearchFilter.YouTube(FILTER_ALBUM) to R.drawable.album,
                            OnlineSearchFilter.YouTube(FILTER_ARTIST) to R.drawable.person,
                            OnlineSearchFilter.YouTube(FILTER_PODCAST) to R.drawable.mic,
                            OnlineSearchFilter.YouTube(FILTER_COMMUNITY_PLAYLIST) to R.drawable.queue_music,
                            OnlineSearchFilter.YouTube(FILTER_FEATURED_PLAYLIST) to R.drawable.playlist_play,
                        ),
                    modifier = Modifier.weight(1f),
                )
                // [FIX-ZCODE] Wave 8 (owner request): source switcher on the results
                // screen itself — one button, tap advances the provider, and the
                // filter follows so results actually come from the chosen source.
                SearchSourceCycler(
                    currentProvider = searchProvider,
                    onAdvance = { next ->
                        searchProvider = next
                        viewModel.searchFilter.value =
                            when (next) {
                                SearchProvider.SPOTIFY -> OnlineSearchFilter.Spotify
                                SearchProvider.SOUNDCLOUD -> OnlineSearchFilter.SoundCloud
                                else -> OnlineSearchFilter.All
                            }
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
            }
        }

        LazyColumn(
            state = lazyListState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .add(WindowInsets(top = 8.dp))
                    .asPaddingValues(),
            modifier = Modifier.weight(1f),
        ) {
            when (effectiveSearchFilter) {
                OnlineSearchFilter.Spotify -> {
                    val tracks = spotifyTracks.orEmpty()
                    val albums = spotifyAlbums.orEmpty()
                    val playlists = spotifyPlaylists.orEmpty()
                    val hasAny = tracks.isNotEmpty() || albums.isNotEmpty() || playlists.isNotEmpty()

                    if (hasAny) {
                        if (tracks.isNotEmpty()) {
                            item(key = "header_spotify_tracks", contentType = "section_header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.filter_songs),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            itemsIndexed(
                                items = tracks,
                                key = { _, track -> "spotify_${track.id}" },
                                contentType = { _, _ -> "spotify_track" },
                            ) { index, track ->
                                val cleanId = remember(track.id) {
                                    track.id
                                        .removePrefix("spotify:track:")
                                        .removePrefix("spotify://track/")
                                        .removePrefix("spotify://")
                                        .substringBefore("?")
                                }
                                val fullId = "spotify:track:$cleanId"
                                val isActive = mediaMetadata?.id == fullId || mediaMetadata?.spotifyTrackId == cleanId

                                val songItem = remember(track) {
                                    SongItem(
                                        id = fullId,
                                        title = track.name,
                                        artists = track.artists.map { Artist(name = it.name, id = it.id) },
                                        album = track.album?.let { Album(name = it.name, id = it.id.orEmpty()) },
                                        duration = if (track.durationMs > 0) track.durationMs / 1000 else null,
                                        thumbnail = SpotifyMapper.getTrackThumbnail(track).orEmpty(),
                                        explicit = track.explicit,
                                    )
                                }

                                val onTrackClick: () -> Unit = {
                                    if (isActive) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val targetTrack = tracks.getOrNull(index)
                                            val preloadItem = targetTrack?.let { SpotifyPlaybackResolver.resolveToMetadata(it) }
                                            withContext(Dispatchers.Main) {
                                                playerConnection.playQueue(
                                                    SpotifyPlaylistQueue(
                                                        title = "Spotify: ${viewModel.query}",
                                                        initialTracks = tracks,
                                                        startIndex = index,
                                                        preloadItem = preloadItem,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }

                                val onTrackLongClick: () -> Unit = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SpotifyTrackMenu(
                                            track = track,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }

                                YouTubeListItem(
                                    item = songItem,
                                    isActive = isActive,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(onClick = onTrackLongClick) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = onTrackClick,
                                                onLongClick = onTrackLongClick,
                                            ).animateItem(),
                                )
                            }
                        }

                        if (albums.isNotEmpty()) {
                            item(key = "divider_spotify_albums", contentType = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }

                            item(key = "header_spotify_albums", contentType = "section_header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.filter_albums),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            items(
                                items = albums,
                                key = { "spotify_album_${it.id}" },
                                contentType = { "spotify_album" },
                            ) { album ->
                                ListItem(
                                    title = album.name,
                                    subtitle = album.artists.joinToString { it.name } +
                                        (album.releaseDate?.take(4)?.let { " • $it" } ?: ""),
                                    thumbnailContent = {
                                        AsyncImage(
                                            model = album.images.firstOrNull()?.url,
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(ListThumbnailSize)
                                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.spotify_icon),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("spotify_playlist/album:${album.id}")
                                            },
                                )
                            }
                        }

                        if (playlists.isNotEmpty()) {
                            item(key = "divider_spotify_playlists", contentType = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }

                            item(key = "header_spotify_playlists", contentType = "section_header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.spotify_playlists),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            items(
                                items = playlists,
                                key = { "spotify_playlist_${it.id}" },
                                contentType = { "spotify_playlist" },
                            ) { playlist ->
                                ListItem(
                                    title = playlist.name,
                                    subtitle = playlist.owner?.displayName ?: "Spotify",
                                    thumbnailContent = {
                                        AsyncImage(
                                            model = SpotifyMapper.getPlaylistThumbnail(playlist),
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(ListThumbnailSize)
                                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.spotify_icon),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("spotify_playlist/${playlist.id}")
                                            },
                                )
                            }
                        }

                        if (viewModel.isSpotifyLoading) {
                            item(key = "loading_spotify", contentType = "loading") {
                                ShimmerHost {
                                    repeat(3) {
                                        ListItemPlaceHolder()
                                    }
                                }
                            }
                        }
                    } else if (viewModel.isSpotifyLoading) {
                        item(key = "initial_loading_spotify", contentType = "loading") {
                            ShimmerHost {
                                repeat(8) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    } else if (viewModel.isSpotifyError) {
                        item(key = "error_spotify", contentType = "empty") {
                            EmptyPlaceholder(
                                icon = R.drawable.spotify_icon,
                                text = stringResource(R.string.spotify_not_connected),
                            )
                        }
                    } else {
                        item(key = "empty_spotify", contentType = "empty") {
                            EmptyPlaceholder(
                                icon = R.drawable.search,
                                text = stringResource(R.string.no_results_found),
                            )
                        }
                    }
                }

                OnlineSearchFilter.SoundCloud -> {
                    val tracks = soundCloudTracks.orEmpty()
                    val playlists = soundCloudPlaylists.orEmpty()
                    val users = soundCloudUsers.orEmpty()
                    val hasAny = tracks.isNotEmpty() || playlists.isNotEmpty() || users.isNotEmpty()

                    if (hasAny) {
                        if (tracks.isNotEmpty()) {
                            item(key = "header_sc_tracks", contentType = "section_header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFFF5500)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.soundcloud_tracks),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            itemsIndexed(
                                items = tracks,
                                key = { _, track -> "sc_track_${track.trackId}" },
                                contentType = { _, _ -> "sc_track" },
                            ) { index, track ->
                                val numericId = remember(track.trackId) {
                                    track.trackId.removePrefix("soundcloud:tracks:").substringBefore("?")
                                }
                                val fullId = "soundcloud:tracks:$numericId"
                                val isActive = mediaMetadata?.id == fullId

                                val songItem = remember(track) {
                                    SongItem(
                                        id = fullId,
                                        title = track.title,
                                        artists = listOf(Artist(name = track.artist, id = null)),
                                        album = null,
                                        duration = track.durationMs?.let { if (it > 0) (it / 1000).toInt() else null },
                                        thumbnail = track.artworkUrl.orEmpty(),
                                        explicit = false,
                                    )
                                }

                                val onTrackClick: () -> Unit = {
                                    if (isActive) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val queueItems = tracks.map { scTrack ->
                                                val mMetadata = SoundCloudAudioProvider.toMediaMetadata(scTrack)
                                                database.insert(mMetadata)
                                                mMetadata.toMediaItem()
                                            }
                                            withContext(Dispatchers.Main) {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = "SoundCloud: ${viewModel.query}",
                                                        items = queueItems,
                                                        startIndex = index,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }

                                // [FIX-ZCODE] Wave 9: long-press / "..." opens the SoundCloud track
                                // menu (like, library, download) exactly like Spotify rows do.
                                val onTrackLongClick: () -> Unit = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SoundCloudTrackMenu(
                                            track = track,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }

                                YouTubeListItem(
                                    item = songItem,
                                    isActive = isActive,
                                    isPlaying = isPlaying,
                                    isSwipeable = false,
                                    trailingContent = {
                                        IconButton(onClick = onTrackLongClick) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = onTrackClick,
                                                onLongClick = onTrackLongClick,
                                            ).animateItem(),
                                )
                            }
                        }

                        if (playlists.isNotEmpty()) {
                            item(key = "divider_sc_playlists", contentType = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }

                            item(key = "header_sc_playlists", contentType = "section_header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFFF5500)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.soundcloud_playlists),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            items(
                                items = playlists,
                                key = { "sc_playlist_${it.playlistId}" },
                                contentType = { "sc_playlist" },
                            ) { playlist ->
                                ListItem(
                                    title = playlist.title,
                                    subtitle = "${playlist.trackCount} ${stringResource(R.string.filter_songs)}",
                                    thumbnailContent = {
                                        AsyncImage(
                                            model = playlist.artworkUrl,
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(ListThumbnailSize)
                                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.soundcloud_icon),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (playlist.tracks.isNotEmpty()) {
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val queueItems = playlist.tracks.map { scTrack ->
                                                            val trackNumId = scTrack.trackId.removePrefix("soundcloud:tracks:").substringBefore("?")
                                                            val mMetadata = MediaMetadata(
                                                                id = "soundcloud:tracks:$trackNumId",
                                                                title = scTrack.title,
                                                                artists = listOf(MediaMetadata.Artist(id = null, name = scTrack.artist)),
                                                                duration = if ((scTrack.durationMs ?: 0L) > 0) ((scTrack.durationMs ?: 0L) / 1000L).toInt() else -1,
                                                                thumbnailUrl = scTrack.artworkUrl,
                                                            )
                                                            database.query {
                                                                upsert(mMetadata.toSongEntity())
                                                            }
                                                            mMetadata.toMediaItem()
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            playerConnection.playQueue(
                                                                ListQueue(
                                                                    title = playlist.title,
                                                                    items = queueItems,
                                                                    startIndex = 0,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                } else if (playlist.permalinkUrl.isNotBlank()) {
                                                    runCatching { uriHandler.openUri(playlist.permalinkUrl) }
                                                }
                                            },
                                )
                            }
                        }

                        if (users.isNotEmpty()) {
                            item(key = "divider_sc_users", contentType = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }

                            item(key = "header_sc_users", contentType = "section_header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFFF5500)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.soundcloud_artists),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            items(
                                items = users,
                                key = { "sc_user_${it.userId}" },
                                contentType = { "sc_user" },
                            ) { user ->
                                ListItem(
                                    title = user.username,
                                    subtitle = user.trackCount?.let { "$it ${stringResource(R.string.filter_songs)}" } ?: stringResource(R.string.filter_artists),
                                    thumbnailContent = {
                                        AsyncImage(
                                            model = user.avatarUrl,
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(ListThumbnailSize)
                                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.soundcloud_icon),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (user.permalinkUrl.isNotBlank()) {
                                                    runCatching { uriHandler.openUri(user.permalinkUrl) }
                                                }
                                            },
                                )
                            }
                        }

                        if (viewModel.isSoundCloudLoading) {
                            item(key = "loading_sc", contentType = "loading") {
                                ShimmerHost {
                                    repeat(3) {
                                        ListItemPlaceHolder()
                                    }
                                }
                            }
                        }
                    } else if (viewModel.isSoundCloudLoading) {
                        item(key = "initial_loading_sc", contentType = "loading") {
                            ShimmerHost {
                                repeat(8) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    } else {
                        item(key = "empty_sc", contentType = "empty") {
                            EmptyPlaceholder(
                                icon = R.drawable.soundcloud_icon,
                                text = stringResource(R.string.no_results_found),
                            )
                        }
                    }
                }

                OnlineSearchFilter.All -> {
                    if (searchProvider == SearchProvider.APPLE_MUSIC) {
                        // [FIX-ZCODE] Wave 6: Apple Music provider results (iTunes catalog search),
                        // rendered in-process instead of the merged YouTube-centric view.
                        appleMusicProviderSection(
                            query = viewModel.query,
                            items = appleMusicItems,
                            failed = appleMusicFailed,
                            playerConnection = playerConnection,
                            coroutineScope = coroutineScope,
                        )
                    } else {
                    allModeSections.forEachIndexed { index, summary ->
                        if (index > 0) {
                            item(key = "divider_$index", contentType = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }

                        item(
                            key = "section_header_${summary.title}_$index",
                            contentType = "section_header",
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = summary.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        itemsIndexed(
                            items = viewModel.sortedItems(summary.items, searchSort),
                            key = { itemIndex, item -> "${summary.title}/${item.id}/$itemIndex" },
                            contentType = { _, _ -> "search_result" },
                        ) { _, item ->
                            ytItemContent(item)
                        }

                        item(
                            key = "section_spacer_${summary.title}_$index",
                            contentType = "section_spacer",
                        ) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (allModeSections.isEmpty() && isAllModeLoaded) {
                        item(key = "empty_all", contentType = "empty") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text =
                                        if (viewModel.isYouTubeBlocked) {
                                            stringResource(R.string.youtube_search_unavailable)
                                        } else {
                                            stringResource(R.string.no_results_found)
                                        },
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.searchFilter.value = OnlineSearchFilter.Spotify
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.spotify_icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.search_in_spotify))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.searchFilter.value = OnlineSearchFilter.SoundCloud
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5500)),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.soundcloud_icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.search_in_soundcloud),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    }
                }

                is OnlineSearchFilter.YouTube -> {
                    if (spotifyAlbumsFilterActive) {
                        // [FIX-ZCODE] Wave 9: Spotify albums for the Albums filter when the
                        // Spotify provider is active (route reuses the Spotify album screen).
                        val albums = spotifyAlbums.orEmpty()
                        if (albums.isNotEmpty()) {
                            items(
                                items = albums,
                                key = { "spotify_album_filter_${it.id}" },
                                contentType = { "spotify_album" },
                            ) { album ->
                                ListItem(
                                    title = album.name,
                                    subtitle = album.artists.joinToString { it.name } +
                                        (album.releaseDate?.take(4)?.let { " • $it" } ?: ""),
                                    thumbnailContent = {
                                        AsyncImage(
                                            model = album.images.firstOrNull()?.url,
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(ListThumbnailSize)
                                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.spotify_icon),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("spotify_playlist/album:${album.id}")
                                            },
                                )
                            }
                        } else if (viewModel.isSpotifyLoading) {
                            item(key = "loading_spotify_albums", contentType = "loading") {
                                ShimmerHost {
                                    repeat(3) {
                                        ListItemPlaceHolder()
                                    }
                                }
                            }
                        } else {
                            item(key = "empty_spotify_albums", contentType = "empty") {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found),
                                )
                            }
                        }
                    } else {
                    items(
                        items = viewModel.sortedItems(itemsPage?.items.orEmpty().distinctBy { it.id }, searchSort),
                        key = { "filtered_${it.id}" },
                        contentType = { "search_result" },
                        itemContent = ytItemContent,
                    )

                    if (itemsPage?.continuation != null) {
                        item(key = "loading", contentType = "loading") {
                            ShimmerHost {
                                repeat(3) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    if (itemsPage?.items?.isEmpty() == true) {
                        item(key = "empty_filtered", contentType = "empty") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text =
                                        if (viewModel.isYouTubeBlocked) {
                                            stringResource(R.string.youtube_search_unavailable)
                                        } else {
                                            stringResource(R.string.no_results_found)
                                        },
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.searchFilter.value = OnlineSearchFilter.Spotify
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.spotify_icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.search_in_spotify))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.searchFilter.value = OnlineSearchFilter.SoundCloud
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5500)),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.soundcloud_icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.search_in_soundcloud),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }

            if (searchFilter == OnlineSearchFilter.All && allModeSections.isEmpty() && !isAllModeLoaded ||
                searchFilter is OnlineSearchFilter.YouTube && itemsPage == null
            ) {
                item(key = "initial_loading", contentType = "loading") {
                    ShimmerHost {
                        repeat(8) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }
        }
    }
}

// [FIX-ZCODE] Wave 6: Apple Music provider results inside the unified online search
// screen (iTunes catalog search; tracks are resolved to a YouTube source on tap, same
// fallback path the Apple Music flow uses elsewhere in the fork).
private fun LazyListScope.appleMusicProviderSection(
    query: String,
    items: List<AppleMusicSearchItem>?,
    failed: Boolean,
    playerConnection: PlayerConnection,
    coroutineScope: CoroutineScope,
) {
    item(key = "apple_music_section_header", contentType = "section_header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.search_source_apple_music),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    when {
        items == null -> {
            item(key = "apple_music_loading", contentType = "loading") {
                ShimmerHost {
                    repeat(6) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }

        items.orEmpty().isEmpty() -> {
            item(key = "apple_music_empty", contentType = "empty") {
                EmptyPlaceholder(
                    icon = R.drawable.apple_music_icon,
                    text =
                        if (failed) {
                            stringResource(R.string.error_unknown)
                        } else {
                            stringResource(R.string.no_results_found)
                        },
                )
            }
        }

        else -> {
            itemsIndexed(
                items = items.orEmpty(),
                key = { _, item -> "apple_music_${item.key}" },
                contentType = { _, _ -> "apple_music_item" },
            ) { _, item ->
                AppleMusicSearchResultRow(
                    item = item,
                    playerConnection = playerConnection,
                    coroutineScope = coroutineScope,
                )
            }
        }
    }
}

@Composable
private fun AppleMusicSearchResultRow(
    item: AppleMusicSearchItem,
    playerConnection: PlayerConnection,
    coroutineScope: CoroutineScope,
) {
    val context = LocalContext.current
    var resolving by remember(item.key) { mutableStateOf(false) }

    val subtitle =
        when (item) {
            is AppleMusicSearchItem.Track -> item.artist
            is AppleMusicSearchItem.Album -> moe.rukamori.archivetune.utils.joinByBullet(item.artist)
            is AppleMusicSearchItem.Artist -> item.genre
        }

    val onClick: () -> Unit = {
        when (item) {
            is AppleMusicSearchItem.Track -> {
                if (!resolving) {
                    resolving = true
                    coroutineScope.launch {
                        try {
                            val song =
                                withContext(Dispatchers.IO) {
                                    AppleMusicPlaybackResolver.resolveTrack(item)
                                }
                            if (song != null) {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(song.toMediaMetadata()),
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.apple_music_track_unavailable),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } finally {
                            resolving = false
                        }
                    }
                }
            }

            else -> Unit
        }
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        title = item.title,
        subtitle = subtitle,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = item.artworkUrl,
                isActive = false,
                isPlaying = false,
                shape =
                    when (item) {
                        is AppleMusicSearchItem.Artist -> CircleShape
                        else -> RoundedCornerShape(ThumbnailCornerRadius)
                    },
                placeholderIconRes =
                    when (item) {
                        is AppleMusicSearchItem.Track -> R.drawable.music_note
                        is AppleMusicSearchItem.Album -> R.drawable.album
                        is AppleMusicSearchItem.Artist -> R.drawable.person
                    },
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = {
            if (resolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.apple_music_icon),
                    contentDescription = stringResource(R.string.search_source_apple_music),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}
