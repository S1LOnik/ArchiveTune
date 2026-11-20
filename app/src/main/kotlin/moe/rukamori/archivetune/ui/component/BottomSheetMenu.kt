/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.BackAnimationStyle
import moe.rukamori.archivetune.ui.motion.LocalMotionConfig
import moe.rukamori.archivetune.ui.motion.applyMenuMotion
import moe.rukamori.archivetune.ui.motion.effectAt
import moe.rukamori.archivetune.ui.motion.rememberMotionBlurEffects
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.abs

val LocalMenuState = compositionLocalOf { MenuState() }

val LocalMenuDialogDismissal = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Presentation of the popup host surface.
 *
 * [FLOATING] — the historic floating panel: 16dp side margins, bottom inset,
 * 28dp corners on all four sides, 40% screen-height cap. Every menu that is
 * not the full-player overflow popup uses this.
 *
 * [IMMERSIVE] — the full-player overflow popup presentation: edge-to-edge
 * sheet anchored flush to the bottom of the window (content keeps
 * navigation-bars padding so controls stay above the gesture area), large
 * top-only corner radius, drag handle always rendered, taller height cap.
 */
enum class MenuPresentation {
    FLOATING,
    IMMERSIVE,
}

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    var presentation by mutableStateOf(MenuPresentation.FLOATING)
        private set
    internal var dialogContent by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    private var lastDismissUptimeMs: Long = Long.MIN_VALUE
    private var lastShowUptimeMs: Long = Long.MIN_VALUE

    fun show(content: @Composable ColumnScope.() -> Unit) {
        show(content, MenuPresentation.FLOATING)
    }

    /** Shows the content as an edge-to-edge bottom sheet (see [MenuPresentation.IMMERSIVE]). */
    fun showImmersive(content: @Composable ColumnScope.() -> Unit) {
        show(content, MenuPresentation.IMMERSIVE)
    }

    private fun show(
        content: @Composable ColumnScope.() -> Unit,
        presentation: MenuPresentation,
    ) {
        val now = android.os.SystemClock.uptimeMillis()
        if (presentation == MenuPresentation.FLOATING && isVisible && this.presentation == presentation && (now - lastShowUptimeMs) in 0..260L) {
            dismiss()
            return
        }
        if (presentation == MenuPresentation.FLOATING && !isVisible && lastDismissUptimeMs != Long.MIN_VALUE && (now - lastDismissUptimeMs) in 0..220L) {
            return
        }
        lastShowUptimeMs = now
        dialogContent = null
        isVisible = true
        this.presentation = presentation
        this.content = content
    }

    fun dismiss() {
        if (isVisible) {
            lastDismissUptimeMs = android.os.SystemClock.uptimeMillis()
        }
        isVisible = false
    }

    fun showDialog(content: @Composable () -> Unit) {
        isVisible = false
        lastDismissUptimeMs = Long.MIN_VALUE
        dialogContent = content
    }

    fun dismissDialog() {
        dialogContent = null
    }
}

