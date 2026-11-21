/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.MiniPlayerGlassGlowKey
import moe.rukamori.archivetune.constants.MiniPlayerGlassGlowIntensityKey
import moe.rukamori.archivetune.constants.NavigationBarArtworkStyleKey
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyleKey
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_CORNER_RADIUS_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_HEIGHT_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_LABEL_SPACING_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_OPACITY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_TRANSPARENCY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_WIDTH_DEFAULT
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.NavigationBarCompactBehaviorKey
import moe.rukamori.archivetune.constants.NavigationBarCornerRadiusKey
import moe.rukamori.archivetune.constants.NavigationBarFrostedBlurKey
import moe.rukamori.archivetune.constants.NavigationBarBitchordKey
import moe.rukamori.archivetune.constants.SukisuLiquidGlassNavBarKey
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.constants.LiquidGlassNavBarEnabledKey
import moe.rukamori.archivetune.ui.component.NavigationBarGlassGlowKey
import moe.rukamori.archivetune.ui.component.NavigationBarGlassGlowIntensityKey
import moe.rukamori.archivetune.ui.component.NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT
import moe.rukamori.archivetune.constants.NavigationBarTintFrostedBlurKey
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarHeightKey
import moe.rukamori.archivetune.constants.NavigationBarLabelSpacingKey
import moe.rukamori.archivetune.constants.NavigationBarOpacityKey
import moe.rukamori.archivetune.constants.NavigationBarTransparencyKey
import moe.rukamori.archivetune.constants.NavigationBarWidthKey
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.FrostedHeaderPill
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.player.BottomBarBackground
import moe.rukamori.archivetune.ui.player.supportsGlassGlow
import moe.rukamori.archivetune.ui.player.usesArtworkPalette
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.settings.studio.NavBarMaterial
import moe.rukamori.archivetune.ui.screens.settings.studio.miniPlayerBackgroundStyleLabelRes
import moe.rukamori.archivetune.ui.screens.settings.studio.writeNavigationBarMaterial
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.asPaddingValues
import moe.rukamori.archivetune.ui.screens.ScreenHeaderHaze
import moe.rukamori.archivetune.ui.screens.rememberScreenHeaderHaze
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import moe.rukamori.archivetune.ui.component.SettingsPageTopBar

