/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audiosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchingTest {

    @Test
    fun normalizeHandlesDiacriticsAndPunctuation() {
        assertEquals("beyonce", TrackMatching.normalize("Beyoncé"))
        assertEquals("motley crue", TrackMatching.normalize("Mötley Crüe"))
        assertEquals("ac dc", TrackMatching.normalize("AC/DC"))
        assertEquals("dvorak", TrackMatching.normalize("Dvořák"))
        assertEquals("", TrackMatching.normalize(null))
        assertEquals("", TrackMatching.normalize("   "))
        assertEquals("", TrackMatching.normalize("Кино - Группа крови"))
        assertEquals("yoasobi", TrackMatching.normalize("YOASOBI - 夜に駆ける"))
    }

    @Test
    fun normalizeTitleStripsFeaturingAndVersionWords() {
        assertEquals(
            "song title",
            TrackMatching.normalizeTitle("Song Title (feat. Drake & Future)"),
        )
        assertEquals(
            "song title 2021",
            TrackMatching.normalizeTitle("Song Title - Remastered 2021"),
        )
        assertEquals(
            "hotel california live on mtv 1994",
            TrackMatching.normalizeTitle("Hotel California - Live On MTV / 1994"),
        )
    }

    @Test
    fun durationMatchesWithin45Seconds() {
        assertTrue(TrackMatching.durationMatches(180_000L, 200_000L))
        assertTrue(TrackMatching.durationMatches(180_000L, 225_000L))
        assertFalse(TrackMatching.durationMatches(180_000L, 226_000L))
        assertTrue(TrackMatching.durationMatches(null, 180_000L))
        assertTrue(TrackMatching.durationMatches(180_000L, null))
        assertTrue(TrackMatching.durationMatches(null, null))
    }

    @Test
    fun scoreRejectsVersionMismatch() {
        val score = TrackMatching.score(
            wantedTitle = "blinding lights acoustic",
            wantedArtists = listOf("the weeknd"),
            candidateTitle = "blinding lights",
            candidateArtist = "the weeknd",
            wantedDurationMs = 200_000L,
            candidateDurationMs = 200_000L,
        )
        assertEquals(Int.MIN_VALUE, score)
    }

    @Test
    fun scoreRejectsLowTitleOverlap() {
        val score = TrackMatching.score(
            wantedTitle = "completely different track name",
            wantedArtists = listOf("artist a"),
            candidateTitle = "totally unrelated song title",
            candidateArtist = "artist a",
            wantedDurationMs = 200_000L,
            candidateDurationMs = 200_000L,
        )
        assertEquals(Int.MIN_VALUE, score)
    }

    @Test
    fun scoreComputesOverlapAndDurationBonus() {
        val score = TrackMatching.score(
            wantedTitle = "hotel california",
            wantedArtists = listOf("eagles"),
            candidateTitle = "hotel california",
            candidateArtist = "eagles",
            wantedDurationMs = 390_000L,
            candidateDurationMs = 391_000L,
        )
        // titleOverlap: 2/2 = 1.0 -> 100
        // artistOverlap: 1/1 = 1.0 -> 60
        // duration matches -> +30
        // Total = 190
        assertEquals(190, score)
    }

    @Test
    fun bestSelectsMatchingCandidateAndPreservesTies() {
        val target = TrackMatching.Target(
            title = "Starboy",
            artists = listOf("The Weeknd", "Daft Punk"),
            album = "Starboy",
            durationMs = 230_000L,
        )
        val candidate1 = TrackMatching.Candidate(
            id = "1",
            title = "Starboy",
            artists = listOf("The Weeknd"),
            album = "Starboy",
            durationMs = 230_500L,
        )
        val candidate2 = TrackMatching.Candidate(
            id = "2",
            title = "Starboy",
            artists = listOf("The Weeknd"),
            album = "Starboy Deluxe",
            durationMs = 230_500L,
        )
        val candidate3 = TrackMatching.Candidate(
            id = "3",
            title = "Starboy (Kygo Remix)",
            artists = listOf("The Weeknd"),
            album = "Remixes",
            durationMs = 240_000L,
        )

        val best = TrackMatching.best(target, listOf(candidate1, candidate2, candidate3))
        assertNotNull(best)
        // Candidate 1 and Candidate 2 have the same score, candidate 1 comes first
        assertEquals("1", best?.id)
    }

    @Test
    fun bestReturnsNullWhenBelowThreshold() {
        val target = TrackMatching.Target(
            title = "Bohemian Rhapsody",
            artists = listOf("Queen"),
            album = "A Night at the Opera",
            durationMs = 354_000L,
        )
        val candidates = listOf(
            TrackMatching.Candidate(
                id = "bad1",
                title = "Another One Bites the Dust",
                artists = listOf("Queen"),
                album = "The Game",
                durationMs = 215_000L,
            ),
        )
        val best = TrackMatching.best(target, candidates)
        assertNull(best)
    }
}
