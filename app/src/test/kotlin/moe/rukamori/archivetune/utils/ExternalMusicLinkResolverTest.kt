package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalMusicLinkResolverTest {
    @Test
    fun recognizesSpotifyAndSoundCloudHosts() {
        listOf(
            "open.spotify.com",
            "spotify.link",
            "soundcloud.com",
            "www.soundcloud.com",
            "m.soundcloud.com",
            "on.soundcloud.com",
        ).forEach { assertTrue(it, ExternalMusicLinkResolver.isExternalMusicHost(it)) }
    }

    @Test
    fun hostMatchingIsCaseInsensitive() {
        assertTrue(ExternalMusicLinkResolver.isExternalMusicHost("Open.Spotify.COM"))
    }

    @Test
    fun rejectsOtherHostsAndNull() {
        assertFalse(ExternalMusicLinkResolver.isExternalMusicHost(null))
        assertFalse(ExternalMusicLinkResolver.isExternalMusicHost("music.youtube.com"))
        assertFalse(ExternalMusicLinkResolver.isExternalMusicHost("evil-soundcloud.com"))
        assertFalse(ExternalMusicLinkResolver.isExternalMusicHost("soundcloud.com.evil.io"))
    }

    @Test
    fun metaContentReadsOpenGraphTagAndDecodesEntities() {
        val html =
            """<html><head><meta property="og:title" content="Rock &amp; Roll &#039;n&#039; Blues"/></head></html>"""
        assertEquals("Rock & Roll 'n' Blues", ExternalMusicLinkResolver.metaContent(html, "og:title"))
    }

    @Test
    fun metaContentSupportsSingleQuotes() {
        val html = """<meta property='og:description' content='Artist · Album'>"""
        assertEquals("Artist · Album", ExternalMusicLinkResolver.metaContent(html, "og:description"))
    }

    @Test
    fun metaContentReturnsNullForMissingOrBlank() {
        assertNull(ExternalMusicLinkResolver.metaContent("<html></html>", "og:title"))
        assertNull(ExternalMusicLinkResolver.metaContent("""<meta property="og:title" content="  ">""", "og:title"))
    }

    @Test
    fun spotifyArtistIsFirstDescriptionPart() {
        assertEquals(
            "Daft Punk",
            ExternalMusicLinkResolver.spotifyArtistFromDescription("Daft Punk · Discovery · Song · 2001", "One More Time"),
        )
    }

    @Test
    fun spotifyArtistIgnoredWhenEqualToTitleOrBlank() {
        assertNull(ExternalMusicLinkResolver.spotifyArtistFromDescription("Same", "same"))
        assertNull(ExternalMusicLinkResolver.spotifyArtistFromDescription("  · x", "Title"))
        assertNull(ExternalMusicLinkResolver.spotifyArtistFromDescription(null, "Title"))
    }

    @Test
    fun soundCloudTitleStripsAuthorSuffix() {
        assertEquals("Song", ExternalMusicLinkResolver.soundCloudTitleWithoutAuthor("Song by Artist", "Artist"))
    }

    @Test
    fun soundCloudTitleKeepsInnerByWhenAuthorMatches() {
        assertEquals(
            "Stand by Me",
            ExternalMusicLinkResolver.soundCloudTitleWithoutAuthor("Stand by Me by Ben", "Ben"),
        )
    }

    @Test
    fun soundCloudTitleFallsBackToLastByWhenAuthorDiffers() {
        assertEquals(
            "Stand by Me",
            ExternalMusicLinkResolver.soundCloudTitleWithoutAuthor("Stand by Me by Someone", "Other"),
        )
        assertEquals("Plain", ExternalMusicLinkResolver.soundCloudTitleWithoutAuthor("Plain", null))
    }
}