@Composable
fun NavigationBarSettings(navController: NavController, scrollTo: String? = null) {
    val (navigationBarFrostedBlur) =
        rememberPreference(NavigationBarFrostedBlurKey, defaultValue = false)
    val (navigationBarTintFrostedBlur) =
        rememberPreference(NavigationBarTintFrostedBlurKey, defaultValue = false)

    val (liquidGlassEnabled) =
        rememberPreference(LiquidGlassEnabledKey, defaultValue = true)
    val (liquidGlassNavBarEnabled) =
        rememberPreference(LiquidGlassNavBarEnabledKey, defaultValue = false)
    val (navigationBarGlassGlow, onNavigationBarGlassGlowChange) =
        rememberPreference(NavigationBarGlassGlowKey, defaultValue = true)
    val (navigationBarGlassGlowIntensity, onNavigationBarGlassGlowIntensityChange) =
        rememberPreference(
            NavigationBarGlassGlowIntensityKey,
            defaultValue = NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
        )

    val (navigationBarBitchord) =
        rememberPreference(NavigationBarBitchordKey, defaultValue = false)
    val (sukisuLiquidGlassNavBar) =
        rememberPreference(SukisuLiquidGlassNavBarKey, defaultValue = false)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (navArtwork) = rememberEnumPreference(NavigationBarArtworkStyleKey, MiniPlayerBackgroundStyle.THEME)
    val onNavMaterialChange: (NavBarMaterial) -> Unit = { material ->
        scope.launch { context.dataStore.edit { it.writeNavigationBarMaterial(material) } }
    }
    val (hideNavigationBarLabels, onHideNavigationBarLabelsChange) =
        rememberPreference(HideNavigationBarLabelsKey, defaultValue = false)

    val (compactBehavior, onCompactBehaviorChange) =
        rememberEnumPreference(
            NavigationBarCompactBehaviorKey,
            defaultValue = NavigationBarCompactBehavior.ADAPTIVE,
        )

    val (navigationBarWidth, onNavigationBarWidthChange) =
        rememberPreference(NavigationBarWidthKey, defaultValue = NAVIGATION_BAR_WIDTH_DEFAULT)
    val (navigationBarHeight, onNavigationBarHeightChange) =
        rememberPreference(NavigationBarHeightKey, defaultValue = NAVIGATION_BAR_HEIGHT_DEFAULT)
    val (navigationBarOpacity, onNavigationBarOpacityChange) =
        rememberPreference(NavigationBarOpacityKey, defaultValue = NAVIGATION_BAR_OPACITY_DEFAULT)
    val (navigationBarTransparency, onNavigationBarTransparencyChange) =
        rememberPreference(
            NavigationBarTransparencyKey,
            defaultValue = NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
        )
    val (navigationBarLabelSpacing, onNavigationBarLabelSpacingChange) =
        rememberPreference(
            NavigationBarLabelSpacingKey,
            defaultValue = NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
        )
    val (navigationBarCornerRadius, onNavigationBarCornerRadiusChange) =
        rememberPreference(
            NavigationBarCornerRadiusKey,
            defaultValue = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
        )

    val headerHaze = rememberScreenHeaderHaze()
    val systemBarsTopPadding = LocalStableSystemBarsTopPadding.current
    val glassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
                SettingsPageTopBar(
                    titleText = stringResource(R.string.navigation_bar_settings_title),
                    onBack = navController::navigateUp,
                    onBackLongClick = navController::backToMain,
                )
            },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()

        LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        val (miniPlayerStyle, onMiniPlayerStyleChange) =
            rememberEnumPreference(MiniPlayerStyleKey, defaultValue = MiniPlayerStyle.STANDARD)
        val (miniPlayerBackgroundStyle, onMiniPlayerBackgroundStyleChange) =
            rememberEnumPreference(
                MiniPlayerBackgroundStyleKey,
                defaultValue = MiniPlayerBackgroundStyle.THEME,
            )
        val (miniGlassGlow, onMiniGlassGlowChange) = rememberPreference(MiniPlayerGlassGlowKey, true)
        val (miniGlowIntensity, onMiniGlowIntensityChange) =
            rememberPreference(MiniPlayerGlassGlowIntensityKey, NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT)
        val previewMaterial =
            when {
                sukisuLiquidGlassNavBar -> NavBarMaterial.SUKISU_GLASS
                liquidGlassNavBarEnabled -> NavBarMaterial.LIQUID_GLASS
                navigationBarBitchord -> NavBarMaterial.BITCHORD
                navigationBarTintFrostedBlur -> NavBarMaterial.TINTED
                navigationBarFrostedBlur -> NavBarMaterial.FROSTED
                navArtwork == MiniPlayerBackgroundStyle.GRADIENT -> NavBarMaterial.GRADIENT
                navArtwork == MiniPlayerBackgroundStyle.GLOW -> NavBarMaterial.GLOW
                else -> NavBarMaterial.STANDARD
            }
        val currentNavProfile =
            moe.rukamori.archivetune.ui.screens.settings.studio.NavigationProfile(
                material = previewMaterial,
                compactBehavior = compactBehavior,
                hideLabels = hideNavigationBarLabels,
                widthFraction = navigationBarWidth,
                heightMultiplier = navigationBarHeight,
                opacity = navigationBarOpacity,
                transparency = navigationBarTransparency,
                labelSpacing = navigationBarLabelSpacing,
                cornerRadius = navigationBarCornerRadius,
                glassGlow = navigationBarGlassGlow,
                glassGlowIntensity = navigationBarGlassGlowIntensity,
                miniPlayerStyle = miniPlayerStyle,
                miniPlayerBackground = miniPlayerBackgroundStyle,
                miniPlayerGlassGlow = miniGlassGlow,
                miniPlayerGlassGlowIntensity = miniGlowIntensity,
            )

        Column(
            Modifier
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    ),
                )

                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .hazeSource(headerHaze)
                .padding(top = topPadding)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.preview)) {
                item {
                    moe.rukamori.archivetune.ui.screens.settings.studio.UnifiedNavMiniPlayerPreview(
                        navigation = currentNavProfile,
                        playerStyle = rememberEnumPreference(
                            PlayerDesignStyleKey,
                            defaultValue = PlayerDesignStyle.APPLE_MUSIC,
                        ).value,
                        reducedMotion = !moe.rukamori.archivetune.ui.motion.LocalMotionConfig.current.animationsEnabled,
                        onSelectPreset = { preset ->
                            val nav = preset.navigation
                            onNavMaterialChange(nav.material)
                            onCompactBehaviorChange(nav.compactBehavior)
                            onHideNavigationBarLabelsChange(nav.hideLabels)
                            onNavigationBarWidthChange(nav.widthFraction)
                            onNavigationBarHeightChange(nav.heightMultiplier)
                            onNavigationBarOpacityChange(nav.opacity)
                            onNavigationBarTransparencyChange(nav.transparency)
                            onNavigationBarCornerRadiusChange(nav.cornerRadius)
                            onMiniPlayerStyleChange(nav.miniPlayerStyle)
                            onMiniPlayerBackgroundStyleChange(nav.miniPlayerBackground)
                            onNavigationBarGlassGlowChange(nav.glassGlow)
                            onNavigationBarGlassGlowIntensityChange(nav.glassGlowIntensity)
                            onMiniGlassGlowChange(nav.miniPlayerGlassGlow)
                            onMiniGlowIntensityChange(nav.miniPlayerGlassGlowIntensity)
                        },
                    )
                }
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.mini_player_style_title)) },
                        icon = { Icon(painterResource(R.drawable.music_note), null) },
                        selectedValue = miniPlayerStyle,
                        onValueSelected = onMiniPlayerStyleChange,
                        valueText = {
                            when (it) {
                                MiniPlayerStyle.STANDARD -> stringResource(R.string.mini_player_style_pill)
                                MiniPlayerStyle.BAR -> stringResource(R.string.mini_player_style_bar)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.navigation_bar_compact_behavior)) {
                item {
                    CompactBehaviorPreview(
                        navigation = currentNavProfile,
                        modifier = positions.modifierFor("navigation_bar_compact_behavior_preview"),
                    )
                }
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.navigation_bar_compact_behavior)) },
                        description = stringResource(R.string.navigation_bar_compact_behavior_desc),
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        selectedValue = compactBehavior,
                        onValueSelected = onCompactBehaviorChange,
                        valueText = {
                            when (it) {
                                NavigationBarCompactBehavior.ADAPTIVE ->
                                    stringResource(R.string.navigation_bar_compact_adaptive)
                                NavigationBarCompactBehavior.ALWAYS_EXPANDED ->
                                    stringResource(R.string.navigation_bar_compact_always_expanded)
                                NavigationBarCompactBehavior.ALWAYS_COMPACT ->
                                    stringResource(R.string.navigation_bar_compact_always_compact)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.general)) {
                item {
                    EnumListPreference(
                        modifier = positions.modifierFor(
                            "navigation_bar_frosted_blur",
                            "frosted_nav_bar",
                            "navigation_bar_tint_frosted_blur",
                            "liquid_glass_nav_bar",
                            "navigation_bar_bitchord",
                            "sukisu_liquid_glass_nav_bar",
                        ),
                        title = { Text(stringResource(R.string.studio_nav_background)) },
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        selectedValue = previewMaterial,
                        valueText = { stringResource(it.labelRes) },
                        onValueSelected = onNavMaterialChange,
                    )
                }
                item {
                    EnumListPreference(
                        modifier = positions.modifierFor("mini_player_background_style"),
                        title = { Text(stringResource(R.string.mini_player_background_style)) },
                        icon = { Icon(painterResource(R.drawable.music_note), null) },
                        selectedValue = miniPlayerBackgroundStyle,
                        valueText = { stringResource(miniPlayerBackgroundStyleLabelRes(it)) },
                        onValueSelected = onMiniPlayerBackgroundStyleChange,
                    )
                }
                item {
                    val miniGlassActive = glassSupported && liquidGlassEnabled && miniPlayerBackgroundStyle.supportsGlassGlow
                    SwitchPreference(
                        title = { Text(stringResource(R.string.mini_player_glass_glow)) },
                        description = if (miniGlassActive) null else stringResource(R.string.dependency_nav_material_glow_bar),
                        icon = { Icon(painterResource(R.drawable.solar_brightness_high_linear), null) },
                        checked = miniGlassGlow,
                        isEnabled = miniGlassActive,
                        onCheckedChange = onMiniGlassGlowChange,
                    )
                }
                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.mini_player_glass_glow_intensity),
                        description = stringResource(R.string.navigation_bar_glass_glow_intensity_desc),
                        iconRes = R.drawable.tune,
                        value = miniGlowIntensity,
                        onValueChange = onMiniGlowIntensityChange,
                        range = 0.2f..2f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
                        enabled = glassSupported && liquidGlassEnabled && miniPlayerBackgroundStyle.supportsGlassGlow && miniGlassGlow,
                        disabledReason = stringResource(R.string.dependency_nav_material_glow),
                    )
                }
                item {
                    val glassBarActive = liquidGlassNavBarEnabled || sukisuLiquidGlassNavBar
                    SwitchPreference(
                        modifier = positions.modifierFor("navigation_bar_glass_glow"),
                        title = { Text(stringResource(R.string.navigation_bar_glass_glow)) },
                        description =
                            if (glassBarActive) {
                                stringResource(R.string.navigation_bar_glass_glow_desc)
                            } else {
                                stringResource(R.string.dependency_nav_material_glow_bar)
                            },
                        icon = { Icon(painterResource(R.drawable.solar_brightness_high_linear), null) },
                        checked = navigationBarGlassGlow,
                        isEnabled = glassBarActive,
                        onCheckedChange = onNavigationBarGlassGlowChange,
                    )
                }

                item {
                    SliderPreferenceRow(
                        modifier = positions.modifierFor("navigation_bar_glass_glow_intensity"),
                        title = stringResource(R.string.navigation_bar_glass_glow_intensity),
                        description = stringResource(R.string.navigation_bar_glass_glow_intensity_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarGlassGlowIntensity,
                        onValueChange = onNavigationBarGlassGlowIntensityChange,
                        range = 0.2f..2f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
                        enabled = navigationBarGlassGlow && (liquidGlassNavBarEnabled || sukisuLiquidGlassNavBar),
                        disabledReason = stringResource(R.string.dependency_nav_material_glow),
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("hide_navigation_bar_labels"),
                        title = { Text(stringResource(R.string.hide_navigation_bar_labels)) },
                        description = stringResource(R.string.hide_navigation_bar_labels_desc),
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        checked = hideNavigationBarLabels,
                        onCheckedChange = onHideNavigationBarLabelsChange,
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("navigation_bar_dimensions"),
                title = stringResource(R.string.navigation_bar_dimensions),
            ) {
                if (compactBehavior == NavigationBarCompactBehavior.ALWAYS_COMPACT) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.studio_nav_always_compact_geometry_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                item {
                    SliderPreferenceRow(
                        modifier = positions.modifierFor("navigation_bar_width"),
                        title = stringResource(R.string.navigation_bar_width),
                        description = stringResource(R.string.navigation_bar_width_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarWidth,
                        onValueChange = onNavigationBarWidthChange,
                        range = 0.5f..1.0f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_WIDTH_DEFAULT,
                        preview = { tempWidth ->
                            NavBarPreview(
                                widthFraction = tempWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                material = previewMaterial,
                                hideLabels = hideNavigationBarLabels,
                                glassGlow = navigationBarGlassGlow,
                                glassGlowIntensity = navigationBarGlassGlowIntensity,
                            )
                        },
                        enabled = !sukisuLiquidGlassNavBar && !navigationBarBitchord,
                        disabledReason = stringResource(R.string.dependency_nav_width),
                    )
                }

                item {
                    SliderPreferenceRow(
                        modifier = positions.modifierFor("navigation_bar_height"),
                        title = stringResource(R.string.navigation_bar_height),
                        description = stringResource(R.string.navigation_bar_height_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarHeight,
                        onValueChange = onNavigationBarHeightChange,
                        range = 0.8f..1.4f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_HEIGHT_DEFAULT,
                        preview = { tempHeight ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = tempHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                material = previewMaterial,
                                hideLabels = hideNavigationBarLabels,
                                glassGlow = navigationBarGlassGlow,
                                glassGlowIntensity = navigationBarGlassGlowIntensity,
                            )
                        },
                        enabled = !liquidGlassNavBarEnabled && !sukisuLiquidGlassNavBar,
                        disabledReason = stringResource(R.string.dependency_nav_height),
                    )
                }

                item {
                    SliderPreferenceRow(
                        modifier = positions.modifierFor("navigation_bar_opacity"),
                        title = stringResource(R.string.navigation_bar_opacity),
                        description = stringResource(R.string.navigation_bar_opacity_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarOpacity,
                        onValueChange = onNavigationBarOpacityChange,
                        range = 0.2f..1.0f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_OPACITY_DEFAULT,
                        preview = { tempOpacity ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = tempOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                material = previewMaterial,
                                hideLabels = hideNavigationBarLabels,
                                glassGlow = navigationBarGlassGlow,
                                glassGlowIntensity = navigationBarGlassGlowIntensity,
                            )
                        },
                        enabled = !liquidGlassNavBarEnabled && !sukisuLiquidGlassNavBar && !navigationBarBitchord && !navigationBarFrostedBlur,
                        disabledReason = stringResource(R.string.dependency_nav_material_tint),
                    )
                }

                item {
                    SliderPreferenceRow(
                        modifier = positions.modifierFor("navigation_bar_transparency"),
                        title = stringResource(R.string.navigation_bar_transparency),
                        description = stringResource(R.string.navigation_bar_transparency_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarTransparency,
                        onValueChange = onNavigationBarTransparencyChange,
                        range = 0.0f..0.95f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
                        preview = { tempTransparency ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = tempTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                material = previewMaterial,
                                hideLabels = hideNavigationBarLabels,
                                glassGlow = navigationBarGlassGlow,
                                glassGlowIntensity = navigationBarGlassGlowIntensity,
                            )
                        },
                        enabled = !liquidGlassNavBarEnabled && !sukisuLiquidGlassNavBar && !navigationBarBitchord && !navigationBarFrostedBlur,
                        disabledReason = stringResource(R.string.dependency_nav_material_tint),
                    )
                }

                item {
                    SliderPreferenceRow(
                        modifier = positions.modifierFor("navigation_bar_label_spacing"),
                        title = stringResource(R.string.navigation_bar_label_spacing),
                        description = stringResource(R.string.navigation_bar_label_spacing_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarLabelSpacing,
                        onValueChange = onNavigationBarLabelSpacingChange,
                        range = 0f..16f,
                        valueLabel = { "${it.roundToInt()} dp" },
                        default = NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
                        preview = { tempSpacing ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = tempSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                material = previewMaterial,
                                hideLabels = hideNavigationBarLabels,
                                glassGlow = navigationBarGlassGlow,
                                glassGlowIntensity = navigationBarGlassGlowIntensity,
                            )
                        },
                        enabled = !liquidGlassNavBarEnabled && !sukisuLiquidGlassNavBar && !navigationBarBitchord && !hideNavigationBarLabels,
                        disabledReason =
                            if (hideNavigationBarLabels) {
                                stringResource(R.string.dependency_nav_labels_spacing)
                            } else {
                                stringResource(R.string.dependency_nav_labels_material)
                            },
                    )
                }

                item {
                    SliderPreferenceRow(
                        modifier = positions.modifierFor("navigation_bar_corner_radius"),
                        title = stringResource(R.string.navigation_bar_corner_radius),
                        description = stringResource(R.string.navigation_bar_corner_radius_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarCornerRadius,
                        onValueChange = onNavigationBarCornerRadiusChange,
                        range = 0f..48f,
                        valueLabel = { "${it.roundToInt()} dp" },
                        default = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
                        preview = { tempRadius ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = tempRadius,
                                material = previewMaterial,
                                hideLabels = hideNavigationBarLabels,
                                glassGlow = navigationBarGlassGlow,
                                glassGlowIntensity = navigationBarGlassGlowIntensity,
                            )
                        },
                        enabled = !liquidGlassNavBarEnabled && !sukisuLiquidGlassNavBar && !navigationBarBitchord,
                        disabledReason = stringResource(R.string.dependency_nav_corner_radius),
                    )
                }

                item {
                    val allDefaults =
                        navigationBarWidth == NAVIGATION_BAR_WIDTH_DEFAULT &&
                            navigationBarHeight == NAVIGATION_BAR_HEIGHT_DEFAULT &&
                            navigationBarOpacity == NAVIGATION_BAR_OPACITY_DEFAULT &&
                            navigationBarTransparency == NAVIGATION_BAR_TRANSPARENCY_DEFAULT &&
                            navigationBarLabelSpacing == NAVIGATION_BAR_LABEL_SPACING_DEFAULT &&
                            navigationBarCornerRadius == NAVIGATION_BAR_CORNER_RADIUS_DEFAULT

                    OutlinedButton(
                        onClick = {
                            onNavigationBarWidthChange(NAVIGATION_BAR_WIDTH_DEFAULT)
                            onNavigationBarHeightChange(NAVIGATION_BAR_HEIGHT_DEFAULT)
                            onNavigationBarOpacityChange(NAVIGATION_BAR_OPACITY_DEFAULT)
                            onNavigationBarTransparencyChange(NAVIGATION_BAR_TRANSPARENCY_DEFAULT)
                            onNavigationBarLabelSpacingChange(NAVIGATION_BAR_LABEL_SPACING_DEFAULT)
                            onNavigationBarCornerRadiusChange(NAVIGATION_BAR_CORNER_RADIUS_DEFAULT)
                        },
                        enabled = !allDefaults,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.restore),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.navigation_bar_reset_dimensions))
                    }
                }
            }
        }

        ScreenHeaderHaze(
            hazeState = headerHaze,
            systemBarsTopPadding = systemBarsTopPadding,
        )
        }
}
}

