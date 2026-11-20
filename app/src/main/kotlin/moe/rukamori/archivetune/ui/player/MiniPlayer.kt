/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.MINI_PLAYER_BAR_HEIGHT_DP
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.ui.component.NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT
import moe.rukamori.archivetune.constants.MiniPlayerGlassGlowKey
import moe.rukamori.archivetune.constants.MiniPlayerGlassGlowIntensityKey
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.MiniPlayerCompactHeight
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyleKey
import moe.rukamori.archivetune.constants.NavigationBarHorizontalPadding
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.SwipeSensitivityKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.CompactControlGap
import moe.rukamori.archivetune.ui.component.CompactControlSize
import moe.rukamori.archivetune.playback.artwork.PlayerPaletteCacheKey
import moe.rukamori.archivetune.playback.artwork.guessArtworkProvider
import moe.rukamori.archivetune.ui.component.LocalNavigationBarBackdrop
import moe.rukamori.archivetune.ui.component.LocalLiquidGlassBackdrop
import moe.rukamori.archivetune.ui.component.liquidGlass
import moe.rukamori.archivetune.ui.component.LiquidGlassPillBlurRadius
import moe.rukamori.archivetune.ui.component.liquidGlassContentColor
import moe.rukamori.archivetune.ui.component.borderGuard
import moe.rukamori.archivetune.ui.component.glassGlowOverlay
import moe.rukamori.archivetune.ui.component.tintedBottomBarColor
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.PlayerPaletteCache
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Immutable
internal data class MiniPlayerSpringGeometry(
    val compactFraction: Float,
    val outerStartPadding: Dp,
    val outerEndPadding: Dp,
    val innerHorizontalPadding: Dp,
    val barTopCorner: Dp,
    val barBottomCorner: Dp,
    val containerHeight: Dp,
    val standardExpandedAlpha: Float,
    val standardCompactAlpha: Float,
)

internal fun resolveMiniPlayerSpringGeometry(
    rawCompactFraction: Float,
    miniPlayerStyle: MiniPlayerStyle,
    compactHorizontalPadding: Dp = 16.dp,
    compactReserveEndControl: Boolean = true,
): MiniPlayerSpringGeometry {
    val safeFraction =
        if (rawCompactFraction.isFinite()) {
            rawCompactFraction.coerceIn(0f, 1f)
        } else {
            0f
        }
    val safeCompactHorizontalPadding = compactHorizontalPadding.coerceAtLeast(0.dp)
    val compactStartInset =
        (safeCompactHorizontalPadding + CompactControlSize + CompactControlGap).coerceAtLeast(0.dp)
    val compactEndInset =
        (
            if (compactReserveEndControl) {
                safeCompactHorizontalPadding + CompactControlSize + CompactControlGap
            } else {
                safeCompactHorizontalPadding
            }
            ).coerceAtLeast(0.dp)
    val outerStartPadding = lerp(0.dp, compactStartInset, safeFraction).coerceAtLeast(0.dp)
    val outerEndPadding = lerp(0.dp, compactEndInset, safeFraction).coerceAtLeast(0.dp)
    val innerHorizontalPadding =
        if (miniPlayerStyle == MiniPlayerStyle.BAR) {
            0.dp
        } else {
            lerp(NavigationBarHorizontalPadding, 0.dp, safeFraction).coerceAtLeast(0.dp)
        }
    val barTopCorner = lerp(12.dp, 26.dp, safeFraction).coerceAtLeast(0.dp)
    val barBottomCorner = lerp(0.dp, 26.dp, safeFraction).coerceAtLeast(0.dp)
    val containerHeight =
        if (miniPlayerStyle == MiniPlayerStyle.BAR) {
            MINI_PLAYER_BAR_HEIGHT_DP.dp.coerceAtLeast(0.dp)
        } else {
            lerp(MiniPlayerHeight, MiniPlayerCompactHeight, safeFraction).coerceAtLeast(0.dp)
        }
    val standardExpandedAlpha = (1f - safeFraction).coerceIn(0f, 1f)
    val standardCompactAlpha = safeFraction.coerceIn(0f, 1f)

    return MiniPlayerSpringGeometry(
        compactFraction = safeFraction,
        outerStartPadding = outerStartPadding,
        outerEndPadding = outerEndPadding,
        innerHorizontalPadding = innerHorizontalPadding,
        barTopCorner = barTopCorner,
        barBottomCorner = barBottomCorner,
        containerHeight = containerHeight,
        standardExpandedAlpha = standardExpandedAlpha,
        standardCompactAlpha = standardCompactAlpha,
    )
}

