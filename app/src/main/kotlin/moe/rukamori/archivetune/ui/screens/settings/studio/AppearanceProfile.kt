/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import androidx.annotation.StringRes
import moe.rukamori.archivetune.ui.motion.MotionConfig
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.BackAnimationStyle
import moe.rukamori.archivetune.constants.BackAnimationStyleKey
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.BackgroundMotionPolicy
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.HapticIntensity
import moe.rukamori.archivetune.constants.HapticIntensityKey
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.HomeFeedStyleKey
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.constants.LiquidGlassNavBarEnabledKey
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsBackgroundStyleKey
import moe.rukamori.archivetune.constants.LyricsDisplayCharacter
import moe.rukamori.archivetune.constants.LyricsDisplayCharacterKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsLineSpacingKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.MiniPlayerGlassGlowKey
import moe.rukamori.archivetune.constants.MiniPlayerGlassGlowIntensityKey
import moe.rukamori.archivetune.constants.NavigationBarArtworkStyleKey
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyleKey
import moe.rukamori.archivetune.constants.MotionArtworkTransitionsKey
import moe.rukamori.archivetune.constants.MotionDecorativeBackgroundKey
import moe.rukamori.archivetune.constants.MotionPressFeedbackKey
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.MotionProfileKey
import moe.rukamori.archivetune.constants.MotionBlurEnabledKey
import moe.rukamori.archivetune.constants.MotionDurationPercentKey
import moe.rukamori.archivetune.constants.MotionDurationPercentRange
import moe.rukamori.archivetune.constants.MotionMenuStyleKey
import moe.rukamori.archivetune.constants.MotionScreenStyleKey
import moe.rukamori.archivetune.constants.MotionTransitionStyle
import moe.rukamori.archivetune.constants.MotionScreenTransitionsKey
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_CORNER_RADIUS_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_HEIGHT_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_LABEL_SPACING_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_OPACITY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_TRANSPARENCY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_WIDTH_DEFAULT
import moe.rukamori.archivetune.constants.NavigationBarBitchordKey
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.NavigationBarCompactBehaviorKey
import moe.rukamori.archivetune.constants.NavigationBarCornerRadiusKey
import moe.rukamori.archivetune.constants.NavigationBarFrostedBlurKey
import moe.rukamori.archivetune.constants.NavigationBarHeightKey
import moe.rukamori.archivetune.constants.NavigationBarLabelSpacingKey
import moe.rukamori.archivetune.constants.NavigationBarOpacityKey
import moe.rukamori.archivetune.constants.NavigationBarTintFrostedBlurKey
import moe.rukamori.archivetune.constants.NavigationBarTransparencyKey
import moe.rukamori.archivetune.constants.NavigationBarWidthKey
import moe.rukamori.archivetune.constants.PlayerBackgroundMotionPolicyKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.RandomThemeOnStartupKey
import moe.rukamori.archivetune.constants.RemovedPlayerDesignStyles
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowPlayerVolumeBarKey
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.constants.SukisuLiquidGlassNavBarKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.ui.component.NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT
import moe.rukamori.archivetune.ui.component.NavigationBarGlassGlowIntensityKey
import moe.rukamori.archivetune.ui.component.NavigationBarGlassGlowKey
import moe.rukamori.archivetune.ui.screens.settings.DarkMode

@Serializable
enum class AppearanceSection(
    @StringRes val titleRes: Int,
) {
    HOME(R.string.studio_section_home),
    NAVIGATION(R.string.studio_section_navigation),
    PLAYER(R.string.studio_section_player),
    LYRICS(R.string.studio_section_lyrics),
    COLOR(R.string.studio_section_color),
    MOTION(R.string.studio_section_motion),
}

@Serializable
enum class NavBarMaterial(
    @StringRes val labelRes: Int,
    val backgroundStyle: MiniPlayerBackgroundStyle,
) {
    STANDARD(R.string.follow_theme, MiniPlayerBackgroundStyle.THEME),
    GRADIENT(R.string.gradient, MiniPlayerBackgroundStyle.GRADIENT),
    GLOW(R.string.glow, MiniPlayerBackgroundStyle.GLOW),
    FROSTED(R.string.frosted_blur, MiniPlayerBackgroundStyle.FROSTED),
    TINTED(R.string.nav_material_tinted, MiniPlayerBackgroundStyle.TINTED),
    LIQUID_GLASS(R.string.nav_material_glass, MiniPlayerBackgroundStyle.LIQUID_GLASS),
    SUKISU_GLASS(R.string.nav_material_sukisu, MiniPlayerBackgroundStyle.SUKISU_GLASS),
    BITCHORD(R.string.nav_material_bitchord, MiniPlayerBackgroundStyle.BITCHORD);

    val artworkStyle: MiniPlayerBackgroundStyle?
        get() = if (this == GRADIENT || this == GLOW) backgroundStyle else null
}

internal fun MutablePreferences.writeNavigationBarMaterial(material: NavBarMaterial) {
    this[NavigationBarArtworkStyleKey] = (material.artworkStyle ?: MiniPlayerBackgroundStyle.THEME).name
    this[NavigationBarFrostedBlurKey] = material == NavBarMaterial.FROSTED
    this[NavigationBarTintFrostedBlurKey] = material == NavBarMaterial.TINTED
    this[LiquidGlassNavBarEnabledKey] = material == NavBarMaterial.LIQUID_GLASS
    this[SukisuLiquidGlassNavBarKey] = material == NavBarMaterial.SUKISU_GLASS
    this[NavigationBarBitchordKey] = material == NavBarMaterial.BITCHORD
}

