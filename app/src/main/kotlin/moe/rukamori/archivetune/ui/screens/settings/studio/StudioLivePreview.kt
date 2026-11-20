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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import moe.rukamori.archivetune.constants.FloatingNavigationBarMaxWidth
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.MINI_PLAYER_BAR_HEIGHT_DP
import moe.rukamori.archivetune.constants.MiniPlayerCompactHeight
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.ui.motion.rememberDecorativeMotionActive
import moe.rukamori.archivetune.ui.player.StyledPlaybackSlider
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
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
    val bgIsDark =
        when (snapshot.color.darkMode) {
            DarkMode.ON -> true
            DarkMode.OFF -> false
            DarkMode.AUTO -> MaterialTheme.colorScheme.surface.luminanceCompat() < 0.45f
        }
    val frameSurface =
        when {
            snapshot.color.pureBlack && bgIsDark -> Color.Black
            bgIsDark -> Color(0xFF14171D)
            else -> Color(0xFFF6F7FA)
        }
    val frameOnSurface = if (bgIsDark) Color(0xFFF2F4F8) else Color(0xFF151821)
    val frameOnSurfaceVariant = if (bgIsDark) Color(0xFFAEB5C2) else Color(0xFF565E6E)

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

            // Scaled live app frame
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NavMiniPlayerStarterPresets.forEach { preset ->
                    FilterChip(
                        selected = preset.navigation.material == navigation.material &&
                            preset.navigation.miniPlayerStyle == navigation.miniPlayerStyle &&
                            preset.navigation.hideLabels == navigation.hideLabels,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                text = stringResource(preset.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f),
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
            NavBarMaterial.LIQUID_GLASS,
            NavBarMaterial.SUKISU_GLASS,
            -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)
            NavBarMaterial.BITCHORD ->
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        }

    val miniHeight =
        (
            if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                (MINI_PLAYER_BAR_HEIGHT_DP * previewScale).dp
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
        // Mini-Player positioned honestly relative to navigation bar
        val miniBottomPadding =
            if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                if (collapsed) 0.dp else 3.dp
            } else {
                6.dp
            }
        val miniHorizontalPadding = if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) 0.dp else 12.dp
        val miniShape =
            if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            } else {
                RoundedCornerShape(18.dp)
            }

        Surface(
            onClick = onMiniPlayerClick,
            shape = miniShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier =
                Modifier
                    .padding(horizontal = miniHorizontalPadding)
                    .padding(bottom = miniBottomPadding)
                    .fillMaxWidth()
                    .height(miniHeight),
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
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Brush.linearGradient(track.darkColors)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(track.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(30.dp)) {
                        Icon(
                            painter =
                                painterResource(
                                    if (isPlaying) R.drawable.pause else R.drawable.play,
                                ),
                            contentDescription =
                                stringResource(
                                    if (isPlaying) R.string.widget_pause else R.string.play,
                                ),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(onClick = onNextTrack, modifier = Modifier.size(30.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.apple_skip_next),
                            contentDescription = stringResource(R.string.next),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                    LinearProgressIndicator(
                        progress = { 0.42f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }
        }

        // Real NavBar geometry using real height, widthFraction, cornerRadius & fontScale honesty
        if (visibleBarHeight > 1.dp) {
            Box(
                modifier =
                    Modifier
                        .padding(bottom = visibleBarBottomPad)
                        .widthIn(max = maxWidthCap)
                        .fillMaxWidth(effectiveWidthFraction)
                        .height(visibleBarHeight)
                        .graphicsLayer { alpha = (1f - compactFraction * 1.1f).coerceIn(0f, 1f) }
                        .clip(RoundedCornerShape(cornerDp))
                        .background(barColor)
                        .border(
                            width = if (navigation.material in setOf(NavBarMaterial.LIQUID_GLASS, NavBarMaterial.SUKISU_GLASS)) 1.dp else 0.dp,
                            color =
                                if (navigation.glassGlow) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = (0.35f * navigation.glassGlowIntensity).coerceIn(0.1f, 0.8f))
                                } else {
                                    Color.White.copy(alpha = 0.22f)
                                },
                            shape = RoundedCornerShape(cornerDp),
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
                                        .clip(CircleShape)
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
                                Spacer(modifier = Modifier.height((navigation.labelSpacing * previewScale).coerceAtMost(6f).dp))
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
    val artworkColors =
        when (coverMode) {
            StudioCoverPreviewMode.LIGHT -> track.lightColors
            StudioCoverPreviewMode.DARK -> track.darkColors
            StudioCoverPreviewMode.NONE -> emptyList()
        }
    val playerBgBrush =
        when {
            snapshot.player.backgroundStyle == PlayerBackgroundStyle.DEFAULT ->
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            artworkColors.isEmpty() ->
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            else -> Brush.verticalGradient(artworkColors.map { it.copy(alpha = 0.72f) })
        }
    val onPlayerContent =
        when {
            snapshot.player.backgroundStyle == PlayerBackgroundStyle.DEFAULT || artworkColors.isEmpty() ->
                frameOnSurface
            coverMode == StudioCoverPreviewMode.LIGHT -> Color(0xFF141820)
            else -> Color.White
        }
    val onPlayerSub = onPlayerContent.copy(alpha = 0.75f)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(playerBgBrush)
                .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = snapshot.player.designStyle.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = onPlayerSub,
                )
                Text(
                    text = stringResource(track.sourceLabelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = onPlayerSub,
                )
            }

            if (!snapshot.player.hideThumbnail) {
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
                    val shape = RoundedCornerShape(snapshot.player.thumbnailCornerRadius.dp)
                    val coverModifier =
                        if (snapshot.player.cropToSquare) {
                            Modifier.size(124.dp).aspectRatio(1f).clip(shape)
                        } else {
                            Modifier.width(138.dp).height(112.dp).clip(shape)
                        }
                    when (mode) {
                        StudioCoverPreviewMode.NONE ->
                            Box(
                                modifier =
                                    coverModifier.background(
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
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        StudioCoverPreviewMode.LIGHT ->
                            Box(
                                modifier = coverModifier.background(Brush.linearGradient(activeTrack.lightColors)),
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
                                modifier = coverModifier.background(Brush.linearGradient(activeTrack.darkColors)),
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
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    activeColor = MaterialTheme.colorScheme.primary,
                    isPlaying = isPlaying && !snapshot.motion.disableAnimations,
                    modifier = Modifier.fillMaxWidth().height(26.dp),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNextTrack) {
                        Icon(
                            painter = painterResource(R.drawable.apple_skip_previous),
                            contentDescription = null,
                            tint = onPlayerContent,
                        )
                    }
                    Surface(
                        onClick = onTogglePlay,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp).size(22.dp),
                        )
                    }
                    IconButton(onClick = onNextTrack) {
                        Icon(
                            painter = painterResource(R.drawable.apple_skip_next),
                            contentDescription = null,
                            tint = onPlayerContent,
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

    val previewFontSp = (snapshot.lyrics.textSize * 0.56f).coerceIn(11f, 20f).sp

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snapshot.lyrics.displayCharacter.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
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
        ) {
            StudioDemoData.DemoLyricsLines.forEachIndexed { idx, line ->
                val isCurrent = idx == activeLine
                val inactiveAlpha =
                    when (snapshot.lyrics.displayCharacter) {
                        moe.rukamori.archivetune.constants.LyricsDisplayCharacter.READING -> 0.72f
                        moe.rukamori.archivetune.constants.LyricsDisplayCharacter.FOCUS -> 0.45f
                        moe.rukamori.archivetune.constants.LyricsDisplayCharacter.STAGE -> 0.34f
                    }
                val blurRadius =
                    if (!isCurrent && snapshot.lyrics.lineBlur && !snapshot.color.disableBlur && !reducedMotion) {
                        2.5.dp
                    } else {
                        0.dp
                    }
                val rowBg =
                    if (isCurrent && reducedMotion) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        Color.Transparent
                    }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(rowBg)
                            .clickable { activeLine = idx }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .alpha(if (isCurrent) 1f else inactiveAlpha)
                            .let { mod -> if (blurRadius > 0.dp) mod.blur(blurRadius) else mod },
                ) {
                    Text(
                        text = stringResource(line.primaryTextRes),
                        fontSize = if (isCurrent) previewFontSp else (previewFontSp.value * 0.88f).sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) frameOnSurface else frameOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (line.translationRes != null) {
                        Text(
                            text = stringResource(line.translationRes),
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
}

private fun Color.luminanceCompat(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
