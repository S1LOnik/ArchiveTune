/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.SpotifyEntryPoint
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.ListDialog

@Composable
fun AddToSpotifyPlaylistDialog(
    isVisible: Boolean,
    trackUri: String,
    trackTitle: String,
    artistName: String? = null,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SpotifyEntryPoint::class.java)
            .spotifyLibraryRepository()
    }

    val playlists by repository.playlists.collectAsStateWithLifecycle()
    val isRefreshing by repository.isRefreshing.collectAsStateWithLifecycle()
    var isAdding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.restoreCachedPlaylists()
        if (repository.playlists.value.isEmpty()) {
            runCatching { repository.refreshPlaylists() }
        }
    }

    suspend fun resolveTargetSpotifyUri(): String? {
        val trimmed = trackUri.trim()
        if (trimmed.startsWith("spotify:") || trimmed.startsWith("spotify//")) {
            val clean = trimmed
                .removePrefix("spotify:track:")
                .removePrefix("spotify://track/")
                .removePrefix("spotify://")
                .substringBefore("?")
            return "spotify:track:$clean"
        }
        val query = if (!artistName.isNullOrBlank()) "$trackTitle $artistName" else trackTitle
        val searchResults = runCatching { repository.searchTracks(query = query, limit = 1) }.getOrNull()
        val best = searchResults?.firstOrNull() ?: return null
        val clean = best.id
            .removePrefix("spotify:track:")
            .removePrefix("spotify://track/")
            .removePrefix("spotify://")
            .substringBefore("?")
        return "spotify:track:$clean"
    }

    ListDialog(onDismiss = onDismiss) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.spotify_icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.add_to_spotify_playlist),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }
        }

        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.liked_songs),
                        fontWeight = FontWeight.Medium,
                    )
                },
                supportingContent = {
                    Text(
                        text = "Spotify",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.favorite),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isAdding) {
                        coroutineScope.launch {
                            isAdding = true
                            val targetUri = resolveTargetSpotifyUri()
                            if (targetUri == null) {
                                isAdding = false
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            val result = repository.addToLibrary(listOf(targetUri))
                            isAdding = false
                            if (result.isSuccess) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.added_to_spotify_liked_songs),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    onDismiss()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        result.exceptionOrNull()?.message ?: context.getString(R.string.error_unknown),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }

        if (isRefreshing && playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_playlists_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = playlists,
                key = { it.id },
            ) { playlist ->
                val totalTracks = playlist.tracks?.total ?: 0
                val coverUrl = playlist.images.firstOrNull()?.url

                ListItem(
                    headlineContent = {
                        Text(
                            text = playlist.name,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "$totalTracks ${stringResource(R.string.songs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                        ) {
                            if (!coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isAdding) {
                            coroutineScope.launch {
                                isAdding = true
                                val targetUri = resolveTargetSpotifyUri()
                                if (targetUri == null) {
                                    isAdding = false
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }
                                val result = repository.addTracksToPlaylist(
                                    playlistId = playlist.id,
                                    trackUris = listOf(targetUri),
                                )
                                isAdding = false
                                if (result.isSuccess) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.added_to_spotify_playlist, playlist.name),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        onDismiss()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            result.exceptionOrNull()?.message ?: context.getString(R.string.error_unknown),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun AddToSpotifyPlaylistDialog(
    isVisible: Boolean,
    track: SpotifyTrack,
    onDismiss: () -> Unit,
) = AddToSpotifyPlaylistDialog(
    isVisible = isVisible,
    trackUri = track.id,
    trackTitle = track.name,
    artistName = track.artists.joinToString { it.name },
    onDismiss = onDismiss,
)

@Composable
fun AddToSpotifyPlaylistDialog(
    isVisible: Boolean,
    mediaMetadata: MediaMetadata,
    onDismiss: () -> Unit,
) = AddToSpotifyPlaylistDialog(
    isVisible = isVisible,
    trackUri = mediaMetadata.spotifyTrackId?.let { "spotify:track:$it" } ?: mediaMetadata.id,
    trackTitle = mediaMetadata.title,
    artistName = mediaMetadata.artists.joinToString { it.name },
    onDismiss = onDismiss,
)

@Composable
fun AddToSpotifyPlaylistDialog(
    isVisible: Boolean,
    song: SongItem,
    onDismiss: () -> Unit,
) = AddToSpotifyPlaylistDialog(
    isVisible = isVisible,
    trackUri = song.id,
    trackTitle = song.title,
    artistName = song.artists.joinToString { it.name },
    onDismiss = onDismiss,
)

@Composable
fun AddToSpotifyPlaylistDialog(
    isVisible: Boolean,
    song: Song,
    onDismiss: () -> Unit,
) = AddToSpotifyPlaylistDialog(
    isVisible = isVisible,
    trackUri = song.id,
    trackTitle = song.song.title,
    artistName = song.artists.joinToString { it.name },
    onDismiss = onDismiss,
)
