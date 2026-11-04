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
}
