/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import moe.rukamori.archivetune.constants.YouTubeLikesIncludePodcastsKey
import moe.rukamori.archivetune.constants.YouTubeLikesIncludeVideosKey
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.PlaylistSortDescendingKey
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.PlaylistSortTypeKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryPlaylistsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: (@Composable () -> Unit)?,
    selectedTagIds: Set<String>,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val haptic = LocalHapticFeedback.current

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSortTypeKey,
            PlaylistSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSortDescendingKey, true)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val downloadedPlaylistIds by viewModel.downloadedPlaylistIds.collectAsStateWithLifecycle()
    val filteredPlaylistIds by database
        .playlistIdsByTags(
            if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
        ).collectAsStateWithLifecycle(initialValue = emptyList())
    val (includeVideos) = rememberPreference(YouTubeLikesIncludeVideosKey, false)
    val (includePodcasts) = rememberPreference(YouTubeLikesIncludePodcastsKey, false)
    val likedSongsCount by remember(database, includeVideos, includePodcasts) {
        database.youtubeLikedSongsCount(includeVideos, includePodcasts)
    }.collectAsStateWithLifecycle(initialValue = 0)

    var showHidden by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedPlaylistIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var selectionSearchQuery by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val triggerHaptic = rememberArchiveTuneHapticTrigger()
    val selectedSet = remember(selectedPlaylistIds) { selectedPlaylistIds.toSet() }

    val exitSelectionMode: () -> Unit = {
        selectionMode = false
        selectedPlaylistIds = arrayListOf()
        selectionSearchQuery = ""
    }

    BackHandler(enabled = selectionMode) {
        exitSelectionMode()
    }

    val allCandidates =
        remember(playlists) {
            playlists.map {
                PlaylistVisibilityCandidate(
                    id = it.id,
                    name = it.playlist.name,
                    isHidden = it.playlist.isHidden,
                )
            }
        }

    val applyVisibilityBatch: (Set<String>, Boolean) -> Unit = { targetIds, targetHidden ->
        val plan =
            planPlaylistVisibilityChange(
                playlists = allCandidates,
                selectedIds = targetIds,
                targetHidden = targetHidden,
            )
        if (plan.changedIds.isNotEmpty()) {
            database.transaction {
                plan.changedIds.chunked(500).forEach { chunk ->
                    updatePlaylistVisibility(chunk, plan.targetHidden)
                }
            }
            exitSelectionMode()
            triggerHaptic(ArchiveTuneHapticKind.SELECTION)
            val message =
                context.getString(
                    if (plan.targetHidden) R.string.playlists_hidden_count else R.string.playlists_shown_count,
                    plan.changedIds.size,
                )
            val undoLabel = context.getString(R.string.undo)
            coroutineScope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    database.transaction {
                        plan.changedIds.chunked(500).forEach { chunk ->
                            updatePlaylistVisibility(chunk, !plan.targetHidden)
                        }
                    }
                }
            }
        }
    }

    val hiddenCount =
        remember(playlists) {
            playlists.count { it.playlist.isHidden && !it.playlist.name.contains("episode", ignoreCase = true) }
        }

    var showVisibilityManager by rememberSaveable { mutableStateOf(false) }
    if (showVisibilityManager) {
        PlaylistVisibilityDialog(
            playlists = playlists,
            onDismiss = { showVisibilityManager = false },
            onApply = { ids, hidden ->
                applyVisibilityBatch(ids.toSet(), hidden)
                showVisibilityManager = false
            },
        )
    }

    val visiblePlaylists =
        remember(playlists, selectedTagIds, filteredPlaylistIds, showHidden) {
            playlists.filter { playlist ->
                val name = playlist.playlist.name
                val matchesName = !name.contains("episode", ignoreCase = true)
                val matchesTags = selectedTagIds.isEmpty() || playlist.id in filteredPlaylistIds
                val matchesVisibility = showHidden || !playlist.playlist.isHidden
                matchesName && matchesTags && matchesVisibility
            }
        }
    val matchingIds =
        remember(visiblePlaylists, selectionMode, selectionSearchQuery) {
            if (!selectionMode || selectionSearchQuery.isBlank()) {
                visiblePlaylists.mapTo(HashSet(visiblePlaylists.size)) { it.id }
            } else {
                val candidates =
                    visiblePlaylists.map {
                        PlaylistVisibilityCandidate(it.id, it.playlist.name, it.playlist.isHidden)
                    }
                filterPlaylistsByQuery(candidates, selectionSearchQuery).mapTo(HashSet()) { it.id }
            }
        }
    val displayedPlaylists =
        remember(visiblePlaylists, matchingIds, selectionMode, selectionSearchQuery) {
            if (!selectionMode || selectionSearchQuery.isBlank()) {
                visiblePlaylists
            } else {
                visiblePlaylists.filter { it.id in matchingIds }
            }
        }
    val toggleOrStartSelection: (String) -> Unit = { playlistId ->
        selectionMode = true
        selectedPlaylistIds = ArrayList(togglePlaylistSelection(selectedSet, playlistId))
        triggerHaptic(ArchiveTuneHapticKind.SELECTION)
    }
    val mutablePlaylists = remember { mutableStateListOf<Playlist>() }

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val refreshLibrary = remember(viewModel) { { viewModel.sync() } }
    val onRefreshErrorShown = remember(viewModel) { { viewModel.onRefreshErrorShown() } }
    val lazyListState = rememberLazyListState()
    var pendingPlaylistOrderUpdate by remember { mutableStateOf(false) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (from.index in mutablePlaylists.indices && to.index in mutablePlaylists.indices) {
                mutablePlaylists.move(from.index, to.index)
                pendingPlaylistOrderUpdate = true
            }
        }

    LaunchedEffect(visiblePlaylists) {
        mutablePlaylists.clear()
        mutablePlaylists.addAll(visiblePlaylists)
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && pendingPlaylistOrderUpdate) {
            viewModel.updateCustomPlaylistOrder(
                mergeVisiblePlaylistOrder(
                    currentOrder = playlists,
                    visibleOrder = mutablePlaylists,
                ),
            )
            pendingPlaylistOrderUpdate = false
        }
    }

    // Dialog launcher
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

    // Issue 2: player-aware bottom padding
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    val listBottomPadding = playerAwareBottomPadding + if (selectionMode) 84.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryRefreshContainer(
            state = refreshState,
            onRefresh = refreshLibrary,
            onErrorShown = onRefreshErrorShown,
            modifier = Modifier.fillMaxSize(),
            indicatorOffset = LibraryPullToRefreshIndicatorOffset,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = LibraryHeaderContentPadding),
            ) {
                if (selectionMode) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = exitSelectionMode) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                        Text(
                            text = stringResource(R.string.playlists_selected_count, selectedSet.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        val allDisplayedSelected = matchingIds.isNotEmpty() && selectedSet.containsAll(matchingIds)
                        TextButton(
                            enabled = matchingIds.isNotEmpty(),
                            onClick = {
                                selectedPlaylistIds = ArrayList(toggleSelectAllVisible(selectedSet, matchingIds))
                                triggerHaptic(ArchiveTuneHapticKind.SELECTION)
                            },
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (allDisplayedSelected) {
                                            R.string.playlist_clear_matches
                                        } else {
                                            R.string.playlist_select_matches
                                        },
                                    ),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = selectionSearchQuery,
                        onValueChange = { selectionSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.playlist_search_placeholder)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (selectionSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { selectionSearchQuery = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.close),
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                } else {
                    // Control row (Sort dropdown, grid/list layout toggle, + add button)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                // Left: Sort dropdown
                val sortMenuToggle = moe.rukamori.archivetune.ui.component.rememberDropdownMenuToggle()
                val currentSortLabel =
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> {
                            stringResource(R.string.recently_added)
                        }

                        PlaylistSortType.NAME -> {
                            if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                        }

                        PlaylistSortType.SONG_COUNT -> {
                            stringResource(R.string.tracks_count_label)
                        }

                        PlaylistSortType.LAST_UPDATED -> {
                            stringResource(R.string.recently_updated)
                        }

                        PlaylistSortType.CUSTOM -> {
                            stringResource(R.string.custom_order)
                        }
                    }

                val showSortDirection = sortType != PlaylistSortType.CUSTOM
                val sortDirectionRotation by animateFloatAsState(
                    targetValue = if (sortDescending) 0f else 180f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    label = "PlaylistSortDirectionRotation",
                )

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Row(
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable(onClick = sortMenuToggle::toggle)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentSortLabel,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.expand_more),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = sortMenuToggle.expanded,
                            onDismissRequest = sortMenuToggle::onDismissRequest,
                        ) {
                            PlaylistSortType.entries.forEach { type ->
                                val label =
                                    when (type) {
                                        PlaylistSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                        PlaylistSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                        PlaylistSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                        PlaylistSortType.LAST_UPDATED -> stringResource(R.string.recently_updated)
                                        PlaylistSortType.CUSTOM -> stringResource(R.string.custom_order)
                                    }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onSortTypeChange(type)
                                        if (type == PlaylistSortType.NAME) {
                                            onSortDescendingChange(false)
                                        }
                                        sortMenuToggle.closeFromItemSelection()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.manage_playlist_visibility)) },
                                onClick = {
                                    sortMenuToggle.closeFromItemSelection()
                                    showVisibilityManager = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (hiddenCount > 0) {
                                            "${stringResource(R.string.hidden_playlists)} ($hiddenCount)"
                                        } else {
                                            stringResource(R.string.hidden_playlists)
                                        },
                                    )
                                },
                                onClick = {
                                    showHidden = !showHidden
                                    sortMenuToggle.closeFromItemSelection()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.visibility_off),
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = {
                                    if (showHidden) {
                                        Icon(
                                            painter = painterResource(R.drawable.check),
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                        }
                    }

                    if (showSortDirection) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onSortDescendingChange(!sortDescending) },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_downward),
                                contentDescription =
                                    stringResource(
                                        if (sortDescending) {
                                            R.string.sort_order_descending
                                        } else {
                                            R.string.sort_order_ascending
                                        },
                                    ),
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = sortDirectionRotation },
                            )
                        }
                    }
                }

                // Right: list/grid toggle & add button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sortType == PlaylistSortType.CUSTOM) {
                        IconButton(
                            onClick = { locked = !locked },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                contentDescription = null,
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // List/Grid Toggle
                    Row(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (!isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { isGridView = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.queue_music),
                                contentDescription = stringResource(R.string.list_view),
                                tint = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { isGridView = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.album),
                                contentDescription = stringResource(R.string.grid_view),
                                tint = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                        IconButton(
                            onClick = { showCreatePlaylistDialog = true },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.add),
                                contentDescription = stringResource(R.string.create_playlist),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                }

            if (hiddenCount > 0 || showHidden) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = showHidden,
                        onClick = { showHidden = !showHidden },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.visibility_off),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.hidden_playlists),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (hiddenCount > 0) {
                                    Badge {
                                        Text(hiddenCount.toString())
                                    }
                                }
                            }
                        },
                    )
                }
            }

            val playlistTagFilterContent = filterContent
            if (playlistTagFilterContent != null) {
                playlistTagFilterContent()
            }

            Spacer(modifier = Modifier.height(8.dp))

            val createPlaylist = remember { { showCreatePlaylistDialog = true } }

            // Main Content
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = listBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Liked Songs item
                    if (!selectionMode) {
                        item(
                            key = "liked",
                            contentType = "special_playlist",
                        ) {
                            YouTubeMusicLikedCard(
                                likedCount = likedSongsCount,
                                onClick = { navController.navigate("auto_playlist/liked_ytm") },
                            )
                        }
                    }

                    if (displayedPlaylists.isEmpty()) {
                        item(
                            span = { GridItemSpan(2) },
                            key = "playlists_empty",
                            contentType = "library_empty_state",
                        ) {
                            LibraryEmptyState(
                                iconRes = R.drawable.playlist_add,
                                actionLabelRes = R.string.create_playlist,
                                onAction = createPlaylist,
                            )
                        }
                    }

                    items(
                        items = displayedPlaylists,
                        key = { playlist -> playlist.id },
                        contentType = { "playlist_grid" },
                    ) { playlist ->
                        val isSelected = playlist.id in selectedSet
                        PlaylistGridCard(
                            playlist = playlist,
                            isDownloaded = playlist.id in downloadedPlaylistIds,
                            isSelected = isSelected,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    toggleOrStartSelection(playlist.id)
                                } else {
                                    openPlaylist(navController, playlist)
                                }
                            },
                            onPlay = {
                                playerConnection?.let { conn ->
                                    coroutineScope.launch {
                                        database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                            if (songs.isNotEmpty()) {
                                                conn.playQueue(ListQueue(items = songs.map { it.song.toMediaItem() }))
                                            }
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                toggleOrStartSelection(playlist.id)
                            },
                        )
                    }
                }
            } else {
                val listPlaylists =
                    if (sortType == PlaylistSortType.CUSTOM && !selectionMode) mutablePlaylists else displayedPlaylists
                val showDragHandles = sortType == PlaylistSortType.CUSTOM && !locked && !selectionMode
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = listBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Liked Songs item
                    if (!selectionMode) {
                        item(
                            key = "liked",
                            contentType = "special_playlist",
                        ) {
                            YouTubeMusicLikedListCard(
                                likedCount = likedSongsCount,
                                onClick = { navController.navigate("auto_playlist/liked_ytm") },
                            )
                        }
                    }

                    if (listPlaylists.isEmpty()) {
                        item(key = "playlists_empty", contentType = "library_empty_state") {
                            LibraryEmptyState(
                                iconRes = R.drawable.playlist_add,
                                actionLabelRes = R.string.create_playlist,
                                onAction = createPlaylist,
                            )
                        }
                    }

                    itemsIndexed(
                        items = listPlaylists,
                        key = { _, playlist -> playlist.id },
                        contentType = { _, _ -> "playlist_list" },
                    ) { _, playlist ->
                        val isSelected = playlist.id in selectedSet
                        ReorderableItem(
                            state = reorderableState,
                            key = playlist.id,
                            modifier =
                                Modifier.graphicsLayer {
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                },
                        ) {
                            PlaylistListCard(
                                playlist = playlist,
                                isDownloaded = playlist.id in downloadedPlaylistIds,
                                isSelected = isSelected,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        toggleOrStartSelection(playlist.id)
                                    } else {
                                        openPlaylist(navController, playlist)
                                    }
                                },
                                onLongClick = {
                                    toggleOrStartSelection(playlist.id)
                                },
                                onPlay = {
                                    playerConnection?.let { conn ->
                                        coroutineScope.launch {
                                            database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                                if (songs.isNotEmpty()) {
                                                    conn.playQueue(ListQueue(items = songs.map { it.song.toMediaItem() }))
                                                }
                                            }
                                        }
                                    }
                                },
                                onMenuClick = {
                                    menuState.show {
                                        triggerPlaylistMenu(playlist, coroutineScope, menuState)
                                    }
                                },
                                showDragHandle = showDragHandles,
                                dragHandleModifier =
                                    Modifier
                                        .draggableHandle()
                                        .graphicsLayer { alpha = 0.99f },
                            )
                        }
                    }
                }
            }
        }
        }

        val hidePlan =
            remember(allCandidates, selectedSet) {
                planPlaylistVisibilityChange(allCandidates, selectedSet, targetHidden = true)
            }
        val showPlan =
            remember(allCandidates, selectedSet) {
                planPlaylistVisibilityChange(allCandidates, selectedSet, targetHidden = false)
            }

        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = playerAwareBottomPadding),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        enabled = hidePlan.changedIds.isNotEmpty(),
                        onClick = { applyVisibilityBatch(selectedSet, true) },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text =
                                if (hidePlan.changedIds.isNotEmpty()) {
                                    "${stringResource(R.string.playlist_action_hide)} (${hidePlan.changedIds.size})"
                                } else {
                                    stringResource(R.string.playlist_action_hide)
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        enabled = showPlan.changedIds.isNotEmpty(),
                        onClick = { applyVisibilityBatch(selectedSet, false) },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text =
                                if (showPlan.changedIds.isNotEmpty()) {
                                    "${stringResource(R.string.playlist_action_show)} (${showPlan.changedIds.size})"
                                } else {
                                    stringResource(R.string.playlist_action_show)
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = playerAwareBottomPadding + if (selectionMode) 80.dp else 0.dp),
        )
    }
}

