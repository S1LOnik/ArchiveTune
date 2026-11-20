/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.playerTransportIcons
import moe.rukamori.archivetune.constants.FloatingNavigationBarMaxWidth
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsDisplayCharacter
import moe.rukamori.archivetune.constants.MINI_PLAYER_BAR_HEIGHT_DP
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerCompactHeight
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.MotionConfig
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.ui.motion.rememberDecorativeMotionActive
import moe.rukamori.archivetune.ui.player.StyledPlaybackSlider
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneThemeColor
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneColorScheme
import moe.rukamori.archivetune.ui.theme.resolveCustomThemeSelection
import kotlin.math.roundToInt

enum class StudioPreviewStage {
    HOME,
    PLAYER,
    LYRICS,
}

@Composable
fun StudioInteractivePreview(
    snapshot: AppearanceProfileSnapshot,
    isComparingSaved: Boolean,
    coverMode: StudioCoverPreviewMode,
    onCoverModeChange: (StudioCoverPreviewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stage by remember { mutableStateOf(StudioPreviewStage.HOME) }
    var trackIndex by remember { mutableIntStateOf(0) }
    var replaySeed by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    val haptic = rememberArchiveTuneHapticTrigger()
    val reducedMotion =
        snapshot.motion.disableAnimations || snapshot.motion.profile == MotionProfile.REDUCED

    val replayScale = remember { Animatable(1f) }
    LaunchedEffect(replaySeed) {
        if (replaySeed > 0) {
            if (reducedMotion) {
                replayScale.snapTo(1f)
            } else {
                val stiffness =
                    when (snapshot.motion.profile) {
                        MotionProfile.FAST -> 650f
                        MotionProfile.SMOOTH -> 320f
                        else -> Spring.StiffnessMediumLow
                    }
                replayScale.snapTo(0.95f)
                replayScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = stiffness))
            }
        }
    }

    val stageIndex = stage.ordinal
    val systemIsDark = isSystemInDarkTheme()
    val bgIsDark =
        when (snapshot.color.darkMode) {
            DarkMode.ON -> true
            DarkMode.OFF -> false
            DarkMode.AUTO -> systemIsDark
        }
    val currentAppThemeColor = LocalArchiveTuneThemeColor.current
    val (customColor, customSeedPalette) =
        remember(snapshot.color.customThemeColor) {
            resolveCustomThemeSelection(snapshot.color.customThemeColor)
        }
    val effectiveThemeColor = if (snapshot.color.dynamicTheme) currentAppThemeColor else customColor
    val effectiveSeedPalette = if (snapshot.color.dynamicTheme) null else customSeedPalette
    val previewColorScheme =
        rememberArchiveTuneColorScheme(
            darkTheme = bgIsDark,
            pureBlack = snapshot.color.pureBlack,
            themeColor = effectiveThemeColor,
            seedPalette = effectiveSeedPalette,
        )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Stage switcher row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    StudioPreviewStage.HOME to R.string.studio_section_home,
                    StudioPreviewStage.PLAYER to R.string.studio_preview_tab_player,
                    StudioPreviewStage.LYRICS to R.string.studio_preview_tab_lyrics,
                ).forEach { (itemStage, labelRes) ->
                    FilterChip(
                        selected = stage == itemStage,
                        onClick = {
                            haptic(ArchiveTuneHapticKind.SELECTION)
                            stage = itemStage
                        },
                        label = {
                            Text(
                                text = stringResource(labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            // Scaled live app frame scoped to the draft's real ColorScheme
            MaterialTheme(colorScheme = previewColorScheme) {
                val frameSurface = MaterialTheme.colorScheme.background
                val frameOnSurface = MaterialTheme.colorScheme.onBackground
                val frameOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(332.dp)
                            .graphicsLayer {
                                scaleX = replayScale.value
                                scaleY = replayScale.value
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .background(frameSurface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                RoundedCornerShape(20.dp),
                            ),
                ) {
                    AnimatedVisibility(visible = isComparingSaved) {
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.studio_comparing_saved),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = stageIndex,
                            transitionSpec = {
                                if (reducedMotion || !snapshot.motion.screenTransitions) {
                                    fadeIn(snap()) togetherWith fadeOut(snap())
                                } else {
                                    val dir = if (targetState >= initialState) 1 else -1
                                    (slideInHorizontally(tween(240)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally(tween(220)) { -it / 5 * dir } + fadeOut(tween(180)))
                                }
                            },
                            label = "studioPreviewStage",
                        ) { activeStage ->
                            when (StudioPreviewStage.entries[activeStage]) {
                                StudioPreviewStage.HOME ->
                                    StudioHomeAndNavStage(
                                        snapshot = snapshot,
                                        frameOnSurface = frameOnSurface,
                                        frameOnSurfaceVariant = frameOnSurfaceVariant,
                                        track = StudioDemoData.DemoTracks[trackIndex],
                                        isPlaying = isPlaying,
                                        onTogglePlay = {
                                            haptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                                            isPlaying = !isPlaying
                                        },
                                        onNextTrack = {
                                            haptic(ArchiveTuneHapticKind.SELECTION)
                                            trackIndex = (trackIndex + 1) % StudioDemoData.DemoTracks.size
                                        },
                                        onOpenPlayer = { stage = StudioPreviewStage.PLAYER },
                                    )

                                StudioPreviewStage.PLAYER ->
                                    StudioPlayerStage(
                                        snapshot = snapshot,
                                        track = StudioDemoData.DemoTracks[trackIndex],
                                        coverMode = coverMode,
                                        isPlaying = isPlaying,
                                        frameOnSurface = frameOnSurface,
                                        frameOnSurfaceVariant = frameOnSurfaceVariant,
                                        onTogglePlay = {
                                            haptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                                            isPlaying = !isPlaying
                                        },
                                        onNextTrack = {
                                            haptic(ArchiveTuneHapticKind.SELECTION)
                                            trackIndex = (trackIndex + 1) % StudioDemoData.DemoTracks.size
                                        },
                                    )

                                StudioPreviewStage.LYRICS ->
                                    StudioLyricsStage(
                                        snapshot = snapshot,
                                        track = StudioDemoData.DemoTracks[trackIndex],
                                        isPlaying = isPlaying,
                                        frameOnSurface = frameOnSurface,
                                        frameOnSurfaceVariant = frameOnSurfaceVariant,
                                        onTogglePlay = {
                                            haptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                                            isPlaying = !isPlaying
                                        },
                                    )
                            }
                        }
                    }
                }
            }

            // Quick demo controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        haptic(ArchiveTuneHapticKind.SELECTION)
                        trackIndex = (trackIndex + 1) % StudioDemoData.DemoTracks.size
                        replaySeed++
                    },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.studio_preview_track_swap),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalButton(
                    onClick = {
                        haptic(ArchiveTuneHapticKind.SELECTION)
                        replaySeed++
                    },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.studio_preview_replay_motion),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (stage == StudioPreviewStage.PLAYER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        StudioCoverPreviewMode.LIGHT to R.string.studio_preview_cover_light,
                        StudioCoverPreviewMode.DARK to R.string.studio_preview_cover_dark,
                        StudioCoverPreviewMode.NONE to R.string.studio_preview_cover_none,
                    ).forEach { (mode, labelRes) ->
                        FilterChip(
                            selected = coverMode == mode,
                            onClick = { onCoverModeChange(mode) },
                            label = {
                                Text(
                                    text = stringResource(labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioHomeAndNavStage(
    snapshot: AppearanceProfileSnapshot,
    frameOnSurface: Color,
    frameOnSurfaceVariant: Color,
    track: StudioDemoTrack,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val feedState = rememberLazyListState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var manualCollapsed by remember { mutableStateOf(false) }

    val scrolledDown by remember {
        derivedStateOf {
            feedState.firstVisibleItemIndex > 0 || feedState.firstVisibleItemScrollOffset > 36
        }
    }
    val navCollapsed =
        when (snapshot.navigation.compactBehavior) {
            NavigationBarCompactBehavior.ALWAYS_COMPACT -> true
            NavigationBarCompactBehavior.ALWAYS_EXPANDED -> false
            NavigationBarCompactBehavior.ADAPTIVE -> scrolledDown || manualCollapsed
        }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = feedState,
            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            item(key = "home_mode_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            when (snapshot.home.homeFeedStyle) {
                                HomeFeedStyle.STANDARD -> stringResource(R.string.home_feed_style_standard)
                                HomeFeedStyle.COMPACT -> stringResource(R.string.home_feed_style_compact)
                                HomeFeedStyle.GALLERY -> stringResource(R.string.home_feed_style_gallery)
                                HomeFeedStyle.NOW -> stringResource(R.string.home_feed_style_now)
                                HomeFeedStyle.COVERS -> stringResource(R.string.home_feed_style_covers)
                            },
                        style = MaterialTheme.typography.titleSmall,
                        color = frameOnSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        onClick = { manualCollapsed = !manualCollapsed },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (navCollapsed) {
                                        R.string.studio_preview_collapsed_nav
                                    } else {
                                        R.string.studio_preview_expanded_nav
                                    },
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            if (snapshot.home.showHomeCategoryChips) {
                item(key = "demo_chips") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudioDemoData.DemoChipResIds.forEachIndexed { idx, labelRes ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color =
                                    if (idx == 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (idx == 0) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            frameOnSurfaceVariant
                                        },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            when (snapshot.home.homeFeedStyle) {
                HomeFeedStyle.COVERS -> {
                    item(key = "covers_mosaic") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().height(84.dp),
                        ) {
                            DemoCoverTile(StudioDemoData.DemoHomeCards[0], Modifier.weight(1.2f).fillMaxSize())
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(0.9f).fillMaxSize(),
                            ) {
                                DemoCoverTile(StudioDemoData.DemoHomeCards[1], Modifier.weight(1f).fillMaxWidth())
                                DemoCoverTile(StudioDemoData.DemoHomeCards[2], Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                }

                HomeFeedStyle.NOW -> {
                    item(key = "now_continue") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.linearGradient(track.darkColors),
                                            ),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(StudioDemoData.DemoHomeCards[0].titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = frameOnSurface,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = stringResource(StudioDemoData.DemoHomeCards[0].subtitleRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = frameOnSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }

            itemsIndexed(
                items = StudioDemoData.DemoHomeCards,
                key = { _, item -> item.id },
            ) { _, card ->
                val rowHeight = if (snapshot.home.homeFeedStyle == HomeFeedStyle.COMPACT) 38.dp else 46.dp
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(rowHeight - 14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Brush.linearGradient(listOf(card.accent, card.secondary))),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(card.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = frameOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(card.subtitleRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = frameOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Dedicated non-overlapping bottom dock for Mini-Player + Navigation Bar
        UnifiedNavAndMiniPlayerBarOverlay(
            navigation = snapshot.navigation,
            playerStyle = snapshot.player.designStyle,
            reducedMotion = snapshot.motion.disableAnimations || snapshot.motion.profile == MotionProfile.REDUCED,
            collapsed = navCollapsed,
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            track = track,
            isPlaying = isPlaying,
            onTogglePlay = onTogglePlay,
            onNextTrack = onNextTrack,
            onMiniPlayerClick = onOpenPlayer,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DemoCoverTile(
    card: StudioDemoHomeCard,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(card.accent, card.secondary)))
                .padding(8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = stringResource(card.titleRes),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun UnifiedNavMiniPlayerPreview(
    navigation: NavigationProfile,
    playerStyle: PlayerDesignStyle = PlayerDesignStyle.APPLE_MUSIC,
    reducedMotion: Boolean = false,
    onSelectPreset: ((NavMiniPlayerPreset) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var scrollDemoCollapsed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrolledDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 28
        }
    }
    val collapsed =
        when (navigation.compactBehavior) {
            NavigationBarCompactBehavior.ALWAYS_COMPACT -> true
            NavigationBarCompactBehavior.ALWAYS_EXPANDED -> false
            NavigationBarCompactBehavior.ADAPTIVE -> scrolledDown || scrollDemoCollapsed
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onSelectPreset != null) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(NavMiniPlayerStarterPresets, key = { it.id }) { preset ->
                    FilterChip(
                        selected = preset.navigation.material == navigation.material &&
                            preset.navigation.miniPlayerStyle == navigation.miniPlayerStyle &&
                            preset.navigation.hideLabels == navigation.hideLabels,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                text = stringResource(preset.titleRes),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(248.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ),
                    ),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                item(key = "nav_demo_toggle") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(navigation.material.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            onClick = { scrollDemoCollapsed = !scrollDemoCollapsed },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (collapsed) {
                                            R.string.studio_preview_collapsed_nav
                                        } else {
                                            R.string.studio_preview_expanded_nav
                                        },
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                itemsIndexed(StudioDemoData.DemoHomeCards) { _, card ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(card.accent),
                            )
                            Text(
                                text = stringResource(card.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            UnifiedNavAndMiniPlayerBarOverlay(
                navigation = navigation,
                playerStyle = playerStyle,
                reducedMotion = reducedMotion,
                collapsed = collapsed,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                track = StudioDemoData.DemoTracks[0],
                isPlaying = isPlaying,
                onTogglePlay = { isPlaying = !isPlaying },
                onNextTrack = {},
                onMiniPlayerClick = { scrollDemoCollapsed = !scrollDemoCollapsed },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UnifiedNavAndMiniPlayerBarOverlay(
    navigation: NavigationProfile,
    playerStyle: PlayerDesignStyle,
    reducedMotion: Boolean,
    collapsed: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    track: StudioDemoTrack,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transportIcons = playerTransportIcons(playerStyle)
    val animatedCompactFraction by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.88f, stiffness = 420f),
        label = "studioNavCompactFraction",
    )
    val compactFraction = animatedCompactFraction.coerceIn(0f, 1f)

    val effectiveWidthFraction =
        when (navigation.material) {
            NavBarMaterial.SUKISU_GLASS,
            NavBarMaterial.BITCHORD,
            -> 0.92f
            else -> navigation.widthFraction.coerceIn(0.5f, 1.0f)
        }
    val previewScale = 0.76f
    val realBarHeight =
        when (navigation.material) {
            NavBarMaterial.LIQUID_GLASS,
            NavBarMaterial.SUKISU_GLASS,
            -> 66.dp * previewScale
            else -> NavigationBarHeight * navigation.heightMultiplier * previewScale
        }
    val cornerDp =
        when (navigation.material) {
            NavBarMaterial.LIQUID_GLASS,
            NavBarMaterial.SUKISU_GLASS,
            -> 26.dp
            NavBarMaterial.BITCHORD -> 22.dp
            else -> (navigation.cornerRadius * previewScale).dp
        }
    val maxWidthCap =
        when (navigation.material) {
            NavBarMaterial.LIQUID_GLASS -> FloatingNavigationBarMaxWidth
            else -> NavigationBarMaxWidth
        }

    val isGlassMaterial = navBarMaterialSupportsGlassGlow(navigation.material)
    val barColor =
        when (navigation.material) {
            NavBarMaterial.STANDARD ->
                MaterialTheme.colorScheme.surfaceContainer.copy(
                    alpha = (navigation.opacity * (1f - navigation.transparency)).coerceIn(0.12f, 1f),
                )
            NavBarMaterial.FROSTED ->
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            NavBarMaterial.TINTED ->
                MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = (navigation.opacity * (1f - navigation.transparency)).coerceIn(0.25f, 0.98f),
                )
            NavBarMaterial.LIQUID_GLASS ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.56f)
            NavBarMaterial.SUKISU_GLASS ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.66f)
            NavBarMaterial.BITCHORD ->
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        }
    val barBorderWidth =
        when {
            isGlassMaterial -> 1.2.dp
            navigation.material == NavBarMaterial.FROSTED ||
                navigation.material == NavBarMaterial.BITCHORD -> 1.dp
            else -> 0.dp
        }
    val barBorderColor =
        when {
            isGlassMaterial && navigation.glassGlow ->
                MaterialTheme.colorScheme.primary.copy(
                    alpha = (0.38f * navigation.glassGlowIntensity).coerceIn(0.14f, 0.85f),
                )
            isGlassMaterial -> Color.White.copy(alpha = 0.28f)
            navigation.material == NavBarMaterial.FROSTED -> Color.White.copy(alpha = 0.22f)
            navigation.material == NavBarMaterial.BITCHORD ->
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            else -> Color.Transparent
        }

    val miniHeight =
        (
            if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                androidx.compose.ui.unit.lerp(
                    (MINI_PLAYER_BAR_HEIGHT_DP * previewScale).dp,
                    MiniPlayerCompactHeight * previewScale,
                    compactFraction,
                )
            } else {
                androidx.compose.ui.unit.lerp(
                    MiniPlayerHeight * previewScale,
                    MiniPlayerCompactHeight * previewScale,
                    compactFraction,
                )
            }
            ).coerceAtLeast(0.dp)
    val visibleBarHeight = androidx.compose.ui.unit.lerp(realBarHeight, 0.dp, compactFraction).coerceAtLeast(0.dp)
    val visibleBarBottomPad =
        if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR && effectiveWidthFraction >= 0.99f) {
            0.dp
        } else {
            androidx.compose.ui.unit.lerp(8.dp, 0.dp, compactFraction).coerceAtLeast(0.dp)
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        // Mini-Player + flanking compact Library / Search circular controls (matches MainActivity)
        val miniBottomPadding =
            if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                androidx.compose.ui.unit.lerp(3.dp, 6.dp, compactFraction).coerceAtLeast(0.dp)
            } else {
                6.dp
            }
        val baseMiniHorizontalPad = if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) 0.dp else 12.dp
        val miniHorizontalPadding =
            androidx.compose.ui.unit.lerp(
                baseMiniHorizontalPad,
                46.dp,
                compactFraction,
            ).coerceAtLeast(0.dp)
        val miniShape =
            if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                val topRadius = androidx.compose.ui.unit.lerp(12.dp, 22.dp, compactFraction).coerceAtLeast(0.dp)
                val bottomRadius = androidx.compose.ui.unit.lerp(0.dp, 22.dp, compactFraction).coerceAtLeast(0.dp)
                RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius,
                )
            } else {
                RoundedCornerShape(
                    androidx.compose.ui.unit.lerp(18.dp, 24.dp, compactFraction).coerceAtLeast(0.dp),
                )
            }

        val miniIsGradient = navigation.miniPlayerBackground == MiniPlayerBackgroundStyle.GRADIENT
        val miniTitleColor = if (miniIsGradient) Color.White else MaterialTheme.colorScheme.onSurface
        val miniSubtitleColor =
            if (miniIsGradient) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
        val miniBgBrush =
            when (navigation.miniPlayerBackground) {
                MiniPlayerBackgroundStyle.THEME ->
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                        ),
                    )
                MiniPlayerBackgroundStyle.GRADIENT ->
                    Brush.horizontalGradient(
                        track.darkColors.map { it.copy(alpha = 0.92f) },
                    )
                MiniPlayerBackgroundStyle.GLOW ->
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.78f),
                        ),
                    )
                MiniPlayerBackgroundStyle.FROSTED ->
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
                        ),
                    )
                MiniPlayerBackgroundStyle.LIQUID_GLASS ->
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f),
                        ),
                    )
            }
        val miniBorderColor =
            when (navigation.miniPlayerBackground) {
                MiniPlayerBackgroundStyle.LIQUID_GLASS ->
                    if (navigation.glassGlow) {
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = (0.36f * navigation.glassGlowIntensity).coerceIn(0.14f, 0.8f),
                        )
                    } else {
                        Color.White.copy(alpha = 0.28f)
                    }
                MiniPlayerBackgroundStyle.FROSTED -> Color.White.copy(alpha = 0.24f)
                MiniPlayerBackgroundStyle.GLOW -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else -> Color.Transparent
            }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = miniBottomPadding),
        ) {
            if (compactFraction > 0.01f) {
                StudioCompactCircleButton(
                    iconRes = Screens.Library.iconIdActive,
                    bgColor = barColor,
                    borderWidth = barBorderWidth,
                    borderColor = barBorderColor,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 10.dp)
                            .alpha(compactFraction.coerceIn(0f, 1f)),
                )
                StudioCompactCircleButton(
                    iconRes = R.drawable.search,
                    bgColor = barColor,
                    borderWidth = barBorderWidth,
                    borderColor = barBorderColor,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                            .alpha(compactFraction.coerceIn(0f, 1f)),
                )
            }

            Surface(
                onClick = onMiniPlayerClick,
                shape = miniShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = if (navigation.miniPlayerBackground == MiniPlayerBackgroundStyle.LIQUID_GLASS) 2.dp else 4.dp,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = miniHorizontalPadding)
                        .fillMaxWidth()
                        .height(miniHeight)
                        .clip(miniShape)
                        .background(miniBgBrush)
                        .then(
                            if (miniBorderColor != Color.Transparent) {
                                Modifier.border(1.dp, miniBorderColor, miniShape)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Brush.linearGradient(track.darkColors)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(track.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = miniTitleColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = miniSubtitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onTogglePlay, modifier = Modifier.size(28.dp)) {
                            Icon(
                                painter = if (isPlaying) transportIcons.pause else transportIcons.play,
                                contentDescription =
                                    stringResource(
                                        if (isPlaying) R.string.widget_pause else R.string.play,
                                    ),
                                tint = miniTitleColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (compactFraction < 0.7f) {
                            IconButton(onClick = onNextTrack, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    painter = transportIcons.next,
                                    contentDescription = stringResource(R.string.next),
                                    tint = miniTitleColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                        LinearProgressIndicator(
                            progress = { 0.42f },
                            color = if (miniIsGradient) Color.White else MaterialTheme.colorScheme.primary,
                            trackColor = miniSubtitleColor.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                        )
                    }
                }
            }
        }

        // Real NavBar geometry using real height, widthFraction, cornerRadius & material sheen
        if (visibleBarHeight > 1.dp) {
            val barShape = RoundedCornerShape(cornerDp.coerceAtLeast(0.dp))
            Box(
                modifier =
                    Modifier
                        .padding(bottom = visibleBarBottomPad)
                        .widthIn(max = maxWidthCap)
                        .fillMaxWidth(effectiveWidthFraction)
                        .height(visibleBarHeight)
                        .graphicsLayer { alpha = (1f - compactFraction * 1.1f).coerceIn(0f, 1f) }
                        .clip(barShape)
                        .background(barColor)
                        .then(
                            if (isGlassMaterial) {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.16f),
                                            Color.Transparent,
                                            if (navigation.glassGlow) {
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = (0.12f * navigation.glassGlowIntensity).coerceIn(0.05f, 0.28f),
                                                )
                                            } else {
                                                Color.Transparent
                                            },
                                        ),
                                    ),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (barBorderWidth > 0.dp) {
                                Modifier.border(barBorderWidth, barBorderColor, barShape)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Screens.MainScreens.forEachIndexed { idx, screen ->
                        val selected = idx == selectedTab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onSelectTab(idx) },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .clip(
                                            if (navigation.material == NavBarMaterial.BITCHORD) {
                                                RoundedCornerShape(10.dp)
                                            } else {
                                                CircleShape
                                            },
                                        )
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                                    contentDescription = null,
                                    tint =
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            if (!navigation.hideLabels) {
                                Spacer(modifier = Modifier.height((navigation.labelSpacing * previewScale).coerceIn(0f, 6f).dp))
                                Text(
                                    text = stringResource(screen.titleId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioCompactCircleButton(
    iconRes: Int,
    bgColor: Color,
    borderWidth: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(bgColor)
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(borderWidth, borderColor, CircleShape)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun StudioPlayerStage(
    snapshot: AppearanceProfileSnapshot,
    track: StudioDemoTrack,
    coverMode: StudioCoverPreviewMode,
    isPlaying: Boolean,
    frameOnSurface: Color,
    frameOnSurfaceVariant: Color,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0.38f) }
    val style = snapshot.player.designStyle
    val transportIcons = playerTransportIcons(style)
    val supportsCustomBg = playerStyleSupportsCustomBackground(style)
    val showVolumeBar = playerStyleSupportsVolumeBar(style) && snapshot.player.showVolumeBar
    val artworkColors =
        when (coverMode) {
            StudioCoverPreviewMode.LIGHT -> track.lightColors
            StudioCoverPreviewMode.DARK -> track.darkColors
            StudioCoverPreviewMode.NONE -> emptyList()
        }
    val primaryAccent = artworkColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val secondaryAccent = artworkColors.lastOrNull() ?: MaterialTheme.colorScheme.tertiary

    val isStyleDarkBackdrop =
        when {
            !supportsCustomBg ->
                style in setOf(
                    PlayerDesignStyle.V7,
                    PlayerDesignStyle.V8,
                    PlayerDesignStyle.APPLE_MUSIC,
                    PlayerDesignStyle.BITCHORD,
                    PlayerDesignStyle.TIKTOK,
                    PlayerDesignStyle.LOOPER,
                )
            snapshot.player.backgroundStyle == PlayerBackgroundStyle.DEFAULT -> false
            snapshot.player.backgroundStyle in setOf(
                PlayerBackgroundStyle.GLOW,
                PlayerBackgroundStyle.GLOW_ANIMATED,
                PlayerBackgroundStyle.BLUR,
                PlayerBackgroundStyle.BLUR_GRADIENT,
                PlayerBackgroundStyle.CUSTOM,
            ) -> true
            coverMode == StudioCoverPreviewMode.LIGHT -> false
            else -> true
        }

    val playerBgBrush =
        if (!supportsCustomBg) {
            when (style) {
                PlayerDesignStyle.V7,
                PlayerDesignStyle.V8,
                -> Brush.verticalGradient(listOf(primaryAccent.copy(alpha = 0.85f), Color(0xFF0E1118)))
                PlayerDesignStyle.V9 ->
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        ),
                    )
                PlayerDesignStyle.V10 ->
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                PlayerDesignStyle.APPLE_MUSIC ->
                    Brush.linearGradient(
                        listOf(
                            primaryAccent.copy(alpha = 0.78f),
                            secondaryAccent.copy(alpha = 0.86f),
                            Color(0xFF12141C),
                        ),
                    )
                PlayerDesignStyle.BITCHORD ->
                    Brush.verticalGradient(listOf(Color(0xFF131620), Color(0xFF1F2433)))
                PlayerDesignStyle.TIKTOK ->
                    Brush.verticalGradient(
                        listOf(
                            primaryAccent.copy(alpha = 0.68f),
                            Color(0xFF0C0D12),
                            Color(0xFF08090D),
                        ),
                    )
                PlayerDesignStyle.SIMPMUSIC ->
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.background,
                        ),
                    )
                PlayerDesignStyle.SPATIALFLOW ->
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    )
                PlayerDesignStyle.LOOPER ->
                    Brush.verticalGradient(listOf(Color(0xFF14181F), Color(0xFF0B0E14)))
                else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            }
        } else {
            when (snapshot.player.backgroundStyle) {
                PlayerBackgroundStyle.DEFAULT ->
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                PlayerBackgroundStyle.GRADIENT ->
                    if (artworkColors.isEmpty()) {
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        )
                    } else {
                        Brush.verticalGradient(artworkColors.map { it.copy(alpha = 0.82f) })
                    }
                PlayerBackgroundStyle.COLORING -> {
                    val solid =
                        if (artworkColors.isEmpty()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            primaryAccent.copy(alpha = 0.88f)
                        }
                    Brush.verticalGradient(listOf(solid, solid))
                }
                PlayerBackgroundStyle.BLUR ->
                    Brush.radialGradient(
                        colors =
                            listOf(
                                primaryAccent.copy(alpha = if (snapshot.color.disableBlur) 0.55f else 0.78f),
                                secondaryAccent.copy(alpha = 0.62f),
                                Color(0xFF12151E),
                            ),
                    )
                PlayerBackgroundStyle.BLUR_GRADIENT ->
                    Brush.verticalGradient(
                        listOf(
                            primaryAccent.copy(alpha = 0.78f),
                            secondaryAccent.copy(alpha = 0.56f),
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                    )
                PlayerBackgroundStyle.GLOW ->
                    Brush.radialGradient(
                        colors = listOf(primaryAccent.copy(alpha = 0.82f), Color(0xFF10131B)),
                    )
                PlayerBackgroundStyle.GLOW_ANIMATED ->
                    Brush.linearGradient(
                        listOf(
                            primaryAccent.copy(alpha = 0.85f),
                            secondaryAccent.copy(alpha = 0.76f),
                            Color(0xFF10121A),
                        ),
                    )
                PlayerBackgroundStyle.CUSTOM ->
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF20253A),
                            primaryAccent.copy(alpha = 0.45f),
                            Color(0xFF141824),
                        ),
                    )
            }
        }

    val onPlayerContent =
        when {
            isStyleDarkBackdrop -> Color.White
            supportsCustomBg && snapshot.player.backgroundStyle != PlayerBackgroundStyle.DEFAULT && artworkColors.isNotEmpty() ->
                Color(0xFF141820)
            else -> frameOnSurface
        }
    val onPlayerSub = onPlayerContent.copy(alpha = 0.74f)
    val coverBaseSize = if (showVolumeBar) 98.dp else 112.dp
    val leftAlignedHeader =
        style in setOf(
            PlayerDesignStyle.APPLE_MUSIC,
            PlayerDesignStyle.V10,
            PlayerDesignStyle.TIKTOK,
            PlayerDesignStyle.BITCHORD,
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(playerBgBrush),
    ) {
        // Extra atmospheric layer for GLOW_ANIMATED / CUSTOM / BITCHORD / SPATIALFLOW
        if (supportsCustomBg && snapshot.player.backgroundStyle == PlayerBackgroundStyle.GLOW_ANIMATED) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primaryAccent.copy(alpha = 0.34f),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.25f, size.height * 0.28f),
                )
                drawCircle(
                    color = secondaryAccent.copy(alpha = 0.28f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.82f, size.height * 0.65f),
                )
            }
        } else if (supportsCustomBg && snapshot.player.backgroundStyle == PlayerBackgroundStyle.CUSTOM) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (i in 0..4) {
                    val y = size.height * (i / 4f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y - 36f),
                        strokeWidth = 2f,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: style name + own-backdrop pill or source pill
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (style == PlayerDesignStyle.APPLE_MUSIC) {
                    Box(
                        modifier =
                            Modifier
                                .width(32.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.42f)),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(playerDesignStyleLabelRes(style)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onPlayerContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!supportsCustomBg) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = onPlayerContent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = stringResource(R.string.studio_preview_own_backdrop),
                                style = MaterialTheme.typography.labelSmall,
                                color = onPlayerContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(playerBackgroundStyleLabelRes(snapshot.player.backgroundStyle)),
                            style = MaterialTheme.typography.labelSmall,
                            color = onPlayerSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Middle artwork + style-specific framing
            if (!snapshot.player.hideThumbnail) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        if (style == PlayerDesignStyle.TIKTOK) Arrangement.SpaceBetween else Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = track to coverMode,
                        transitionSpec = {
                            if (snapshot.motion.disableAnimations || !snapshot.motion.artworkTransitions) {
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(180)))
                            }
                        },
                        label = "studioPlayerTrackSwap",
                    ) { (activeTrack, mode) ->
                        val shape = RoundedCornerShape(snapshot.player.thumbnailCornerRadius.coerceAtLeast(0f).dp)
                        val coverModifier =
                            when {
                                style == PlayerDesignStyle.SIMPMUSIC ->
                                    Modifier.width(coverBaseSize + 36.dp).height(coverBaseSize - 6.dp).clip(shape)
                                snapshot.player.cropToSquare ->
                                    Modifier.size(coverBaseSize).aspectRatio(1f).clip(shape)
                                else ->
                                    Modifier.width(coverBaseSize + 14.dp).height(coverBaseSize - 10.dp).clip(shape)
                            }
                        val framedModifier =
                            when (style) {
                                PlayerDesignStyle.BITCHORD,
                                PlayerDesignStyle.V10,
                                -> coverModifier.border(1.dp, onPlayerContent.copy(alpha = 0.22f), shape)
                                else -> coverModifier
                            }
                        when (mode) {
                            StudioCoverPreviewMode.NONE ->
                                Box(
                                    modifier =
                                        framedModifier.background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                                ),
                                            ),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.14f),
                                            radius = size.minDimension * 0.34f,
                                            center = Offset(size.width * 0.72f, size.height * 0.3f),
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            StudioCoverPreviewMode.LIGHT ->
                                Box(
                                    modifier = framedModifier.background(Brush.linearGradient(activeTrack.lightColors)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(activeTrack.albumRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1E2430),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            StudioCoverPreviewMode.DARK ->
                                Box(
                                    modifier = framedModifier.background(Brush.linearGradient(activeTrack.darkColors)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(activeTrack.albumRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                        }
                    }

                    // TikTok vertical right-rail action stack or BitChord dock accent
                    if (style == PlayerDesignStyle.TIKTOK) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 2.dp),
                        ) {
                            listOf(R.drawable.music_note, R.drawable.lyrics, R.drawable.tune).forEach { iconRes ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.16f)),
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Controls container (tonal card for V9/SPATIALFLOW, frosted dock for BITCHORD, clean for others)
            val controlsCardBg =
                when (style) {
                    PlayerDesignStyle.V9 -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
                    PlayerDesignStyle.SPATIALFLOW -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                    PlayerDesignStyle.BITCHORD -> Color.White.copy(alpha = 0.08f)
                    PlayerDesignStyle.LOOPER -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Transparent
                }
            val controlsShape = RoundedCornerShape(14.dp)

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (controlsCardBg != Color.Transparent) {
                                Modifier
                                    .clip(controlsShape)
                                    .background(controlsCardBg)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            } else {
                                Modifier
                            },
                        ),
                horizontalAlignment = if (leftAlignedHeader) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(track.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onPlayerContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = onPlayerSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                StyledPlaybackSlider(
                    sliderStyle = snapshot.player.sliderStyle,
                    value = progress,
                    valueRange = 0f..1f,
                    onValueChange = { progress = it },
                    onValueChangeFinished = {},
                    activeColor = if (isStyleDarkBackdrop) Color.White else MaterialTheme.colorScheme.primary,
                    isPlaying = isPlaying && !snapshot.motion.disableAnimations,
                    modifier = Modifier.fillMaxWidth().height(22.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNextTrack, modifier = Modifier.size(34.dp)) {
                        Icon(
                            painter = transportIcons.previous,
                            contentDescription = null,
                            tint = onPlayerContent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    val playButtonShape =
                        when (style) {
                            PlayerDesignStyle.V9,
                            PlayerDesignStyle.BITCHORD,
                            -> RoundedCornerShape(12.dp)
                            else -> CircleShape
                        }
                    Surface(
                        onClick = onTogglePlay,
                        shape = playButtonShape,
                        color = if (isStyleDarkBackdrop) Color.White else MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            painter = if (isPlaying) transportIcons.pause else transportIcons.play,
                            contentDescription = null,
                            tint = if (isStyleDarkBackdrop) Color(0xFF14171D) else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    IconButton(onClick = onNextTrack, modifier = Modifier.size(34.dp)) {
                        Icon(
                            painter = transportIcons.next,
                            contentDescription = null,
                            tint = onPlayerContent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (showVolumeBar) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.volume_up),
                            contentDescription = null,
                            tint = onPlayerSub,
                            modifier = Modifier.size(14.dp),
                        )
                        LinearProgressIndicator(
                            progress = { 0.68f },
                            color = onPlayerContent.copy(alpha = 0.85f),
                            trackColor = onPlayerContent.copy(alpha = 0.22f),
                            modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioLyricsStage(
    snapshot: AppearanceProfileSnapshot,
    track: StudioDemoTrack,
    isPlaying: Boolean,
    frameOnSurface: Color,
    frameOnSurfaceVariant: Color,
    onTogglePlay: () -> Unit,
) {
    var activeLine by remember { mutableIntStateOf(1) }
    val reducedMotion =
        snapshot.motion.disableAnimations || snapshot.motion.profile == MotionProfile.REDUCED
    val decorativeMotionActive = rememberDecorativeMotionActive(isPlaying = isPlaying && !reducedMotion, isVisible = true)

    LaunchedEffect(decorativeMotionActive) {
        if (!decorativeMotionActive) return@LaunchedEffect
        while (true) {
            delay(2_200L)
            activeLine = (activeLine + 1) % StudioDemoData.DemoLyricsLines.size
        }
    }

    val effectivePlayerBg =
        if (playerStyleSupportsCustomBackground(snapshot.player.designStyle)) {
            snapshot.player.backgroundStyle
        } else {
            PlayerBackgroundStyle.DEFAULT
        }
    val resolvedLyricsBg = snapshot.lyrics.backgroundStyle.resolveFor(effectivePlayerBg)
    val usesOwnLyrics = playerStyleUsesOwnFullscreenLyrics(snapshot.player.designStyle)

    val lyricsBgBrush =
        when (resolvedLyricsBg) {
            LyricsBackgroundStyle.DEFAULT ->
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.background,
                    ),
                )
            LyricsBackgroundStyle.FOLLOW_THEME ->
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            LyricsBackgroundStyle.COLORING ->
                Brush.verticalGradient(
                    listOf(
                        track.darkColors.first().copy(alpha = 0.90f),
                        track.darkColors.last().copy(alpha = 0.94f),
                    ),
                )
            LyricsBackgroundStyle.MOVING_BLUR ->
                Brush.radialGradient(
                    colors =
                        listOf(
                            track.darkColors.first().copy(alpha = 0.82f),
                            track.darkColors.last().copy(alpha = 0.72f),
                            Color(0xFF0E111A),
                        ),
                )
            LyricsBackgroundStyle.CUSTOM ->
                Brush.linearGradient(
                    listOf(
                        Color(0xFF20253A),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        Color(0xFF141824),
                    ),
                )
        }
    val darkLyricsSurface =
        resolvedLyricsBg in setOf(
            LyricsBackgroundStyle.COLORING,
            LyricsBackgroundStyle.MOVING_BLUR,
            LyricsBackgroundStyle.CUSTOM,
        )
    val activeTextColor = if (darkLyricsSurface) Color.White else frameOnSurface
    val inactiveTextColor =
        if (darkLyricsSurface) Color.White.copy(alpha = 0.72f) else frameOnSurfaceVariant

    val previewFontSp = (snapshot.lyrics.textSize * 0.54f).coerceIn(11f, 19f).sp
    val characterLabel =
        stringResource(
            when (snapshot.lyrics.displayCharacter) {
                LyricsDisplayCharacter.READING -> R.string.lyrics_character_reading
                LyricsDisplayCharacter.FOCUS -> R.string.lyrics_character_focus
                LyricsDisplayCharacter.STAGE -> R.string.lyrics_character_stage
            },
        )
    val textAlign =
        if (snapshot.lyrics.displayCharacter == LyricsDisplayCharacter.STAGE) {
            TextAlign.Center
        } else {
            TextAlign.Start
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(lyricsBgBrush)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$characterLabel · ${stringResource(lyricsBackgroundStyleLabelRes(resolvedLyricsBg))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (darkLyricsSurface) Color.White else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (usesOwnLyrics) {
                    Text(
                        text = stringResource(R.string.studio_preview_own_lyrics_style),
                        style = MaterialTheme.typography.labelSmall,
                        color = inactiveTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                onClick = onTogglePlay,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = if (isPlaying) "II" else "▶",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy((4f * snapshot.lyrics.lineSpacing).dp),
            horizontalAlignment =
                if (snapshot.lyrics.displayCharacter == LyricsDisplayCharacter.STAGE) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                },
        ) {
            StudioDemoData.DemoLyricsLines.forEachIndexed { idx, line ->
                val isCurrent = idx == activeLine
                val inactiveAlpha =
                    when (snapshot.lyrics.displayCharacter) {
                        LyricsDisplayCharacter.READING -> 0.74f
                        LyricsDisplayCharacter.FOCUS -> 0.44f
                        LyricsDisplayCharacter.STAGE -> 0.28f
                    }
                val activeFontScale =
                    when (snapshot.lyrics.displayCharacter) {
                        LyricsDisplayCharacter.READING -> 1.02f
                        LyricsDisplayCharacter.FOCUS -> 1.10f
                        LyricsDisplayCharacter.STAGE -> 1.18f
                    }
                val blurRadius =
                    if (!isCurrent && snapshot.lyrics.lineBlur && !snapshot.color.disableBlur && !reducedMotion) {
                        when (snapshot.lyrics.displayCharacter) {
                            LyricsDisplayCharacter.READING -> 1.2.dp
                            LyricsDisplayCharacter.FOCUS -> 2.4.dp
                            LyricsDisplayCharacter.STAGE -> 3.2.dp
                        }
                    } else {
                        0.dp
                    }
                val rowBg =
                    when {
                        isCurrent && snapshot.lyrics.displayCharacter == LyricsDisplayCharacter.STAGE ->
                            (if (darkLyricsSurface) Color.White else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f)
                        isCurrent && reducedMotion ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    }

                Column(
                    horizontalAlignment =
                        if (snapshot.lyrics.displayCharacter == LyricsDisplayCharacter.STAGE) {
                            Alignment.CenterHorizontally
                        } else {
                            Alignment.Start
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(rowBg)
                            .clickable { activeLine = idx }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .alpha((if (isCurrent) 1f else inactiveAlpha).coerceIn(0f, 1f))
                            .let { mod -> if (blurRadius > 0.dp) mod.blur(blurRadius) else mod },
                ) {
                    Text(
                        text = stringResource(line.primaryTextRes),
                        fontSize = if (isCurrent) (previewFontSp.value * activeFontScale).sp else (previewFontSp.value * 0.86f).sp,
                        fontWeight =
                            when {
                                !isCurrent -> FontWeight.Normal
                                snapshot.lyrics.displayCharacter == LyricsDisplayCharacter.STAGE -> FontWeight.ExtraBold
                                else -> FontWeight.Bold
                            },
                        color = if (isCurrent) activeTextColor else inactiveTextColor,
                        textAlign = textAlign,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (line.translationRes != null) {
                        Text(
                            text = stringResource(line.translationRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = inactiveTextColor,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudioMotionDemoCard(
    motion: MotionProfileState,
    modifier: Modifier = Modifier,
) {
    val demoConfig =
        remember(motion) {
            MotionConfig(
                profile = motion.profile,
                legacyDisableAnimations = motion.disableAnimations,
                systemAnimatorScale = 1f,
                screenTransitionsChannel = motion.screenTransitions,
                artworkMotionChannel = motion.artworkTransitions,
                pressFeedbackChannel = motion.pressFeedback,
                decorativeBackgroundChannel = motion.decorativeBackground,
            )
        }
    val haptic = rememberArchiveTuneHapticTrigger()
    var step by remember { mutableIntStateOf(0) }
    val pressScale = remember { Animatable(1f) }

    LaunchedEffect(step, demoConfig) {
        if (step == 0) return@LaunchedEffect
        if (demoConfig.pressFeedbackEnabled) {
            pressScale.snapTo(0.93f)
            pressScale.animateTo(1f, demoConfig.pressSpring())
        } else {
            pressScale.snapTo(1f)
        }
    }

    val activeTab = step % 3
    val tabFraction by animateFloatAsState(
        targetValue = activeTab / 2f,
        animationSpec = demoConfig.navSpring(),
        label = "studioMotionDemoTab",
    )
    val safeTabFraction = tabFraction.coerceIn(0f, 1f)
    val glowAlpha by animateFloatAsState(
        targetValue = if (demoConfig.decorativeBackgroundEnabled && step % 2 == 1) 0.34f else 0.12f,
        animationSpec = demoConfig.microTween(baseMillis = 360),
        label = "studioMotionDemoGlow",
    )
    val track = StudioDemoData.DemoTracks[step % StudioDemoData.DemoTracks.size]

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                }
                .clickable {
                    haptic(ArchiveTuneHapticKind.SELECTION)
                    step++
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha.coerceIn(0f, 0.5f)),
                                Color.Transparent,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = (glowAlpha * 0.7f).coerceIn(0f, 0.4f)),
                            ),
                        ),
                    )
                    .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.studio_motion_test_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.studio_motion_test_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            haptic(ArchiveTuneHapticKind.SELECTION)
                            step++
                        },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(R.string.studio_motion_test_button))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            if (!demoConfig.artworkMotionEnabled) {
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                (slideInHorizontally(demoConfig.trackSwapSpec()) { it / 2 } +
                                    fadeIn(demoConfig.trackSwapSpec())) togetherWith
                                    (slideOutHorizontally(demoConfig.trackSwapSpec()) { -it / 2 } +
                                        fadeOut(demoConfig.trackSwapSpec()))
                            }
                        },
                        label = "studioMotionDemoArtwork",
                    ) { activeTrack ->
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Brush.linearGradient(activeTrack.darkColors)),
                        )
                    }

                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        val pillWidth = (maxWidth / 3f).coerceAtLeast(24.dp)
                        val travel = (maxWidth - pillWidth).coerceAtLeast(0.dp)
                        Box(
                            modifier =
                                Modifier
                                    .offset(x = (travel * safeTabFraction).coerceAtLeast(0.dp))
                                    .width(pillWidth)
                                    .fillMaxSize()
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf("1", "2", "3").forEachIndexed { idx, label ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (idx == activeTab) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
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

private fun Color.luminanceCompat(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
