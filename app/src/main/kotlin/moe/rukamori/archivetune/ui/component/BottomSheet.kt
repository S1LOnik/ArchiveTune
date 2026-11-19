/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import moe.rukamori.archivetune.ui.player.LocalRootOverlayActive
import androidx.compose.animation.core.snap
import moe.rukamori.archivetune.constants.BackAnimationStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.BottomSheetAnimationSpec
import moe.rukamori.archivetune.constants.BottomSheetSoftAnimationSpec
import moe.rukamori.archivetune.ui.motion.LocalMotionConfig
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.isSpecified

@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,

    backgroundColor: () -> Color,
    onDismiss: (() -> Unit)? = null,
    keepContentAlive: Boolean = false,
    morphMode: Boolean = false,
    backHandlerEnabled: Boolean = true,
    opaqueBackground: Boolean = false,
    collapsedClickEnabled: Boolean = true,
    onCollapsedContentClick: (() -> Unit)? = null,
    navbarHiddenOffset: (() -> Float)? = null,
    sharedLayer: (@Composable BoxScope.() -> Unit)? = null,
    predictiveBackState: PredictiveSheetBackState? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    SideEffect {
        state.onDismissCallback = onDismiss
    }
    val sheetShape =
        remember(state, morphMode) {
            PlayerSheetDynamicShape(
                baseCornerDp = if (morphMode) 28.dp else 18.dp,
                progressProvider = { state.progress.coerceIn(0f, 1f) },
            )
        }
    val predictiveBack = predictiveBackState ?: rememberPredictiveSheetBackState()
    val predictiveBackStyle = LocalBackAnimationStyle.current == BackAnimationStyle.PREDICTIVE
    Box(
        modifier =
            modifier
                .fillMaxSize()

                .graphicsLayer {
                    val y =
                        (state.expandedBound - state.value)
                            .roundToPx()
                            .coerceAtLeast(0)

                    val takeOver =
                        navbarHiddenOffset?.invoke()?.coerceAtLeast(0f) ?: 0f
                    translationY = y + takeOver * (1f - state.progress.coerceIn(0f, 1f))

                    if (predictiveBackStyle) {
                        val p = predictiveBack.progress.value
                        if (p > 0f) {
                            val scale = 1f - 0.08f * p
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.25f * p
                        }
                    }
                }.bottomSheetDraggable(state, onDismiss)
                .clip(sheetShape)
                .then(
                    if (opaqueBackground) {
                        Modifier.drawBehind {
                            if (state.progress > 0f) {
                                val color = backgroundColor()
                                if (color.isSpecified && color.alpha > 0f) {
                                    drawRect(color = color)
                                }
                            }
                        }
                    } else {
                        Modifier.drawBehind {
                            val color = backgroundColor()
                            if (color.isSpecified && color.alpha > 0f) {
                                drawRect(color = color)
                            }
                        }
                    },
                ),
    ) {
        if (state.isExpandedOrExpanding && backHandlerEnabled && !LocalRootOverlayActive.current) {
            PredictiveStyleBackHandler(
                backState = predictiveBack,
                enabled = true,
                onBack = state::collapseSoft,
            )
        }

        if (sharedLayer != null) {
            val sharedZIndex by remember(state) {
                derivedStateOf { if (state.progress > 0.5f) 3f else 2.5f }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(sharedZIndex),
                content = sharedLayer,
            )
        }

        val fullContentZIndex by remember(state) {
            derivedStateOf { if (state.progress > 0.5f) 2f else 1f }
        }
        if (keepContentAlive) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(fullContentZIndex)
                        .graphicsLayer {
                            val p = state.progress.coerceIn(0f, 1f)
                            if (morphMode) {
                                alpha = ((p - 0.5f) * 2).coerceIn(0f, 1f)
                            } else {
                                alpha = if (state.isCollapsed || p <= 0.01f) 0f else ((p - 0.14f) / 0.52f).coerceIn(0f, 1f)
                            }
                            if (p <= 0.01f) translationY = 10_000f
                        },
                content = content,
            )
        } else if (!state.isCollapsed) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(fullContentZIndex)
                        .graphicsLayer {
                            val p = state.progress.coerceIn(0f, 1f)
                            alpha =
                                if (morphMode) {
                                    ((p - 0.5f) * 2).coerceIn(0f, 1f)
                                } else {
                                    ((p - 0.14f) / 0.52f).coerceIn(0f, 1f)
                                }
                        },
                content = content,
            )
        }

        if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            val miniZIndex by remember(state) {
                derivedStateOf { if (state.progress > 0.5f) 1f else 2f }
            }
            Box(
                modifier =
                    Modifier
                        .zIndex(miniZIndex)
                        .graphicsLayer {
                            val p = state.progress.coerceIn(0f, 1f)
                            alpha =
                                if (morphMode) {
                                    (1f - 2f * p).coerceIn(0f, 1f)
                                } else {
                                    (1f - p * 4f).coerceIn(0f, 1f)
                                }
                        }.then(
                            if (collapsedClickEnabled) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCollapsedContentClick ?: state::expandSoft,
                                )
                            } else {
                                Modifier
                            },
                        ).fillMaxWidth()
                        .height(state.collapsedBound),
                content = collapsedContent,
            )
        }
    }
}