@Serializable
@Immutable
data class HomeProfile(
    val homeFeedStyle: HomeFeedStyle = HomeFeedStyle.STANDARD,
    val showHomeCategoryChips: Boolean = true,
)

@Serializable
@Immutable
data class NavigationProfile(
    val material: NavBarMaterial = NavBarMaterial.STANDARD,
    val compactBehavior: NavigationBarCompactBehavior = NavigationBarCompactBehavior.ADAPTIVE,
    val hideLabels: Boolean = false,
    val widthFraction: Float = NAVIGATION_BAR_WIDTH_DEFAULT,
    val heightMultiplier: Float = NAVIGATION_BAR_HEIGHT_DEFAULT,
    val opacity: Float = NAVIGATION_BAR_OPACITY_DEFAULT,
    val transparency: Float = NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
    val labelSpacing: Float = NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
    val cornerRadius: Float = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
    val glassGlow: Boolean = true,
    val glassGlowIntensity: Float = NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
    val miniPlayerStyle: MiniPlayerStyle = MiniPlayerStyle.STANDARD,
    val miniPlayerBackground: MiniPlayerBackgroundStyle = MiniPlayerBackgroundStyle.THEME,
    val miniPlayerGlassGlow: Boolean = true,
    val miniPlayerGlassGlowIntensity: Float = NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
)

@Serializable
@Immutable
data class PlayerProfile(
    val designStyle: PlayerDesignStyle = PlayerDesignStyle.APPLE_MUSIC,
    val backgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    val sliderStyle: SliderStyle = SliderStyle.Standard,
    val showVolumeBar: Boolean = true,
    val hideThumbnail: Boolean = false,
    val cropToSquare: Boolean = false,
    val thumbnailCornerRadius: Float = 16f,
)

@Serializable
@Immutable
data class LyricsProfile(
    val mode: LyricsMode = LyricsMode.ENHANCED,
    val displayCharacter: LyricsDisplayCharacter = LyricsDisplayCharacter.FOCUS,
    val backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.DEFAULT,
    val lineBlur: Boolean = true,
    val textSize: Float = 26f,
    val lineSpacing: Float = 1.3f,
)

@Serializable
@Immutable
data class ColorProfile(
    val darkMode: DarkMode = DarkMode.AUTO,
    val pureBlack: Boolean = false,
    val dynamicTheme: Boolean = true,
    val customThemeColor: String = "default",
    val randomThemeOnStartup: Boolean = false,
    val backdropEnabled: Boolean = true,
    val backdropBlurAmount: Int = 60,
    val disableBlur: Boolean = false,
    val liquidGlassEnabled: Boolean = true,
    val fontPreference: AppFontPreference = AppFontPreference.DEFAULT,
    val backgroundMotionPolicy: BackgroundMotionPolicy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
)

@Serializable
@Immutable
data class MotionProfileState(
    val profile: MotionProfile = MotionProfile.SYSTEM,
    val durationPercent: Int = 100,
    val screenStyle: MotionTransitionStyle = MotionTransitionStyle.DEFAULT,
    val menuStyle: MotionTransitionStyle = MotionTransitionStyle.DEFAULT,
    val motionBlur: Boolean = false,
    val disableAnimations: Boolean = false,
    val screenTransitions: Boolean = true,
    val artworkTransitions: Boolean = true,
    val pressFeedback: Boolean = true,
    val decorativeBackground: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.STANDARD,
    val hapticsEnabled: Boolean = true,
    val forceHighRefreshRate: Boolean = false,
    val backAnimationStyle: BackAnimationStyle = BackAnimationStyle.DEFAULT,
)

internal fun MotionProfileState.toMotionConfig(systemAnimatorScale: Float): MotionConfig =
    MotionConfig(
        profile = profile,
        legacyDisableAnimations = disableAnimations,
        systemAnimatorScale = systemAnimatorScale,
        screenTransitionsChannel = screenTransitions,
        artworkMotionChannel = artworkTransitions,
        pressFeedbackChannel = pressFeedback,
        decorativeBackgroundChannel = decorativeBackground,
        durationPercent = durationPercent,
        screenTransitionStyle = screenStyle,
        menuTransitionStyle = menuStyle,
        motionBlurChannel = motionBlur,
    )

fun playerStyleSupportsCustomBackground(style: PlayerDesignStyle): Boolean =
    style in setOf(
        PlayerDesignStyle.V1,
        PlayerDesignStyle.V2,
        PlayerDesignStyle.V3,
        PlayerDesignStyle.V4,
        PlayerDesignStyle.V5,
        PlayerDesignStyle.V6,
    )

fun playerStyleSupportsVolumeBar(style: PlayerDesignStyle): Boolean =
    style == PlayerDesignStyle.V7 ||
        style == PlayerDesignStyle.V8 ||
        style == PlayerDesignStyle.BITCHORD

