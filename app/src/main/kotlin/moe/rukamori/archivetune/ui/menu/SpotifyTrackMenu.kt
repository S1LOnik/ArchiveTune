/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil3.compose.AsyncImage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.di.SpotifyEntryPoint
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import java.time.LocalDateTime

@Composable
fun SpotifyTrackMenu(
    track: SpotifyTrack,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    // [FIX-ZCODE] Wave 7 (owner report): Spotify tracks could not be liked from search results.
    val repository = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SpotifyEntryPoint::class.java)
            .spotifyLibraryRepository()
    }

    var showAddToSpotifyPlaylistDialog by rememberSaveable { mutableStateOf(false) }

    val cleanId = remember(track.id) {
        track.id
            .removePrefix("spotify:track:")
            .removePrefix("spotify://track/")
            .removePrefix("spotify://")
            .substringBefore("?")
    }
    val songId = remember(cleanId) { "spotify:track:$cleanId" }
    val songState by database.song(songId).collectAsState(initial = null)
    val isLiked = songState?.song?.liked == true

    val toggleLike: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val uri = "spotify:track:$cleanId"
            val result =
                runCatching {
                    if (isLiked) {
                        repository.removeFromLibrary(listOf(uri)).getOrThrow()
                    } else {
                        repository.addToLibrary(listOf(uri)).getOrThrow()
                    }
                }
            if (result.isSuccess) {
                try {
                    database.withTransaction {
                        val existing = getSongById(songId)
                        if (existing != null) {
                            update(existing.song.toggleLike())
                        } else if (!isLiked) {
                            upsert(
                                SongEntity(
                                    id = songId,
                                    title = track.name,
                                    duration = (track.durationMs / 1000).toInt(),
                                    thumbnailUrl = SpotifyMapper.getTrackThumbnail(track),
                                    liked = true,
                                    likedDate = LocalDateTime.now(),
                                    inLibrary = LocalDateTime.now(),
                                    explicit = track.explicit,
                                ),
                            )
                            track.artists.forEachIndexed { index, artist ->
                                val artistId = "spotify_artist_${artist.name.hashCode()}"
                                upsert(moe.rukamori.archivetune.db.entities.ArtistEntity(id = artistId, name = artist.name))
                                insert(moe.rukamori.archivetune.db.entities.SongArtistMap(songId = songId, artistId = artistId, position = index))
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) { onDismiss() }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AddToSpotifyPlaylistDialog(
        isVisible = showAddToSpotifyPlaylistDialog,
        track = track,
        onDismiss = {
            showAddToSpotifyPlaylistDialog = false
            onDismiss()
        },
    )

    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val addToPlaylistText = stringResource(R.string.add_to_spotify_playlist)
    val shareText = stringResource(R.string.share)

    val likeText = stringResource(if (isLiked) R.string.action_remove_like else R.string.action_like)
    val primaryActions = remember(track, likeText, isLiked, playNextText, addToQueueText, addToPlaylistText, shareText) {
        listOf(
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = likeText,
                onClick = toggleLike,
            ),
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_play),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = playNextText,
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val mediaItem = SpotifyPlaybackResolver.resolveToMediaItem(track)
                        if (mediaItem != null) {
                            withContext(Dispatchers.Main) {
                                playerConnection.playNext(mediaItem)
                                onDismiss()
                            }
                        }
                    }
                },
            ),
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = addToQueueText,
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val mediaItem = SpotifyPlaybackResolver.resolveToMediaItem(track)
                        if (mediaItem != null) {
                            withContext(Dispatchers.Main) {
                                playerConnection.addToQueue(mediaItem)
                                onDismiss()
                            }
                        }
                    }
                },
            ),
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_add),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = addToPlaylistText,
                onClick = {
                    showAddToSpotifyPlaylistDialog = true
                },
            ),
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = shareText,
                onClick = {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://open.spotify.com/track/$cleanId")
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                },
            ),
        )
    }

    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            ListItem(
                headlineContent = {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.basicMarquee(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text =
                            joinByBullet(
                                track.artists.joinToString { it.name },
                                if (track.durationMs > 0) makeTimeString(track.durationMs.toLong()) else null,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                    ) {
                        AsyncImage(
                            model = SpotifyMapper.getTrackThumbnail(track),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions = primaryActions,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                ListItem(
                    headlineContent = {
                        Text(text = likeText)
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                            contentDescription = null,
                            tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.clickable {
                        toggleLike()
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.add_to_spotify_playlist))
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.spotify_icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.clickable {
                        showAddToSpotifyPlaylistDialog = true
                    },
                )
            }
        }
    }
}