@Composable
fun MiniPlayer(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    isPairedWithNavigation: Boolean = false,
    compactFractionState: State<Float> = remember { mutableStateOf(0f) },
    compactHorizontalPadding: Dp = 16.dp,
    compactReserveEndControl: Boolean = true,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    // [FIX-ZCODE] donor-compatible proximity hook used by MainActivity's floating bars
    miniPlayerProximityProvider: (() -> Float)? = null,
    onExpand: (() -> Unit)? = null,
) {
    val compactFraction = compactFractionState.value.coerceIn(0f, 1f)
    val miniPlayerStyle by rememberEnumPreference(
        key = MiniPlayerStyleKey,
        defaultValue = MiniPlayerStyle.STANDARD,
    )
    val springGeometry =
        resolveMiniPlayerSpringGeometry(
            rawCompactFraction = compactFraction,
            miniPlayerStyle = miniPlayerStyle,
            compactHorizontalPadding = compactHorizontalPadding,
            compactReserveEndControl = compactReserveEndControl,
        )
    val compactModifier =
        modifier.padding(
            start = springGeometry.outerStartPadding,
            end = springGeometry.outerEndPadding,
        )
    NewMiniPlayer(
        positionProvider = positionProvider,
        durationProvider = durationProvider,
        modifier = compactModifier,
        pureBlack = pureBlack,
        isPairedWithNavigation = isPairedWithNavigation,
        miniPlayerStyle = miniPlayerStyle,
        compactFraction = springGeometry.compactFraction,

        compactShowTransportControls = !compactReserveEndControl,
        onArtworkSlotPositioned = onArtworkSlotPositioned,
        onExpand = onExpand,
    )
}

