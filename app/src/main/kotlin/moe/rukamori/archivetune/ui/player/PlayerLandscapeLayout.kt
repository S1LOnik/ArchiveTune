/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.Lyrics
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.getNextFallbackUrl
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerLandscapeLayout(
    mediaMetadata: MediaMetadata,
    playerDesignStyle: PlayerDesignStyle,
    gradientColors: List<Color>,
    useBlackBackground: Boolean,
    playerBackground: PlayerBackgroundStyle,
    playbackState: Int,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentSongLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    lyricsSyncOffset: Int,
    sleepTimerEnabled: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onShowSleepTimerDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val triggerHaptic = rememberArchiveTuneHapticTrigger()
    val hidePlayerThumbnail by rememberPreference(HidePlayerThumbnailKey, false)
    val lowDataModeActive = rememberLowDataModeActive()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var rightPane by rememberSaveable { mutableStateOf(LandscapeRightPane.QUEUE) }

    val useDarkBackdrop =
        useBlackBackground ||
            playerBackground != PlayerBackgroundStyle.DEFAULT ||
            playerDesignStyle == PlayerDesignStyle.V7 ||
            playerDesignStyle == PlayerDesignStyle.BITCHORD ||
            playerDesignStyle == PlayerDesignStyle.TIKTOK ||
            playerDesignStyle == PlayerDesignStyle.SIMPMUSIC ||
            playerDesignStyle == PlayerDesignStyle.LOOPER

    val primaryInk = if (useDarkBackdrop) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryInk = if (useDarkBackdrop) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor =
        gradientColors.firstOrNull()?.takeIf { useDarkBackdrop }
            ?: MaterialTheme.colorScheme.primary
    val containerSurface =
        if (useDarkBackdrop) {
            Color.White.copy(alpha = 0.09f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
        }
    val ownsBackground =
        !playerDesignStyle.let {
            it == PlayerDesignStyle.V1 ||
                it == PlayerDesignStyle.V2 ||
                it == PlayerDesignStyle.V3 ||
                it == PlayerDesignStyle.V4 ||
                it == PlayerDesignStyle.V6
        } || playerBackground == PlayerBackgroundStyle.DEFAULT

    val backdropBrush =
        remember(gradientColors, useBlackBackground, useDarkBackdrop, ownsBackground) {
            if (!ownsBackground) {
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.36f)))
            } else {
                val topColor =
                    when {
                        useBlackBackground -> Color.Black
                        gradientColors.isNotEmpty() -> gradientColors.first().copy(alpha = 0.45f)
                        useDarkBackdrop -> Color(0xFF151820)
                        else -> Color.Transparent
                    }
                val bottomColor =
                    when {
                        useBlackBackground -> Color.Black
                        gradientColors.size >= 2 -> gradientColors[1].copy(alpha = 0.35f)
                        useDarkBackdrop -> Color(0xFF0C0E14)
                        else -> Color.Transparent
                    }
                Brush.verticalGradient(listOf(topColor, bottomColor))
            }
        }

    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backdropBrush)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left pane: prioritized cover sizing for short landscape screens + responsive transport controls
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(0.46f)
                        .fillMaxHeight(),
            ) {
                val fontScale = density.fontScale
                val chromeEstimate = if (fontScale > 1.15f) 186f else 154f
                val artworkSize =
                    resolveLandscapeArtworkSizeDp(
                        paneWidthDp = maxWidth.value,
                        paneHeightDp = maxHeight.value,
                        chromeHeightDp = chromeEstimate,
                        minArtworkDp = 82f,
                        maxArtworkDp = 290f,
                    ).dp

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    val baseArtworkUrl = mediaMetadata.thumbnailUrl?.highRes()
                    val swapState =
                        rememberThumbnailSwapState(
                            videoId = mediaMetadata.id,
                            ytmUrl = baseArtworkUrl,
                            lowDataMode = lowDataModeActive,
                            isMusicVideo = mediaMetadata.isMusicVideo,
                        )
                    var displayArtworkUrl by remember(swapState.displayUrl) {
                        mutableStateOf(swapState.displayUrl)
                    }
                    var artworkLoadFailed by remember(swapState.displayUrl) {
                        mutableStateOf(false)
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(artworkSize)
                                .clip(RoundedCornerShape(16.dp))
                                .pointerInput(canSkipPrevious, canSkipNext) {
                                    var accumulatedX = 0f
                                    val threshold = 64.dp.toPx()
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (accumulatedX <= -threshold && canSkipNext) {
                                                triggerHaptic(ArchiveTuneHapticKind.SKIP)
                                                playerConnection.seekToNext()
                                            } else if (accumulatedX >= threshold && canSkipPrevious) {
                                                triggerHaptic(ArchiveTuneHapticKind.SKIP)
                                                playerConnection.seekToPrevious()
                                            }
                                            accumulatedX = 0f
                                        },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        accumulatedX += dragAmount
                                    }
                                },
                    ) {
                        if (hidePlayerThumbnail || displayArtworkUrl.isNullOrBlank() || artworkLoadFailed) {
                            LocalArtworkFallback(modifier = Modifier.fillMaxSize())
                        } else {
                            AsyncImage(
                                model = rememberOfflineArtworkImageRequest(displayArtworkUrl),
                                contentDescription = mediaMetadata.title,
                                contentScale = ContentScale.Crop,
                                onState = { imageState ->
                                    if (imageState is AsyncImagePainter.State.Error) {
                                        val nextFallback = getNextFallbackUrl(displayArtworkUrl)
                                        if (nextFallback != null) {
                                            displayArtworkUrl = nextFallback
                                        } else {
                                            artworkLoadFailed = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val shouldMarquee =
                                    isPlaying &&
                                        moe.rukamori.archivetune.ui.motion.LocalMotionConfig.current.animationsEnabled
                                Text(
                                    text = mediaMetadata.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .clickable(onClick = titleActions.onTitleClick)
                                            .then(
                                                if (shouldMarquee) {
                                                    Modifier.basicMarquee(iterations = 2)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                )
                                Text(
                                    text = mediaMetadata.artists.joinToString { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier.clickable {
                                            mediaMetadata.artists.firstOrNull()?.id?.let { artistId ->
                                                titleActions.onArtistClick(artistId)
                                            }
                                        },
                                )
                            }

                            IconButton(
                                onClick = {
                                    triggerHaptic(ArchiveTuneHapticKind.LIKE)
                                    playerConnection.toggleLike()
                                },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (currentSongLiked) R.drawable.favorite else R.drawable.favorite_border,
                                        ),
                                    contentDescription =
                                        stringResource(
                                            if (currentSongLiked) R.string.action_remove_like else R.string.action_like,
                                        ),
                                    tint = if (currentSongLiked) MaterialTheme.colorScheme.error else primaryInk,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        val safeDuration = duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 1L
                        val activePosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
                        Slider(
                            value = activePosition.toFloat(),
                            valueRange = 0f..safeDuration.toFloat(),
                            enabled = duration > 0L && duration != C.TIME_UNSET,
                            onValueChange = { onSliderValueChange(it.toLong()) },
                            onValueChangeFinished = onSliderValueChangeFinished,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = secondaryInk.copy(alpha = 0.25f),
                                ),
                            modifier = Modifier.fillMaxWidth().height(26.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = makeTimeString(activePosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk,
                            )
                            Text(
                                text = if (duration > 0L && duration != C.TIME_UNSET) makeTimeString(duration) else "--:--",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                enabled = canSkipPrevious,
                                onClick = {
                                    triggerHaptic(ArchiveTuneHapticKind.SKIP)
                                    playerConnection.seekToPrevious()
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.apple_skip_previous),
                                    contentDescription = stringResource(R.string.widget_previous),
                                    tint = if (canSkipPrevious) primaryInk else secondaryInk.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            FilledTonalIconButton(
                                onClick = {
                                    triggerHaptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                                    if (playbackState == Player.STATE_ENDED) {
                                        playerConnection.player.seekTo(0, 0L)
                                        playerConnection.player.playWhenReady = true
                                    } else {
                                        playerConnection.player.togglePlayPause()
                                    }
                                },
                                colors =
                                    IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = accentColor,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                modifier = Modifier.size(50.dp),
                            ) {
                                if (playbackState == Player.STATE_ENDED) {
                                    Icon(
                                        painter = painterResource(R.drawable.replay),
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(26.dp),
                                    )
                                } else {
                                    MorphingPlayPauseIcon(
                                        isPlaying = isPlaying,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        size = 28.dp,
                                        contentDescription =
                                            stringResource(
                                                if (isPlaying) R.string.widget_pause else R.string.play,
                                            ),
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            IconButton(
                                enabled = canSkipNext,
                                onClick = {
                                    triggerHaptic(ArchiveTuneHapticKind.SKIP)
                                    playerConnection.seekToNext()
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.apple_skip_next),
                                    contentDescription = stringResource(R.string.next),
                                    tint = if (canSkipNext) primaryInk else secondaryInk.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Right pane: persistent Queue / Lyrics switcher + compact actions
            Surface(
                color = containerSurface,
                shape = RoundedCornerShape(20.dp),
                modifier =
                    Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { state.collapseSoft() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = stringResource(R.string.player_landscape_collapse),
                                tint = primaryInk,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Row(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(primaryInk.copy(alpha = 0.08f))
                                    .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            LandscapePaneChip(
                                label = stringResource(R.string.player_landscape_queue_tab),
                                selected = rightPane == LandscapeRightPane.QUEUE,
                                selectedContainer = accentColor,
                                selectedInk = MaterialTheme.colorScheme.onPrimary,
                                unselectedInk = primaryInk,
                                onClick = {
                                    triggerHaptic(ArchiveTuneHapticKind.SELECTION)
                                    rightPane = LandscapeRightPane.QUEUE
                                },
                                modifier = Modifier.weight(1f),
                            )
                            LandscapePaneChip(
                                label = stringResource(R.string.player_landscape_lyrics_tab),
                                selected = rightPane == LandscapeRightPane.LYRICS,
                                selectedContainer = accentColor,
                                selectedInk = MaterialTheme.colorScheme.onPrimary,
                                unselectedInk = primaryInk,
                                onClick = {
                                    triggerHaptic(ArchiveTuneHapticKind.SELECTION)
                                    rightPane = LandscapeRightPane.LYRICS
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        IconButton(
                            onClick = {
                                if (sleepTimerEnabled) {
                                    playerConnection.service.sleepTimer.clear()
                                } else {
                                    onShowSleepTimerDialog()
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.bedtime),
                                contentDescription = stringResource(R.string.sleep_timer),
                                tint = if (sleepTimerEnabled) accentColor else primaryInk,
                                modifier = Modifier.size(19.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                menuState.showImmersive {
                                    PlayerMenu(
                                        mediaMetadata = mediaMetadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(mediaMetadata.id)
                                            }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = stringResource(R.string.more_options),
                                tint = primaryInk,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    ) {
                        when (rightPane) {
                            LandscapeRightPane.QUEUE -> {
                                LandscapeQueuePane(
                                    playerConnection = playerConnection,
                                    primaryInk = primaryInk,
                                    secondaryInk = secondaryInk,
                                    accentColor = accentColor,
                                    onMoveItem = { from, to ->
                                        playerConnection.player.moveMediaItem(from, to)
                                    },
                                    onRemoveItem = { index ->
                                        playerConnection.player.removeMediaItem(index)
                                    },
                                    onPlayAt = { index ->
                                        playerConnection.player.seekToDefaultPosition(index)
                                        playerConnection.player.playWhenReady = true
                                    },
                                    onScrollToCurrent = { listState, index ->
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    },
                                )
                            }

                            LandscapeRightPane.LYRICS -> {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Lyrics(
                                        sliderPositionProvider = { sliderPosition },
                                        lyricsSyncOffset = lyricsSyncOffset,
                                        modifier = Modifier.fillMaxSize(),
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

@Composable
private fun LandscapePaneChip(
    label: String,
    selected: Boolean,
    selectedContainer: Color,
    selectedInk: Color,
    unselectedInk: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) selectedContainer else Color.Transparent,
        shape = CircleShape,
        modifier =
            modifier
                .height(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) selectedInk else unselectedInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LandscapeQueuePane(
    playerConnection: PlayerConnection,
    primaryInk: Color,
    secondaryInk: Color,
    accentColor: Color,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onPlayAt: (Int) -> Unit,
    onScrollToCurrent: (androidx.compose.foundation.lazy.LazyListState, Int) -> Unit,
) {
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val isCurrentOffscreen by remember(queueWindows, currentWindowIndex) {
        derivedStateOf {
            if (currentWindowIndex !in queueWindows.indices) return@derivedStateOf false
            val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
            visibleIndices.isNotEmpty() && currentWindowIndex !in visibleIndices
        }
    }

    LaunchedEffect(Unit) {
        if (currentWindowIndex in queueWindows.indices) {
            listState.scrollToItem(currentWindowIndex)
        }
    }

    if (queueWindows.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.player_landscape_queue_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryInk,
            )
        }
        return
    }

    val moveUpLabel = stringResource(R.string.player_landscape_move_up)
    val moveDownLabel = stringResource(R.string.player_landscape_move_down)
    val removeLabel = stringResource(R.string.player_landscape_remove_queue)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = queueWindows,
                key = { _, w -> (w.uid.hashCode().toLong() shl 32) xor (w.firstPeriodIndex.toLong() and 0xFFFFFFFFL) },
            ) { index, window ->
                val meta = window.mediaItem.metadata
                val isCurrent = index == currentWindowIndex
                Surface(
                    color = if (isCurrent) accentColor.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlayAt(window.firstPeriodIndex) }
                            .semantics {
                                customActions =
                                    buildList {
                                        if (index > 0) {
                                            add(
                                                CustomAccessibilityAction(moveUpLabel) {
                                                    onMoveItem(index, index - 1)
                                                    true
                                                },
                                            )
                                        }
                                        if (index < queueWindows.lastIndex) {
                                            add(
                                                CustomAccessibilityAction(moveDownLabel) {
                                                    onMoveItem(index, index + 1)
                                                    true
                                                },
                                            )
                                        }
                                        add(
                                            CustomAccessibilityAction(removeLabel) {
                                                onRemoveItem(index)
                                                true
                                            },
                                        )
                                    }
                            },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                        ) {
                            val artUrl = meta?.thumbnailUrl
                            if (artUrl.isNullOrBlank()) {
                                LocalArtworkFallback(modifier = Modifier.fillMaxSize())
                            } else {
                                AsyncImage(
                                    model = rememberOfflineArtworkImageRequest(artUrl),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meta?.title ?: window.mediaItem.mediaMetadata.title?.toString().orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) accentColor else primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    meta?.artists?.joinToString { it.name }
                                        ?: window.mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (!isCurrent) {
                            IconButton(
                                onClick = { onRemoveItem(index) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = removeLabel,
                                    tint = secondaryInk,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isCurrentOffscreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
        ) {
            Surface(
                color = accentColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                shadowElevation = 4.dp,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable { onScrollToCurrent(listState, currentWindowIndex) },
            ) {
                Text(
                    text = stringResource(R.string.player_landscape_jump_current),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
