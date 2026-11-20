/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AiContentFilterEnabledKey
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.BackgroundMotionPolicy
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.HapticIntensity
import moe.rukamori.archivetune.constants.HapticIntensityKey
import moe.rukamori.archivetune.constants.HideCachedCardKey
import moe.rukamori.archivetune.constants.HideLikedSongsCardKey
import moe.rukamori.archivetune.constants.HideLocalFilesCardKey
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.HideOfflineCardKey
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.HideTop50CardKey
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.HomeFeedStyleKey
import moe.rukamori.archivetune.constants.HomeHiddenSectionsKey
import moe.rukamori.archivetune.constants.HomeSectionOrderKey
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
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyleKey
import moe.rukamori.archivetune.constants.MotionArtworkTransitionsKey
import moe.rukamori.archivetune.constants.MotionDecorativeBackgroundKey
import moe.rukamori.archivetune.constants.MotionPressFeedbackKey
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.MotionProfileKey
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
import moe.rukamori.archivetune.constants.ShowCastInPlayerMenuKey
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowPlayerVolumeBarKey
import moe.rukamori.archivetune.constants.ShowTagsInLibraryKey
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.constants.SukisuLiquidGlassNavBarKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.utils.isLowRamDevice
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface SettingValueText {
    data class Resource(@StringRes val resId: Int) : SettingValueText

    data class ResourceInt(
        @StringRes val resId: Int,
        val arg: Int,
    ) : SettingValueText

    data class Plain(val value: String) : SettingValueText

    fun format(context: Context): String =
        when (this) {
            is Resource -> context.getString(resId)
            is ResourceInt -> context.getString(resId, arg)
            is Plain -> value
        }
}

data class ModifiedSettingEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val categoryRes: Int,
    val route: String,
    val currentValue: SettingValueText,
    val defaultValue: SettingValueText,
    val reset: MutablePreferences.() -> Unit,
)

private interface SettingSpec {
    val id: String
    val titleRes: Int
    val categoryRes: Int
    val route: String

    fun evaluate(
        prefs: Preferences?,
        isLowRamDevice: Boolean,
    ): ModifiedSettingEntry?

    fun reset(mutablePreferences: MutablePreferences)
}

private fun booleanSpec(
    key: Preferences.Key<Boolean>,
    id: String = key.name,
    @StringRes titleRes: Int,
    @StringRes categoryRes: Int,
    route: String,
    defaultProvider: (isLowRamDevice: Boolean) -> Boolean,
): SettingSpec =
    object : SettingSpec {
        override val id: String = id
        override val titleRes: Int = titleRes
        override val categoryRes: Int = categoryRes
        override val route: String = route

        override fun evaluate(
            prefs: Preferences?,
            isLowRamDevice: Boolean,
        ): ModifiedSettingEntry? {
            val defaultValue = defaultProvider(isLowRamDevice)
            val currentValue = prefs?.get(key) ?: return null
            if (currentValue == defaultValue) return null
            return ModifiedSettingEntry(
                id = id,
                titleRes = titleRes,
                categoryRes = categoryRes,
                route = route,
                currentValue = boolValueText(currentValue),
                defaultValue = boolValueText(defaultValue),
                reset = { remove(key) },
            )
        }

        override fun reset(mutablePreferences: MutablePreferences) {
            mutablePreferences.remove(key)
        }
    }

private fun floatSpec(
    key: Preferences.Key<Float>,
    @StringRes titleRes: Int,
    @StringRes categoryRes: Int,
    route: String,
    defaultValue: Float,
    formatter: (Float) -> SettingValueText,
    tolerance: Float = 0.005f,
): SettingSpec =
    object : SettingSpec {
        override val id: String = key.name
        override val titleRes: Int = titleRes
        override val categoryRes: Int = categoryRes
        override val route: String = route

        override fun evaluate(
            prefs: Preferences?,
            isLowRamDevice: Boolean,
        ): ModifiedSettingEntry? {
            val currentValue = prefs?.get(key) ?: return null
            if (abs(currentValue - defaultValue) <= tolerance) return null
            return ModifiedSettingEntry(
                id = id,
                titleRes = titleRes,
                categoryRes = categoryRes,
                route = route,
                currentValue = formatter(currentValue),
                defaultValue = formatter(defaultValue),
                reset = { remove(key) },
            )
        }

        override fun reset(mutablePreferences: MutablePreferences) {
            mutablePreferences.remove(key)
        }
    }

