/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// [FIX-ZCODE] Wave 9: unit tests for the settings search engine (ported from the donor).
class SettingsSearchTest {
    private object FakePainter : Painter() {
        override val intrinsicSize = androidx.compose.ui.geometry.Size(24f, 24f)
        override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {}
    }

    private val groups =
        listOf(
            SettingsGroup(
                title = "Appearance",
                items =
                    listOf(
                        SettingsItem(
                            key = "appearance",
                            icon = FakePainter,
                            title = "Appearance",
                            subtitle = "Theme, colors, fonts",
                            keywords = listOf("theme", "color", "dark", "light", "amoled", "font"),
                            children =
                                listOf(
                                    SettingsChild("Dark theme", "dark_theme", listOf("dark", "dark theme", "night", "amoled")),
                                    SettingsChild("Random theme on startup", "random_theme", listOf("random theme", "shuffle theme")),
                                    SettingsChild("Font", "font", listOf("font", "typeface", "system font")),
                                ),
                            onClick = {},
                        ),
                    ),
            ),
            SettingsGroup(
                title = "Playback",
                items =
                    listOf(
                        SettingsItem(
                            key = "playback",
                            icon = FakePainter,
                            title = "Player",
                            subtitle = "Audio quality, crossfade, equalizer",
                            keywords = listOf("audio", "quality", "crossfade", "equalizer", "volume"),
                            children =
                                listOf(
                                    SettingsChild("Crossfade duration", "crossfade", listOf("crossfade", "fade")),
                                    SettingsChild("Audio quality", "audio_quality", listOf("bitrate", "lossless", "quality", "flac")),
                                ),
                            onClick = {},
                        ),
                    ),
            ),
        )

    private val routeFor: (String, String) -> String? = { parentKey, scrollKey ->
        "settings/$parentKey?scrollTo=$scrollKey"
    }

    @Test
    fun `exact child title match is found`() {
        val results = SettingsSearch.search(groups, "dark theme", routeFor)
        assertTrue(results.isNotEmpty())
        assertEquals("Dark theme", results.first().title)
        assertEquals("appearance", results.first().parentKey)
    }

    @Test
    fun `synonym night resolves to dark theme`() {
        val results = SettingsSearch.search(groups, "night mode", routeFor)
        assertTrue(results.isNotEmpty())
        assertEquals("Dark theme", results.first().title)
    }

    @Test
    fun `routeFor supplies the parent route`() {
        val results = SettingsSearch.search(groups, "crossfade", routeFor)
        assertTrue(results.isNotEmpty())
        assertEquals("settings/playback?scrollTo=crossfade", results.first().parentRoute)
    }

    @Test
    fun `blank query returns no results`() {
        assertTrue(SettingsSearch.search(groups, "", routeFor).isEmpty())
        assertTrue(SettingsSearch.search(groups, "   ", routeFor).isEmpty())
    }

    @Test
    fun `garbage query returns no results`() {
        assertTrue(SettingsSearch.search(groups, "zzzqqqxxx", routeFor).isEmpty())
    }

    @Test
    fun `strong keyword match outranks weak context match`() {
        val results = SettingsSearch.search(groups, "flac", routeFor)
        assertEquals("Audio quality", results.first().title)
    }

    @Test
    fun `dp import is exercised so compose types compile in tests`() {
        // Compose types (Painter, dp) must resolve on the unit-test classpath.
        assertEquals(24.dp.value, 24f)
    }

    @Test
    fun `cyrillic normalization folds yo to ye and normalizes hyphens`() {
        assertEquals("темная тема", SettingsSearch.normalizeText("Тёмная тёма"))
        assertEquals("ии исполнители", SettingsSearch.normalizeText("ИИ-исполнители"))
        assertEquals("убрать каст", SettingsSearch.normalizeText("  Убрать  каст! "))
    }

    @Test
    fun `exact title match outranks partial or longer titles`() {
        val mixedGroups =
            listOf(
                SettingsGroup(
                    title = "Mixed",
                    items =
                        listOf(
                            SettingsItem(
                                key = "appearance",
                                icon = FakePainter,
                                title = "Appearance",
                                keywords = listOf("theme", "dark"),
                                children =
                                    listOf(
                                        SettingsChild("Dark theme schedule", "schedule", listOf("dark theme")),
                                        SettingsChild("Dark theme", "dark_theme", listOf("dark", "night")),
                                    ),
                                onClick = {},
                            ),
                        ),
                ),
            )
        val results = SettingsSearch.search(mixedGroups, "Dark theme", routeFor)
        assertTrue(results.isNotEmpty())
        assertEquals("Dark theme", results.first().title)
    }

