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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import moe.rukamori.archivetune.ui.component.LocalLiquidGlassBackdrop
import moe.rukamori.archivetune.ui.component.LocalNavigationBarBackdrop
import moe.rukamori.archivetune.ui.component.NavigationBarBackdrop
import moe.rukamori.archivetune.ui.component.glassSource
import moe.rukamori.archivetune.ui.component.rememberThrottledBackdrop
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.resolveLyricsCharacterStyle
import moe.rukamori.archivetune.ui.motion.LocalMotionConfig
import moe.rukamori.archivetune.ui.player.rememberBottomBarArtworkPalette
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
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.MotionConfig
import moe.rukamori.archivetune.ui.motion.applyMenuMotion
import moe.rukamori.archivetune.ui.motion.effectAt
import moe.rukamori.archivetune.ui.motion.rememberMotionBlurEffects
import moe.rukamori.archivetune.ui.motion.rememberSystemAnimatorScale
import moe.rukamori.archivetune.ui.motion.screenEnter
import moe.rukamori.archivetune.ui.motion.screenExit
import moe.rukamori.archivetune.ui.motion.screenMotionBlur
import moe.rukamori.archivetune.ui.player.BottomBarArtworkPalette
import moe.rukamori.archivetune.ui.player.BottomBarBackground
import moe.rukamori.archivetune.ui.player.supportsGlassGlow
import moe.rukamori.archivetune.ui.player.usesArtworkPalette
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.ui.player.StyledPlaybackSlider
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneThemeColor
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
    val player = LocalPlayerConnection.current
    val currentTrack = player?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val queue = player?.queueWindows?.collectAsStateWithLifecycle()?.value.orEmpty()
    val trackTemplates = StudioDemoData.DemoTracks
    val tracks = remember(currentTrack, queue) {
        val currentIndex = queue.indexOfFirst { it.mediaItem.mediaId == currentTrack?.id }
        val upcoming = if (currentIndex >= 0) queue.drop(currentIndex + 1) else queue
        val available = (listOfNotNull(currentTrack) + upcoming.take(8).mapNotNull { it.mediaItem.metadata })
            .distinctBy { it.id }
        available.map(::studioTrackFromMedia).ifEmpty { trackTemplates }
    }
    var trackIndex by remember(currentTrack?.id) { mutableIntStateOf(0) }
    var replaySeed by remember { mutableIntStateOf(0) }
    val playbackPlaying = player?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false
    var playingOverride by remember(currentTrack?.id) { mutableStateOf<Boolean?>(null) }
    val isPlaying = playingOverride ?: playbackPlaying
    val track = tracks[trackIndex % tracks.size]
    val artwork = rememberBottomBarArtworkPalette(track.media)
    val haptic = rememberArchiveTuneHapticTrigger()
    val motion = snapshot.motion.toMotionConfig(rememberSystemAnimatorScale())

    fun switchTrack(direction: Int) {
        trackIndex = Math.floorMod(trackIndex + direction, tracks.size)
    }

    val replayScale = remember { Animatable(1f) }
    LaunchedEffect(replaySeed) {
        if (replaySeed > 0 && motion.pressFeedbackEnabled) {
            replayScale.snapTo(0.95f)
            replayScale.animateTo(1f, motion.pressSpring())
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
    val customFontUri by rememberPreference(CustomFontUriKey, "")

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
            CompositionLocalProvider(
                LocalMotionConfig provides motion,
            ) {
                ArchiveTuneTheme(
                    darkTheme = bgIsDark,
                    pureBlack = snapshot.color.pureBlack,
                    themeColor = effectiveThemeColor,
                    seedPalette = effectiveSeedPalette,
                    fontPreference = snapshot.color.fontPreference,
                    customFontUri = customFontUri,
                    disableAnimations = !motion.animationsEnabled,
                ) {
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
                                    val direction = if (targetState >= initialState) 1 else -1
                                    val duration = motion.tabTransitionDurationMs()
                                    motion.screenEnter(direction, duration) togetherWith motion.screenExit(direction, duration)
                                },
                                label = "studioPreviewStage",
                            ) { activeStage ->
                                Box(modifier = Modifier.fillMaxSize().then(screenMotionBlur(motion))) {
                                    when (StudioPreviewStage.entries[activeStage]) {
                                        StudioPreviewStage.HOME ->
                                            StudioHomeAndNavStage(
                                                snapshot = snapshot,
                                                frameOnSurface = frameOnSurface,
                                                frameOnSurfaceVariant = frameOnSurfaceVariant,
                                                track = track,
                                                upcomingTracks = tracks.filter { it.media != null && it.id != track.id },
                                                artwork = artwork,
                                                isPlaying = isPlaying,
                                                onTogglePlay = {
                                                    haptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                                                    playingOverride = !isPlaying
                                                },
                                                onNextTrack = {
                                                    haptic(ArchiveTuneHapticKind.SELECTION)
                                                    switchTrack(1)
                                                },
                                                onPreviousTrack = { switchTrack(-1) },
                                        onOpenPlayer = { stage = StudioPreviewStage.PLAYER },
                                            )

                                        StudioPreviewStage.PLAYER ->
                                            StudioPlayerStage(
                                                snapshot = snapshot,
                                                track = track,
                                                coverMode = coverMode,
                                                artwork = artwork,
                                                isPlaying = isPlaying,
                                                frameOnSurface = frameOnSurface,
                                                frameOnSurfaceVariant = frameOnSurfaceVariant,
                                                onTogglePlay = {
                                                    haptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                                                    playingOverride = !isPlaying
                                                },
                                                onPreviousTrack = { switchTrack(-1) },
                                                onNextTrack = {
                                                    haptic(ArchiveTuneHapticKind.SELECTION)
                                                    switchTrack(1)
                                                },
                                            )

                                        StudioPreviewStage.LYRICS ->
                                            StudioLyricsStage(
                                                snapshot = snapshot,
                                                track = track,
                                                artwork = artwork,
                                                isPlaying = isPlaying,
                                                frameOnSurface = frameOnSurface,
                                                frameOnSurfaceVariant = frameOnSurfaceVariant,
                                                onTogglePlay = {
                                                    haptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                                                    playingOverride = !isPlaying
                                                },
                                            )
                                    }
                                }
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
                    enabled = tracks.size > 1,
                    onClick = {
                        haptic(ArchiveTuneHapticKind.SELECTION)
                        switchTrack(1)
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        StudioCoverPreviewMode.CURRENT to R.string.studio_preview_cover_current,
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
                        )
                    }
                }
            }
        }
    }
}