@Composable
private fun SliderPreferenceRow(
    title: String,
    description: String,
    iconRes: Int,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    default: Float? = null,
    preview: (@Composable (Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (showDialog) {
        var tempValue by remember { mutableFloatStateOf(value) }

        DefaultDialog(
            onDismiss = {
                tempValue = value
                showDialog = false
            },
            buttons = {
                if (default != null) {
                    TextButton(
                        onClick = { tempValue = default },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        tempValue = value
                        showDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onValueChange(tempValue)
                        showDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                if (preview != null) {
                    Text(
                        text = stringResource(R.string.preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    preview(tempValue)
                    Spacer(modifier = Modifier.padding(top = 16.dp))
                }

                Text(
                    text = valueLabel(tempValue),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Slider(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    Box(modifier = modifier) {
        PreferenceEntry(
            title = { Text(title) },
            description =
                if (!enabled && !disabledReason.isNullOrBlank()) {
                    "${valueLabel(value)} · $disabledReason"
                } else {
                    valueLabel(value)
                },
            icon = { Icon(painterResource(iconRes), null) },
            onClick = { showDialog = true },
            isEnabled = enabled,
        )
    }
}

@Composable
private fun NavBarPreview(
    widthFraction: Float,
    heightMultiplier: Float,
    opacity: Float,
    transparency: Float,
    labelSpacing: Float,
    cornerRadius: Float,
    material: NavBarMaterial =
        NavBarMaterial.STANDARD,
    hideLabels: Boolean = false,
    glassGlow: Boolean = true,
    glassGlowIntensity: Float = NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
) {
    val effectiveWidthFraction =
        when (material) {
            NavBarMaterial.SUKISU_GLASS,
            NavBarMaterial.BITCHORD,
            -> 0.92f
            else -> widthFraction.coerceIn(0.5f, 1f)
        }
    val resolvedBarHeight =
        when (material) {
            NavBarMaterial.LIQUID_GLASS,
            NavBarMaterial.SUKISU_GLASS,
            -> 66.dp
            else -> (NavigationBarHeight * heightMultiplier).coerceAtLeast(48.dp)
        }
    val resolvedCorner =
        when (material) {
            NavBarMaterial.LIQUID_GLASS,
            NavBarMaterial.SUKISU_GLASS,
            -> 28.dp
            NavBarMaterial.BITCHORD -> 22.dp
            else -> cornerRadius.coerceAtLeast(0f).dp
        }
    val shape = RoundedCornerShape(resolvedCorner)

    val effectiveAlpha = (opacity * (1f - transparency)).coerceIn(0.08f, 1f)
    val barColor =
        when (material) {
            NavBarMaterial.STANDARD, NavBarMaterial.GRADIENT, NavBarMaterial.GLOW ->
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = effectiveAlpha)
            NavBarMaterial.FROSTED ->
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            NavBarMaterial.TINTED ->
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = effectiveAlpha.coerceIn(0.25f, 0.98f))
            NavBarMaterial.LIQUID_GLASS ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.56f)
            NavBarMaterial.SUKISU_GLASS ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.66f)
            NavBarMaterial.BITCHORD ->
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        }
    val isGlassMaterial =
        moe.rukamori.archivetune.ui.screens.settings.studio.navBarMaterialSupportsGlassGlow(material)
    val borderWidth =
        when {
            isGlassMaterial -> 1.2.dp
            material == NavBarMaterial.FROSTED ||
                material == NavBarMaterial.BITCHORD -> 1.dp
            else -> 0.dp
        }
    val borderColor =
        when {
            isGlassMaterial && glassGlow ->
                MaterialTheme.colorScheme.primary.copy(alpha = (0.38f * glassGlowIntensity).coerceIn(0.14f, 0.85f))
            isGlassMaterial -> Color.White.copy(alpha = 0.30f)
            material == NavBarMaterial.FROSTED ->
                Color.White.copy(alpha = 0.22f)
            material == NavBarMaterial.BITCHORD ->
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            else -> Color.Transparent
        }
    val indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)

    val fauxScreenBrush =
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(fauxScreenBrush),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(
                        bottom = 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ).fillMaxWidth(effectiveWidthFraction)
                    .height(resolvedBarHeight)
                    .then(
                        if (borderWidth > 0.dp) {
                            Modifier.border(borderWidth, borderColor, shape)
                        } else {
                            Modifier
                        },
                    ),
            shape = shape,
            color = barColor,
            tonalElevation = if (isGlassMaterial) 0.dp else NavigationBarDefaults.Elevation,
            shadowElevation = if (isGlassMaterial) 4.dp else 8.dp,
        ) {
            material.artworkStyle?.let { style ->
                BottomBarBackground(style = style, shape = shape, modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val items = Screens.MainScreens
                items.forEachIndexed { index, screen ->
                    val selected = index == 0
                    val selectedColor = MaterialTheme.colorScheme.primary
                    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(if (selected) indicatorColor else Color.Transparent)
                                    .padding(horizontal = 18.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (selected) screen.iconIdActive else screen.iconIdInactive,
                                    ),
                                contentDescription = null,
                                tint = if (selected) selectedColor else unselectedColor,
                            )
                        }
                        if (!hideLabels) {
                            Spacer(Modifier.height(labelSpacing.coerceAtLeast(0f).dp))
                            Text(
                                text = stringResource(screen.titleId),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) selectedColor else unselectedColor,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBehaviorPreview(
    navigation: moe.rukamori.archivetune.ui.screens.settings.studio.NavigationProfile,
    modifier: Modifier = Modifier,
) {
    val behavior = navigation.compactBehavior
    var demoCompact by remember { mutableStateOf(false) }
    LaunchedEffect(behavior) {
        when (behavior) {
            NavigationBarCompactBehavior.ALWAYS_EXPANDED -> demoCompact = false
            NavigationBarCompactBehavior.ALWAYS_COMPACT -> demoCompact = true
            NavigationBarCompactBehavior.ADAPTIVE -> Unit
        }
    }
    val animatedCompactFraction by animateFloatAsState(
        targetValue = when (behavior) {
            NavigationBarCompactBehavior.ALWAYS_EXPANDED -> 0f
            NavigationBarCompactBehavior.ALWAYS_COMPACT -> 1f
            NavigationBarCompactBehavior.ADAPTIVE -> if (demoCompact) 1f else 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 350f),
        label = "compactBehaviorPreviewFraction",
    )
    val compactFraction = animatedCompactFraction.coerceIn(0f, 1f)

    val primary = MaterialTheme.colorScheme.primary
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val isGlassMaterial =
        moe.rukamori.archivetune.ui.screens.settings.studio.navBarMaterialSupportsGlassGlow(navigation.material)
    val navBgColor =
        when (navigation.material) {
            NavBarMaterial.STANDARD, NavBarMaterial.GRADIENT, NavBarMaterial.GLOW ->
                surfaceContainer.copy(alpha = (navigation.opacity * (1f - navigation.transparency)).coerceIn(0.18f, 1f))
            NavBarMaterial.FROSTED ->
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f)
            NavBarMaterial.TINTED ->
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
            NavBarMaterial.LIQUID_GLASS ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.56f)
            NavBarMaterial.SUKISU_GLASS ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.66f)
            NavBarMaterial.BITCHORD ->
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        }
    val glassBorderColor =
        when {
            isGlassMaterial && navigation.glassGlow ->
                primary.copy(alpha = (0.38f * navigation.glassGlowIntensity).coerceIn(0.14f, 0.85f))
            isGlassMaterial -> Color.White.copy(alpha = 0.30f)
            navigation.material == NavBarMaterial.FROSTED ->
                Color.White.copy(alpha = 0.22f)
            else -> Color.Transparent
        }
    val miniBgColor =
        when (navigation.miniPlayerBackground) {
            MiniPlayerBackgroundStyle.THEME, MiniPlayerBackgroundStyle.BITCHORD -> surfaceContainer.copy(alpha = 0.96f)
            MiniPlayerBackgroundStyle.TINTED -> moe.rukamori.archivetune.ui.component.tintedBottomBarColor()
            MiniPlayerBackgroundStyle.GRADIENT -> primary.copy(alpha = 0.85f)
            MiniPlayerBackgroundStyle.GLOW -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
            MiniPlayerBackgroundStyle.FROSTED -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.76f)
            MiniPlayerBackgroundStyle.LIQUID_GLASS, MiniPlayerBackgroundStyle.SUKISU_GLASS ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)
        }
    val miniContentTint =
        if (navigation.miniPlayerBackground.usesArtworkPalette) {
            Color.White
        } else {
            onSurfaceVariant
        }
    val fauxScreenBrush =
        Brush.verticalGradient(
            listOf(
                primary.copy(alpha = 0.30f),
                MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(212.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(fauxScreenBrush)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (behavior == NavigationBarCompactBehavior.ADAPTIVE) {
                            demoCompact = !demoCompact
                        }
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(primary.copy(alpha = 0.22f)),
                    )
                }
            }

            val miniPlayerHeight = lerp(34.dp, 30.dp, compactFraction).coerceAtLeast(0.dp)
            val navSlide = lerp(0.dp, 62.dp, compactFraction).coerceAtLeast(0.dp)
            val miniSideInset =
                lerp(
                    if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) 0.dp else 8.dp,
                    36.dp,
                    compactFraction,
                ).coerceAtLeast(0.dp)
            val miniCorner =
                if (navigation.miniPlayerStyle == MiniPlayerStyle.BAR) {
                    lerp(8.dp, 16.dp, compactFraction).coerceAtLeast(0.dp)
                } else {
                    16.dp
                }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = lerp(64.dp, 18.dp, compactFraction).coerceAtLeast(0.dp)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = miniSideInset)
                            .height(miniPlayerHeight)
                            .clip(RoundedCornerShape(miniCorner))
                            .background(miniBgColor)
                            .then(
                                if (navigation.miniPlayerBackground == MiniPlayerBackgroundStyle.LIQUID_GLASS ||
                                    navigation.miniPlayerBackground == MiniPlayerBackgroundStyle.FROSTED
                                ) {
                                    Modifier.border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.25f),
                                        RoundedCornerShape(miniCorner),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (navigation.miniPlayerBackground == MiniPlayerBackgroundStyle.GRADIENT) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
                                        } else {
                                            primary.copy(alpha = 0.55f)
                                        },
                                    ),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(miniContentTint.copy(alpha = 0.42f)),
                        )
                        Icon(
                            painter = painterResource(R.drawable.solar_play_linear),
                            contentDescription = null,
                            tint = miniContentTint,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .alpha((1f - compactFraction * 0.4f).coerceIn(0f, 1f)),
                        )
                    }
                }
                CompactPreviewCircle(
                    iconRes = Screens.Library.iconIdActive,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .alpha(compactFraction.coerceIn(0f, 1f)),
                    tint = navBgColor,
                    borderColor = glassBorderColor,
                    iconTint = onSurfaceVariant,
                )
                CompactPreviewCircle(
                    iconRes = R.drawable.search,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .alpha(compactFraction.coerceIn(0f, 1f)),
                    tint = navBgColor,
                    borderColor = glassBorderColor,
                    iconTint = onSurfaceVariant,
                )
            }

            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(
                            when (navigation.material) {
                                NavBarMaterial.SUKISU_GLASS,
                                NavBarMaterial.BITCHORD,
                                -> 0.88f
                                else -> navigation.widthFraction.coerceIn(0.6f, 0.94f)
                            },
                        )
                        .height(if (navigation.hideLabels) 38.dp else 46.dp)
                        .offset { IntOffset(x = 0, y = (navSlide.toPx()).roundToInt()) }
                        .graphicsLayer { alpha = (1f - compactFraction * 0.95f).coerceIn(0f, 1f) }
                        .then(
                            if (glassBorderColor != Color.Transparent) {
                                Modifier.border(1.dp, glassBorderColor, RoundedCornerShape(24.dp))
                            } else {
                                Modifier
                            },
                        ),
                shape = RoundedCornerShape(24.dp),
                color = navBgColor,
                shadowElevation = if (isGlassMaterial) 3.dp else 6.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val items = Screens.MainScreens
                    items.forEachIndexed { index, screen ->
                        val selected = index == 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (selected) screen.iconIdActive else screen.iconIdInactive,
                                ),
                                contentDescription = null,
                                tint = if (selected) primary else onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            if (!navigation.hideLabels) {
                                Text(
                                    text = stringResource(screen.titleId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) primary else onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.navigation_bar_tap_to_try),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun CompactPreviewCircle(
    iconRes: Int,
    modifier: Modifier = Modifier,
    tint: Color,
    borderColor: Color = Color.Transparent,
    iconTint: Color,
) {
    Box(
        modifier =
            modifier
                .size(28.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(tint)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(1.dp, borderColor, RoundedCornerShape(percent = 50))
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
        )
    }
}