private class PlayerSheetDynamicShape(
    private val baseCornerDp: Dp = 28.dp,
    private val progressProvider: () -> Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: Density,
    ): Outline {
        val progress = progressProvider()
        val cornerPx = with(density) { androidx.compose.ui.unit.lerp(baseCornerDp, 0.dp, progress).toPx() }
        return Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
            ),
        )
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    private val animationsDisabled: Boolean,
    private val defaultAnimationSpec: AnimationSpec<Dp> = BottomSheetAnimationSpec,
    private val softAnimationSpec: AnimationSpec<Dp> = BottomSheetSoftAnimationSpec,
    private val density: Density,
    val collapsedBound: Dp,
    initialAnchor: Int = DISMISSED_ANCHOR,
) : DraggableState by draggableState {
    internal var onDismissCallback: (() -> Unit)? = null

    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    var targetAnchor by mutableIntStateOf(initialAnchor)
        private set

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value >= animatable.upperBound!! - 0.5.dp
    }

    val isExpandedOrExpanding: Boolean
        get() = targetAnchor == EXPANDED_ANCHOR

    val progress by derivedStateOf {
        val range = (animatable.upperBound!! - collapsedBound).value
        if (range <= 0f) {
            if (isExpanded) 1f else 0f
        } else {
            1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
        }
    }

    private fun updateAnchor(anchor: Int) {
        targetAnchor = anchor
        onAnchorChanged(anchor)
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        updateAnchor(COLLAPSED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(collapsedBound, animationSpec)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        updateAnchor(EXPANDED_ANCHOR)
        sheetScrollConnection.resetLatch()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.upperBound!!, animationSpec)
        }
    }

    fun collapse(animationSpec: AnimationSpec<Dp>, velocityPx: Float) {
        updateAnchor(COLLAPSED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(
                collapsedBound,
                animationSpec,
                initialVelocity = velocityPx.toAnimatableVelocity(),
            )
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>, velocityPx: Float) {
        updateAnchor(EXPANDED_ANCHOR)
        sheetScrollConnection.resetLatch()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(
                animatable.upperBound!!,
                animationSpec,
                initialVelocity = velocityPx.toAnimatableVelocity(),
            )
        }
    }

    private fun Float.toAnimatableVelocity(): Dp =
        with(density) { toDp() }

    private fun collapse(velocityPx: Float = 0f) {
        collapse(if (animationsDisabled) snap() else defaultAnimationSpec, velocityPx)
    }

    private fun expand(velocityPx: Float = 0f) {
        expand(if (animationsDisabled) snap() else defaultAnimationSpec, velocityPx)
    }

    fun collapseSoft() {
        collapse(if (animationsDisabled) snap() else softAnimationSpec)
    }

    fun expandSoft() {
        expand(if (animationsDisabled) snap() else softAnimationSpec)
    }

    fun dismiss() {
        updateAnchor(DISMISSED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.lowerBound!!, if (animationsDisabled) snap() else defaultAnimationSpec)
        }
    }

    fun snapTo(value: Dp) {
        updateAnchor(
            when (value) {
                expandedBound -> EXPANDED_ANCHOR
                collapsedBound -> COLLAPSED_ANCHOR
                dismissedBound -> DISMISSED_ANCHOR
                else -> COLLAPSED_ANCHOR
            },
        )
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value)
        }
    }

    fun performFling(
        velocity: Float,
        onDismiss: (() -> Unit)?,
    ) {
        val effectiveOnDismiss = onDismiss ?: onDismissCallback
        if (velocity > 250) {
            expand(velocity)
        } else if (velocity < -250) {
            if (value < collapsedBound && effectiveOnDismiss != null) {
                dismiss()
                effectiveOnDismiss.invoke()
            } else {
                collapse(velocity)
            }
        } else {
            val l0 = dismissedBound
            val l1 = dismissedBound + (collapsedBound - dismissedBound) / 2
            val l2 = collapsedBound + (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when {
                value <= l1 -> {
                    if (effectiveOnDismiss != null && collapsedBound > dismissedBound) {
                        dismiss()
                        effectiveOnDismiss.invoke()
                    } else {
                        collapse(velocity)
                    }
                }

                value <= l2 -> {
                    collapse(velocity)
                }

                value <= l3 -> {
                    expand(velocity)
                }

                else -> {
                    expand(velocity)
                }
            }
        }
    }

    private val sheetScrollConnection = PreUpPostDownNestedScrollConnection(this)

    val preUpPostDownNestedScrollConnection: NestedScrollConnection get() = sheetScrollConnection
}

