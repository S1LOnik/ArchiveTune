/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.home.hasVisibleContent
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu

@Immutable
private data class CoversMosaicTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val isActive: Boolean,
    val onClick: () -> Unit,
)

fun LazyListScope.renderOrderedHomeSection(
    sectionId: HomeSectionId,
    uiState: HomeUiState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    forgottenFavoritesGridState: LazyGridState,
    forgottenItemWidth: Dp,
    forgottenSnapLayoutInfoProvider: SnapLayoutInfoProvider,
    spotifyPlaylists: List<SpotifyPlaylist>,
    spotifyTracks: List<SpotifyTrack>,
    onAction: (HomeAction) -> Unit,
    sectionSpacer: LazyListScope.(String) -> Unit,
) {
    val remoteQuickPicks =
        uiState
            .takeIf { it.quickPicksMode == QuickPicks.QUICK_PICKS }
            ?.remoteQuickPicks
    when (sectionId) {
        HomeSectionId.QUICK_PICKS -> {
            if (remoteQuickPicks?.items?.isNotEmpty() == true) {
                item(
                    key = "home_remote_quick_picks_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.quick_picks),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_remote_quick_picks",
                    contentType = "quick_picks",
                ) {
                    RemoteQuickPicksSection(
                        section = remoteQuickPicks,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        displayMode = uiState.quickPicksDisplayMode,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        modifier = Modifier.animateItem(),
                    )
                }
            } else {
                val localPicks =
                    when {
                        uiState.quickPicksMode == QuickPicks.DONT_SHOW -> emptyList()
                        uiState.quickPicks.isNotEmpty() -> uiState.quickPicks
                        else -> uiState.downloadedSongs
                    }
                if (localPicks.isNotEmpty()) {
                    item(
                        key = "home_quick_picks_header",
                        contentType = "section_header",
                    ) {
                        HomeSectionHeader(
                            title = stringResource(R.string.quick_picks),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(
                        key = "home_quick_picks",
                        contentType = "quick_picks",
                    ) {
                        QuickPicksSection(
                            quickPicks = localPicks,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            displayMode = uiState.quickPicksDisplayMode,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        HomeSectionId.CONTINUE_LISTEN -> {
            if (uiState.keepListening.isNotEmpty()) {
                sectionSpacer("keep_listening")
                item(
                    key = "home_keep_listening_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.keep_listening),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_keep_listening",
                    contentType = "media_shelf",
                ) {
                    KeepListeningSection(
                        keepListening = uiState.keepListening,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.PINNED_PLAYLISTS -> {
            if (uiState.pinnedPlaylists.isNotEmpty()) {
                sectionSpacer("pinned_playlists")
                item(
                    key = "home_pinned_playlists_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.home_section_pinned_playlists),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_pinned_playlists",
                    contentType = "pinned_playlists",
                ) {
                    HomePinnedPlaylistsStrip(
                        playlists = uiState.pinnedPlaylists,
                        navController = navController,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.MIXES -> {
            if (uiState.mixPages.isNotEmpty()) {
                sectionSpacer("home_mix_pages")
                item(
                    key = "home_mix_pages_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.home_mix_pages_title),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_mix_pages",
                    contentType = "mix_pages",
                ) {
                    HomeMixPagesSection(
                        pages = uiState.mixPages,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        scope = scope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.SPOTIFY_PLAYLISTS -> {
            if (spotifyPlaylists.isNotEmpty()) {
                sectionSpacer("spotify_daily_discover")
                item(
                    key = "home_spotify_discover_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.your_daily_discover),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_spotify_discover",
                    contentType = "media_shelf",
                ) {
                    SpotifyDailyDiscoverSection(
                        playlists = spotifyPlaylists,
                        navController = navController,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.SPOTIFY_TRENDING -> {
            if (spotifyTracks.isNotEmpty()) {
                sectionSpacer("spotify_trending_songs")
                item(
                    key = "home_spotify_trending_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.trending_songs_for_you),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_spotify_trending",
                    contentType = "media_shelf",
                ) {
                    SpotifyTrendingSection(
                        tracks = spotifyTracks,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                        scope = scope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.COMMUNITY -> {
            uiState.communitySection?.takeIf { section -> section.featuredCards.isNotEmpty() }?.let { section ->
                sectionSpacer("community")
                item(
                    key = "home_community_header",
                    contentType = "section_header",
                ) {
                    val playAll =
                        remember(section.playEndpoint, playerConnection) {
                            section.playEndpoint?.let { endpoint ->
                                { playerConnection.playQueue(YouTubeQueue.playlist(endpoint)) }
                            }
                        }
                    HomePageSectionTitle(
                        section = section,
                        navController = navController,
                        onPlayAll = playAll,
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_community",
                    contentType = "featured_playlist_shelf",
                ) {
                    HomePageSectionContent(
                        section = section,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        onOpenRemoteItem = { itemId -> onAction(HomeAction.OpenRemoteItem(itemId)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.SPEED_DIAL -> {
            if (uiState.speedDialItems.isNotEmpty()) {
                sectionSpacer("speed_dial")
                item(
                    key = "home_speed_dial_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.speed_dial),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_speed_dial",
                    contentType = "speed_dial",
                ) {
                    SpeedDialSection(
                        speedDialItems = uiState.speedDialItems,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.ACCOUNT_PLAYLISTS -> {
            if (uiState.accountPlaylists.isNotEmpty()) {
                sectionSpacer("account_playlists")
                item(
                    key = "home_account_playlists",
                    contentType = "media_shelf",
                ) {
                    Column(modifier = Modifier.animateItem()) {
                        AccountPlaylistsTitle(
                            accountName = uiState.accountName,
                            accountImageUrl = uiState.accountImageUrl,
                            onClick = { navController.navigate("account") },
                        )
                        AccountPlaylistsSection(
                            accountPlaylists = uiState.accountPlaylists,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                        )
                    }
                }
            }
        }

        HomeSectionId.FORGOTTEN_FAVORITES -> {
            if (uiState.forgottenFavorites.isNotEmpty()) {
                sectionSpacer("forgotten_favorites")
                item(
                    key = "home_forgotten_favorites_header",
                    contentType = "section_header",
                ) {
                    HomeSectionHeader(
                        title = stringResource(R.string.forgotten_favorites),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(
                    key = "home_forgotten_favorites",
                    contentType = "song_shelf",
                ) {
                    ForgottenFavoritesSection(
                        forgottenFavorites = uiState.forgottenFavorites,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        horizontalLazyGridItemWidth = forgottenItemWidth,
                        lazyGridState = forgottenFavoritesGridState,
                        snapLayoutInfoProvider = forgottenSnapLayoutInfoProvider,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.SIMILAR -> {
            if (uiState.similarRecommendations.isNotEmpty()) {
                sectionSpacer("similar_recommendations")
                item(
                    key = "home_similar_recommendations",
                    contentType = "discovery_decks",
                ) {
                    SimilarRecommendationsSection(
                        recommendations = uiState.similarRecommendations,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        HomeSectionId.REMOTE_SECTIONS -> {
            uiState.homePage
                ?.sections
                .orEmpty()
                .filter(HomePage.Section::hasVisibleContent)
                .forEachIndexed { index, section ->
                    val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
                    sectionSpacer("remote_$sectionKey")
                    item(
                        key = "home_remote_header_$sectionKey",
                        contentType = "section_header",
                    ) {
                        val playAll =
                            remember(section.playEndpoint, playerConnection) {
                                section.playEndpoint?.let { endpoint ->
                                    { playerConnection.playQueue(YouTubeQueue.playlist(endpoint)) }
                                }
                            }
                        HomePageSectionTitle(
                            section = section,
                            navController = navController,
                            onPlayAll = playAll,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(
                        key = "home_remote_$sectionKey",
                        contentType = "media_shelf",
                    ) {
                        HomePageSectionContent(
                            section = section,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                            onOpenRemoteItem = { itemId -> onAction(HomeAction.OpenRemoteItem(itemId)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
        }
    }
}

fun LazyListScope.renderNowLayout(
    uiState: HomeUiState,
    orderedVisibleSections: List<HomeSectionId>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    forgottenFavoritesGridState: LazyGridState,
    forgottenItemWidth: Dp,
    forgottenSnapLayoutInfoProvider: SnapLayoutInfoProvider,
    spotifyPlaylists: List<SpotifyPlaylist>,
    spotifyTracks: List<SpotifyTrack>,
    onAction: (HomeAction) -> Unit,
    sectionSpacer: LazyListScope.(String) -> Unit,
) {
    // Above-the-fold compact block: Continue + Pinned Playlists + 4 personal quick tracks
    if (HomeSectionId.CONTINUE_LISTEN in orderedVisibleSections) {
        item(key = "now_continue_card", contentType = "now_continue") {
            val offlineCandidate =
                remember(uiState.downloadedSongs, uiState.keepListening, uiState.quickPicks, uiState.forgottenFavorites) {
                    HomeLayoutPolicy.selectOfflineContinueSong(
                        downloaded = uiState.downloadedSongs,
                        keepListening = uiState.keepListening,
                        quickPicks = uiState.quickPicks,
                        forgottenFavorites = uiState.forgottenFavorites,
                    )
                }
            NowContinueCard(
                activeMetadata = mediaMetadata,
                fallbackSong = offlineCandidate,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                isOffline = uiState.homePage == null && uiState.remoteQuickPicks == null,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).animateItem(),
            )
        }
    }

    if (HomeSectionId.PINNED_PLAYLISTS in orderedVisibleSections &&
        (uiState.pinnedPlaylists.isNotEmpty() || uiState.accountPlaylists.isNotEmpty())
    ) {
        item(key = "now_pinned_strip", contentType = "pinned_playlists") {
            HomePinnedPlaylistsStrip(
                playlists = uiState.pinnedPlaylists,
                remoteFallback = uiState.accountPlaylists,
                navController = navController,
                modifier = Modifier.padding(top = 6.dp).animateItem(),
            )
        }
    }

    if (HomeSectionId.QUICK_PICKS in orderedVisibleSections) {
        val remoteSongs =
            uiState.remoteQuickPicks?.items?.filterIsInstance<SongItem>().orEmpty().take(4)
        val localSongs =
            (uiState.quickPicks.ifEmpty { uiState.downloadedSongs }.ifEmpty { uiState.forgottenFavorites })
                .distinctBy { it.id }
                .take(4)
        if (remoteSongs.isNotEmpty() || localSongs.isNotEmpty()) {
            item(key = "now_quick_tracks_header", contentType = "section_header") {
                HomeSectionHeader(
                    title = stringResource(R.string.home_quick_tracks_title),
                    modifier = Modifier.animateItem(),
                )
            }
            if (remoteSongs.isNotEmpty()) {
                items(
                    items = remoteSongs,
                    key = { "now_yt_${it.id}" },
                    contentType = { "now_track" },
                ) { song ->
                    NowRemoteTrackRow(
                        song = song,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                    )
                }
            } else {
                items(
                    items = localSongs,
                    key = { "now_local_${it.id}" },
                    contentType = { "now_track" },
                ) { song ->
                    NowLocalTrackRow(
                        song = song,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                    )
                }
            }
        }
    }

    val remaining =
        orderedVisibleSections.filterNot {
            it in setOf(HomeSectionId.CONTINUE_LISTEN, HomeSectionId.PINNED_PLAYLISTS, HomeSectionId.QUICK_PICKS)
        }
    remaining.forEach { sectionId ->
        renderOrderedHomeSection(
            sectionId = sectionId,
            uiState = uiState,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            navController = navController,
            playerConnection = playerConnection,
            menuState = menuState,
            haptic = haptic,
            scope = scope,
            forgottenFavoritesGridState = forgottenFavoritesGridState,
            forgottenItemWidth = forgottenItemWidth,
            forgottenSnapLayoutInfoProvider = forgottenSnapLayoutInfoProvider,
            spotifyPlaylists = spotifyPlaylists,
            spotifyTracks = spotifyTracks,
            onAction = onAction,
            sectionSpacer = sectionSpacer,
        )
    }
}

fun LazyListScope.renderCoversLayout(
    uiState: HomeUiState,
    orderedVisibleSections: List<HomeSectionId>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    forgottenFavoritesGridState: LazyGridState,
    forgottenItemWidth: Dp,
    forgottenSnapLayoutInfoProvider: SnapLayoutInfoProvider,
    spotifyPlaylists: List<SpotifyPlaylist>,
    spotifyTracks: List<SpotifyTrack>,
    onAction: (HomeAction) -> Unit,
    sectionSpacer: LazyListScope.(String) -> Unit,
) {
    item(key = "covers_mosaic_section", contentType = "covers_mosaic") {
        CoversAsymmetricMosaicSection(
            uiState = uiState,
            mediaMetadata = mediaMetadata,
            navController = navController,
            playerConnection = playerConnection,
            modifier = Modifier.animateItem(),
        )
    }

    val listenAgainItems =
        (uiState.keepListening + uiState.forgottenFavorites)
            .distinctBy(LocalItem::stableHomeKey)
    if (HomeSectionId.CONTINUE_LISTEN in orderedVisibleSections && listenAgainItems.isNotEmpty()) {
        sectionSpacer("covers_listen_again")
        item(key = "covers_listen_again_header", contentType = "section_header") {
            HomeSectionHeader(
                title = stringResource(R.string.home_listen_again),
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "covers_listen_again_row", contentType = "media_shelf") {
            KeepListeningSection(
                keepListening = listenAgainItems,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                modifier = Modifier.animateItem(),
            )
        }
    }

    if (HomeSectionId.QUICK_PICKS in orderedVisibleSections) {
        val remoteSongs = uiState.remoteQuickPicks?.items?.filterIsInstance<SongItem>().orEmpty().take(8)
        val localSongs = uiState.quickPicks.ifEmpty { uiState.downloadedSongs }.distinctBy { it.id }.take(8)
        if (remoteSongs.isNotEmpty() || localSongs.isNotEmpty()) {
            sectionSpacer("covers_track_list")
            item(key = "covers_track_list_header", contentType = "section_header") {
                HomeSectionHeader(
                    title = stringResource(R.string.quick_picks),
                    modifier = Modifier.animateItem(),
                )
            }
            if (remoteSongs.isNotEmpty()) {
                items(
                    items = remoteSongs,
                    key = { "covers_yt_${it.id}" },
                    contentType = { "covers_track" },
                ) { song ->
                    NowRemoteTrackRow(
                        song = song,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                    )
                }
            } else {
                items(
                    items = localSongs,
                    key = { "covers_local_${it.id}" },
                    contentType = { "covers_track" },
                ) { song ->
                    NowLocalTrackRow(
                        song = song,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                    )
                }
            }
        }
    }

    val remaining =
        orderedVisibleSections.filterNot {
            it in setOf(HomeSectionId.QUICK_PICKS, HomeSectionId.CONTINUE_LISTEN, HomeSectionId.FORGOTTEN_FAVORITES)
        }
    remaining.forEach { sectionId ->
        renderOrderedHomeSection(
            sectionId = sectionId,
            uiState = uiState,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            navController = navController,
            playerConnection = playerConnection,
            menuState = menuState,
            haptic = haptic,
            scope = scope,
            forgottenFavoritesGridState = forgottenFavoritesGridState,
            forgottenItemWidth = forgottenItemWidth,
            forgottenSnapLayoutInfoProvider = forgottenSnapLayoutInfoProvider,
            spotifyPlaylists = spotifyPlaylists,
            spotifyTracks = spotifyTracks,
            onAction = onAction,
            sectionSpacer = sectionSpacer,
        )
    }
}

@Composable
private fun NowContinueCard(
    activeMetadata: MediaMetadata?,
    fallbackSong: Song?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = activeMetadata?.title ?: fallbackSong?.song?.title ?: return
    val subtitle =
        activeMetadata?.artists?.joinToString { it.name }
            ?: fallbackSong?.artists?.joinToString { it.name }.orEmpty()
    val thumb = activeMetadata?.thumbnailUrl ?: fallbackSong?.song?.thumbnailUrl
    val hasActive = activeMetadata != null

    Surface(
        onClick = {
            if (hasActive) {
                playerConnection.player.togglePlayPause()
            } else if (fallbackSong != null) {
                playerConnection.playQueue(
                    if (fallbackSong.song.isLocal || isOffline) {
                        ListQueue(items = listOf(fallbackSong.toMediaItem()))
                    } else {
                        YouTubeQueue.radio(fallbackSong.toMediaMetadata())
                    },
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(if (hasActive) R.string.home_continue_resume else R.string.home_continue_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isOffline) {
                        Text(
                            text = "· ${stringResource(R.string.home_offline_saved_badge)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    painter = painterResource(if (hasActive && isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun HomePinnedPlaylistsStrip(
    playlists: List<Playlist>,
    remoteFallback: List<PlaylistItem> = emptyList(),
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (playlists.isNotEmpty()) {
            items(playlists, key = { "pinned_local_${it.id}" }) { playlist ->
                Surface(
                    onClick = { navController.navigate("local_playlist/${playlist.id}") },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AsyncImage(
                            model = playlist.thumbnails.firstOrNull(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Text(
                            text = playlist.playlist.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(116.dp),
                        )
                    }
                }
            }
        } else {
            items(remoteFallback.take(8), key = { "pinned_yt_${it.id}" }) { playlist ->
                Surface(
                    onClick = { navController.navigate("online_playlist/${playlist.id}") },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AsyncImage(
                            model = playlist.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(116.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowLocalTrackRow(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
    haptic: HapticFeedback,
) {
    SongListItem(
        song = song,
        showInLibraryIcon = true,
        isActive = isActive,
        isPlaying = isPlaying,
        isSwipeable = false,
        trailingContent = {
            IconButton(
                onClick = {
                    menuState.show {
                        SongMenu(
                            originalSong = song,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            ) {
                Icon(painterResource(R.drawable.more_vert), contentDescription = null)
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isActive) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                if (song.song.isLocal) {
                                    ListQueue(items = listOf(song.toMediaItem()))
                                } else {
                                    YouTubeQueue.radio(song.toMediaMetadata())
                                },
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowRemoteTrackRow(
    song: SongItem,
    isActive: Boolean,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
    haptic: HapticFeedback,
) {
    YouTubeListItem(
        item = song,
        squareThumbnail = true,
        isActive = isActive,
        isPlaying = isPlaying,
        isSwipeable = false,
        trailingContent = {
            IconButton(
                onClick = {
                    menuState.show {
                        YouTubeSongMenu(
                            song = song,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            ) {
                Icon(painterResource(R.drawable.more_vert), contentDescription = null)
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isActive) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubeQueue(
                                    endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id),
                                    preloadItem = song.toMediaMetadata(),
                                ),
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubeSongMenu(
                                song = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                ),
    )
}

@Composable
private fun CoversAsymmetricMosaicSection(
    uiState: HomeUiState,
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val tiles =
        remember(
            uiState.speedDialItems,
            uiState.remoteQuickPicks,
            uiState.quickPicks,
            uiState.accountPlaylists,
            uiState.pinnedPlaylists,
            uiState.downloadedSongs,
            mediaMetadata?.id,
        ) {
            buildList {
                uiState.speedDialItems.forEach { item ->
                    when (item) {
                        is Album ->
                            add(
                                CoversMosaicTile(
                                    key = "album_${item.id}",
                                    title = item.album.title,
                                    subtitle = item.artists.joinToString { it.name },
                                    thumbnailUrl = item.album.thumbnailUrl,
                                    isActive = false,
                                    onClick = { navController.navigate("album/${item.id}") },
                                ),
                            )
                        is Playlist ->
                            add(
                                CoversMosaicTile(
                                    key = "playlist_${item.id}",
                                    title = item.playlist.name,
                                    subtitle = "",
                                    thumbnailUrl = item.thumbnails.firstOrNull(),
                                    isActive = false,
                                    onClick = { navController.navigate("local_playlist/${item.id}") },
                                ),
                            )
                        is Artist ->
                            add(
                                CoversMosaicTile(
                                    key = "artist_${item.id}",
                                    title = item.artist.name,
                                    subtitle = "",
                                    thumbnailUrl = item.artist.thumbnailUrl,
                                    isActive = false,
                                    onClick = { navController.navigate("artist/${item.id}") },
                                ),
                            )
                        is Song ->
                            add(
                                CoversMosaicTile(
                                    key = "song_${item.id}",
                                    title = item.song.title,
                                    subtitle = item.artists.joinToString { it.name },
                                    thumbnailUrl = item.song.thumbnailUrl,
                                    isActive = item.id == mediaMetadata?.id,
                                    onClick = {
                                        playerConnection.playQueue(
                                            if (item.song.isLocal) {
                                                ListQueue(items = listOf(item.toMediaItem()))
                                            } else {
                                                YouTubeQueue.radio(item.toMediaMetadata())
                                            },
                                        )
                                    },
                                ),
                            )
                    }
                }
                uiState.remoteQuickPicks?.items.orEmpty().forEach { item ->
                    when (item) {
                        is SongItem ->
                            add(
                                CoversMosaicTile(
                                    key = "yt_song_${item.id}",
                                    title = item.title,
                                    subtitle = item.artists.joinToString { it.name },
                                    thumbnailUrl = item.thumbnail,
                                    isActive = item.id == mediaMetadata?.id,
                                    onClick = {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                endpoint = item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                preloadItem = item.toMediaMetadata(),
                                            ),
                                        )
                                    },
                                ),
                            )
                        is AlbumItem ->
                            add(
                                CoversMosaicTile(
                                    key = "yt_album_${item.id}",
                                    title = item.title,
                                    subtitle = item.artists?.joinToString { it.name }.orEmpty(),
                                    thumbnailUrl = item.thumbnail,
                                    isActive = false,
                                    onClick = { navController.navigate("album/${item.id}") },
                                ),
                            )
                        is PlaylistItem ->
                            add(
                                CoversMosaicTile(
                                    key = "yt_playlist_${item.id}",
                                    title = item.title,
                                    subtitle = item.author?.name.orEmpty(),
                                    thumbnailUrl = item.thumbnail,
                                    isActive = false,
                                    onClick = { navController.navigate("online_playlist/${item.id}") },
                                ),
                            )
                        is ArtistItem ->
                            add(
                                CoversMosaicTile(
                                    key = "yt_artist_${item.id}",
                                    title = item.title,
                                    subtitle = "",
                                    thumbnailUrl = item.thumbnail,
                                    isActive = false,
                                    onClick = { navController.navigate("artist/${item.id}") },
                                ),
                            )
                        else -> Unit
                    }
                }
                uiState.pinnedPlaylists.forEach { playlist ->
                    add(
                        CoversMosaicTile(
                            key = "local_pl_${playlist.id}",
                            title = playlist.playlist.name,
                            subtitle = "",
                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                            isActive = false,
                            onClick = { navController.navigate("local_playlist/${playlist.id}") },
                        ),
                    )
                }
                (uiState.quickPicks + uiState.downloadedSongs).forEach { song ->
                    add(
                        CoversMosaicTile(
                            key = "song_${song.id}",
                            title = song.song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnailUrl = song.song.thumbnailUrl,
                            isActive = song.id == mediaMetadata?.id,
                            onClick = {
                                playerConnection.playQueue(
                                    if (song.song.isLocal) {
                                        ListQueue(items = listOf(song.toMediaItem()))
                                    } else {
                                        YouTubeQueue.radio(song.toMediaMetadata())
                                    },
                                )
                            },
                        ),
                    )
                }
            }.distinctBy(CoversMosaicTile::key).take(10)
        }

    if (tiles.isEmpty()) return
    val rows = remember(tiles.size) { CoversMosaicPlanner.planRows(tiles.size) }
    val titlesBelowCover = LocalDensity.current.fontScale >= 1.15f

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { rowSpec ->
            when (rowSpec) {
                is CoversMosaicRowSpec.SingleHero -> {
                    CoversMosaicCard(
                        tile = tiles[rowSpec.tile.index],
                        imageHeight = if (rowSpec.tile.isHero) 210.dp else 156.dp,
                        titlesBelowCover = titlesBelowCover,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is CoversMosaicRowSpec.AsymmetricPair -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CoversMosaicCard(
                            tile = tiles[rowSpec.primary.index],
                            imageHeight = 168.dp,
                            titlesBelowCover = titlesBelowCover,
                            modifier = Modifier.weight(1.3f),
                        )
                        CoversMosaicCard(
                            tile = tiles[rowSpec.secondary.index],
                            imageHeight = 168.dp,
                            titlesBelowCover = titlesBelowCover,
                            modifier = Modifier.weight(0.95f),
                        )
                    }
                }

                is CoversMosaicRowSpec.HeroAndStackTwo -> {
                    val smallHeight = 104.dp
                    val heroHeight = smallHeight * 2 + 10.dp
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CoversMosaicCard(
                            tile = tiles[rowSpec.hero.index],
                            imageHeight = if (titlesBelowCover) heroHeight + 28.dp else heroHeight,
                            titlesBelowCover = titlesBelowCover,
                            modifier = Modifier.weight(1.28f),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            CoversMosaicCard(
                                tile = tiles[rowSpec.top.index],
                                imageHeight = smallHeight,
                                titlesBelowCover = titlesBelowCover,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            CoversMosaicCard(
                                tile = tiles[rowSpec.bottom.index],
                                imageHeight = smallHeight,
                                titlesBelowCover = titlesBelowCover,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                is CoversMosaicRowSpec.EqualRow -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowSpec.tiles.forEach { spec ->
                            CoversMosaicCard(
                                tile = tiles[spec.index],
                                imageHeight = if (rowSpec.tiles.size >= 3) 114.dp else 148.dp,
                                titlesBelowCover = titlesBelowCover,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoversMosaicCard(
    tile: CoversMosaicTile,
    imageHeight: Dp,
    titlesBelowCover: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor =
        if (tile.isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        }

    Column(
        modifier = modifier.clip(shape).clickable(onClick = tile.onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(shape)
                    .border(BorderStroke(if (tile.isActive) 2.dp else 1.dp, borderColor), shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = tile.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (!titlesBelowCover) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.45f to Color.Black.copy(alpha = 0.08f),
                                    1f to Color.Black.copy(alpha = 0.82f),
                                ),
                            ),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tile.subtitle.isNotBlank()) {
                        Text(
                            text = tile.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (titlesBelowCover) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tile.subtitle.isNotBlank()) {
                    Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
