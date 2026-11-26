/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoilBitmapLoaderTest {
    @Test
    fun buildsYouTubeFallbackChainWithHqDefaultBeforeMqDefault() {
        val candidates =
            buildArtworkCandidateUrls("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg")
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg",
            ),
            candidates,
        )
    }

    @Test
    fun buildsSoundCloudFallbackChain() {
        val candidates =
            buildArtworkCandidateUrls("https://i1.sndcdn.com/artworks-000123456-abcdef-t500x500.jpg")
        assertEquals(
            listOf(
                "https://i1.sndcdn.com/artworks-000123456-abcdef-t500x500.jpg",
                "https://i1.sndcdn.com/artworks-000123456-abcdef-t300x300.jpg",
                "https://i1.sndcdn.com/artworks-000123456-abcdef-large.jpg",
            ),
            candidates,
        )
    }

    @Test
    fun buildsGoogleCdnFallbackToMediumSize() {
        val candidates =
            buildArtworkCandidateUrls("https://lh3.googleusercontent.com/abc=w1080-h1080-p-l90-rj")
        assertEquals(
            listOf(
                "https://lh3.googleusercontent.com/abc=w1080-h1080-p-l90-rj",
                "https://lh3.googleusercontent.com/abc=w544-h544-p-l90-rj",
            ),
            candidates,
        )
    }

    @Test
    fun preservesLocalContentAndFileUrisWithoutExtraCandidates() {
        val contentUri = "content://moe.rukamori.archivetune.FileProvider/local_artwork/123.jpg"
        assertEquals(listOf(contentUri), buildArtworkCandidateUrls(contentUri))
        assertEquals(emptyList<String>(), buildArtworkCandidateUrls("   "))
    }

    @Test
    fun classifiesPermanentVsTransientHttpErrors() {
        assertTrue(isPermanentArtworkHttpError(404))
        assertTrue(isPermanentArtworkHttpError(403))
        assertTrue(isPermanentArtworkHttpError(410))
        assertFalse(isPermanentArtworkHttpError(408))
        assertFalse(isPermanentArtworkHttpError(429))
        assertFalse(isPermanentArtworkHttpError(503))
        assertFalse(isPermanentArtworkHttpError(null))
    }
}