private fun mergeVisiblePlaylistOrder(
    currentOrder: List<Playlist>,
    visibleOrder: List<Playlist>,
): List<Playlist> {
    if (visibleOrder.isEmpty()) return currentOrder

    val visibleIds = visibleOrder.mapTo(HashSet(visibleOrder.size)) { playlist -> playlist.id }
    val reorderedVisible = visibleOrder.iterator()
    return currentOrder.map { playlist ->
        if (playlist.id in visibleIds) {
            reorderedVisible.next()
        } else {
            playlist
        }
    }
}

private fun openPlaylist(
    navController: NavController,
    playlist: Playlist,
) {
    if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0) {
        navController.navigate("online_playlist/${playlist.playlist.browseId}")
    } else {
        navController.navigate("local_playlist/${playlist.id}")
    }
}

@Composable
private fun triggerPlaylistMenu(
    playlist: Playlist,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
) {
    if (playlist.playlist.isEditable || playlist.songCount != 0) {
        PlaylistMenu(
            playlist = playlist,
            coroutineScope = coroutineScope,
            onDismiss = menuState::dismiss,
        )
    } else {
        playlist.playlist.browseId?.let { browseId ->
            YouTubePlaylistMenu(
                playlist =
                    PlaylistItem(
                        id = browseId,
                        title = playlist.playlist.name,
                        author = null,
                        songCountText = null,
                        thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                        playEndpoint =
                            WatchEndpoint(
                                playlistId = browseId,
                                params = playlist.playlist.playEndpointParams,
                            ),
                        shuffleEndpoint =
                            WatchEndpoint(
                                playlistId = browseId,
                                params = playlist.playlist.shuffleEndpointParams,
                            ),
                        radioEndpoint =
                            WatchEndpoint(
                                playlistId = "RDAMPL$browseId",
                                params = playlist.playlist.radioEndpointParams,
                            ),
                        isEditable = false,
                    ),
                coroutineScope = coroutineScope,
                onDismiss = menuState::dismiss,
            )
        }
    }
}