private fun intSpec(
    key: Preferences.Key<Int>,
    @StringRes titleRes: Int,
    @StringRes categoryRes: Int,
    route: String,
    defaultValue: Int,
    formatter: (Int) -> SettingValueText,
): SettingSpec =
    object : SettingSpec {
        override val id: String = key.name
        override val titleRes: Int = titleRes
        override val categoryRes: Int = categoryRes
        override val route: String = route

        override fun evaluate(
            prefs: Preferences?,
            isLowRamDevice: Boolean,
        ): ModifiedSettingEntry? {
            val currentValue = prefs?.get(key) ?: return null
            if (currentValue == defaultValue) return null
            return ModifiedSettingEntry(
                id = id,
                titleRes = titleRes,
                categoryRes = categoryRes,
                route = route,
                currentValue = formatter(currentValue),
                defaultValue = formatter(defaultValue),
                reset = { remove(key) },
            )
        }

        override fun reset(mutablePreferences: MutablePreferences) {
            mutablePreferences.remove(key)
        }
    }

private inline fun <reified T : Enum<T>> enumSpec(
    key: Preferences.Key<String>,
    @StringRes titleRes: Int,
    @StringRes categoryRes: Int,
    route: String,
    defaultValue: T,
    crossinline normalize: (T) -> T = { it },
    crossinline labelFor: (T) -> SettingValueText,
): SettingSpec =
    object : SettingSpec {
        override val id: String = key.name
        override val titleRes: Int = titleRes
        override val categoryRes: Int = categoryRes
        override val route: String = route

        override fun evaluate(
            prefs: Preferences?,
            isLowRamDevice: Boolean,
        ): ModifiedSettingEntry? {
            val raw = prefs?.get(key) ?: return null
            val parsed = runCatching { enumValueOf<T>(raw) }.getOrNull() ?: return null
            val normalized = normalize(parsed)
            if (normalized == defaultValue) return null
            return ModifiedSettingEntry(
                id = id,
                titleRes = titleRes,
                categoryRes = categoryRes,
                route = route,
                currentValue = labelFor(normalized),
                defaultValue = labelFor(defaultValue),
                reset = { remove(key) },
            )
        }

        override fun reset(mutablePreferences: MutablePreferences) {
            mutablePreferences.remove(key)
        }
    }

private fun boolValueText(value: Boolean): SettingValueText =
    SettingValueText.Resource(if (value) R.string.enabled_state else R.string.disabled_state)

