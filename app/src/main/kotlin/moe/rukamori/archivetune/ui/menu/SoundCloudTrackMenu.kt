/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.compose.AsyncImage
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import java.time.LocalDateTime

// [FIX-ZCODE] Wave 9: track menu for SoundCloud search results — like / add to library
// (local Room persistence with the soundcloud: id), download through ExoDownloadService,
// play next / add to queue (resolved as a soundcloud: MediaItem) and share the permalink.
@Composable
fun SoundCloudTrackMenu(
    track: SoundCloudAudioProvider.SoundCloudTrackMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()

    val numericId = track.trackId.removePrefix("soundcloud:tracks:").substringBefore("?")
    val songId = "soundcloud:tracks:$numericId"
    val songState by database.song(songId).collectAsState(initial = null)
    val isLiked = songState?.song?.liked == true
    val inLibrary = songState?.song?.inLibrary != null
    val download by downloadUtil.getDownload(songId).collectAsState(initial = null)

    val toggleLike: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val existing = getSongById(songId)
                    if (existing != null) {
                        update(existing.song.toggleLike())
                    } else {
                        upsert(
                            SongEntity(
                                id = songId,
                                title = track.title,
                                duration = track.durationMs?.let { if (it > 0) (it / 1000L).toInt() else -1 } ?: -1,
                                thumbnailUrl = track.artworkUrl,
                                liked = true,
                                likedDate = LocalDateTime.now(),
                                inLibrary = LocalDateTime.now(),
                            ),
                        )
                        if (track.artist.isNotBlank()) {
                            val artistId = "soundcloud_artist_${track.artist.hashCode()}"
                            upsert(moe.rukamori.archivetune.db.entities.ArtistEntity(id = artistId, name = track.artist))
                            insert(moe.rukamori.archivetune.db.entities.SongArtistMap(songId = songId, artistId = artistId, position = 0))
                        }
                    }
                }
                withContext(Dispatchers.Main) { onDismiss() }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val toggleLibrary: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val existing = getSongById(songId)
                    if (existing != null) {
                        update(existing.song.copy(inLibrary = LocalDateTime.now()))
                    } else {
                        upsert(
                            SongEntity(
                                id = songId,
                                title = track.title,
                                duration = track.durationMs?.let { if (it > 0) (it / 1000L).toInt() else -1 } ?: -1,
                                thumbnailUrl = track.artworkUrl,
                                inLibrary = LocalDateTime.now(),
                            ),
                        )
                    }
                }
                withContext(Dispatchers.Main) { onDismiss() }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val shareText = stringResource(R.string.share)
    val likeText = stringResource(if (isLiked) R.string.action_remove_like else R.string.action_like)

    val primaryActions =
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
                    onDismiss()
                    coroutineScope.launch(Dispatchers.IO) {
                        val metadata = SoundCloudAudioProvider.toMediaMetadata(track)
                        database.insert(metadata)
                        val mediaItem = metadata.toMediaItem()
                        withContext(Dispatchers.Main) {
                            playerConnection.playNext(mediaItem)
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
                    onDismiss()
                    coroutineScope.launch(Dispatchers.IO) {
                        val metadata = SoundCloudAudioProvider.toMediaMetadata(track)
                        database.insert(metadata)
                        val mediaItem = metadata.toMediaItem()
                        withContext(Dispatchers.Main) {
                            playerConnection.addToQueue(mediaItem)
                        }
                    }
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
                    val intent =
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, track.permalinkUrl)
                        }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                },
            ),
        )

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
                        text = track.title,
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
                                track.artist,
                                track.durationMs?.takeIf { it > 0 }?.let { makeTimeString(it) },
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
                            model = track.artworkUrl,
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
                    modifier = Modifier.clickable { toggleLike() },
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text =
                                stringResource(
                                    if (inLibrary) R.string.remove_from_library else R.string.add_to_library,
                                ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter =
                                painterResource(
                                    if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.clickable { toggleLibrary() },
                )
                when (download?.state) {
                    Download.STATE_COMPLETED ->
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.remove_download), color = MaterialTheme.colorScheme.error)
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.offline),
                                    tint = MaterialTheme.colorScheme.error,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        download?.request?.id ?: songId,
                                        false,
                                    )
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.downloading)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                    else ->
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.action_download)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    val downloadRequest =
                                        DownloadRequest
                                            .Builder(songId, songId.toUri())
                                            .setCustomCacheKey(songId)
                                            .setData(track.title.toByteArray())
                                            .build()
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        false,
                                    )
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                }
            }
        }
    }
}