fun playerStyleUsesOwnFullscreenLyrics(style: PlayerDesignStyle): Boolean =
    style == PlayerDesignStyle.APPLE_MUSIC ||
        style == PlayerDesignStyle.TIKTOK ||
        style == PlayerDesignStyle.SPATIALFLOW

@StringRes
fun playerDesignStyleLabelRes(style: PlayerDesignStyle): Int =
    when (style) {
        PlayerDesignStyle.V1 -> R.string.player_design_v1
        PlayerDesignStyle.V2 -> R.string.player_design_v2
        PlayerDesignStyle.V3 -> R.string.player_design_v3
        PlayerDesignStyle.V4 -> R.string.player_design_v4
        PlayerDesignStyle.V5 -> R.string.player_design_v5
        PlayerDesignStyle.V6 -> R.string.player_design_v6
        PlayerDesignStyle.V7 -> R.string.player_design_v7
        PlayerDesignStyle.V8 -> R.string.player_design_v8
        PlayerDesignStyle.V9 -> R.string.player_design_v9
        PlayerDesignStyle.V10 -> R.string.player_design_v10
        PlayerDesignStyle.APPLE_MUSIC -> R.string.player_design_apple_music
        PlayerDesignStyle.BITCHORD -> R.string.player_design_bitchord
        PlayerDesignStyle.TIKTOK -> R.string.player_design_tiktok
        PlayerDesignStyle.SIMPMUSIC -> R.string.player_design_simpmusic
        PlayerDesignStyle.SPATIALFLOW -> R.string.player_design_spatialflow
        PlayerDesignStyle.LOOPER -> R.string.player_design_looper
    }

@StringRes
fun playerBackgroundStyleLabelRes(style: PlayerBackgroundStyle): Int =
    when (style) {
        PlayerBackgroundStyle.DEFAULT -> R.string.follow_theme
        PlayerBackgroundStyle.GRADIENT -> R.string.gradient
        PlayerBackgroundStyle.CUSTOM -> R.string.custom
        PlayerBackgroundStyle.BLUR -> R.string.player_background_blur
        PlayerBackgroundStyle.COLORING -> R.string.coloring
        PlayerBackgroundStyle.BLUR_GRADIENT -> R.string.blur_gradient
        PlayerBackgroundStyle.GLOW -> R.string.glow
        PlayerBackgroundStyle.GLOW_ANIMATED -> R.string.player_background_glow_animated
    }

@StringRes
fun miniPlayerBackgroundStyleLabelRes(style: MiniPlayerBackgroundStyle): Int =
    when (style) {
        MiniPlayerBackgroundStyle.THEME -> R.string.follow_theme
        MiniPlayerBackgroundStyle.GRADIENT -> R.string.gradient
        MiniPlayerBackgroundStyle.GLOW -> R.string.glow
        MiniPlayerBackgroundStyle.FROSTED -> R.string.frosted_blur
        MiniPlayerBackgroundStyle.LIQUID_GLASS -> R.string.liquid_glass
        MiniPlayerBackgroundStyle.TINTED -> R.string.nav_material_tinted
        MiniPlayerBackgroundStyle.SUKISU_GLASS -> R.string.nav_material_sukisu
        MiniPlayerBackgroundStyle.BITCHORD -> R.string.nav_material_bitchord
    }

@StringRes
fun lyricsModeLabelRes(mode: LyricsMode): Int =
    when (mode) {
        LyricsMode.V2 -> R.string.lyrics_mode_v2
        LyricsMode.ENHANCED -> R.string.lyrics_mode_enhanced
        LyricsMode.SPOTIFY -> R.string.lyrics_mode_spotify
    }

@StringRes
fun lyricsBackgroundStyleLabelRes(style: LyricsBackgroundStyle): Int =
    when (style) {
        LyricsBackgroundStyle.DEFAULT -> R.string.lyrics_background_default
        LyricsBackgroundStyle.FOLLOW_THEME -> R.string.follow_theme
        LyricsBackgroundStyle.COLORING -> R.string.coloring
        LyricsBackgroundStyle.MOVING_BLUR -> R.string.lyrics_background_moving_blur
        LyricsBackgroundStyle.CUSTOM -> R.string.custom
    }

fun navBarMaterialSupportsGlassGlow(material: NavBarMaterial): Boolean =
    material == NavBarMaterial.LIQUID_GLASS || material == NavBarMaterial.SUKISU_GLASS

fun navBarMaterialLocksWidth(material: NavBarMaterial): Boolean =
    material == NavBarMaterial.SUKISU_GLASS || material == NavBarMaterial.BITCHORD

fun navBarMaterialLocksHeight(material: NavBarMaterial): Boolean =
    material == NavBarMaterial.LIQUID_GLASS || material == NavBarMaterial.SUKISU_GLASS

fun navBarMaterialLocksOpacity(material: NavBarMaterial): Boolean =
    material == NavBarMaterial.LIQUID_GLASS ||
        material == NavBarMaterial.SUKISU_GLASS ||
        material == NavBarMaterial.BITCHORD ||
        material == NavBarMaterial.FROSTED

fun navBarMaterialLocksCornerRadius(material: NavBarMaterial): Boolean =
    material == NavBarMaterial.LIQUID_GLASS ||
        material == NavBarMaterial.SUKISU_GLASS ||
        material == NavBarMaterial.BITCHORD