private class PreUpPostDownNestedScrollConnection(
    private val sheet: BottomSheetState,
) : NestedScrollConnection {
    var isTopReached = false

    fun resetLatch() {
        isTopReached = false
    }

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) {
            if (sheet.isExpanded) isTopReached = false
            return Offset.Zero
        }
        if ((sheet.isExpanded || sheet.value >= sheet.expandedBound - 0.5.dp) && available.y < 0) {
            isTopReached = false
        }

        return if (isTopReached && available.y < 0) {
            sheet.dispatchRawDelta(available.y)
            available
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) {
            if (sheet.isExpanded) isTopReached = false
            return Offset.Zero
        }
        if (!isTopReached && consumed.y == 0f && available.y > 0) {
            isTopReached = true
        }

        return if (isTopReached) {
            sheet.dispatchRawDelta(available.y)
            available
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (isTopReached) {
            val velocity = -available.y
            isTopReached = false
            sheet.performFling(velocity, sheet.onDismissCallback)
            available
        } else {
            Velocity.Zero
        }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        isTopReached = false
        return Velocity.Zero
    }
}

const val EXPANDED_ANCHOR = 2
const val COLLAPSED_ANCHOR = 1
const val DISMISSED_ANCHOR = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = DISMISSED_ANCHOR,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val motionConfig = LocalMotionConfig.current
    val animationsDisabled = LocalAnimationsDisabled.current || !motionConfig.animationsEnabled
    val defaultAnimationSpec = motionConfig.sheetSpring<Dp>()
    val softAnimationSpec = motionConfig.sheetSoftSpring<Dp>()

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable =
        remember {
            Animatable(0.dp, Dp.VectorConverter)
        }

    var previousBounds by remember {
        mutableStateOf(Triple(dismissedBound, expandedBound, collapsedBound))
    }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope, animationsDisabled, defaultAnimationSpec, softAnimationSpec) {
        val initialValue =
            when (previousAnchor) {
                EXPANDED_ANCHOR -> expandedBound
                COLLAPSED_ANCHOR -> collapsedBound
                DISMISSED_ANCHOR -> dismissedBound
                else -> error("Unknown BottomSheet anchor")
            }

        val wasSettledAtAnchor =
            animatable.value == previousBounds.first ||
                animatable.value == previousBounds.second ||
                animatable.value == previousBounds.third

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (wasSettledAtAnchor && animatable.value != initialValue) {
                animatable.snapTo(initialValue)
            } else {
                animatable.animateTo(initialValue, if (animationsDisabled) snap() else defaultAnimationSpec)
            }
        }
        previousBounds = Triple(dismissedBound, expandedBound, collapsedBound)

        BottomSheetState(
            draggableState =
                DraggableState { delta ->
                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                    }
                },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            animationsDisabled = animationsDisabled,
            defaultAnimationSpec = defaultAnimationSpec,
            softAnimationSpec = softAnimationSpec,
            density = density,
            collapsedBound = collapsedBound,
            initialAnchor = previousAnchor,
        )
    }
}

@Composable
fun Modifier.bottomSheetDraggable(
    state: BottomSheetState,
    onDismiss: (() -> Unit)? = null,
): Modifier =
    this.pointerInput(state) {
        val velocityTracker = VelocityTracker()

        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                velocityTracker.addPointerInputChange(change)
                state.dispatchRawDelta(dragAmount)
            },
            onDragCancel = {
                val velocity = -velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                state.performFling(velocity, onDismiss)
            },
            onDragEnd = {
                val velocity = -velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                state.performFling(velocity, onDismiss)
            },
        )
    }