@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = Color.Unspecified,
) {
    val focusManager = LocalFocusManager.current

    state.dialogContent?.invoke()

    var renderState by remember { mutableStateOf(false) }
    val enterProgress = remember { Animatable(0f) }

    val scrimInteractionSource = remember { MutableInteractionSource() }
    val popupInteractionSource = remember { MutableInteractionSource() }

    val motionConfig = LocalMotionConfig.current
    val animationsEnabled = !LocalAnimationsDisabled.current && motionConfig.animationsEnabled

    LaunchedEffect(state.isVisible, animationsEnabled, motionConfig) {
        if (state.isVisible) {
            renderState = true
            if (!animationsEnabled) {
                enterProgress.snapTo(1f)
            } else {
                enterProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = motionConfig.sheetSpring(),
                )
            }
        } else if (renderState) {
            try {
                if (animationsEnabled) {
                    enterProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = motionConfig.microTween(170),
                    )
                } else {
                    enterProgress.snapTo(0f)
                }
            } finally {
                if (!state.isVisible) {
                    if (state.dialogContent == null) focusManager.clearFocus()
                    renderState = false
                }
            }
        }
    }

    val menuPredictiveBack = rememberPredictiveSheetBackState()
    PredictiveStyleBackHandler(
        backState = menuPredictiveBack,
        enabled = renderState && state.isVisible && state.dialogContent == null,
        onBack = state::dismiss,
    )
    PredictiveStyleBackHandler(
        backState = menuPredictiveBack,
        enabled = state.dialogContent != null,
        onBack = state::dismissDialog,
    )

    if (!renderState) return

    val immersive = state.presentation == MenuPresentation.IMMERSIVE
    val menuShape = if (immersive) ImmersiveMenuShape else FloatingMenuShape

    val menuGlassBackdrop = LocalMenuGlassBackdrop.current
    val liquidGlassBackdrop = menuGlassBackdrop ?: LocalLiquidGlassBackdrop.current
    val glassTuning = LocalLiquidGlassTuning.current
    val glassModifier =
        remember(liquidGlassBackdrop, glassTuning, background, menuShape) {
            if (liquidGlassBackdrop != null && background.isUnspecified) {
                Modifier.drawBackdrop(
                    backdrop = liquidGlassBackdrop,
                    effects = {
                        colorControls(saturation = glassTuning.saturation)

                        blur((20f * glassTuning.blurFactor).dp.toPx())

                        lens(
                            refractionHeight = minOf(
                                (16f * glassTuning.refractionHeightFactor).dp.toPx(),
                                size.minDimension / 2f,
                            ),
                            refractionAmount = (40f * glassTuning.refractionAmountFactor).dp.toPx(),
                            depthEffect = glassTuning.depth3D,
                            chromaticAberration = glassTuning.chromaticAberration,
                        )
                    },
                    onDrawBackdrop = { drawBackdrop ->
                        drawBackdrop()
                    },
                    shape = { menuShape },
                )
            } else {
                null
            }
        }

    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val glassTint =
        if (immersive) {
            // Immersive full-player sheet: near-opaque charcoal-blue glass that still
            // lets the blurred player ambience breathe through (reference design).
            if (dark) {
                Color(0xD91E2126)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
            }
        } else if (dark) {
            Color(0x8C1C1C1E).copy(alpha = (0.55f * glassTuning.tintFactor).coerceIn(0f, 1f))
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = (0.42f * glassTuning.tintFactor).coerceIn(0f, 1f))
        }

    val fallbackColor =
        when {
            !background.isUnspecified -> background
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val fallbackBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val contentInk =
        if (dark) {
            Color.White
        } else {
            Color(0xFF1C1B1F)
        }

    val glassColorScheme =
        MaterialTheme.colorScheme.copy(
            onSurface = contentInk,
            onBackground = contentInk,
            onSurfaceVariant = contentInk.copy(alpha = 0.72f),
            surfaceContainerLow = Color.Transparent,
            surfaceContainer = Color.Transparent,
            surfaceContainerHigh =
                if (dark) {
                    Color.White.copy(alpha = 0.08f)
                } else {
                    Color.Black.copy(alpha = 0.05f)
                },
            surfaceContainerHighest =
                if (dark) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                },
            outlineVariant = contentInk.copy(alpha = 0.12f),
            error = Color(0xFFFF453A),
        )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var expanded by remember(immersive) { mutableStateOf(false) }
    var handleDrag by remember(immersive) { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isVisible) {
        if (state.isVisible) handleDrag = 0f
    }
    val dragReturn = remember { Animatable(0f) }
    val threshold = with(density) { 32.dp.toPx() }
    val flingThreshold = with(density) { 420.dp.toPx() }
    val dismissDistance = with(density) { 72.dp.toPx() }
    val dismissVelocity = with(density) { 900.dp.toPx() }
    val contentDismissGesture = remember(state, state.isVisible, immersive, dismissDistance, dismissVelocity, motionConfig, animationsEnabled) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!state.isVisible || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y <= 0f) {
                    if (consumed.y != 0f) handleDrag = 0f
                    return Offset.Zero
                }
                handleDrag = (handleDrag + available.y).coerceAtMost(dismissDistance * 2f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (handleDrag <= 0f || !state.isVisible) return Velocity.Zero
                val distance = if (immersive) dismissDistance else threshold
                if (handleDrag >= distance || (handleDrag >= threshold * 0.5f && available.y >= dismissVelocity)) {
                    state.dismiss()
                    return Velocity(0f, available.y.coerceAtLeast(0f))
                }
                dragReturn.snapTo(handleDrag)
                dragReturn.animateTo(0f, if (animationsEnabled) motionConfig.microTween(120) else snap()) {
                    handleDrag = value
                }
                return Velocity(0f, available.y.coerceAtLeast(0f))
            }
        }
    }
    val maxPopupHeight by animateDpAsState(
        targetValue = configuration.screenHeightDp.dp * when {
            immersive && expanded -> 1f
            immersive -> 0.72f
            else -> 0.44f
        },
        animationSpec = if (animationsEnabled) motionConfig.microTween(210) else snap(),
        label = "menuHeight",
    )
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(0.dp)
    val predictiveBackStyle = animationsEnabled && LocalBackAnimationStyle.current == BackAnimationStyle.PREDICTIVE
    val blurEffects = rememberMotionBlurEffects(enabled = motionConfig.motionBlurEnabled, vertical = true)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = if (predictiveBackStyle) menuPredictiveBack.progress.value.coerceIn(0f, 1f) else 0f
                        val backFade = (1f - 0.35f * p).coerceIn(0f, 1f)
                        alpha = enterProgress.value.coerceIn(0f, 1f) * backFade
                    }
                    .background(Color.Black.copy(alpha = 0.50f))
                    .then(
                        if (state.isVisible) {
                            Modifier.clickable(
                                interactionSource = scrimInteractionSource,
                                indication = null,
                            ) {
                                state.dismiss()
                            }
                        } else {
                            Modifier
                        },
                    ),
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (immersive) {
                            Modifier.statusBarsPadding()
                        } else {
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = bottomInset + 12.dp)
                        },
                    )
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height = maxPopupHeight.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
                        val placeable = measurable.measure(constraints.copy(maxHeight = height))
                        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                    }
                    .graphicsLayer {
                        val progress = enterProgress.value.coerceIn(0f, 1f)
                        val back = if (predictiveBackStyle) menuPredictiveBack.progress.value.coerceIn(0f, 1f) else 0f
                        applyMenuMotion(
                            progress = progress,
                            style = motionConfig.menuTransitionStyle,
                            backProgress = back,
                            dragOffset = handleDrag.coerceAtLeast(0f) * 0.55f,
                        )
                        renderEffect = blurEffects.effectAt(1f - progress)
                    }
                    .shadow(
                        elevation = (24f * glassTuning.shadowFactor).coerceAtLeast(0f).dp,
                        shape = menuShape,
                        clip = false,
                    )
                    .then(
                        if (glassModifier != null) {
                            glassModifier.background(glassTint)
                        } else {
                            Modifier

                                .background(fallbackColor, menuShape)
                                .border(1.dp, fallbackBorderColor, menuShape)
                        },
                    )
                    .clip(menuShape)
                    .nestedScroll(contentDismissGesture)
                    .then(
                        if (state.isVisible) {
                            Modifier.clickable(
                                interactionSource = popupInteractionSource,
                                indication = null,
                            ) {
                            }
                        } else {
                            Modifier
                        },
                    ),
        ) {
            val unglassedColorScheme = MaterialTheme.colorScheme

            val useGlassInk = glassModifier != null || !background.isUnspecified
            val menuContent: @Composable () -> Unit = {
                Column(
                    modifier =
                        if (immersive) {
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                ) {
                    state.content(this)
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (immersive) {
                    val closeDesc = stringResource(R.string.close_dialog)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .draggable(
                                state = rememberDraggableState { handleDrag = (handleDrag + it).coerceIn(-threshold * 3f, threshold * 3f) },
                                orientation = Orientation.Vertical,
                                enabled = state.isVisible,
                                onDragStarted = {
                                    dragReturn.stop()
                                    handleDrag = 0f
                                },
                                onDragStopped = { velocity ->
                                    val distance = handleDrag
                                    val committed =
                                        abs(distance) >= threshold ||
                                            (abs(velocity) >= flingThreshold && distance * velocity > 0f)
                                    if (committed && distance < 0f) expanded = true
                                    if (committed && distance > 0f) {
                                        if (expanded) {
                                            handleDrag = 0f
                                            expanded = false
                                        } else {
                                            state.dismiss()
                                            return@draggable
                                        }
                                    }
                                    dragReturn.snapTo(handleDrag)
                                    dragReturn.animateTo(0f, if (animationsEnabled) motionConfig.microTween(160) else snap()) {
                                        handleDrag = value
                                    }
                                },
                            )
                            .clickable(
                                onClickLabel = stringResource(if (expanded) R.string.player_menu_collapse else R.string.player_menu_expand),
                                onClick = { expanded = !expanded },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)),
                        )
                        androidx.compose.material3.IconButton(
                            onClick = state::dismiss,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 6.dp)
                                    .size(36.dp)
                                    .semantics { contentDescription = closeDesc },
                        ) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = closeDesc,
                                tint = contentInk.copy(alpha = 0.72f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else if (glassModifier == null) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp, end = 6.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 32.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    )
                                    .align(Alignment.Center),
                        )
                    }
                }

                CompositionLocalProvider(
                    LocalContentColor provides if (useGlassInk) contentInk else unglassedColorScheme.onSurface,

                    LocalGlassMenuContent provides (glassModifier != null),

                    LocalUnglassColorScheme provides unglassedColorScheme,

                    LocalMenuDialogDismissal provides ({ state.isVisible = false }),
                ) {
                    if (useGlassInk) {
                        MaterialTheme(colorScheme = glassColorScheme) {
                            menuContent()
                        }
                    } else {
                        menuContent()
                    }
                }
            }
        }
    }
}

private val FloatingMenuShape = RoundedCornerShape(28.dp)

private val ImmersiveMenuShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