@Serializable
@Immutable
data class AppearanceProfileSnapshot(
    val home: HomeProfile = HomeProfile(),
    val navigation: NavigationProfile = NavigationProfile(),
    val player: PlayerProfile = PlayerProfile(),
    val lyrics: LyricsProfile = LyricsProfile(),
    val color: ColorProfile = ColorProfile(),
    val motion: MotionProfileState = MotionProfileState(),
) {
    fun normalized(): AppearanceProfileSnapshot {
        val resolvedDesign =
            if (player.designStyle in RemovedPlayerDesignStyles) {
                PlayerDesignStyle.APPLE_MUSIC
            } else {
                player.designStyle
            }

        return copy(
            navigation =
                navigation.copy(
                    widthFraction = navigation.widthFraction.coerceIn(0.5f, 1.0f),
                    heightMultiplier = navigation.heightMultiplier.coerceIn(0.8f, 1.4f),
                    opacity = navigation.opacity.coerceIn(0.2f, 1.0f),
                    transparency = navigation.transparency.coerceIn(0.0f, 0.95f),
                    labelSpacing = navigation.labelSpacing.coerceIn(0f, 16f),
                    cornerRadius = navigation.cornerRadius.coerceIn(0f, 48f),
                    glassGlowIntensity = navigation.glassGlowIntensity.coerceIn(0.2f, 2f),
                    miniPlayerGlassGlowIntensity = navigation.miniPlayerGlassGlowIntensity.coerceIn(0.2f, 2f),
                ),
            player =
                player.copy(
                    designStyle = resolvedDesign,
                    backgroundStyle = player.backgroundStyle,
                    thumbnailCornerRadius = player.thumbnailCornerRadius.coerceIn(0f, 48f),
                ),
            lyrics =
                lyrics.copy(
                    textSize = lyrics.textSize.coerceIn(14f, 42f),
                    lineSpacing = lyrics.lineSpacing.coerceIn(1.0f, 2.0f),
                ),
            color =
                color.copy(
                    backdropBlurAmount = color.backdropBlurAmount.coerceIn(0, 100),
                ),
            motion = motion.copy(durationPercent = motion.durationPercent.coerceIn(MotionDurationPercentRange)),
        )
    }

    fun resetSection(
        section: AppearanceSection,
        isLowRamDevice: Boolean = false,
    ): AppearanceProfileSnapshot {
        val defaults = defaultSnapshot(isLowRamDevice)
        return when (section) {
            AppearanceSection.HOME -> copy(home = defaults.home)
            AppearanceSection.NAVIGATION -> copy(navigation = defaults.navigation)
            AppearanceSection.PLAYER -> copy(player = defaults.player)
            AppearanceSection.LYRICS -> copy(lyrics = defaults.lyrics)
            AppearanceSection.COLOR -> copy(color = defaults.color)
            AppearanceSection.MOTION -> copy(motion = defaults.motion)
        }
    }

    fun diffSections(other: AppearanceProfileSnapshot): List<AppearanceSection> {
        val a = normalized()
        val b = other.normalized()
        return buildList {
            if (a.home != b.home) add(AppearanceSection.HOME)
            if (a.navigation != b.navigation) add(AppearanceSection.NAVIGATION)
            if (a.player != b.player) add(AppearanceSection.PLAYER)
            if (a.lyrics != b.lyrics) add(AppearanceSection.LYRICS)
            if (a.color != b.color) add(AppearanceSection.COLOR)
            if (a.motion != b.motion) add(AppearanceSection.MOTION)
        }
    }

    fun writeToPreferences(prefs: MutablePreferences) {
        val safe = normalized()
        // Home
        prefs[HomeFeedStyleKey] = safe.home.homeFeedStyle.name
        prefs[ShowHomeCategoryChipsKey] = safe.home.showHomeCategoryChips

        // Navigation & MiniPlayer
        prefs.writeNavigationBarMaterial(safe.navigation.material)
        prefs[MiniPlayerGlassGlowKey] = safe.navigation.miniPlayerGlassGlow
        prefs[MiniPlayerGlassGlowIntensityKey] = safe.navigation.miniPlayerGlassGlowIntensity
        prefs[NavigationBarCompactBehaviorKey] = safe.navigation.compactBehavior.name
        prefs[HideNavigationBarLabelsKey] = safe.navigation.hideLabels
        prefs[NavigationBarWidthKey] = safe.navigation.widthFraction
        prefs[NavigationBarHeightKey] = safe.navigation.heightMultiplier
        prefs[NavigationBarOpacityKey] = safe.navigation.opacity
        prefs[NavigationBarTransparencyKey] = safe.navigation.transparency
        prefs[NavigationBarLabelSpacingKey] = safe.navigation.labelSpacing
        prefs[NavigationBarCornerRadiusKey] = safe.navigation.cornerRadius
        prefs[NavigationBarGlassGlowKey] = safe.navigation.glassGlow
        prefs[NavigationBarGlassGlowIntensityKey] = safe.navigation.glassGlowIntensity
        prefs[MiniPlayerStyleKey] = safe.navigation.miniPlayerStyle.name
        prefs[MiniPlayerBackgroundStyleKey] = safe.navigation.miniPlayerBackground.name

        // Player
        prefs[PlayerDesignStyleKey] = safe.player.designStyle.name
        prefs[PlayerBackgroundStyleKey] = safe.player.backgroundStyle.name
        prefs[SliderStyleKey] = safe.player.sliderStyle.name
        prefs[ShowPlayerVolumeBarKey] = safe.player.showVolumeBar
        prefs[HidePlayerThumbnailKey] = safe.player.hideThumbnail
        prefs[CropThumbnailToSquareKey] = safe.player.cropToSquare
        prefs[ThumbnailCornerRadiusKey] = safe.player.thumbnailCornerRadius

        // Lyrics
        prefs[LyricsModeKey] = safe.lyrics.mode.name
        prefs[LyricsDisplayCharacterKey] = safe.lyrics.displayCharacter.name
        prefs[LyricsBackgroundStyleKey] = safe.lyrics.backgroundStyle.name
        prefs[LyricsLineBlurKey] = safe.lyrics.lineBlur
        prefs[LyricsTextSizeKey] = safe.lyrics.textSize
        prefs[LyricsLineSpacingKey] = safe.lyrics.lineSpacing

        // Color & Background
        prefs[DarkModeKey] = safe.color.darkMode.name
        prefs[PureBlackKey] = safe.color.pureBlack
        prefs[DynamicThemeKey] = safe.color.dynamicTheme
        prefs[CustomThemeColorKey] = safe.color.customThemeColor
        prefs[RandomThemeOnStartupKey] = safe.color.randomThemeOnStartup
        prefs[BackdropEnabledKey] = safe.color.backdropEnabled
        prefs[BackdropBlurAmountKey] = safe.color.backdropBlurAmount
        prefs[DisableBlurKey] = safe.color.disableBlur
        prefs[LiquidGlassEnabledKey] = safe.color.liquidGlassEnabled
        prefs[FontPreferenceKey] = safe.color.fontPreference.name
        prefs[PlayerBackgroundMotionPolicyKey] = safe.color.backgroundMotionPolicy.name

        // Motion
        prefs[MotionProfileKey] = safe.motion.profile.name
        prefs[MotionDurationPercentKey] = safe.motion.durationPercent
        prefs[MotionScreenStyleKey] = safe.motion.screenStyle.name
        prefs[MotionMenuStyleKey] = safe.motion.menuStyle.name
        prefs[MotionBlurEnabledKey] = safe.motion.motionBlur
        prefs[DisableAnimationsKey] = safe.motion.disableAnimations
        prefs[MotionScreenTransitionsKey] = safe.motion.screenTransitions
        prefs[MotionArtworkTransitionsKey] = safe.motion.artworkTransitions
        prefs[MotionPressFeedbackKey] = safe.motion.pressFeedback
        prefs[MotionDecorativeBackgroundKey] = safe.motion.decorativeBackground
        prefs[HapticIntensityKey] = safe.motion.hapticIntensity.name
        prefs[EnableHapticFeedbackKey] = safe.motion.hapticsEnabled
        prefs[ForceHighRefreshRateKey] = safe.motion.forceHighRefreshRate
        prefs[BackAnimationStyleKey] = safe.motion.backAnimationStyle.name
    }

    companion object {
        private val draftJson =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun encodeDraftJson(snapshot: AppearanceProfileSnapshot): String =
            draftJson.encodeToString(snapshot.normalized())

        fun decodeDraftJson(raw: String): AppearanceProfileSnapshot? =
            runCatching {
                draftJson.decodeFromString<AppearanceProfileSnapshot>(raw).normalized()
            }.getOrNull()

        fun defaultSnapshot(isLowRamDevice: Boolean = false): AppearanceProfileSnapshot =
            AppearanceProfileSnapshot(
                motion =
                    MotionProfileState(
                        profile = if (isLowRamDevice) MotionProfile.REDUCED else MotionProfile.SYSTEM,
                        disableAnimations = isLowRamDevice,
                    ),
            )

        fun fromPreferences(
            prefs: Preferences?,
            isLowRamDevice: Boolean = false,
        ): AppearanceProfileSnapshot {
            if (prefs == null) return defaultSnapshot(isLowRamDevice)
            val material =
                when {
                    prefs[SukisuLiquidGlassNavBarKey] == true -> NavBarMaterial.SUKISU_GLASS
                    prefs[LiquidGlassNavBarEnabledKey] == true -> NavBarMaterial.LIQUID_GLASS
                    prefs[NavigationBarBitchordKey] == true -> NavBarMaterial.BITCHORD
                    prefs[NavigationBarTintFrostedBlurKey] == true -> NavBarMaterial.TINTED
                    prefs[NavigationBarFrostedBlurKey] == true -> NavBarMaterial.FROSTED
                    prefs[NavigationBarArtworkStyleKey] == MiniPlayerBackgroundStyle.GRADIENT.name -> NavBarMaterial.GRADIENT
                    prefs[NavigationBarArtworkStyleKey] == MiniPlayerBackgroundStyle.GLOW.name -> NavBarMaterial.GLOW
                    else -> NavBarMaterial.STANDARD
                }
            return AppearanceProfileSnapshot(
                home =
                    HomeProfile(
                        homeFeedStyle = parseEnum(prefs[HomeFeedStyleKey], HomeFeedStyle.STANDARD),
                        showHomeCategoryChips = prefs[ShowHomeCategoryChipsKey] ?: true,
                    ),
                navigation =
                    NavigationProfile(
                        material = material,
                        compactBehavior =
                            parseEnum(
                                prefs[NavigationBarCompactBehaviorKey],
                                NavigationBarCompactBehavior.ADAPTIVE,
                            ),
                        hideLabels = prefs[HideNavigationBarLabelsKey] ?: false,
                        widthFraction = prefs[NavigationBarWidthKey] ?: NAVIGATION_BAR_WIDTH_DEFAULT,
                        heightMultiplier = prefs[NavigationBarHeightKey] ?: NAVIGATION_BAR_HEIGHT_DEFAULT,
                        opacity = prefs[NavigationBarOpacityKey] ?: NAVIGATION_BAR_OPACITY_DEFAULT,
                        transparency =
                            prefs[NavigationBarTransparencyKey] ?: NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
                        labelSpacing =
                            prefs[NavigationBarLabelSpacingKey] ?: NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
                        cornerRadius =
                            prefs[NavigationBarCornerRadiusKey] ?: NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
                        glassGlow = prefs[NavigationBarGlassGlowKey] ?: true,
                        glassGlowIntensity =
                            prefs[NavigationBarGlassGlowIntensityKey]
                                ?: NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
                        miniPlayerStyle = parseEnum(prefs[MiniPlayerStyleKey], MiniPlayerStyle.STANDARD),
                        miniPlayerBackground =
                            parseEnum(prefs[MiniPlayerBackgroundStyleKey], MiniPlayerBackgroundStyle.THEME),
                        miniPlayerGlassGlow = prefs[MiniPlayerGlassGlowKey] ?: true,
                        miniPlayerGlassGlowIntensity =
                            prefs[MiniPlayerGlassGlowIntensityKey] ?: NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT,
                    ),
                player =
                    PlayerProfile(
                        designStyle = parseEnum(prefs[PlayerDesignStyleKey], PlayerDesignStyle.APPLE_MUSIC),
                        backgroundStyle =
                            parseEnum(prefs[PlayerBackgroundStyleKey], PlayerBackgroundStyle.DEFAULT),
                        sliderStyle = parseEnum(prefs[SliderStyleKey], SliderStyle.Standard),
                        showVolumeBar = prefs[ShowPlayerVolumeBarKey] ?: true,
                        hideThumbnail = prefs[HidePlayerThumbnailKey] ?: false,
                        cropToSquare = prefs[CropThumbnailToSquareKey] ?: false,
                        thumbnailCornerRadius = prefs[ThumbnailCornerRadiusKey] ?: 16f,
                    ),
                lyrics =
                    LyricsProfile(
                        mode = parseEnum(prefs[LyricsModeKey], LyricsMode.ENHANCED),
                        displayCharacter =
                            parseEnum(prefs[LyricsDisplayCharacterKey], LyricsDisplayCharacter.FOCUS),
                        backgroundStyle =
                            parseEnum(prefs[LyricsBackgroundStyleKey], LyricsBackgroundStyle.DEFAULT),
                        lineBlur = prefs[LyricsLineBlurKey] ?: true,
                        textSize = prefs[LyricsTextSizeKey] ?: 26f,
                        lineSpacing = prefs[LyricsLineSpacingKey] ?: 1.3f,
                    ),
                color =
                    ColorProfile(
                        darkMode = parseEnum(prefs[DarkModeKey], DarkMode.AUTO),
                        pureBlack = prefs[PureBlackKey] ?: false,
                        dynamicTheme = prefs[DynamicThemeKey] ?: true,
                        customThemeColor = prefs[CustomThemeColorKey] ?: "default",
                        randomThemeOnStartup = prefs[RandomThemeOnStartupKey] ?: false,
                        backdropEnabled = prefs[BackdropEnabledKey] ?: true,
                        backdropBlurAmount = prefs[BackdropBlurAmountKey] ?: 60,
                        disableBlur = prefs[DisableBlurKey] ?: false,
                        liquidGlassEnabled = prefs[LiquidGlassEnabledKey] ?: true,
                        fontPreference = parseEnum(prefs[FontPreferenceKey], AppFontPreference.DEFAULT),
                        backgroundMotionPolicy =
                            parseEnum(
                                prefs[PlayerBackgroundMotionPolicyKey],
                                BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
                            ),
                    ),
                motion =
                    MotionProfileState(
                        profile = parseEnum(prefs[MotionProfileKey], MotionProfile.SYSTEM),
                        durationPercent = prefs[MotionDurationPercentKey] ?: 100,
                        screenStyle = parseEnum(prefs[MotionScreenStyleKey], MotionTransitionStyle.DEFAULT),
                        menuStyle = parseEnum(prefs[MotionMenuStyleKey], MotionTransitionStyle.DEFAULT),
                        motionBlur = prefs[MotionBlurEnabledKey] ?: false,
                        disableAnimations = prefs[DisableAnimationsKey] ?: isLowRamDevice,
                        screenTransitions = prefs[MotionScreenTransitionsKey] ?: true,
                        artworkTransitions = prefs[MotionArtworkTransitionsKey] ?: true,
                        pressFeedback = prefs[MotionPressFeedbackKey] ?: true,
                        decorativeBackground = prefs[MotionDecorativeBackgroundKey] ?: true,
                        hapticIntensity = parseEnum(prefs[HapticIntensityKey], HapticIntensity.STANDARD),
                        hapticsEnabled = prefs[EnableHapticFeedbackKey] ?: true,
                        forceHighRefreshRate = prefs[ForceHighRefreshRateKey] ?: false,
                        backAnimationStyle = parseEnum(prefs[BackAnimationStyleKey], BackAnimationStyle.DEFAULT),
                    ),
            ).normalized()
        }
    }
}

