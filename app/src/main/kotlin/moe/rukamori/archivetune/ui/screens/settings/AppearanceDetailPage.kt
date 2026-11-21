/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.StringRes
import moe.rukamori.archivetune.R

internal enum class AppearanceDetailPage(val id: String, @StringRes val title: Int) {
    THEME("theme", R.string.studio_basic_theme),
    DISPLAY("display", R.string.studio_basic_display),
    MOTION("motion", R.string.studio_basic_motion),
    PLAYER("player", R.string.player),
    HOME("home", R.string.home),
    LIBRARY("library", R.string.filter_library),
    GLASS("glass", R.string.studio_glass_details),
    ;

    fun route(setting: String? = null): String {
        val destination = "settings/appearance_studio/$id"
        return if (setting.isNullOrBlank()) destination else "$destination?scrollTo=$setting"
    }
}

internal fun appearanceSearchRoute(setting: String?): String =
    when (setting) {
        null -> "settings/appearance_studio"
        "disable_blur" ->
            "settings/appearance_studio?scrollTo=$setting"
        "home_feed_style", "show_home_category_chips", "default_open_tab", "minimal_home_mode",
        "show_mix_song_titles", "quick_picks_display_mode" -> AppearanceDetailPage.HOME.route(setting)
        "backdrop_blur", "backdrop_blur_amount", "swipe_to_song", "album_large_cover",
        "default_lib_chips", "arrange_library_chips", "arrange_playlist_tags", "manage_playlist_tags" ->
            AppearanceDetailPage.LIBRARY.route(setting)
        "font_preference", "custom_font", "ui_scale", "app_language", "hide_status_bar", "hide_scrollbar",
        "tablet_mode" -> AppearanceDetailPage.DISPLAY.route(setting)
        "use_system_font" -> AppearanceDetailPage.DISPLAY.route("font_preference")
        "disable_animations", "back_animation_style", "force_high_refresh_rate", "haptics" ->
            AppearanceDetailPage.MOTION.route(setting)
        "player_design_style", "player_background_style", "lyrics_background_style", "customized_background",
        "player_buttons_style", "player_slider_style", "hide_player_thumbnail", "thumbnail_corner_radius",
        "crop_thumbnail_to_square", "tiktok_main_lyrics", "show_player_volume_bar", "enable_swipe_thumbnail",
        "swipe_sensitivity" -> AppearanceDetailPage.PLAYER.route(setting)
        "liquid_glass_customisation" -> AppearanceDetailPage.GLASS.route()
        "liquid_glass_effects", "glass_chromatic_aberration", "glass_depth_3d", "glass_backdrop_vibrancy", "glass_adaptive_luminance",
        "liquid_glass_intensity", "liquid_glass_refraction_height", "liquid_glass_refraction_amount",
        "liquid_glass_blur_radius", "liquid_glass_tint_opacity", "liquid_glass_shadow_depth" ->
            AppearanceDetailPage.GLASS.route(setting)
        "frosted_nav_bar" -> "settings/appearance/navigation_bar?scrollTo=navigation_bar_frosted_blur"
        "liquid_glass_nav_bar", "mini_player_background_style" ->
            "settings/appearance/navigation_bar?scrollTo=$setting"
        "theme_creator", "palette_picker" -> "settings/appearance/$setting"
        "app_icon" -> "settings/appearance/icon"
        "album_canvas_enabled" -> "settings/canvas"
        "extras" -> "settings/appearance/extras"
        else -> AppearanceDetailPage.THEME.route(setting)
    }