object ModifiedSettingsRegistry {
    private val specs: List<SettingSpec> =
        listOf(
            // Theme & Motion
            booleanSpec(
                key = DynamicThemeKey,
                titleRes = R.string.enable_dynamic_theme,
                categoryRes = R.string.theme,
                route = "settings/appearance",
            ) { true },
            booleanSpec(
                key = RandomThemeOnStartupKey,
                titleRes = R.string.random_theme_on_startup,
                categoryRes = R.string.theme,
                route = "settings/appearance",
            ) { false },
            enumSpec(
                key = DarkModeKey,
                titleRes = R.string.dark_theme,
                categoryRes = R.string.theme,
                route = "settings/appearance?scrollTo=dark_theme",
                defaultValue = DarkMode.AUTO,
            ) { mode ->
                SettingValueText.Resource(
                    when (mode) {
                        DarkMode.ON -> R.string.dark_theme_on
                        DarkMode.OFF -> R.string.dark_theme_off
                        DarkMode.AUTO -> R.string.dark_theme_follow_system
                    },
                )
            },
            booleanSpec(
                key = PureBlackKey,
                titleRes = R.string.pure_black,
                categoryRes = R.string.theme,
                route = "settings/appearance",
            ) { false },
            booleanSpec(
                key = DisableBlurKey,
                titleRes = R.string.disable_blur,
                categoryRes = R.string.theme,
                route = "settings/appearance",
            ) { false },
            booleanSpec(
                key = DisableAnimationsKey,
                titleRes = R.string.disable_animations,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance?scrollTo=disable_animations",
            ) { isLowRam -> isLowRam },
            enumSpec(
                key = MotionProfileKey,
                titleRes = R.string.motion_profile_title,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance_studio?scrollTo=motion_profile",
                defaultValue = MotionProfile.SYSTEM,
            ) { profile ->
                SettingValueText.Resource(
                    when (profile) {
                        MotionProfile.SYSTEM -> R.string.motion_profile_system
                        MotionProfile.FAST -> R.string.motion_profile_fast
                        MotionProfile.SMOOTH -> R.string.motion_profile_smooth
                        MotionProfile.SPRINGY -> R.string.motion_profile_springy
                        MotionProfile.CINEMATIC -> R.string.motion_profile_cinematic
                        MotionProfile.REDUCED -> R.string.motion_profile_reduced
                    },
                )
            },
            booleanSpec(
                key = MotionScreenTransitionsKey,
                titleRes = R.string.motion_channel_screen,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance_studio?scrollTo=motion_profile",
            ) { true },
            booleanSpec(
                key = MotionArtworkTransitionsKey,
                titleRes = R.string.motion_channel_artwork,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance_studio?scrollTo=motion_profile",
            ) { true },
            booleanSpec(
                key = MotionPressFeedbackKey,
                titleRes = R.string.motion_channel_press,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance_studio?scrollTo=motion_profile",
            ) { true },
            booleanSpec(
                key = MotionDecorativeBackgroundKey,
                titleRes = R.string.motion_channel_decorative,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance_studio?scrollTo=motion_profile",
            ) { true },
            enumSpec(
                key = HapticIntensityKey,
                titleRes = R.string.haptic_intensity_title,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance_studio?scrollTo=motion_profile",
                defaultValue = HapticIntensity.STANDARD,
            ) { intensity ->
                SettingValueText.Resource(
                    when (intensity) {
                        HapticIntensity.OFF -> R.string.haptic_intensity_off
                        HapticIntensity.LIGHT -> R.string.haptic_intensity_light
                        HapticIntensity.STANDARD -> R.string.haptic_intensity_standard
                        HapticIntensity.STRONG -> R.string.haptic_intensity_strong
                    },
                )
            },
            booleanSpec(
                key = EnableHapticFeedbackKey,
                titleRes = R.string.haptics,
                categoryRes = R.string.motion_profile_title,
                route = "settings/appearance",
            ) { true },
            booleanSpec(
                key = ForceHighRefreshRateKey,
                titleRes = R.string.force_high_refresh_rate,
                categoryRes = R.string.theme,
                route = "settings/appearance",
            ) { false },
            booleanSpec(
                key = BackdropEnabledKey,
                titleRes = R.string.album_backdrop,
                categoryRes = R.string.theme,
                route = "settings/appearance",
            ) { true },
            intSpec(
                key = BackdropBlurAmountKey,
                titleRes = R.string.backdrop_blur_amount,
                categoryRes = R.string.theme,
                route = "settings/appearance",
                defaultValue = 60,
            ) { SettingValueText.Plain("$it%") },

            // Home
            enumSpec(
                key = HomeFeedStyleKey,
                titleRes = R.string.home_feed_style,
                categoryRes = R.string.home,
                route = "settings/appearance?scrollTo=home_feed_style",
                defaultValue = HomeFeedStyle.STANDARD,
            ) { style ->
                SettingValueText.Resource(
                    when (style) {
                        HomeFeedStyle.STANDARD -> R.string.home_feed_style_standard
                        HomeFeedStyle.COMPACT -> R.string.home_feed_style_compact
                        HomeFeedStyle.GALLERY -> R.string.home_feed_style_gallery
                        HomeFeedStyle.NOW -> R.string.home_feed_style_now
                        HomeFeedStyle.COVERS -> R.string.home_feed_style_covers
                    },
                )
            },
            booleanSpec(
                key = ShowHomeCategoryChipsKey,
                titleRes = R.string.show_home_category_chips,
                categoryRes = R.string.home,
                route = "settings/appearance/extras?scrollTo=show_home_category_chips",
            ) { true },
            booleanSpec(
                key = ShowTagsInLibraryKey,
                titleRes = R.string.show_tags_in_library,
                categoryRes = R.string.library,
                route = "settings/appearance/extras?scrollTo=show_tags_in_library",
            ) { true },
            booleanSpec(
                key = HideLikedSongsCardKey,
                titleRes = R.string.hide_liked_songs_card,
                categoryRes = R.string.library,
                route = "settings/appearance/extras?scrollTo=hide_liked_songs_card",
            ) { false },
            booleanSpec(
                key = HideOfflineCardKey,
                titleRes = R.string.hide_offline_card,
                categoryRes = R.string.library,
                route = "settings/appearance/extras?scrollTo=hide_offline_card",
            ) { false },
            booleanSpec(
                key = HideCachedCardKey,
                titleRes = R.string.hide_cached_card,
                categoryRes = R.string.library,
                route = "settings/appearance/extras?scrollTo=hide_cached_card",
            ) { false },
            booleanSpec(
                key = HideLocalFilesCardKey,
                titleRes = R.string.hide_local_files_card,
                categoryRes = R.string.library,
                route = "settings/appearance/extras?scrollTo=hide_local_files_card",
            ) { false },
            booleanSpec(
                key = HideTop50CardKey,
                titleRes = R.string.hide_top50_card,
                categoryRes = R.string.library,
                route = "settings/appearance/extras?scrollTo=hide_top50_card",
            ) { false },

            // Navigation & MiniPlayer
            enumSpec(
                key = NavigationBarCompactBehaviorKey,
                titleRes = R.string.navigation_bar_compact_behavior,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_compact_behavior_preview",
                defaultValue = NavigationBarCompactBehavior.ADAPTIVE,
            ) { behavior ->
                SettingValueText.Resource(
                    when (behavior) {
                        NavigationBarCompactBehavior.ADAPTIVE -> R.string.navigation_bar_compact_adaptive
                        NavigationBarCompactBehavior.ALWAYS_EXPANDED -> R.string.navigation_bar_compact_always_expanded
                        NavigationBarCompactBehavior.ALWAYS_COMPACT -> R.string.navigation_bar_compact_always_compact
                    },
                )
            },
            booleanSpec(
                key = NavigationBarFrostedBlurKey,
                titleRes = R.string.navigation_bar_frosted_blur,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_frosted_blur",
            ) { false },
            booleanSpec(
                key = NavigationBarTintFrostedBlurKey,
                titleRes = R.string.navigation_bar_tint_frosted_blur,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_tint_frosted_blur",
            ) { false },
            booleanSpec(
                key = LiquidGlassEnabledKey,
                titleRes = R.string.liquid_glass_nav_bar,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=liquid_glass_nav_bar",
            ) { true },
            booleanSpec(
                key = LiquidGlassNavBarEnabledKey,
                titleRes = R.string.liquid_glass_nav_bar,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=liquid_glass_nav_bar",
            ) { false },
            booleanSpec(
                key = NavigationBarBitchordKey,
                titleRes = R.string.navigation_bar_bitchord,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_bitchord",
            ) { false },
            booleanSpec(
                key = SukisuLiquidGlassNavBarKey,
                titleRes = R.string.sukisu_liquid_glass_nav_bar,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=sukisu_liquid_glass_nav_bar",
            ) { false },
            booleanSpec(
                key = HideNavigationBarLabelsKey,
                titleRes = R.string.hide_navigation_bar_labels,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=hide_navigation_bar_labels",
            ) { false },
            floatSpec(
                key = NavigationBarWidthKey,
                titleRes = R.string.navigation_bar_width,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_width",
                defaultValue = NAVIGATION_BAR_WIDTH_DEFAULT,
                formatter = { SettingValueText.Plain("${(it * 100).roundToInt()}%") },
            ),
            floatSpec(
                key = NavigationBarHeightKey,
                titleRes = R.string.navigation_bar_height,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_height",
                defaultValue = NAVIGATION_BAR_HEIGHT_DEFAULT,
                formatter = { SettingValueText.Plain("${(it * 100).roundToInt()}%") },
            ),
            floatSpec(
                key = NavigationBarOpacityKey,
                titleRes = R.string.navigation_bar_opacity,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_opacity",
                defaultValue = NAVIGATION_BAR_OPACITY_DEFAULT,
                formatter = { SettingValueText.Plain("${(it * 100).roundToInt()}%") },
            ),
            floatSpec(
                key = NavigationBarTransparencyKey,
                titleRes = R.string.navigation_bar_transparency,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_transparency",
                defaultValue = NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
                formatter = { SettingValueText.Plain("${(it * 100).roundToInt()}%") },
            ),
            floatSpec(
                key = NavigationBarLabelSpacingKey,
                titleRes = R.string.navigation_bar_label_spacing,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_label_spacing",
                defaultValue = NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
                formatter = { SettingValueText.Plain("${it.roundToInt()} dp") },
            ),
            floatSpec(
                key = NavigationBarCornerRadiusKey,
                titleRes = R.string.navigation_bar_corner_radius,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance/navigation_bar?scrollTo=navigation_bar_corner_radius",
                defaultValue = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
                formatter = { SettingValueText.Plain("${it.roundToInt()} dp") },
            ),
            enumSpec(
                key = MiniPlayerStyleKey,
                titleRes = R.string.mini_player_style_title,
                categoryRes = R.string.navigation_bar_settings_title,
                route = "settings/appearance_studio?scrollTo=navigation_mini_player",
                defaultValue = MiniPlayerStyle.STANDARD,
            ) { style ->
                SettingValueText.Resource(
                    when (style) {
                        MiniPlayerStyle.STANDARD -> R.string.mini_player_style_pill
                        MiniPlayerStyle.BAR -> R.string.mini_player_style_bar
                    },
                )
            },

            // Player
            enumSpec(
                key = PlayerDesignStyleKey,
                titleRes = R.string.player_design_style,
                categoryRes = R.string.player,
                route = "settings/appearance?scrollTo=player_design_style",
                defaultValue = PlayerDesignStyle.APPLE_MUSIC,
                normalize = { if (it in RemovedPlayerDesignStyles) PlayerDesignStyle.APPLE_MUSIC else it },
            ) { style ->
                SettingValueText.Resource(
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
                    },
                )
            },
            enumSpec(
                key = PlayerBackgroundStyleKey,
                titleRes = R.string.player_background_style,
                categoryRes = R.string.player,
                route = "settings/appearance?scrollTo=player_background_style",
                defaultValue = PlayerBackgroundStyle.DEFAULT,
            ) { bg ->
                SettingValueText.Plain(bg.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase))
            },
            enumSpec(
                key = PlayerBackgroundMotionPolicyKey,
                titleRes = R.string.player_background_motion_policy_title,
                categoryRes = R.string.player,
                route = "settings/appearance_studio?scrollTo=color_background",
                defaultValue = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ) { policy ->
                SettingValueText.Resource(
                    when (policy) {
                        BackgroundMotionPolicy.WHILE_PLAYING_OPEN -> R.string.player_background_motion_while_open
                        BackgroundMotionPolicy.RESPECT_POWER_SAVER -> R.string.player_background_motion_power_saver
                        BackgroundMotionPolicy.NEVER -> R.string.player_background_motion_never
                    },
                )
            },
            enumSpec(
                key = SliderStyleKey,
                titleRes = R.string.player_slider_style,
                categoryRes = R.string.player,
                route = "settings/appearance",
                defaultValue = SliderStyle.Standard,
            ) { slider ->
                SettingValueText.Resource(
                    when (slider) {
                        SliderStyle.Standard -> R.string.slider_style_standard
                        SliderStyle.Wavy -> R.string.slider_style_wavy
                        SliderStyle.Thick -> R.string.slider_style_thick
                        SliderStyle.Circular -> R.string.slider_style_circular
                        SliderStyle.Simple -> R.string.slider_style_simple
                    },
                )
            },
            booleanSpec(
                key = ShowPlayerVolumeBarKey,
                titleRes = R.string.show_player_volume_bar,
                categoryRes = R.string.player,
                route = "settings/appearance",
            ) { true },
            booleanSpec(
                key = HidePlayerThumbnailKey,
                titleRes = R.string.hide_player_thumbnail,
                categoryRes = R.string.player,
                route = "settings/appearance",
            ) { false },
            booleanSpec(
                key = CropThumbnailToSquareKey,
                titleRes = R.string.crop_thumbnail_to_square,
                categoryRes = R.string.player,
                route = "settings/appearance?scrollTo=crop_thumbnail_to_square",
            ) { false },
            floatSpec(
                key = ThumbnailCornerRadiusKey,
                titleRes = R.string.custom_radius,
                categoryRes = R.string.player,
                route = "settings/appearance",
                defaultValue = 16f,
                formatter = { SettingValueText.Plain("${it.roundToInt()} dp") },
            ),
            enumSpec(
                key = MiniPlayerBackgroundStyleKey,
                titleRes = R.string.mini_player_style_title,
                categoryRes = R.string.player,
                route = "settings/appearance",
                defaultValue = MiniPlayerBackgroundStyle.THEME,
            ) { style ->
                SettingValueText.Plain(style.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase))
            },
            booleanSpec(
                key = ShowCastInPlayerMenuKey,
                titleRes = R.string.show_cast_in_player_menu,
                categoryRes = R.string.player,
                route = "settings/player?scrollTo=show_cast_in_player_menu",
            ) { true },

            // Lyrics
            enumSpec(
                key = LyricsModeKey,
                titleRes = R.string.lyrics_mode,
                categoryRes = R.string.lyrics,
                route = "settings/lyrics?scrollTo=lyrics_mode",
                defaultValue = LyricsMode.ENHANCED,
            ) { mode ->
                SettingValueText.Resource(
                    when (mode) {
                        LyricsMode.V2 -> R.string.lyrics_mode_v2
                        LyricsMode.ENHANCED -> R.string.lyrics_mode_enhanced
                        LyricsMode.SPOTIFY -> R.string.lyrics_mode_spotify
                    },
                )
            },
            enumSpec(
                key = LyricsDisplayCharacterKey,
                titleRes = R.string.lyrics_display_character_title,
                categoryRes = R.string.lyrics,
                route = "settings/lyrics?scrollTo=lyrics_display_character",
                defaultValue = LyricsDisplayCharacter.FOCUS,
            ) { character ->
                SettingValueText.Resource(
                    when (character) {
                        LyricsDisplayCharacter.READING -> R.string.lyrics_character_reading
                        LyricsDisplayCharacter.FOCUS -> R.string.lyrics_character_focus
                        LyricsDisplayCharacter.STAGE -> R.string.lyrics_character_stage
                    },
                )
            },
            enumSpec(
                key = LyricsBackgroundStyleKey,
                titleRes = R.string.lyrics_background_style,
                categoryRes = R.string.lyrics,
                route = "settings/appearance",
                defaultValue = LyricsBackgroundStyle.DEFAULT,
            ) { style ->
                SettingValueText.Plain(style.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase))
            },
            booleanSpec(
                key = LyricsLineBlurKey,
                titleRes = R.string.lyrics_line_blur,
                categoryRes = R.string.lyrics,
                route = "settings/lyrics?scrollTo=lyrics_line_blur",
            ) { true },
            floatSpec(
                key = LyricsTextSizeKey,
                titleRes = R.string.lyrics_text_size,
                categoryRes = R.string.lyrics,
                route = "settings/lyrics",
                defaultValue = 26f,
                formatter = { SettingValueText.Plain("${it.roundToInt()} sp") },
            ),
            floatSpec(
                key = LyricsLineSpacingKey,
                titleRes = R.string.lyrics_line_spacing,
                categoryRes = R.string.lyrics,
                route = "settings/lyrics",
                defaultValue = 1.3f,
                formatter = { SettingValueText.Plain("${(it * 10f).roundToInt() / 10f}x") },
            ),

            // Content & Filters
            booleanSpec(
                key = AiContentFilterEnabledKey,
                titleRes = R.string.ai_music_filter_enable,
                categoryRes = R.string.ai_content_filter,
                route = "settings/content?scrollTo=ai_content_filter",
            ) { false },
        )

    fun collectModified(
        prefs: Preferences?,
        isLowRamDevice: Boolean,
    ): List<ModifiedSettingEntry> {
        if (prefs == null) return emptyList()
        val results = specs.mapNotNull { it.evaluate(prefs, isLowRamDevice) }.toMutableList()

        val homeOrder = prefs[HomeSectionOrderKey]
        if (!homeOrder.isNullOrBlank()) {
            results +=
                ModifiedSettingEntry(
                    id = HomeSectionOrderKey.name,
                    titleRes = R.string.home,
                    categoryRes = R.string.home,
                    route = "settings/appearance_studio?scrollTo=home",
                    currentValue = SettingValueText.Plain(homeOrder.split(',').size.toString()),
                    defaultValue = SettingValueText.Resource(R.string.home_feed_style_standard),
                    reset = { remove(HomeSectionOrderKey) },
                )
        }

        val hiddenSections = prefs[HomeHiddenSectionsKey]
        if (!hiddenSections.isNullOrEmpty()) {
            results +=
                ModifiedSettingEntry(
                    id = HomeHiddenSectionsKey.name,
                    titleRes = R.string.home,
                    categoryRes = R.string.home,
                    route = "settings/appearance_studio?scrollTo=home",
                    currentValue = SettingValueText.Plain(hiddenSections.size.toString()),
                    defaultValue = SettingValueText.Plain("0"),
                    reset = { remove(HomeHiddenSectionsKey) },
                )
        }

        return results
    }

    fun resetAll(mutablePreferences: MutablePreferences) {
        specs.forEach { it.reset(mutablePreferences) }
        mutablePreferences.remove(HomeSectionOrderKey)
        mutablePreferences.remove(HomeHiddenSectionsKey)
    }

    fun count(
        context: Context,
        prefs: Preferences?,
    ): Int = collectModified(prefs, context.isLowRamDevice()).size
}