@Immutable
data class NavMiniPlayerPreset(
    val id: String,
    @StringRes val titleRes: Int,
    val navigation: NavigationProfile,
)

val NavMiniPlayerStarterPresets: List<NavMiniPlayerPreset> =
    listOf(
        NavMiniPlayerPreset(
            id = "floating",
            titleRes = R.string.nav_preset_floating,
            navigation =
                NavigationProfile(
                    material = NavBarMaterial.LIQUID_GLASS,
                    compactBehavior = NavigationBarCompactBehavior.ADAPTIVE,
                    hideLabels = true,
                    widthFraction = 0.78f,
                    heightMultiplier = 1.0f,
                    cornerRadius = 28f,
                    miniPlayerStyle = MiniPlayerStyle.STANDARD,
                ),
        ),
        NavMiniPlayerPreset(
            id = "compact",
            titleRes = R.string.nav_preset_compact,
            navigation =
                NavigationProfile(
                    material = NavBarMaterial.STANDARD,
                    compactBehavior = NavigationBarCompactBehavior.ALWAYS_COMPACT,
                    hideLabels = true,
                    widthFraction = 0.92f,
                    heightMultiplier = 0.85f,
                    cornerRadius = 20f,
                    miniPlayerStyle = MiniPlayerStyle.BAR,
                ),
        ),
        NavMiniPlayerPreset(
            id = "frosted",
            titleRes = R.string.nav_preset_frosted,
            navigation =
                NavigationProfile(
                    material = NavBarMaterial.FROSTED,
                    compactBehavior = NavigationBarCompactBehavior.ADAPTIVE,
                    hideLabels = false,
                    widthFraction = 0.88f,
                    heightMultiplier = 1.05f,
                    cornerRadius = 24f,
                    miniPlayerStyle = MiniPlayerStyle.STANDARD,
                ),
        ),
        NavMiniPlayerPreset(
            id = "contrast",
            titleRes = R.string.nav_preset_contrast,
            navigation =
                NavigationProfile(
                    material = NavBarMaterial.TINTED,
                    compactBehavior = NavigationBarCompactBehavior.ALWAYS_EXPANDED,
                    hideLabels = false,
                    widthFraction = 1.0f,
                    heightMultiplier = 1.15f,
                    opacity = 0.96f,
                    transparency = 0.05f,
                    cornerRadius = 12f,
                    miniPlayerStyle = MiniPlayerStyle.BAR,
                ),
        ),
    )

