/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.ui.player.LocalArtworkFallback
import moe.rukamori.archivetune.utils.makeTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherSourcesSheet(
    mediaMetadata: MediaMetadata?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SourceTrack>>(emptyList()) }
    var failedSources by remember { mutableStateOf<List<TrackSource>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchTrigger by remember { mutableIntStateOf(0) }
    var selectedFilter by rememberSaveable { mutableStateOf(SourceVersionFilter.ALL) }

    // Search executes only while this sheet is open / composed for the current track.
    LaunchedEffect(mediaMetadata?.id, searchTrigger) {
        val currentMeta = mediaMetadata ?: return@LaunchedEffect

        isLoading = true
        errorMessage = null
        failedSources = emptyList()

        try {
            val aggregated =
                withContext(Dispatchers.IO) {
                    val cleanedInfo =
                        cleanTrackInfo(
                            currentMeta.title,
                            currentMeta.artists.firstOrNull()?.name ?: "",
                        )
                    val trackName = cleanedInfo.first
                    val artistName = cleanedInfo.second
                    val query = "$trackName $artistName".trim()

                    coroutineScope {
                        val ytDeferred =
                            async {
                                searchYouTubeSources(query, trackName, artistName)
                            }
                        val spotifyDeferred =
                            async {
                                searchSpotifySources(query, trackName, artistName)
                            }
                        val soundCloudDeferred =
                            async {
                                searchSoundCloudSources(query, trackName, artistName)
                            }

                        aggregateProviderSearchResults(
                            listOf(
                                ytDeferred.await(),
                                spotifyDeferred.await(),
                                soundCloudDeferred.await(),
                            ),
                        )
                    }
                }

            searchResults = aggregated.tracks
            failedSources = aggregated.failedSources
            if (aggregated.tracks.isEmpty() && aggregated.failedSources.size == TrackSource.entries.size) {
                errorMessage = context.getString(R.string.other_sources_search_error)
            }
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: context.getString(R.string.other_sources_search_error)
        } finally {
            isLoading = false
        }
    }

    val filteredResults =
        remember(searchResults, selectedFilter) {
            when (selectedFilter) {
                SourceVersionFilter.ALL -> searchResults
                SourceVersionFilter.ORIGINAL -> searchResults.filter { it.version == TrackVersion.ORIGINAL }
                SourceVersionFilter.LIVE -> searchResults.filter { it.version == TrackVersion.LIVE }
                SourceVersionFilter.REMIX ->
                    searchResults.filter {
                        it.version == TrackVersion.REMIX ||
                            it.version == TrackVersion.VIP ||
                            it.version == TrackVersion.SLOWED
                    }
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.other_sources),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (mediaMetadata != null) {
                        Text(
                            text = "${mediaMetadata.title} • ${mediaMetadata.artists.firstOrNull()?.name.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(android.R.string.cancel),
                    )
                }
            }

            if (!isLoading && (searchResults.isNotEmpty() || failedSources.isNotEmpty())) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceVersionFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(stringResource(filter.labelRes)) },
                        )
                    }
                }
            }

            if (failedSources.isNotEmpty() && !isLoading) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    failedSources.forEach { source ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.other_sources_provider_failed_fmt, source.displayName),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    TextButton(onClick = { searchTrigger++ }) {
                        Text(stringResource(R.string.other_sources_retry))
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.other_sources_searching),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                errorMessage != null && filteredResults.isEmpty() -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { searchTrigger++ }) {
                                Text(stringResource(R.string.other_sources_retry))
                            }
                        }
                    }
                }

                filteredResults.isEmpty() -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.other_sources_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 500.dp),
                        contentPadding =
                            PaddingValues(
                                bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                            ),
                    ) {
                        val grouped = filteredResults.groupBy { it.version }

                        TrackVersion.entries.forEach { version ->
                            val tracksForVersion = grouped[version] ?: emptyList()
                            if (tracksForVersion.isNotEmpty()) {
                                item(key = "header_${version.name}") {
                                    Text(
                                        text = "${stringResource(version.labelRes)} (${tracksForVersion.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                                    )
                                }

                                items(
                                    items = tracksForVersion,
                                    key = { it.id },
                                ) { track ->
                                    SourceTrackItem(
                                        track = track,
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    track.metadata?.let { meta ->
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = meta.title,
                                                                items = listOf(meta.toMediaItem()),
                                                            ),
                                                        )
                                                        Toast
                                                            .makeText(
                                                                context,
                                                                context.getString(
                                                                    R.string.other_sources_switched_fmt,
                                                                    track.source.displayName,
                                                                    meta.title,
                                                                ),
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        onDismiss()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            e.localizedMessage
                                                                ?: context.getString(R.string.other_sources_play_error),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun searchYouTubeSources(
    query: String,
    trackName: String,
    artistName: String,
): ProviderSearchResult =
    try {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        val tracks =
            result.items
                .filterIsInstance<SongItem>()
                .mapNotNull { song ->
                    val relevance =
                        calculateRelevance(
                            trackName = trackName,
                            artistName = artistName,
                            resultTitle = song.title,
                            resultArtist = song.artists.firstOrNull()?.name ?: "",
                        )
                    if (relevance > 0.28f) {
                        val meta = song.toMediaMetadata()
                        SourceTrack(
                            id = "yt:${song.id}",
                            title = song.title,
                            artist = song.artists.joinToString(", ") { it.name },
                            durationSeconds = meta.duration.takeIf { it > 0 },
                            thumbnailUrl = song.thumbnail,
                            source = TrackSource.YOUTUBE,
                            version = detectVersion(song.title),
                            relevance = relevance,
                            metadata = meta,
                        )
                    } else {
                        null
                    }
                }
        ProviderSearchResult(TrackSource.YOUTUBE, tracks, failed = false)
    } catch (_: Exception) {
        ProviderSearchResult(TrackSource.YOUTUBE, emptyList(), failed = true)
    }

private suspend fun searchSpotifySources(
    query: String,
    trackName: String,
    artistName: String,
): ProviderSearchResult =
    try {
        val result = Spotify.search(query, limit = 20).getOrThrow()
        val tracks =
            result.tracks?.items
                ?.mapNotNull { track ->
                    val relevance =
                        calculateRelevance(
                            trackName = trackName,
                            artistName = artistName,
                            resultTitle = track.name,
                            resultArtist = track.artists.firstOrNull()?.name ?: "",
                        )
                    if (relevance > 0.28f) {
                        val durationSec = if (track.durationMs > 0) (track.durationMs / 1000).toInt() else -1
                        val meta =
                            MediaMetadata(
                                id = "spotify:track:${track.id}",
                                title = track.name,
                                artists = track.artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
                                duration = durationSec,
                                thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                                album = track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) },
                                spotifyTrackId = track.id,
                                explicit = track.explicit,
                            )
                        SourceTrack(
                            id = "sp:${track.id}",
                            title = track.name,
                            artist = track.artists.joinToString(", ") { it.name },
                            durationSeconds = durationSec.takeIf { it > 0 },
                            thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                            source = TrackSource.SPOTIFY,
                            version = detectVersion(track.name),
                            relevance = relevance,
                            metadata = meta,
                        )
                    } else {
                        null
                    }
                }.orEmpty()
        ProviderSearchResult(TrackSource.SPOTIFY, tracks, failed = false)
    } catch (_: Exception) {
        ProviderSearchResult(TrackSource.SPOTIFY, emptyList(), failed = true)
    }

private suspend fun searchSoundCloudSources(
    query: String,
    trackName: String,
    artistName: String,
): ProviderSearchResult =
    try {
        val tracks =
            SoundCloudAudioProvider
                .searchMetadata(term = query, limit = 20)
                .mapNotNull { track ->
                    val relevance =
                        calculateRelevance(
                            trackName = trackName,
                            artistName = artistName,
                            resultTitle = track.title,
                            resultArtist = track.artist,
                        )
                    if (relevance > 0.25f) {
                        val meta = SoundCloudAudioProvider.toMediaMetadata(track)
                        SourceTrack(
                            id = "sc:${track.trackId}",
                            title = track.title,
                            artist = track.artist,
                            durationSeconds = meta.duration.takeIf { it > 0 },
                            thumbnailUrl = track.artworkUrl,
                            source = TrackSource.SOUNDCLOUD,
                            version = detectVersion(track.title),
                            relevance = relevance,
                            metadata = meta,
                        )
                    } else {
                        null
                    }
                }
        ProviderSearchResult(TrackSource.SOUNDCLOUD, tracks, failed = false)
    } catch (_: Exception) {
        ProviderSearchResult(TrackSource.SOUNDCLOUD, emptyList(), failed = true)
    }

@Composable
private fun SourceTrackItem(
    track: SourceTrack,
    onClick: () -> Unit,
) {
    val durationLabel =
        remember(track.durationSeconds) {
            track.durationSeconds?.takeIf { it > 0 }?.let { makeTimeString(it.toLong() * 1000L) }
        }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!track.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                LocalArtworkFallback(
                    minIconSize = 18.dp,
                    maxIconSize = 22.dp,
                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp)),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                    if (durationLabel != null) {
                        "${track.artist} • $durationLabel"
                    } else {
                        track.artist
                    }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = track.source.color,
                contentColor = Color.White,
            ) {
                Text(
                    text = track.source.shortName,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

private enum class SourceVersionFilter(
    @param:StringRes val labelRes: Int,
) {
    ALL(R.string.other_sources_filter_all),
    ORIGINAL(R.string.other_sources_version_original),
    LIVE(R.string.other_sources_version_live),
    REMIX(R.string.other_sources_version_remix),
}

internal data class SourceTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String?,
    val source: TrackSource,
    val version: TrackVersion,
    val relevance: Float,
    val metadata: MediaMetadata?,
)

internal enum class TrackSource(
    val displayName: String,
    val shortName: String,
    val color: Color,
) {
    YOUTUBE("YouTube Music", "YT", Color(0xFFE53935)),
    SPOTIFY("Spotify", "Spotify", Color(0xFF1DB954)),
    SOUNDCLOUD("SoundCloud", "SoundCloud", Color(0xFFFF7700)),
}

internal enum class TrackVersion(
    @param:StringRes val labelRes: Int,
) {
    ORIGINAL(R.string.other_sources_version_original),
    LIVE(R.string.other_sources_version_live),
    REMIX(R.string.other_sources_version_remix),
    VIP(R.string.other_sources_version_vip),
    SLOWED(R.string.other_sources_version_slowed),
    OTHER(R.string.other_sources_version_other),
}

internal data class ProviderSearchResult(
    val source: TrackSource,
    val tracks: List<SourceTrack>,
    val failed: Boolean,
)

internal data class AggregatedSourcesResult(
    val tracks: List<SourceTrack>,
    val failedSources: List<TrackSource>,
)

internal fun aggregateProviderSearchResults(
    results: List<ProviderSearchResult>,
): AggregatedSourcesResult {
    val failed = results.filter { it.failed }.map { it.source }
    val sortedTracks =
        results
            .flatMap { it.tracks }
            .sortedWith(
                compareBy<SourceTrack> { it.version.ordinal }
                    .thenByDescending { it.relevance },
            )
    return AggregatedSourcesResult(tracks = sortedTracks, failedSources = failed)
}

internal fun cleanTrackInfo(
    title: String,
    artist: String,
): Pair<String, String> {
    val patterns =
        listOf(
            "\\(.*?remix.*?\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[.*?remix.*?\\]".toRegex(RegexOption.IGNORE_CASE),
            "\\(.*?slowed.*?\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[.*?slowed.*?\\]".toRegex(RegexOption.IGNORE_CASE),
            "\\(.*?sped up.*?\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[.*?sped up.*?\\]".toRegex(RegexOption.IGNORE_CASE),
            "\\(.*?speed up.*?\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[.*?speed up.*?\\]".toRegex(RegexOption.IGNORE_CASE),
            "\\(.*?vip.*?\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[.*?vip.*?\\]".toRegex(RegexOption.IGNORE_CASE),
            "\\(.*?live.*?\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[.*?live.*?\\]".toRegex(RegexOption.IGNORE_CASE),
            "\\(.*?feat\\.?.*?\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[.*?feat\\.?.*?\\]".toRegex(RegexOption.IGNORE_CASE),
            "feat\\.?.*".toRegex(RegexOption.IGNORE_CASE),
            "ft\\.?.*".toRegex(RegexOption.IGNORE_CASE),
            "-\\s*official.*".toRegex(RegexOption.IGNORE_CASE),
            "\\(official.*\\)".toRegex(RegexOption.IGNORE_CASE),
            "\\[official.*\\]".toRegex(RegexOption.IGNORE_CASE),
        )

    var cleanTitle = title
    patterns.forEach { pattern ->
        cleanTitle = cleanTitle.replace(pattern, "")
    }
    cleanTitle = cleanTitle.trim().trimEnd('-', '|', ':').trim()

    return Pair(cleanTitle.ifBlank { title }, artist)
}

internal fun detectVersion(title: String): TrackVersion {
    val lower = title.lowercase()
    return when {
        lower.contains("slowed") || lower.contains("sped up") || lower.contains("speed up") || lower.contains("nightcore") ->
            TrackVersion.SLOWED
        lower.contains("vip") || lower.contains("club mix") || lower.contains("extended") ->
            TrackVersion.VIP
        lower.contains("remix") || lower.contains("rmx") || lower.contains("flip") || lower.contains("bootleg") || lower.contains("edit") ->
            TrackVersion.REMIX
        lower.contains("live") || lower.contains("concert") || lower.contains("unplugged") || lower.contains("session") ||
            lower.contains("концерт") || lower.contains("лайв") ->
            TrackVersion.LIVE
        lower.contains("cover") || lower.contains("instrumental") || lower.contains("karaoke") ->
            TrackVersion.OTHER
        else -> TrackVersion.ORIGINAL
    }
}

internal fun calculateRelevance(
    trackName: String,
    artistName: String,
    resultTitle: String,
    resultArtist: String,
): Float {
    val trackWords = trackName.lowercase().split(Regex("[\\s,.-]+")).filter { it.length > 2 }
    val artistWords = artistName.lowercase().split(Regex("[\\s,.-]+")).filter { it.length > 2 }
    val resultTitleLower = resultTitle.lowercase()
    val resultArtistLower = resultArtist.lowercase()

    var score = 0f
    var maxScore = 0f

    trackWords.forEach { word ->
        maxScore += 2f
        if (resultTitleLower.contains(word)) score += 2f
    }

    artistWords.forEach { word ->
        maxScore += 1.5f
        if (resultArtistLower.contains(word) || resultTitleLower.contains(word)) score += 1.5f
    }

    if (resultTitleLower.contains(trackName.lowercase())) {
        score += 3f
        maxScore += 3f
    }
    if (artistName.isNotBlank() && (resultArtistLower.contains(artistName.lowercase()) || resultTitleLower.contains(artistName.lowercase()))) {
        score += 2f
        maxScore += 2f
    }

    return if (maxScore > 0) (score / maxScore).coerceIn(0f, 1f) else 0f
}