@Composable
private fun NewMiniPlayer(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    isPairedWithNavigation: Boolean,
    miniPlayerStyle: MiniPlayerStyle = MiniPlayerStyle.STANDARD,
    compactFraction: Float = 0f,
    compactShowTransportControls: Boolean = false,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val swipeSensitivity by rememberPreference(SwipeSensitivityKey, 0.73f)
    val swipeThumbnail by rememberPreference(moe.rukamori.archivetune.constants.SwipeThumbnailKey, true)
    val miniPlayerBackgroundStyle by rememberEnumPreference(
        key = MiniPlayerBackgroundStyleKey,
        defaultValue = MiniPlayerBackgroundStyle.THEME,
    )
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    val glassEnabled by rememberPreference(LiquidGlassEnabledKey, true)
    val glassGlow by rememberPreference(MiniPlayerGlassGlowKey, true)
    val glowIntensity by rememberPreference(MiniPlayerGlassGlowIntensityKey, NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT)
    val effectiveBackgroundStyle =
        if (!glassEnabled && miniPlayerBackgroundStyle.supportsGlassGlow) {
            MiniPlayerBackgroundStyle.THEME
        } else {
            miniPlayerBackgroundStyle
        }
    val contentColors =
        rememberMiniPlayerContentColors(
            useArtworkBackground = effectiveBackgroundStyle.usesArtworkPalette,
            useLiquidGlass = effectiveBackgroundStyle.supportsGlassGlow && LocalLiquidGlassBackdrop.current != null,
        )
    val safeCompactFraction = compactFraction.coerceIn(0f, 1f)
    val springGeometry =
        remember(safeCompactFraction, miniPlayerStyle) {
            resolveMiniPlayerSpringGeometry(
                rawCompactFraction = safeCompactFraction,
                miniPlayerStyle = miniPlayerStyle,
            )
        }
    val miniPlayerShape =
        remember(isPairedWithNavigation, safeCompactFraction, miniPlayerStyle, springGeometry) {
            if (miniPlayerStyle == MiniPlayerStyle.BAR) {
                RoundedCornerShape(
                    topStart = springGeometry.barTopCorner,
                    topEnd = springGeometry.barTopCorner,
                    bottomStart = springGeometry.barBottomCorner,
                    bottomEnd = springGeometry.barBottomCorner,
                )
            } else if (safeCompactFraction > 0.5f) {
                RoundedCornerShape(percent = 50)
            } else if (isPairedWithNavigation) {
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp,
                )
            } else {
                null
            }
        } ?: MaterialTheme.shapes.extraLarge

    val expandActionLabel = stringResource(R.string.player_mini_expand_action)
    val togglePlaybackActionLabel = stringResource(R.string.player_mini_toggle_playback)
    val previousActionLabel = stringResource(R.string.player_mini_previous_action)
    val nextActionLabel = stringResource(R.string.player_mini_next_action)

    SwipeableMiniPlayerBox(
        modifier = modifier,
        contentMaxWidth =
            if (isPairedWithNavigation && miniPlayerStyle != MiniPlayerStyle.BAR) {
                NavigationBarMaxWidth
            } else {
                null
            },
        swipeSensitivity = swipeSensitivity,
        swipeThumbnail = swipeThumbnail,
        playerConnection = playerConnection,
        layoutDirection = layoutDirection,
        coroutineScope = coroutineScope,
        pureBlack = pureBlack,
        useLegacyBackground = false,
        miniPlayerStyle = miniPlayerStyle,
        compactFraction = safeCompactFraction,
    ) { offsetX ->
        val expandLabel = stringResource(R.string.player_menu_expand)
        val containerHeight = springGeometry.containerHeight
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(containerHeight)

                    .graphicsLayer {
                        translationX = offsetX
                    }
                    .clip(miniPlayerShape)
                    .semantics {
                        customActions =
                            buildList {
                                if (onExpand != null) {
                                    add(
                                        CustomAccessibilityAction(expandActionLabel) {
                                            onExpand()
                                            true
                                        },
                                    )
                                }
                                add(
                                    CustomAccessibilityAction(togglePlaybackActionLabel) {
                                        playerConnection.player.togglePlayPause()
                                        true
                                    },
                                )
                                if (canSkipPrevious) {
                                    add(
                                        CustomAccessibilityAction(previousActionLabel) {
                                            playerConnection.seekToPrevious()
                                            true
                                        },
                                    )
                                }
                                if (canSkipNext) {
                                    add(
                                        CustomAccessibilityAction(nextActionLabel) {
                                            playerConnection.seekToNext()
                                            true
                                        },
                                    )
                                }
                            }
                    }
                    .then(
                        if (onExpand != null) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = expandLabel,
                                onClick = onExpand,
                            )
                        } else {
                            Modifier
                        },
                    ),
        ) {
            BottomBarBackground(
                style = effectiveBackgroundStyle,
                shape = miniPlayerShape,
                glowStrength = if (glassGlow && effectiveBackgroundStyle.supportsGlassGlow) glowIntensity else 0f,
                modifier = Modifier.matchParentSize(),
            )
            NewMiniPlayerContent(
                positionProvider = positionProvider,
                durationProvider = durationProvider,
                playerConnection = playerConnection,
                colors = contentColors,
                miniPlayerStyle = miniPlayerStyle,
                compactFraction = compactFraction,
                compactShowTransportControls = compactShowTransportControls,
                onArtworkSlotPositioned = onArtworkSlotPositioned,
            )
        }
    }
}