private fun studioTrackFromMedia(media: MediaMetadata): StudioDemoTrack =
    StudioDemoData.DemoTracks.first().copy(
        id = media.id,
        artist = media.artists.joinToString(", ") { it.name },
        media = media,
    )

@Composable
private fun studioTrackTitle(track: StudioDemoTrack): String =
    track.media?.title ?: stringResource(track.titleRes)

@Composable
private fun StudioTrackArtwork(track: StudioDemoTrack, modifier: Modifier) {
    val url = track.media?.thumbnailUrl
    when {
        !url.isNullOrBlank() -> AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        track.media == null -> Box(modifier.background(Brush.linearGradient(track.darkColors)))
        else -> Box(
            modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.music_note), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StudioHomeAndNavStage(
    snapshot: AppearanceProfileSnapshot,
    frameOnSurface: Color,
    frameOnSurfaceVariant: Color,
    track: StudioDemoTrack,
    upcomingTracks: List<StudioDemoTrack>,
    artwork: BottomBarArtworkPalette?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
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

    val frameColor = MaterialTheme.colorScheme.background
    val navBackdrop = rememberThrottledBackdrop(frameColor)
    val frostedBackdrop = remember(navBackdrop) { NavigationBarBackdrop(navBackdrop.graphicsLayer) }
    val navProfile = if (snapshot.color.liquidGlassEnabled) snapshot.navigation else snapshot.navigation.copy(
        material = if (navBarMaterialSupportsGlassGlow(snapshot.navigation.material)) NavBarMaterial.STANDARD else snapshot.navigation.material,
        miniPlayerBackground = if (snapshot.navigation.miniPlayerBackground.supportsGlassGlow) {
            MiniPlayerBackgroundStyle.THEME
        } else {
            snapshot.navigation.miniPlayerBackground
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = feedState,
            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = if (navCollapsed) 66.dp else 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().glassSource(navBackdrop).onGloballyPositioned {
                frostedBackdrop.contentOffsetInRoot = it.positionInRoot()
            },
        ) {
            item(key = "home_mode_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
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
                        if (track.media != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                            ) {
                                (listOf(track) + upcomingTracks.take(2)).forEach { item ->
                                    StudioTrackArtwork(item, Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp)))
                                }
                            }
                        } else Row(
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
                                StudioTrackArtwork(track, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = studioTrackTitle(track),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = frameOnSurface,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = track.artist,
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

            if (track.media != null) {
                val feedTracks = if (snapshot.home.homeFeedStyle == HomeFeedStyle.NOW) upcomingTracks else listOf(track) + upcomingTracks
                items(feedTracks, key = { it.id }) { item ->
                    val size = if (snapshot.home.homeFeedStyle == HomeFeedStyle.COMPACT) 28.dp else 36.dp
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StudioTrackArtwork(item, Modifier.size(size).clip(RoundedCornerShape(8.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(studioTrackTitle(item), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, style = MaterialTheme.typography.labelSmall, color = frameOnSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            } else {
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
        }

        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides navBackdrop,
            LocalNavigationBarBackdrop provides frostedBackdrop,
        ) {
            UnifiedNavAndMiniPlayerBarOverlay(
                navigation = navProfile,
                playerStyle = snapshot.player.designStyle,
                reducedMotion = !LocalMotionConfig.current.animationsEnabled,
                collapsed = navCollapsed,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                track = track,
                artwork = artwork,
                isPlaying = isPlaying,
                onTogglePlay = onTogglePlay,
                onNextTrack = onNextTrack,
                onPreviousTrack = onPreviousTrack,
                onMiniPlayerClick = onOpenPlayer,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
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
    val currentTrack = LocalPlayerConnection.current?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val track = remember(currentTrack) { currentTrack?.let(::studioTrackFromMedia) ?: StudioDemoData.DemoTracks.first() }
    val artwork = rememberBottomBarArtworkPalette(currentTrack)
    val frameColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val backdrop = rememberThrottledBackdrop(frameColor)
    val frostedBackdrop = remember(backdrop) { NavigationBarBackdrop(backdrop.graphicsLayer) }
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

        Box(
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
                contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = if (collapsed) 64.dp else 106.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize().glassSource(backdrop).onGloballyPositioned {
                    frostedBackdrop.contentOffsetInRoot = it.positionInRoot()
                },
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

            CompositionLocalProvider(
                LocalLiquidGlassBackdrop provides backdrop,
                LocalNavigationBarBackdrop provides frostedBackdrop,
            ) {
                UnifiedNavAndMiniPlayerBarOverlay(
                    navigation = navigation,
                    playerStyle = playerStyle,
                    reducedMotion = reducedMotion,
                    collapsed = collapsed,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    track = track,
                    artwork = artwork,
                    isPlaying = isPlaying,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onNextTrack = {},
                    onMiniPlayerClick = { scrollDemoCollapsed = !scrollDemoCollapsed },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
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
    artwork: BottomBarArtworkPalette? = null,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPreviousTrack: () -> Unit = {},
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

    val navStyle = navigation.material.backgroundStyle
    val isGlassMaterial = navStyle.supportsGlassGlow
    val hasArtworkBackground = navStyle.usesArtworkPalette
    val navAlpha = (navigation.opacity * (1f - navigation.transparency)).coerceIn(0.05f, 1f)
    val palette = artwork ?: BottomBarArtworkPalette.from(track.darkColors.takeIf { track.media == null }.orEmpty())
    val navGlow = if (isGlassMaterial && navigation.glassGlow) navigation.glassGlowIntensity else 0f

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

        val miniStyle = navigation.miniPlayerBackground
        val miniUsesArtwork = miniStyle.usesArtworkPalette
        val glassInk = if (LocalLiquidGlassBackdrop.current != null) {
            moe.rukamori.archivetune.ui.component.liquidGlassContentColor()
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val miniTitleColor = when {
            miniUsesArtwork -> Color.White
            miniStyle.supportsGlassGlow -> glassInk
            else -> MaterialTheme.colorScheme.onSurface
        }
        val miniSubtitleColor = if (miniUsesArtwork || miniStyle.supportsGlassGlow) {
            miniTitleColor.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        val miniGlow = if (miniStyle.supportsGlassGlow && navigation.miniPlayerGlassGlow) {
            navigation.miniPlayerGlassGlowIntensity
        } else {
            0f
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
                    style = navStyle,
                    palette = palette,
                    glowStrength = navGlow,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 10.dp)
                            .alpha(compactFraction.coerceIn(0f, 1f)),
                )
                StudioCompactCircleButton(
                    iconRes = R.drawable.search,
                    style = navStyle,
                    palette = palette,
                    glowStrength = navGlow,
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
                shadowElevation = if (navigation.miniPlayerBackground.supportsGlassGlow) 2.dp else 4.dp,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = miniHorizontalPadding)
                        .fillMaxWidth()
                        .height(miniHeight)
                        .clip(miniShape)
            ) {
                BottomBarBackground(style = miniStyle, shape = miniShape, palette = palette, glowStrength = miniGlow, modifier = Modifier.fillMaxSize())
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
                        StudioTrackArtwork(track, Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = studioTrackTitle(track),
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
                        if (compactFraction < 0.7f) {
                            IconButton(onClick = onPreviousTrack, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    painter = transportIcons.previous,
                                    contentDescription = stringResource(R.string.player_mini_previous_action),
                                    tint = miniTitleColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
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
                            color = if (miniUsesArtwork) Color.White else MaterialTheme.colorScheme.primary,
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
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = navAlpha)),
            ) {
                if (navigation.material != NavBarMaterial.STANDARD) {
                    BottomBarBackground(
                        style = navStyle,
                        shape = barShape,
                        palette = palette,
                        glowStrength = navGlow,
                        modifier = Modifier.matchParentSize().graphicsLayer {
                            alpha = if (navStyle.usesArtworkPalette || navStyle == MiniPlayerBackgroundStyle.TINTED) navAlpha else 1f
                        },
                    )
                }
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
                                                if (hasArtworkBackground || isGlassMaterial) Color.White.copy(alpha = 0.20f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                                    contentDescription = null,
                                    tint = when {
                                        hasArtworkBackground -> Color.White.copy(alpha = if (selected) 1f else 0.76f)
                                        isGlassMaterial -> glassInk.copy(alpha = if (selected) 1f else 0.76f)
                                        selected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
    style: MiniPlayerBackgroundStyle,
    palette: BottomBarArtworkPalette?,
    glowStrength: Float,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(34.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        BottomBarBackground(style = style, shape = CircleShape, palette = palette, glowStrength = glowStrength, modifier = Modifier.matchParentSize())
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (style.usesArtworkPalette) Color.White else iconTint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun StudioPlayerStage(
    snapshot: AppearanceProfileSnapshot,
    track: StudioDemoTrack,
    coverMode: StudioCoverPreviewMode,
    artwork: BottomBarArtworkPalette?,
    isPlaying: Boolean,
    frameOnSurface: Color,
    frameOnSurfaceVariant: Color,
    onTogglePlay: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0.38f) }
    val style = snapshot.player.designStyle
    val transportIcons = playerTransportIcons(style)
    val supportsCustomBg = playerStyleSupportsCustomBackground(style)
    val showVolumeBar = playerStyleSupportsVolumeBar(style) && snapshot.player.showVolumeBar
    val artworkColors =
        when (coverMode) {
            StudioCoverPreviewMode.CURRENT -> artwork?.let { listOf(it.first, it.second, it.third) }
                ?: track.darkColors.takeIf { track.media == null }.orEmpty()
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
    val coverBaseSize = when {
        style == PlayerDesignStyle.APPLE_MUSIC -> 170.dp
        showVolumeBar -> 98.dp
        else -> 132.dp
    }
    val motion = LocalMotionConfig.current
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
                            if (!motion.artworkMotionEnabled) {
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                (slideInHorizontally(motion.trackSwapSpec()) { it / 4 } + fadeIn(motion.trackSwapSpec())) togetherWith
                                    (slideOutHorizontally(motion.trackSwapSpec()) { -it / 4 } + fadeOut(motion.trackSwapSpec()))
                            }
                        },
                        label = "studioPlayerTrackSwap",
                    ) { (activeTrack, mode) ->
                        val shape = RoundedCornerShape(snapshot.player.thumbnailCornerRadius.coerceAtLeast(0f).dp)
                        val coverModifier =
                            when {
                                style == PlayerDesignStyle.SIMPMUSIC ->
                                    Modifier.width(coverBaseSize + 36.dp).height(coverBaseSize - 6.dp).clip(shape)
                                style == PlayerDesignStyle.APPLE_MUSIC || snapshot.player.cropToSquare ->
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
                            StudioCoverPreviewMode.CURRENT -> StudioTrackArtwork(activeTrack, framedModifier)
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
                                        text = activeTrack.media?.album?.title ?: stringResource(activeTrack.albumRes),
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
                                        text = activeTrack.media?.album?.title ?: stringResource(activeTrack.albumRes),
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
                    text = studioTrackTitle(track),
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
                    IconButton(onClick = onPreviousTrack, modifier = Modifier.size(34.dp)) {
                        Icon(
                            painter = transportIcons.previous,
                            contentDescription = null,
                            tint = onPlayerContent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    if (style == PlayerDesignStyle.APPLE_MUSIC) {
                        IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter = if (isPlaying) transportIcons.pause else transportIcons.play,
                                contentDescription = stringResource(if (isPlaying) R.string.widget_pause else R.string.play),
                                tint = onPlayerContent,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    } else {
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
    artwork: BottomBarArtworkPalette?,
    isPlaying: Boolean,
    frameOnSurface: Color,
    frameOnSurfaceVariant: Color,
    onTogglePlay: () -> Unit,
) {
    val trackId = track.media?.id
    val database = LocalDatabase.current
    val lyricsFlow = remember(database, trackId) { trackId?.let(database::lyrics) }
    val savedLyrics = lyricsFlow?.collectAsStateWithLifecycle(initialValue = null)?.value?.lyrics
    val parsedLines = remember(savedLyrics) {
        savedLyrics?.takeUnless { it == LyricsEntity.LYRICS_NOT_FOUND }?.let(LyricsUtils::parseLyrics)
            .orEmpty().filter { it.text.isNotBlank() && !it.isInstrumental }
    }
    val sampleLines = if (trackId == null) {
        StudioDemoData.DemoLyricsLines.map { line ->
            stringResource(line.primaryTextRes) to line.translationRes?.let { stringResource(it) }
        }
    } else {
        remember(parsedLines) { parsedLines.map { it.text to it.providerTranslationText } }
    }
    val motion = LocalMotionConfig.current
    val reducedMotion = !motion.animationsEnabled
    val characterStyle = resolveLyricsCharacterStyle(snapshot.lyrics.displayCharacter, motion.animationsEnabled)
    var activeLine by remember(trackId, sampleLines.size) { mutableIntStateOf(if (sampleLines.size > 1) 1 else 0) }

    LaunchedEffect(isPlaying, sampleLines.size, trackId) {
        if (!isPlaying || sampleLines.size < 2) return@LaunchedEffect
        while (true) {
            delay(2_200L)
            activeLine = (activeLine + 1) % sampleLines.size
        }
    }

    val sampleStart = (activeLine - 2).coerceIn(0, (sampleLines.size - 5).coerceAtLeast(0))
    val visibleLines = sampleLines.drop(sampleStart).take(5)
    val coverColors = artwork?.let { listOf(it.first, it.third) } ?: listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surfaceContainer,
    )

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
                        coverColors.first().copy(alpha = 0.90f),
                        coverColors.last().copy(alpha = 0.94f),
                    ),
                )
            LyricsBackgroundStyle.MOVING_BLUR ->
                Brush.radialGradient(
                    colors =
                        listOf(
                            coverColors.first().copy(alpha = 0.82f),
                            coverColors.last().copy(alpha = 0.72f),
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
                    text = "${stringResource(lyricsModeLabelRes(snapshot.lyrics.mode))} · $characterLabel",
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
            if (visibleLines.isEmpty()) {
                Text(
                    text = stringResource(R.string.studio_preview_no_cached_lyrics),
                    color = inactiveTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            visibleLines.forEachIndexed { offset, line ->
                val idx = sampleStart + offset
                val isCurrent = idx == activeLine
                val inactiveAlpha =
                    when (kotlin.math.abs(idx - activeLine)) {
                        1 -> characterStyle.nearInactiveAlpha
                        2 -> characterStyle.midInactiveAlpha
                        else -> characterStyle.farInactiveAlpha
                    }
                val blurRadius =
                    if (!isCurrent && snapshot.lyrics.lineBlur && !snapshot.color.disableBlur) {
                        (2f * characterStyle.blurMultiplier).dp
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
                        text = line.first,
                        fontSize = if (isCurrent) previewFontSp * characterStyle.activeScale else previewFontSp * characterStyle.inactiveScale,
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
                    if (!line.second.isNullOrBlank()) {
                        Text(
                            text = line.second.orEmpty(),
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
    val demoConfig = motion.toMotionConfig(rememberSystemAnimatorScale())
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
                StudioScreenAndMenuMotionDemo(step = step, config = demoConfig)
            }
        }
    }
}

@Composable
private fun StudioScreenAndMenuMotionDemo(
    step: Int,
    config: MotionConfig,
) {
    val menuVisible = step % 2 == 1
    val menuProgress by animateFloatAsState(
        targetValue = if (menuVisible) 1f else 0f,
        animationSpec = if (menuVisible) config.sheetSpring() else config.microTween(200),
        label = "studioMotionDemoMenu",
    )
    val menuEffects = rememberMotionBlurEffects(config.motionBlurEnabled, vertical = true)
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)),
    ) {
        AnimatedContent(
            targetState = step % 2,
            transitionSpec = {
                config.screenEnter(1, config.tabTransitionDurationMs()) togetherWith
                    config.screenExit(1, config.tabTransitionDurationMs())
            },
            label = "studioMotionDemoScreen",
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize().then(screenMotionBlur(config)).background(
                    if (page == 0) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
                ).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(stringResource(R.string.studio_motion_preview_screen), style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).width(106.dp).height(38.dp).graphicsLayer {
                applyMenuMotion(menuProgress, config.menuTransitionStyle)
                renderEffect = menuEffects.effectAt(1f - menuProgress)
            }.background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(topStart = 10.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.studio_motion_preview_menu), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Color.luminanceCompat(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
