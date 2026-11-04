/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import moe.rukamori.archivetune.soundcloud.SoundCloudLibraryViewModel
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.utils.makeTimeString

private val SoundCloudOrange = Color(0xFFFF5500)

@Composable
fun LibrarySoundCloudScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: SoundCloudLibraryViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 16.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
        indicatorOffset = LibraryPullToRefreshIndicatorOffset,
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    top = LibraryHeaderContentPadding,
                    end = 16.dp,
                    bottom = playerAwareBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Search Input Row
            item(key = "sc_search_bar", contentType = "sc_search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_in_soundcloud),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = SoundCloudOrange,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SoundCloudOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Genre Chips Row
            item(key = "sc_genre_chips", contentType = "sc_chips") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(viewModel.genres) { genre ->
                        val isSelected = genre == selectedGenre && query.isEmpty()
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onGenreSelected(genre) },
                            label = { Text(genre) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SoundCloudOrange.copy(alpha = 0.22f),
                                    selectedLabelColor = SoundCloudOrange,
                                ),
                            border =
                                FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) SoundCloudOrange else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            // Loading state
            if (isLoading && tracks.isEmpty()) {
                item(key = "sc_loading") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    ) {
                        CircularProgressIndicator(
                            color = SoundCloudOrange,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            // Playlists Section (if any)
            if (playlists.isNotEmpty()) {
                item(key = "sc_playlists_header", contentType = "sc_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SoundCloudOrange),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.soundcloud_playlists),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                item(key = "sc_playlists_shelf", contentType = "sc_shelf") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(playlists, key = { it.playlistId }) { playlist ->
                            SoundCloudPlaylistCard(
                                playlist = playlist,
                                onPlayClick = {
                                    if (playlist.tracks.isNotEmpty()) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val queueItems = playlist.tracks.map { track ->
                                                val trackNumId = track.trackId.removePrefix("soundcloud:tracks:").substringBefore("?")
                                                val metadata = MediaMetadata(
                                                    id = "soundcloud:tracks:$trackNumId",
                                                    title = track.title,
                                                    artists = listOf(MediaMetadata.Artist(id = null, name = track.artist)),
                                                    duration = if ((track.durationMs ?: 0L) > 0) ((track.durationMs ?: 0L) / 1000L).toInt() else -1,
                                                    thumbnailUrl = track.artworkUrl,
                                                )
                                                database.insert(metadata)
                                                metadata.toMediaItem()
                                            }
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.title,
                                                    items = queueItems,
                                                    startIndex = 0,
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Tracks Header
            if (tracks.isNotEmpty()) {
                item(key = "sc_tracks_header", contentType = "sc_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SoundCloudOrange),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.soundcloud_tracks),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Tracks Items
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> "sc_${track.trackId}" },
                    contentType = { _, _ -> "sc_track_row" },
                ) { index, track ->
                    val numericId = remember(track.trackId) {
                        track.trackId.removePrefix("soundcloud:tracks:").substringBefore("?")
                    }
                    val fullId = "soundcloud:tracks:$numericId"
                    val isActive = currentMetadata?.id == fullId

                    SoundCloudTrackRow(
                        track = track,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        onClick = {
                            if (isActive) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val queueItems = tracks.map { scTrack ->
                                        val trackNumId = scTrack.trackId.removePrefix("soundcloud:tracks:").substringBefore("?")
                                        val metadata = MediaMetadata(
                                            id = "soundcloud:tracks:$trackNumId",
                                            title = scTrack.title,
                                            artists = listOf(MediaMetadata.Artist(id = null, name = scTrack.artist)),
                                            duration = if ((scTrack.durationMs ?: 0L) > 0) ((scTrack.durationMs ?: 0L) / 1000L).toInt() else -1,
                                            thumbnailUrl = scTrack.artworkUrl,
                                        )
                                        database.insert(metadata)
                                        metadata.toMediaItem()
                                    }
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "SoundCloud - $selectedGenre",
                                            items = queueItems,
                                            startIndex = index,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            // Empty state
            if (!isLoading && tracks.isEmpty() && playlists.isEmpty()) {
                item(key = "sc_empty_state") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.soundcloud_icon),
                                contentDescription = null,
                                tint = SoundCloudOrange.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = errorMessage ?: "No tracks found on SoundCloud",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCloudPlaylistCard(
    playlist: SoundCloudAudioProvider.SoundCloudPlaylistResult,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onPlayClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.width(136.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${playlist.trackCount} tracks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SoundCloudTrackRow(
    track: SoundCloudAudioProvider.SoundCloudTrackMetadata,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isActive) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    track.durationMs?.takeIf { it > 0 }?.let { durMs ->
                        Text(
                            text = " • ${makeTimeString(durMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // SC badge
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SoundCloudOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "SC",
                    color = SoundCloudOrange,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
            }
        }
    }
}