@Immutable
data class StudioStarterPreset(
    val id: String,
    val titleEn: String,
    val titleRu: String,
    val snapshot: AppearanceProfileSnapshot,
) {
    fun localizedTitle(isRussian: Boolean): String = if (isRussian) titleRu else titleEn
}

val StudioStarterPresets: List<StudioStarterPreset> =
    listOf(
        StudioStarterPreset(
            id = "now_floating",
            titleEn = "Now · Floating",
            titleRu = "Сейчас · Воздушный",
            snapshot =
                AppearanceProfileSnapshot(
                    home = HomeProfile(homeFeedStyle = HomeFeedStyle.NOW, showHomeCategoryChips = true),
                    navigation = NavMiniPlayerStarterPresets[0].navigation,
                    player =
                        PlayerProfile(
                            designStyle = PlayerDesignStyle.APPLE_MUSIC,
                            backgroundStyle = PlayerBackgroundStyle.GRADIENT,
                            sliderStyle = SliderStyle.Wavy,
                            cropToSquare = true,
                            thumbnailCornerRadius = 20f,
                        ),
                    lyrics = LyricsProfile(displayCharacter = LyricsDisplayCharacter.STAGE, lineBlur = true),
                    color =
                        ColorProfile(
                            darkMode = DarkMode.AUTO,
                            backdropEnabled = true,
                            backgroundMotionPolicy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
                        ),
                    motion = MotionProfileState(profile = MotionProfile.SMOOTH, hapticIntensity = HapticIntensity.STANDARD),
                ),
        ),
        StudioStarterPreset(
            id = "covers_editorial",
            titleEn = "Covers · Gallery",
            titleRu = "Обложки · Журнал",
            snapshot =
                AppearanceProfileSnapshot(
                    home = HomeProfile(homeFeedStyle = HomeFeedStyle.COVERS, showHomeCategoryChips = false),
                    navigation = NavMiniPlayerStarterPresets[2].navigation,
                    player =
                        PlayerProfile(
                            designStyle = PlayerDesignStyle.SPATIALFLOW,
                            backgroundStyle = PlayerBackgroundStyle.BLUR_GRADIENT,
                            sliderStyle = SliderStyle.Thick,
                            thumbnailCornerRadius = 14f,
                        ),
                    lyrics = LyricsProfile(displayCharacter = LyricsDisplayCharacter.FOCUS, textSize = 27f),
                    color = ColorProfile(darkMode = DarkMode.ON, pureBlack = false, dynamicTheme = false, customThemeColor = "ocean_blue"),
                    motion = MotionProfileState(profile = MotionProfile.FAST, hapticIntensity = HapticIntensity.LIGHT),
                ),
        ),
        StudioStarterPreset(
            id = "compact_contrast",
            titleEn = "Compact · Bar",
            titleRu = "Компакт · Строка",
            snapshot =
                AppearanceProfileSnapshot(
                    home = HomeProfile(homeFeedStyle = HomeFeedStyle.COMPACT, showHomeCategoryChips = true),
                    navigation = NavMiniPlayerStarterPresets[1].navigation,
                    player =
                        PlayerProfile(
                            designStyle = PlayerDesignStyle.BITCHORD,
                            backgroundStyle = PlayerBackgroundStyle.DEFAULT,
                            sliderStyle = SliderStyle.Simple,
                            thumbnailCornerRadius = 10f,
                        ),
                    lyrics = LyricsProfile(displayCharacter = LyricsDisplayCharacter.READING, lineBlur = false, textSize = 22f),
                    color = ColorProfile(darkMode = DarkMode.ON, pureBlack = true, disableBlur = false),
                    motion = MotionProfileState(profile = MotionProfile.FAST, hapticIntensity = HapticIntensity.STANDARD),
                ),
        ),
        StudioStarterPreset(
            id = "quiet_minimal",
            titleEn = "Quiet · Minimal",
            titleRu = "Спокойный · Минимум",
            snapshot =
                AppearanceProfileSnapshot(
                    home = HomeProfile(homeFeedStyle = HomeFeedStyle.STANDARD, showHomeCategoryChips = false),
                    navigation = NavMiniPlayerStarterPresets[3].navigation,
                    player =
                        PlayerProfile(
                            designStyle = PlayerDesignStyle.APPLE_MUSIC,
                            backgroundStyle = PlayerBackgroundStyle.DEFAULT,
                            sliderStyle = SliderStyle.Standard,
                            thumbnailCornerRadius = 12f,
                        ),
                    lyrics = LyricsProfile(displayCharacter = LyricsDisplayCharacter.READING, lineBlur = false),
                    color =
                        ColorProfile(
                            darkMode = DarkMode.AUTO,
                            backdropEnabled = false,
                            disableBlur = true,
                            backgroundMotionPolicy = BackgroundMotionPolicy.NEVER,
                        ),
                    motion =
                        MotionProfileState(
                            profile = MotionProfile.REDUCED,
                            disableAnimations = true,
                            screenTransitions = false,
                            artworkTransitions = false,
                            pressFeedback = false,
                            decorativeBackground = false,
                            hapticIntensity = HapticIntensity.LIGHT,
                        ),
                ),
        ),
    )

