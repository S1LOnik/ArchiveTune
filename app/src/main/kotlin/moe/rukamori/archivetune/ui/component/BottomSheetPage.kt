/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.BackAnimationStyle
import moe.rukamori.archivetune.ui.motion.LocalMotionConfig
import moe.rukamori.archivetune.ui.utils.top
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

val LocalBottomSheetPageState = compositionLocalOf { BottomSheetPageState() }

@Stable
class BottomSheetPageState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    var contentOnScreen by mutableStateOf(false)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@Composable
fun BottomSheetPage(
    modifier: Modifier = Modifier,
    state: BottomSheetPageState,
    background: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val motionConfig = LocalMotionConfig.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(density) { 72.dp.toPx() }
    val predictiveBack = rememberPredictiveSheetBackState()
    val predictiveBackStyle = LocalBackAnimationStyle.current == BackAnimationStyle.PREDICTIVE
    val sheetActive = state.isVisible || state.contentOnScreen

    PredictiveStyleBackHandler(
        backState = predictiveBack,
        enabled = sheetActive,
        onBack = state::dismiss,
    )

    val fadeDuration = motionConfig.subScreenTransitionDurationMs().coerceAtMost(260)
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(animationSpec = tween(fadeDuration)),
        exit = fadeOut(animationSpec = tween(fadeDuration)),
    ) {
        Box(
            modifier =
                Modifier
                    .pointerInput(Unit) {
                        detectTapGestures {
                            state.dismiss()
                        }
                    }.graphicsLayer {
                        if (predictiveBackStyle) {
                            alpha = 1f - 0.35f * predictiveBack.progress.value
                        }
                    }.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .fillMaxSize(),
        )
    }

    AnimatedVisibility(
        visible = state.isVisible,
        enter =
            if (!motionConfig.animationsEnabled) {
                fadeIn(tween(0))
            } else {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(motionConfig.subScreenTransitionDurationMs()),
                )
            },
        exit =
            if (!motionConfig.animationsEnabled) {
                fadeOut(tween(0))
            } else {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(motionConfig.subScreenTransitionDurationMs()),
                )
            },
        modifier = modifier,
    ) {
        DisposableEffect(Unit) {
            state.contentOnScreen = true
            dragOffset = 0f
            onDispose { state.contentOnScreen = false }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(top = 100.dp)
                    .graphicsLayer {
                        val backP = if (predictiveBackStyle) predictiveBack.progress.value else 0f
                        val scale = 1f - 0.06f * backP
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.2f * backP
                        translationY = dragOffset.coerceAtLeast(0f) + with(density) { backP * 32.dp.toPx() }
                    }
                    .clip(ShapeDefaults.Large.top())
                    .background(background)
                    .pointerInput(dismissThresholdPx) {
                        detectVerticalDragGestures(
                            onDragCancel = { dragOffset = 0f },
                            onDragEnd = {
                                if (dragOffset > dismissThresholdPx) {
                                    state.dismiss()
                                }
                                dragOffset = 0f
                            },
                        ) { _, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                        }
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 8.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
                IconButton(
                    onClick = state::dismiss,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close_dialog),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .windowInsetsPadding(contentWindowInsets),
            ) {
                state.content(this)
            }
        }
    }

    LaunchedEffect(state.isVisible) {
        if (state.isVisible) {
            focusManager.clearFocus()
        }
    }
}