@Composable
fun rememberArtworkGradient(
    thumbnailUrl: String?,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): List<Color> {
    val context = LocalContext.current
    var colors by remember(thumbnailUrl) { mutableStateOf(listOf(fallbackColor, fallbackColor.copy(alpha = 0.5f))) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl == null) return@LaunchedEffect
        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

        val result =
            runCatching {
                context.imageLoader.execute(request)
            }.getOrNull()

        if (result != null) {
            val bitmap = result.image?.toBitmap()
            if (bitmap != null) {
                val palette =
                    withContext(Dispatchers.Default) {
                        Palette
                            .from(bitmap)
                            .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                            .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                            .generate()
                    }

                val extractedColors =
                    PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColor.toArgb(),
                    )
                if (extractedColors.size >= 2) {
                    colors = extractedColors
                } else if (extractedColors.isNotEmpty()) {
                    colors = listOf(extractedColors[0], extractedColors[0].copy(alpha = 0.5f))
                }
            }
        }
    }
    return colors
}

@Composable
fun rememberArtworkCardColor(
    thumbnailUrl: String?,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Color {
    val gradientColors =
        rememberArtworkGradient(
            thumbnailUrl = thumbnailUrl,
            fallbackColor = fallbackColor,
        )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val useDarkTheme = remember(surfaceColor) { ColorUtils.calculateLuminance(surfaceColor.toArgb()) < 0.5 }
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

    return remember(gradientColors, useDarkTheme, pureBlack) {
        val baseColor = gradientColors.firstOrNull() ?: fallbackColor
        val baseArgb = baseColor.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(baseArgb, hsv)
        val hue = hsv[0]

        if (useDarkTheme) {
            // Issue 6/3 fix: increased brightness for visibility in pure black mode
            val s = (hsv[1] * 0.45f).coerceIn(0.06f, 0.20f)
            val v = if (pureBlack) 0.18f else 0.12f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
        } else {
            val s = (hsv[1] * 0.30f).coerceIn(0.03f, 0.12f)
            val v = 0.95f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
        }
    }
}

@Composable
fun PlaylistListCard(
    playlist: Playlist,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    val sourceLabel =
        stringResource(
            if (playlist.playlist.isEditable) {
                R.string.personal_label
            } else {
                R.string.youtube_synced
            },
        )
    val sourceColor =
        if (playlist.playlist.isEditable) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary
        }
    val additionalAction: (@Composable RowScope.() -> Unit)? =
        if (showDragHandle) {
            {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {},
                    modifier = dragHandleModifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.drag_handle),
                        contentDescription = null,
                    )
                }
            }
        } else {
            null
        }
    val metadataTrailingContent: (@Composable RowScope.() -> Unit)? =
        if (isDownloaded || playlist.playlist.isHidden) {
            {
                if (isDownloaded) {
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = stringResource(R.string.downloaded_desc),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (playlist.playlist.isHidden) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.visibility_off),
                        contentDescription = stringResource(R.string.hide_playlist),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                }
            }
        } else {
            null
        }

    LibraryMediaListCard(
        title = playlist.playlist.name,
        subtitle = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
        thumbnailUrl = playlist.thumbnails.getOrNull(0),
        sourceLabel = sourceLabel,
        sourceColor = sourceColor,
        onClick = onClick,
        onPlay = onPlay,
        onMenuClick = onMenuClick,
        onLongClick = onLongClick,
        isSelected = isSelected,
        selectionMode = selectionMode,
        contentAlpha = if (playlist.playlist.isHidden) 0.45f else 1f,
        metadataTrailingContent = metadataTrailingContent,
        additionalAction = additionalAction,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
) {
    val cardBgColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            rememberArtworkCardColor(
                thumbnailUrl = playlist.thumbnails.getOrNull(0),
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PlaylistGridCardScale",
    )

    val hiddenAlpha = if (playlist.playlist.isHidden) 0.45f else 1f
    val gridShape = RoundedCornerShape(32.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = hiddenAlpha
                }.clip(gridShape)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, gridShape)
                    } else {
                        Modifier
                    },
                ).background(cardBgColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(26.dp)),
        ) {
            ItemThumbnail(
                thumbnailUrl = playlist.thumbnails.getOrNull(0),
                isActive = false,
                isPlaying = false,
                shape = RoundedCornerShape(26.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else if (isDownloaded) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = stringResource(R.string.downloaded_desc),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Play overlay on bottom right of grid cover
            if (!selectionMode) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (playlist.playlist.isHidden) {
                Icon(
                    painter = painterResource(id = R.drawable.visibility_off),
                    contentDescription = stringResource(R.string.hide_playlist),
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(16.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
fun YouTubeMusicLikedListCard(
    likedCount: Int = 0,
    onClick: () -> Unit,
) {
    LibraryMediaListCard(
        title = stringResource(R.string.liked_songs),
        subtitle = if (likedCount > 0) "$likedCount ${stringResource(R.string.tracks_label)}" else stringResource(R.string.liked_songs),
        thumbnailUrl = null,
        sourceLabel = stringResource(R.string.liked_songs),
        sourceColor = MaterialTheme.colorScheme.error,
        onClick = onClick,
        onPlay = null,
        onMenuClick = null,
        thumbnailContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.favorite),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeMusicLikedCard(
    likedCount: Int = 0,
    onClick: () -> Unit,
) {
    val cardBgColor = MaterialTheme.colorScheme.errorContainer

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "YouTubeMusicLikedCardScale",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(RoundedCornerShape(32.dp))
                .background(cardBgColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = {},
                ).padding(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            // Play overlay on bottom right
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.liked_songs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (likedCount > 0) "$likedCount ${stringResource(R.string.tracks_label)}" else stringResource(R.string.liked_songs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PlaylistVisibilityDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onApply: (List<String>, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selection by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val filtered = remember(playlists, query) {
        playlists.filter { it.playlist.name.contains(query.trim(), ignoreCase = true) }
    }
    val selectedIds = remember(selection) { selection.toSet() }
    val selected = playlists.filter { it.id in selectedIds }
    val filteredIds = remember(filtered) { filtered.mapTo(mutableSetOf()) { it.id } }
    val allSelected = filteredIds.isNotEmpty() && selectedIds.containsAll(filteredIds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_playlist_visibility)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = filteredIds.isNotEmpty(),
                    onClick = {
                        selection = ArrayList(if (allSelected) selectedIds - filteredIds else selectedIds + filteredIds)
                    },
                ) {
                    Text(stringResource(if (allSelected) R.string.playlist_clear_matches else R.string.playlist_select_matches))
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(filtered, key = { it.id }) { playlist ->
                        val checked = playlist.id in selectedIds
                        Row(
                            modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { select ->
                                selection = ArrayList(if (select) selectedIds + playlist.id else selectedIds - playlist.id)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Column {
                                Text(playlist.playlist.name)
                                if (playlist.playlist.isHidden) Text(
                                    stringResource(R.string.playlist_hidden_locally),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(selected.map { it.id }, true) },
                enabled = selected.any { !it.playlist.isHidden },
            ) { Text(stringResource(R.string.playlist_hide_selected)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onApply(selected.map { it.id }, false) },
                enabled = selected.any { it.playlist.isHidden },
            ) { Text(stringResource(R.string.playlist_show_selected)) }
        },
    )
}
