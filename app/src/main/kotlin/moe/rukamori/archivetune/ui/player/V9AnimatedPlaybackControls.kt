/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.LocalMotionConfig
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private enum class V9PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun V9AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = MaterialTheme.colorScheme.secondaryContainer,
    colorPlayPause: Color = MaterialTheme.colorScheme.primary,
    tintPlayPauseIcon: Color = MaterialTheme.colorScheme.onPrimary,
    tintOtherIcons: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    val motionConfig = LocalMotionConfig.current
    var lastClicked by remember { mutableStateOf<V9PlaybackButtonType?>(null) }
    var clickTrigger by remember { mutableStateOf(0) }
    val triggerHaptic = rememberArchiveTuneHapticTrigger()

    val motionScheme = remember { MotionScheme.standard() }
    val defaultSpatialDpSpec = remember { motionScheme.defaultSpatialSpec<Dp>() }

    LaunchedEffect(lastClicked, clickTrigger, motionConfig.animationsEnabled) {
        if (lastClicked != null) {
            if (!motionConfig.animationsEnabled) {
                lastClicked = null
            } else {
                delay(releaseDelay)
                lastClicked = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: V9PlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            val animatedPrevWeight by animateFloatAsState(
                targetValue = weightFor(V9PlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight"
            )
            val prevWeight = if (motionConfig.animationsEnabled) animatedPrevWeight else baseWeight
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorPreviousButton)
                    .clickable {
                        triggerHaptic(ArchiveTuneHapticKind.SKIP)
                        if (motionConfig.animationsEnabled) {
                            lastClicked = V9PlaybackButtonType.PREVIOUS
                            clickTrigger++
                        }
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.widget_previous),
                    tint = tintPreviousIcon,
                    modifier = Modifier.size(iconSize)
                )
            }

            val animatedPlayWeight by animateFloatAsState(
                targetValue = weightFor(V9PlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight"
            )
            val playWeight = if (motionConfig.animationsEnabled) animatedPlayWeight else baseWeight
            val animatedPlayCorner by animateDpAsState(
                targetValue = if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = defaultSpatialDpSpec,
                label = "playCorner"
            )
            val playCorner =
                if (motionConfig.animationsEnabled) animatedPlayCorner
                else if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(playCorner)
                    }
                    .background(colorPlayPause)
                    .clickable {
                        triggerHaptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                        if (motionConfig.animationsEnabled) {
                            lastClicked = V9PlaybackButtonType.PLAY_PAUSE
                            clickTrigger++
                        }
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                MorphingPlayPauseIcon(
                    isPlaying = isPlaying,
                    tint = tintPlayPauseIcon,
                    size = playPauseIconSize,
                )
            }

            val animatedNextWeight by animateFloatAsState(
                targetValue = weightFor(V9PlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight"
            )
            val nextWeight = if (motionConfig.animationsEnabled) animatedNextWeight else baseWeight
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorNextButton)
                    .clickable {
                        triggerHaptic(ArchiveTuneHapticKind.SKIP)
                        if (motionConfig.animationsEnabled) {
                            lastClicked = V9PlaybackButtonType.NEXT
                            clickTrigger++
                        }
                        onNext()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.next),
                    tint = tintNextIcon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