@Composable
private fun rememberMiniPlayerContentColors(
    useArtworkBackground: Boolean,
    useLiquidGlass: Boolean = false,
): MiniPlayerContentColors {
    val colorScheme = MaterialTheme.colorScheme

    val glassInk = liquidGlassContentColor()
    return remember(
        useArtworkBackground,
        useLiquidGlass,
        glassInk,
        colorScheme.primary,
        colorScheme.onPrimary,
        colorScheme.outline,
        colorScheme.onSurface,
        colorScheme.onSurfaceVariant,
        colorScheme.surface,
        colorScheme.surfaceContainerHighest,
        colorScheme.surfaceVariant,
        colorScheme.primaryContainer,
        colorScheme.onPrimaryContainer,
    ) {
        if (useArtworkBackground) {
            MiniPlayerContentColors(
                title = Color.White,
                secondary = Color.White.copy(alpha = 0.72f),
                progress = Color.White,
                progressTrack = Color.White.copy(alpha = 0.24f),
                artworkContainer = Color.White.copy(alpha = 0.14f),
                artworkBorder = Color.White.copy(alpha = 0.22f),
                primaryButtonContainer = Color.White.copy(alpha = 0.92f),

                primaryButtonIcon = Color.White,
                secondaryButtonContainer = Color.Black.copy(alpha = 0.22f),
                buttonIcon = Color.White.copy(alpha = 0.78f),
                disabledButtonIcon = Color.White.copy(alpha = 0.38f),
            )
        } else if (useLiquidGlass) {
            MiniPlayerContentColors(
                title = glassInk,
                secondary = glassInk.copy(alpha = 0.72f),
                progress = glassInk,
                progressTrack = glassInk.copy(alpha = 0.24f),
                artworkContainer = glassInk.copy(alpha = 0.14f),
                artworkBorder = glassInk.copy(alpha = 0.22f),
                primaryButtonContainer = glassInk.copy(alpha = 0.92f),

                primaryButtonIcon = glassInk,
                secondaryButtonContainer = Color.Black.copy(alpha = 0.22f),
                buttonIcon = glassInk.copy(alpha = 0.78f),
                disabledButtonIcon = glassInk.copy(alpha = 0.38f),
            )
        } else {
            MiniPlayerContentColors(
                title = colorScheme.onSurface,
                secondary = colorScheme.onSurfaceVariant,
                progress = colorScheme.primary,
                progressTrack = colorScheme.outline.copy(alpha = 0.18f),
                artworkContainer = colorScheme.surfaceVariant,
                artworkBorder = colorScheme.outline.copy(alpha = 0.2f),
                primaryButtonContainer = colorScheme.primary,
                primaryButtonIcon = colorScheme.onPrimary,
                secondaryButtonContainer = colorScheme.surfaceContainerHighest,
                buttonIcon = colorScheme.onSurface,
                disabledButtonIcon = colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

internal val MiniPlayerBackgroundStyle.usesArtworkPalette: Boolean
    get() = this == MiniPlayerBackgroundStyle.GRADIENT || this == MiniPlayerBackgroundStyle.GLOW

internal val MiniPlayerBackgroundStyle.supportsGlassGlow: Boolean
    get() = this == MiniPlayerBackgroundStyle.LIQUID_GLASS || this == MiniPlayerBackgroundStyle.SUKISU_GLASS

internal val MiniPlayerBackgroundStyle.needsBackdrop: Boolean
    get() = supportsGlassGlow || this == MiniPlayerBackgroundStyle.FROSTED || this == MiniPlayerBackgroundStyle.BITCHORD

internal val LocalBottomBarArtworkPalette = compositionLocalOf<BottomBarArtworkPalette?> { null }

@Composable
internal fun rememberBottomBarArtworkPalette(
    mediaMetadata: MediaMetadata?,
): BottomBarArtworkPalette? {
    if (mediaMetadata == null) return null
    val context = LocalContext.current
    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    var paletteTrackState by remember { mutableStateOf(PlayerPaletteTrackState()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    LaunchedEffect(
        mediaMetadata.id,
        mediaMetadata.thumbnailUrl,
        fallbackColor,
        darkTheme,
    ) {
        val currentMetadata = mediaMetadata
        val thumbnailUrl = currentMetadata.thumbnailUrl
        if (thumbnailUrl.isNullOrBlank()) {
            paletteTrackState =
                resolvePaletteTransitionOnMissingOrFailedArtwork(
                    previousState = paletteTrackState,
                    currentMediaId = currentMetadata.id,
                    currentArtworkUrl = null,
                )
            gradientColors = emptyList()
            return@LaunchedEffect
        }

        val cacheKey =
            PlayerPaletteCacheKey(
                mediaId = currentMetadata.id,
                provider = guessArtworkProvider(thumbnailUrl),
                artworkIdentity = thumbnailUrl,
                backgroundMode = "BOTTOM_BAR_ARTWORK",
                darkTheme = darkTheme,
            )
        PlayerPaletteCache.get(cacheKey)?.let { cachedColors ->
            gradientColors = cachedColors
            paletteTrackState =
                PlayerPaletteTrackState(
                    mediaId = currentMetadata.id,
                    artworkUrl = thumbnailUrl,
                    hasValidPalette = true,
                )
            return@LaunchedEffect
        }

        val preState =
            resolvePaletteTransitionOnMissingOrFailedArtwork(
                previousState = paletteTrackState,
                currentMediaId = currentMetadata.id,
                currentArtworkUrl = thumbnailUrl,
            )
        paletteTrackState = preState
        if (!preState.hasValidPalette) {
            gradientColors = emptyList()
        }

        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }
                if (result !is SuccessResult) {
                    null
                } else {
                    val bitmap = result.image?.toBitmap()
                    if (bitmap == null) {
                        null
                    } else {
                        val palette =
                            withContext(Dispatchers.Default) {
                                Palette
                                    .from(bitmap)
                                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                    .generate()
                            }
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }

        if (extractedColors != null) {
            PlayerPaletteCache.put(cacheKey, extractedColors)
            gradientColors = extractedColors
            paletteTrackState =
                PlayerPaletteTrackState(
                    mediaId = currentMetadata.id,
                    artworkUrl = thumbnailUrl,
                    hasValidPalette = true,
                )
        } else {
            val failedState =
                resolvePaletteTransitionOnMissingOrFailedArtwork(
                    previousState = paletteTrackState,
                    currentMediaId = currentMetadata.id,
                    currentArtworkUrl = thumbnailUrl,
                    fetchFailed = true,
                )
            paletteTrackState = failedState
            if (!failedState.hasValidPalette) {
                gradientColors = emptyList()
            }
        }
    }

    return remember(gradientColors) { BottomBarArtworkPalette.from(gradientColors) }
}

private const val FrostedMiniPlayerBlurRadiusPx = 60f
private const val FrostedMiniPlayerOverlayAlpha = 0.30f

@Composable
internal fun BottomBarBackground(
    style: MiniPlayerBackgroundStyle,
    shape: Shape,
    modifier: Modifier = Modifier,
    palette: BottomBarArtworkPalette? = LocalBottomBarArtworkPalette.current,
    glowStrength: Float = 0f,
) {
    val colorScheme = MaterialTheme.colorScheme
    val baseColor = colorScheme.surfaceContainerHigh
    val effectiveStyle =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && style.needsBackdrop && style != MiniPlayerBackgroundStyle.BITCHORD) {
            MiniPlayerBackgroundStyle.THEME
        } else {
            style
        }
    val artworkPalette = palette ?: BottomBarArtworkPalette(
        first = colorScheme.primary,
        second = colorScheme.secondary,
        third = colorScheme.tertiary,
        fourth = colorScheme.primaryContainer,
    )
    when (effectiveStyle) {
        MiniPlayerBackgroundStyle.THEME -> Box(modifier.background(baseColor))
        MiniPlayerBackgroundStyle.TINTED -> Box(modifier.background(tintedBottomBarColor()))

        MiniPlayerBackgroundStyle.LIQUID_GLASS, MiniPlayerBackgroundStyle.SUKISU_GLASS, MiniPlayerBackgroundStyle.BITCHORD -> {
            val backdrop = LocalLiquidGlassBackdrop.current
            val rendered =
                when {
                    backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                        modifier.background(baseColor)
                    }
                    effectiveStyle == MiniPlayerBackgroundStyle.SUKISU_GLASS -> {
                        modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx())
                                lens(
                                    refractionHeight = minOf(24.dp.toPx(), size.minDimension / 2f),
                                    refractionAmount = 24.dp.toPx(),
                                )
                            },
                            onDrawSurface = { drawRect(baseColor.copy(alpha = 0.40f)) },
                        )
                    }
                    else -> {
                        modifier.liquidGlass(
                            backdrop = backdrop,
                            shape = shape,
                            interactive = false,
                            blurRadius = LiquidGlassPillBlurRadius,
                            baseColor = baseColor,
                        )
                    }
                }
            Box(
                modifier = when {
                    style == MiniPlayerBackgroundStyle.BITCHORD -> rendered.borderGuard(shape)
                    backdrop != null && glowStrength > 0f -> rendered.glassGlowOverlay(glowStrength.coerceIn(0.2f, 2f), shape)
                    else -> rendered
                },
            )
        }

        MiniPlayerBackgroundStyle.FROSTED -> {
            val backdrop = LocalNavigationBarBackdrop.current
            // [FIX-ZCODE] Wave 9 / perf: pre-S devices fall back to the plain base color.
            // The legacy CPU blur (Bitmap.createBitmap + re-blur loop) was removed; on
            // Android 12+ Compose maps BlurEffect to hardware RenderEffect.createBlurEffect.
            if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Box(modifier = modifier.background(baseColor))
            } else {
                val positionInRootState = remember { mutableStateOf(Offset.Zero) }
                val positionInRoot by positionInRootState
                Box(
                    modifier =
                        modifier
                            .onGloballyPositioned(
                                remember(positionInRootState) {
                                    { coordinates -> positionInRootState.value = coordinates.positionInRoot() }
                                },
                            )
                            .background(baseColor),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    renderEffect =
                                        BlurEffect(
                                            radiusX = FrostedMiniPlayerBlurRadiusPx,
                                            radiusY = FrostedMiniPlayerBlurRadiusPx,
                                            edgeTreatment = TileMode.Clamp,
                                        )
                                    alpha = FrostedMiniPlayerOverlayAlpha
                                    clip = true
                                }.drawBehind {
                                    val offset = backdrop.contentOffsetInRoot - positionInRoot
                                    translate(offset.x, offset.y) {
                                        runCatching { drawLayer(backdrop.layer) }
                                    }
                                },
                    )
                }
            }
        }

        MiniPlayerBackgroundStyle.GRADIENT -> {
            val colors = artworkPalette

            val gradientBrush = remember(colors) {
                Brush.verticalGradient(
                    colorStops =
                        arrayOf(
                            0f to colors.first.copy(alpha = 0.95f),
                            0.52f to colors.second.copy(alpha = 0.82f),
                            1f to colors.third.copy(alpha = 0.72f),
                        ),
                )
            }
            val overlayColor = remember { Color.Black.copy(alpha = 0.32f) }
            Box(modifier = modifier) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(gradientBrush),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(overlayColor),
                )
            }
        }

        MiniPlayerBackgroundStyle.GLOW -> {
            val colors = artworkPalette
            Box(
                modifier =
                    modifier.drawWithCache {
                        val width = size.width
                        val height = size.height
                        val startGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.first.copy(alpha = 0.82f), colors.first.copy(alpha = 0.38f), Color.Transparent),
                                center = Offset(width * 0.12f, height * 0.42f),
                                radius = width * 0.72f,
                            )
                        val endGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.second.copy(alpha = 0.78f), colors.second.copy(alpha = 0.34f), Color.Transparent),
                                center = Offset(width * 0.88f, height * 0.58f),
                                radius = width * 0.72f,
                            )
                        val topGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.third.copy(alpha = 0.58f), Color.Transparent),
                                center = Offset(width * 0.52f, height * 0.05f),
                                radius = width * 0.54f,
                            )
                        val bottomGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.fourth.copy(alpha = 0.46f), Color.Transparent),
                                center = Offset(width * 0.46f, height * 1.05f),
                                radius = width * 0.54f,
                            )

                        onDrawBehind {
                            drawRect(Color.Black)
                            drawRect(startGlow)
                            drawRect(endGlow)
                            drawRect(topGlow)
                            drawRect(bottomGlow)
                            drawRect(Color.Black.copy(alpha = 0.24f))
                        }
                    },
            )
        }
    }
}

@Immutable
internal data class BottomBarArtworkPalette(
    val first: Color,
    val second: Color,
    val third: Color,
    val fourth: Color,
) {
    companion object {
        fun from(colors: List<Color>): BottomBarArtworkPalette? {
            val first = colors.firstOrNull() ?: return null
            val second = colors.getOrElse(1) { first }
            val third = colors.getOrElse(2) { second }
            val fourth = colors.getOrElse(3) { first }
            return BottomBarArtworkPalette(
                first = first,
                second = second,
                third = third,
                fourth = fourth,
            )
        }
    }
}