    @Test
    fun `bilingual russian and english queries locate expected settings`() {
        val bilingualGroups =
            listOf(
                SettingsGroup(
                    title = "Settings",
                    items =
                        listOf(
                            SettingsItem(
                                key = "playback",
                                icon = FakePainter,
                                title = "Плеер",
                                keywords = listOf("player", "playback", "плеер"),
                                children =
                                    listOf(
                                        SettingsChild(
                                            title = "Показывать трансляцию в меню плеера",
                                            scrollKey = "show_cast_in_player_menu",
                                            keywords = listOf("cast", "chromecast", "каст", "трансляция", "убрать каст"),
                                        ),
                                        SettingsChild(
                                            title = "Другие источники",
                                            scrollKey = "other_sources",
                                            keywords = listOf("other sources", "sources", "другие источники"),
                                        ),
                                    ),
                                onClick = {},
                            ),
                            SettingsItem(
                                key = "navigation_bar",
                                icon = FakePainter,
                                title = "Navigation bar",
                                keywords = listOf("navigation bar", "navbar", "нижняя панель", "навбар"),
                                children =
                                    listOf(
                                        SettingsChild(
                                            title = "Hide labels in navigation bar",
                                            scrollKey = "hide_navigation_bar_labels",
                                            keywords = listOf("hide labels", "убрать подписи", "подписи", "нижняя панель", "навбар"),
                                        ),
                                    ),
                                onClick = {},
                            ),
                            SettingsItem(
                                key = "appearance_studio",
                                icon = FakePainter,
                                title = "Оформление",
                                keywords = listOf("appearance", "studio", "оформление"),
                                children =
                                    listOf(
                                        SettingsChild(
                                            title = "Характер движения",
                                            scrollKey = "motion_profile",
                                            keywords = listOf("motion", "animations", "движение", "без анимаций"),
                                        ),
                                    ),
                                onClick = {},
                            ),
                            SettingsItem(
                                key = "appearance",
                                icon = FakePainter,
                                title = "Внешний вид",
                                keywords = listOf("appearance"),
                                hidden = true,
                                children =
                                    listOf(
                                        SettingsChild(
                                            title = "Квадратная обложка",
                                            scrollKey = "crop_thumbnail_to_square",
                                            keywords = listOf("crop thumbnail", "square", "обложка"),
                                        ),
                                    ),
                                onClick = {},
                            ),
                            SettingsItem(
                                key = "lyrics",
                                icon = FakePainter,
                                title = "Текст песни",
                                keywords = listOf("lyrics", "слова песни", "текст"),
                                children =
                                    listOf(
                                        SettingsChild(
                                            title = "Режим текста песни",
                                            scrollKey = "lyrics_mode",
                                            keywords = listOf("lyrics", "слова песни", "текст"),
                                        ),
                                    ),
                                onClick = {},
                            ),
                            SettingsItem(
                                key = "account",
                                icon = FakePainter,
                                title = "Аккаунт",
                                keywords = listOf("account"),
                                children =
                                    listOf(
                                        SettingsChild(
                                            title = "Скрытые плейлисты",
                                            scrollKey = "hidden_playlists",
                                            keywords = listOf("hidden playlists", "скрытые плейлисты"),
                                        ),
                                    ),
                                onClick = {},
                            ),
                            SettingsItem(
                                key = "content",
                                icon = FakePainter,
                                title = "Контент",
                                keywords = listOf("content"),
                                children =
                                    listOf(
                                        SettingsChild(
                                            title = "Авторы ИИ-музыки",
                                            scrollKey = "ai_music_authors",
                                            keywords = listOf("ai artists", "ии-исполнители", "авторы ии"),
                                        ),
                                    ),
                                onClick = {},
                            ),
                        ),
                ),
            )

        assertEquals("show_cast_in_player_menu", SettingsSearch.search(bilingualGroups, "убрать каст", routeFor).first().scrollKey)
        assertEquals("show_cast_in_player_menu", SettingsSearch.search(bilingualGroups, "каст", routeFor).first().scrollKey)
        assertEquals("show_cast_in_player_menu", SettingsSearch.search(bilingualGroups, "трансляция", routeFor).first().scrollKey)
        assertEquals("show_cast_in_player_menu", SettingsSearch.search(bilingualGroups, "remove cast", routeFor).first().scrollKey)

        assertEquals("navigation_bar", SettingsSearch.search(bilingualGroups, "нижняя панель", routeFor).first().parentKey)
        assertEquals("navigation_bar", SettingsSearch.search(bilingualGroups, "навбар", routeFor).first().parentKey)
        assertEquals("hide_navigation_bar_labels", SettingsSearch.search(bilingualGroups, "убрать подписи", routeFor).first().scrollKey)
        assertEquals("hide_navigation_bar_labels", SettingsSearch.search(bilingualGroups, "hide labels", routeFor).first().scrollKey)

        assertEquals("motion_profile", SettingsSearch.search(bilingualGroups, "движение", routeFor).first().scrollKey)
        assertEquals("motion_profile", SettingsSearch.search(bilingualGroups, "без анимаций", routeFor).first().scrollKey)
        assertEquals("motion_profile", SettingsSearch.search(bilingualGroups, "animations", routeFor).first().scrollKey)

        assertEquals("lyrics_mode", SettingsSearch.search(bilingualGroups, "слова песни", routeFor).first().scrollKey)
        assertEquals("crop_thumbnail_to_square", SettingsSearch.search(bilingualGroups, "обложка", routeFor).first().scrollKey)
        assertEquals("hidden_playlists", SettingsSearch.search(bilingualGroups, "скрытые плейлисты", routeFor).first().scrollKey)
        assertEquals("ai_music_authors", SettingsSearch.search(bilingualGroups, "ИИ-исполнители", routeFor).first().scrollKey)
        assertEquals("other_sources", SettingsSearch.search(bilingualGroups, "другие источники", routeFor).first().scrollKey)
    }
}
