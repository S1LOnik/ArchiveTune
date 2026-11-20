/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import moe.rukamori.archivetune.constants.LyricsDisplayCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherSourcesSheetTest {
    @Test
    fun `cleanTrackInfo strips live remix slowed and feat suffixes`() {
        val (cleanTitle, artist) = cleanTrackInfo("Midnight City (Live at Coachella) [feat. Guest]", "M83")
        assertEquals("Midnight City", cleanTitle)
        assertEquals("M83", artist)
    }

    @Test
    fun `detectVersion distinguishes live remix vip slowed and original`() {
        assertEquals(TrackVersion.LIVE, detectVersion("Song (Live Acoustic)"))
        assertEquals(TrackVersion.LIVE, detectVersion("Трек (Концерт в Москве)"))
        assertEquals(TrackVersion.REMIX, detectVersion("Track (Club Remix)"))
        assertEquals(TrackVersion.VIP, detectVersion("Track (VIP Extended Mix)"))
        assertEquals(TrackVersion.SLOWED, detectVersion("Track (Slowed + Reverb)"))
        assertEquals(TrackVersion.ORIGINAL, detectVersion("Clean Studio Track"))
    }

    @Test
    fun `per-provider failure preserves surviving providers and reports only failed sources`() {
        val ytTrack =
            SourceTrack(
                id = "yt:1",
                title = "Sample Song",
                artist = "Sample Artist",
                durationSeconds = 210,
                thumbnailUrl = null,
                source = TrackSource.YOUTUBE,
                version = TrackVersion.ORIGINAL,
                relevance = 0.92f,
                metadata = null,
            )
        val scLive =
            SourceTrack(
                id = "sc:2",
                title = "Sample Song (Live Session)",
                artist = "Sample Artist",
                durationSeconds = 245,
                thumbnailUrl = null,
                source = TrackSource.SOUNDCLOUD,
                version = TrackVersion.LIVE,
                relevance = 0.85f,
                metadata = null,
            )

        val aggregated =
            aggregateProviderSearchResults(
                listOf(
                    ProviderSearchResult(TrackSource.YOUTUBE, listOf(ytTrack), failed = false),
                    ProviderSearchResult(TrackSource.SPOTIFY, emptyList(), failed = true),
                    ProviderSearchResult(TrackSource.SOUNDCLOUD, listOf(scLive), failed = false),
                ),
            )

        assertEquals(listOf(TrackSource.SPOTIFY), aggregated.failedSources)
        assertEquals(listOf("yt:1", "sc:2"), aggregated.tracks.map { it.id })
    }

    @Test
    fun `lyrics display character scales expressiveness and disables blur when reduced motion`() {
        val reading = resolveLyricsCharacterStyle(LyricsDisplayCharacter.READING, animationsEnabled = true)
        val stage = resolveLyricsCharacterStyle(LyricsDisplayCharacter.STAGE, animationsEnabled = true)
        val reduced = resolveLyricsCharacterStyle(LyricsDisplayCharacter.STAGE, animationsEnabled = false)

        assertTrue(stage.activeScale > reading.activeScale)
        assertTrue(stage.blurMultiplier > reading.blurMultiplier)
        assertTrue(reading.nearInactiveAlpha > stage.nearInactiveAlpha)
        assertEquals(0f, reduced.blurMultiplier, 0.001f)
        assertEquals(1f, reduced.activeScale, 0.001f)
    }
}
