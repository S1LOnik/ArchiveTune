/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class StudioCoverPreviewMode {
    LIGHT,
    DARK,
    NONE,
}

@Immutable
data class StudioDemoTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationLabel: String,
    val sourceLabel: String,
    val lightColors: List<Color>,
    val darkColors: List<Color>,
)

@Immutable
data class StudioDemoHomeCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val secondary: Color,
)

@Immutable
data class StudioDemoLyricLine(
    val timeLabel: String,
    val primaryText: String,
    val translation: String? = null,
)

object StudioDemoData {
    val DemoTracks: List<StudioDemoTrack> =
        listOf(
            StudioDemoTrack(
                id = "demo_1",
                title = "Полуночный маяк",
                artist = "ArchiveTune Trio",
                album = "Тихий эфир · 2026",
                durationLabel = "3:42",
                sourceLabel = "FLAC · Local",
                lightColors = listOf(Color(0xFFE7ECF6), Color(0xFFAEC8E8)),
                darkColors = listOf(Color(0xFF1F3B5C), Color(0xFF0E1726)),
            ),
            StudioDemoTrack(
                id = "demo_2",
                title = "Morning Glass",
                artist = "Kuroi & Mira",
                album = "Daylight Sketches",
                durationLabel = "4:08",
                sourceLabel = "Hi-Res · Stream",
                lightColors = listOf(Color(0xFFF6E7D8), Color(0xFFE6AC7A)),
                darkColors = listOf(Color(0xFF5D3422), Color(0xFF1B1214)),
            ),
        )

    val DemoHomeCards: List<StudioDemoHomeCard> =
        listOf(
            StudioDemoHomeCard("c1", "Утренний фокус", "6 треков · оффлайн", Color(0xFF4A90D9), Color(0xFF1E3C72)),
            StudioDemoHomeCard("c2", "Ночная дорога", "Синтвейв и электроника", Color(0xFFD95372), Color(0xFF471C3A)),
            StudioDemoHomeCard("c3", "Свежие находки", "Персональная подборка", Color(0xFF2EA887), Color(0xFF144B41)),
            StudioDemoHomeCard("c4", "Винил и джаз", "Из вашей медиатеки", Color(0xFFD98A3C), Color(0xFF563013)),
            StudioDemoHomeCard("c5", "После дождя", "Спокойные инструменталы", Color(0xFF7D6CE2), Color(0xFF2D255E)),
            StudioDemoHomeCard("c6", "Любимые повторы", "История прослушивания", Color(0xFF5F9EA0), Color(0xFF1B3A4B)),
        )

    val DemoLyricsLines: List<StudioDemoLyricLine> =
        listOf(
            StudioDemoLyricLine(
                timeLabel = "00:18",
                primaryText = "Ветер держит тихий ритм на краю ночного берега",
                translation = "The wind keeps a quiet beat by the edge of the night shore",
            ),
            StudioDemoLyricLine(
                timeLabel = "00:24",
                primaryText = "Между тёплым светом лампы и пустой прямой дорогой слышен знакомый аккорд",
                translation = "Between warm lamp glow and an open straight road, a familiar chord plays",
            ),
            StudioDemoLyricLine(
                timeLabel = "00:31",
                primaryText = "Hold the signal clear",
            ),
            StudioDemoLyricLine(
                timeLabel = "00:37",
                primaryText = "Пусть каждая деталь остаётся живой даже на самой малой громкости",
                translation = "Let every detail stay vivid even at the softest volume",
            ),
            StudioDemoLyricLine(
                timeLabel = "00:44",
                primaryText = "Fade slowly into morning light",
            ),
        )
}
