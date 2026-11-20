/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata

enum class StudioCoverPreviewMode {
    CURRENT,
    LIGHT,
    DARK,
    NONE,
}

@Immutable
data class StudioDemoTrack(
    val id: String,
    @param:StringRes val titleRes: Int,
    val artist: String,
    @param:StringRes val albumRes: Int,
    val durationLabel: String,
    @param:StringRes val sourceLabelRes: Int,
    val lightColors: List<Color>,
    val darkColors: List<Color>,
    val media: MediaMetadata? = null,
)

@Immutable
data class StudioDemoHomeCard(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val accent: Color,
    val secondary: Color,
)

@Immutable
data class StudioDemoLyricLine(
    val timeLabel: String,
    @param:StringRes val primaryTextRes: Int,
    @param:StringRes val translationRes: Int? = null,
)

object StudioDemoData {
    val DemoChipResIds: List<Int> =
        listOf(
            R.string.studio_demo_chip_focus,
            R.string.studio_demo_chip_energy,
            R.string.studio_demo_chip_chill,
        )

    val DemoTracks: List<StudioDemoTrack> =
        listOf(
            StudioDemoTrack(
                id = "demo_1",
                titleRes = R.string.studio_demo_track_1_title,
                artist = "ArchiveTune Trio",
                albumRes = R.string.studio_demo_track_1_album,
                durationLabel = "3:42",
                sourceLabelRes = R.string.studio_demo_track_1_source,
                lightColors = listOf(Color(0xFFE7ECF6), Color(0xFFAEC8E8)),
                darkColors = listOf(Color(0xFF1F3B5C), Color(0xFF0E1726)),
            ),
            StudioDemoTrack(
                id = "demo_2",
                titleRes = R.string.studio_demo_track_2_title,
                artist = "Kuroi & Mira",
                albumRes = R.string.studio_demo_track_2_album,
                durationLabel = "4:08",
                sourceLabelRes = R.string.studio_demo_track_2_source,
                lightColors = listOf(Color(0xFFF6E7D8), Color(0xFFE6AC7A)),
                darkColors = listOf(Color(0xFF5D3422), Color(0xFF1B1214)),
            ),
        )

    val DemoHomeCards: List<StudioDemoHomeCard> =
        listOf(
            StudioDemoHomeCard(
                "c1",
                R.string.studio_demo_card_1_title,
                R.string.studio_demo_card_1_subtitle,
                Color(0xFF4A90D9),
                Color(0xFF1E3C72),
            ),
            StudioDemoHomeCard(
                "c2",
                R.string.studio_demo_card_2_title,
                R.string.studio_demo_card_2_subtitle,
                Color(0xFFD95372),
                Color(0xFF471C3A),
            ),
            StudioDemoHomeCard(
                "c3",
                R.string.studio_demo_card_3_title,
                R.string.studio_demo_card_3_subtitle,
                Color(0xFF2EA887),
                Color(0xFF144B41),
            ),
            StudioDemoHomeCard(
                "c4",
                R.string.studio_demo_card_4_title,
                R.string.studio_demo_card_4_subtitle,
                Color(0xFFD98A3C),
                Color(0xFF563013),
            ),
            StudioDemoHomeCard(
                "c5",
                R.string.studio_demo_card_5_title,
                R.string.studio_demo_card_5_subtitle,
                Color(0xFF7D6CE2),
                Color(0xFF2D255E),
            ),
            StudioDemoHomeCard(
                "c6",
                R.string.studio_demo_card_6_title,
                R.string.studio_demo_card_6_subtitle,
                Color(0xFF5F9EA0),
                Color(0xFF1B3A4B),
            ),
        )

    val DemoLyricsLines: List<StudioDemoLyricLine> =
        listOf(
            StudioDemoLyricLine(
                timeLabel = "00:18",
                primaryTextRes = R.string.studio_demo_lyric_1_primary,
                translationRes = R.string.studio_demo_lyric_1_secondary,
            ),
            StudioDemoLyricLine(
                timeLabel = "00:24",
                primaryTextRes = R.string.studio_demo_lyric_2_primary,
                translationRes = R.string.studio_demo_lyric_2_secondary,
            ),
            StudioDemoLyricLine(
                timeLabel = "00:31",
                primaryTextRes = R.string.studio_demo_lyric_3_primary,
            ),
            StudioDemoLyricLine(
                timeLabel = "00:37",
                primaryTextRes = R.string.studio_demo_lyric_4_primary,
                translationRes = R.string.studio_demo_lyric_4_secondary,
            ),
            StudioDemoLyricLine(
                timeLabel = "00:44",
                primaryTextRes = R.string.studio_demo_lyric_5_primary,
            ),
        )
}
