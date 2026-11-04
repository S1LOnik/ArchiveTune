/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.security.SpotifyAntiFraudGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaybackTest {

    @Test
    fun `test anti fraud guard rate limits rapid key requests`() {
        val guard = SpotifyAntiFraudGuard()
        assertTrue("First key request should be allowed", guard.canRequestNextKey())
        guard.recordKeyRequested()
        assertFalse("Immediate subsequent key request should be blocked to prevent 429", guard.canRequestNextKey())
    }

    @Test
    fun `test official client identity parameters`() {
        assertEquals("65b708073fc0480ea92a077233ca87bd", SpotifyAntiFraudGuard.OFFICIAL_CLIENT_ID)
        assertEquals("http://127.0.0.1:8989/login", SpotifyAntiFraudGuard.OFFICIAL_REDIRECT_URI)
        assertTrue(SpotifyAntiFraudGuard.OFFICIAL_DESKTOP_USER_AGENT.contains("Spotify/"))
    }

    @Test
    fun `test spotify track metadata creation`() {
        val track = SpotifyTrack(
            id = "4cOdK2wGLETKBW3PvgPWqT",
            name = "Never Gonna Give You Up",
            artists = listOf(moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist(id = "rick", name = "Rick Astley")),
            durationMs = 213000,
            explicit = false,
        )
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", track.id)
        assertEquals(213, track.durationMs / 1000)
    }

    @Test
    fun `test PKCE code verifier and challenge generation`() {
        val verifier = moe.rukamori.archivetune.spotify.session.SpotifyOAuthLoopbackServer.generateCodeVerifier()
        assertTrue("Verifier should be at least 43 chars", verifier.length >= 43)
        assertFalse("Verifier should not contain padding", verifier.contains("="))
        assertFalse("Verifier should be URL safe (no +)", verifier.contains("+"))
        assertFalse("Verifier should be URL safe (no /)", verifier.contains("/"))

        val challenge = moe.rukamori.archivetune.spotify.session.SpotifyOAuthLoopbackServer.generateCodeChallenge(verifier)
        assertTrue("Challenge should be non-empty", challenge.isNotEmpty())
        assertEquals("SHA-256 base64url length without padding should be 43", 43, challenge.length)
        assertFalse("Challenge should not contain padding", challenge.contains("="))

        // Deterministic check
        val challenge2 = moe.rukamori.archivetune.spotify.session.SpotifyOAuthLoopbackServer.generateCodeChallenge(verifier)
        assertEquals("Same verifier must produce same challenge", challenge, challenge2)
    }

    @Test
    fun `test OAuth authorization URL construction`() {
        val challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        val authUrl = moe.rukamori.archivetune.spotify.session.SpotifyOAuthLoopbackServer.buildAuthUrl(challenge)

        assertTrue("URL must point to Spotify accounts authorize", authUrl.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue("URL must contain client ID", authUrl.contains("client_id=65b708073fc0480ea92a077233ca87bd"))
        assertTrue("URL must contain code challenge", authUrl.contains("code_challenge=$challenge"))
        assertTrue("URL must specify S256 method", authUrl.contains("code_challenge_method=S256"))
        assertTrue("URL must specify code response type", authUrl.contains("response_type=code"))
        assertTrue("URL must contain streaming scope", authUrl.contains("streaming"))
    }

    @Test
    fun `test canonical spotify URI parsing`() {
        // Standard track URI
        assertEquals(
            "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri("spotify:track:4cOdK2wGLETKBW3PvgPWqT")
        )

        // Track URI with query params
        assertEquals(
            "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri("spotify:track:4cOdK2wGLETKBW3PvgPWqT?si=abc123xyz")
        )

        // Double slash track URI
        assertEquals(
            "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri("spotify://track/4cOdK2wGLETKBW3PvgPWqT")
        )

        // Double slash track URI with query params
        assertEquals(
            "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri("spotify://track/4cOdK2wGLETKBW3PvgPWqT?si=abc123xyz")
        )

        // Episode URI
        assertEquals(
            "spotify:episode:512FacNeV157a4Qu26Ir7o",
            moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri("spotify:episode:512FacNeV157a4Qu26Ir7o")
        )

        // Web open.spotify.com URL
        assertEquals(
            "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=foobar")
        )

        // Null and invalid inputs
        org.junit.Assert.assertNull(moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri(null))
        org.junit.Assert.assertNull(moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri(""))
        org.junit.Assert.assertNull(moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.parseCanonicalSpotifyUri("https://example.com/audio.mp3"))
    }
}
