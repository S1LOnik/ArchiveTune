/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.fallback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// [FIX-ZCODE] Wave 9: unit tests for the fallback matrix routing rules.
class UniversalFallbackMatrixTest {
    @Test
    fun `detectSourceOf routes spotify media ids to SPOTIFY`() {
        assertEquals(AudioSourceKind.SPOTIFY, UniversalFallbackMatrix.detectSourceOf("spotify:track:abc123"))
        assertEquals(AudioSourceKind.SPOTIFY, UniversalFallbackMatrix.detectSourceOf("spotify//track/abc123"))
    }

    @Test
    fun `detectSourceOf routes soundcloud media ids to SOUNDCLOUD`() {
        assertEquals(AudioSourceKind.SOUNDCLOUD, UniversalFallbackMatrix.detectSourceOf("soundcloud:tracks:12345"))
        assertEquals(AudioSourceKind.SOUNDCLOUD, UniversalFallbackMatrix.detectSourceOf("soundcloud:tracks:12345?query=1"))
        assertEquals(AudioSourceKind.SOUNDCLOUD, UniversalFallbackMatrix.detectSourceOf("soundcloud-hls:AbCdEf"))
    }

    @Test
    fun `detectSourceOf routes everything else to YOUTUBE`() {
        assertEquals(AudioSourceKind.YOUTUBE, UniversalFallbackMatrix.detectSourceOf("dQw4w9WgXcQ"))
        assertEquals(AudioSourceKind.YOUTUBE, UniversalFallbackMatrix.detectSourceOf(""))
        assertEquals(AudioSourceKind.YOUTUBE, UniversalFallbackMatrix.detectSourceOf("local:song123"))
    }

    @Test
    fun `soundcloud ids are not misclassified as spotify`() {
        assertEquals(AudioSourceKind.SOUNDCLOUD, UniversalFallbackMatrix.detectSourceOf("soundcloud:tracks:1"))
    }

    @Test
    fun `latency monitor reports healthy servers below the degraded threshold`() {
        val monitor = LatencyServerMonitor()
        // Default warm-up latencies (60/40/70ms) are far below the 800ms threshold.
        assertTrue(monitor.isSpotifyHealthy())
        assertTrue(monitor.isYouTubeHealthy())
        assertTrue(monitor.isSoundCloudHealthy())
    }

    @Test
    fun `latency degraded threshold is 800 ms`() {
        assertEquals(800L, LatencyServerMonitor.LATENCY_DEGRADED_THRESHOLD_MS)
    }

    @Test
    fun `fallback result carries source and metadata`() {
        val result =
            FallbackResult(
                newMediaId = "spotify:track:abc",
                source = AudioSourceKind.SPOTIFY,
                preservedMetadata = buildPreservedMetadata(),
                statusMessage = "Стрим переключен на Spotify",
            )
        assertEquals(AudioSourceKind.SPOTIFY, result.source)
        assertEquals("spotify:track:abc", result.newMediaId)
        assertEquals("Test Song", result.preservedMetadata.title)
        assertFalse(result.statusMessage.isBlank())
    }

    @Test
    fun `isCandidateAcceptable rejects original mix when youtube track is hardstyle`() {
        val original = buildPreservedMetadata().copy(title = "Denzel Curry - Ultimate (Hardstyle Remix)", duration = 180)
        val spotifyOrig = moe.rukamori.archivetune.spotify.models.SpotifyTrack(
            id = "123",
            name = "Ultimate - Original Mix",
            artists = listOf(moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist("a1", "Denzel Curry")),
            album = moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum("alb1", "Imperial"),
            durationMs = 180_000,
        )
        assertFalse(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyOrig, isMaxQuality = true))
        assertFalse(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyOrig, isMaxQuality = false))
    }

    @Test
    fun `isCandidateAcceptable accepts candidate when both are hardstyle`() {
        val original = buildPreservedMetadata().copy(title = "Showtek - FTS (Hardstyle Edit)", duration = 180)
        val spotifyHardstyle = moe.rukamori.archivetune.spotify.models.SpotifyTrack(
            id = "124",
            name = "FTS (Hardstyle Edit)",
            artists = listOf(moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist("a2", "Showtek")),
            album = moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum("alb2", "Hardstyle"),
            durationMs = 182_000,
        )
        assertTrue(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyHardstyle, isMaxQuality = true))
        assertTrue(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyHardstyle, isMaxQuality = false))
    }

    @Test
    fun `isCandidateAcceptable rejects remix candidate when original is clean standard track`() {
        val original = buildPreservedMetadata().copy(title = "The Weeknd - Blinding Lights", duration = 200)
        val spotifyRemix = moe.rukamori.archivetune.spotify.models.SpotifyTrack(
            id = "125",
            name = "Blinding Lights (Major Lazer Remix)",
            artists = listOf(moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist("a3", "The Weeknd")),
            album = moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum("alb3", "After Hours"),
            durationMs = 200_000,
        )
        assertFalse(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyRemix, isMaxQuality = true))
        assertFalse(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyRemix, isMaxQuality = false))
    }

    @Test
    fun `isCandidateAcceptable rejects candidate when duration diverges significantly`() {
        val original = buildPreservedMetadata().copy(title = "Standard Track", duration = 200)
        val spotifyTooLong = moe.rukamori.archivetune.spotify.models.SpotifyTrack(
            id = "126",
            name = "Standard Track",
            artists = listOf(moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist("a4", "Artist")),
            album = moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum("alb4", "Album"),
            durationMs = 260_000, // 60s diff
        )
        assertFalse(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyTooLong, isMaxQuality = true))
        assertFalse(UniversalFallbackMatrix.isCandidateAcceptable(original, spotifyTooLong, isMaxQuality = false))
    }

    private fun buildPreservedMetadata() =
        moe.rukamori.archivetune.models.MediaMetadata(
            id = "spotify:track:abc",
            title = "Test Song",
            artists = listOf(moe.rukamori.archivetune.models.MediaMetadata.Artist(id = null, name = "Test Artist")),
            duration = 180,
        )
}