@Serializable
@Immutable
data class SavedAppearanceProfile(
    val id: String,
    val name: String,
    val updatedAtEpochMs: Long,
    val snapshot: AppearanceProfileSnapshot,
)

object SavedProfilesStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun decode(raw: String?): List<SavedAppearanceProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<SavedAppearanceProfile>>(raw).map {
                it.copy(snapshot = it.snapshot.normalized())
            }
        }.getOrDefault(emptyList())
    }

    fun encode(profiles: List<SavedAppearanceProfile>): String = json.encodeToString(profiles)

    fun saveNew(
        existing: List<SavedAppearanceProfile>,
        name: String,
        snapshot: AppearanceProfileSnapshot,
        id: String,
        nowMs: Long,
    ): List<SavedAppearanceProfile> =
        existing +
            SavedAppearanceProfile(
                id = id,
                name = name.trim().ifBlank { "My style" },
                updatedAtEpochMs = nowMs,
                snapshot = snapshot.normalized(),
            )

    fun rename(
        existing: List<SavedAppearanceProfile>,
        id: String,
        newName: String,
        nowMs: Long,
    ): List<SavedAppearanceProfile> {
        val clean = newName.trim()
        if (clean.isEmpty()) return existing
        return existing.map { profile ->
            if (profile.id == id) profile.copy(name = clean, updatedAtEpochMs = nowMs) else profile
        }
    }

    fun duplicate(
        existing: List<SavedAppearanceProfile>,
        sourceId: String,
        newId: String,
        copySuffix: String,
        nowMs: Long,
    ): List<SavedAppearanceProfile> {
        val source = existing.firstOrNull { it.id == sourceId } ?: return existing
        return existing +
            source.copy(
                id = newId,
                name = "${source.name} $copySuffix",
                updatedAtEpochMs = nowMs,
            )
    }

    fun delete(
        existing: List<SavedAppearanceProfile>,
        id: String,
    ): List<SavedAppearanceProfile> = existing.filterNot { it.id == id }
}

private inline fun <reified T : Enum<T>> parseEnum(
    raw: String?,
    defaultValue: T,
): T =
    if (raw.isNullOrBlank()) {
        defaultValue
    } else {
        runCatching { enumValueOf<T>(raw) }.getOrDefault(defaultValue)
    }
