/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlaylistPaginationTest {
    @Test
    fun advancesPastFilteredEntriesIncludingAWholeFilteredPage() {
        val shortPage = SpotifyPaging(listOf("playlist"), total = 125, limit = 50)
        val emptyPage = SpotifyPaging<String>(emptyList(), total = 125, limit = 50)
        assertEquals(50, shortPage.nextSpotifyPlaylistOffset(0, 50))
        assertEquals(100, emptyPage.nextSpotifyPlaylistOffset(50, 50))
        assertNull(shortPage.nextSpotifyPlaylistOffset(100, 50))
    }

    @Test
    fun honorsServerPageSizeAndStopsOnAnEmptyPageWithoutATotal() {
        val page = SpotifyPaging(listOf("playlist"), total = 47, limit = 20)
        assertEquals(20, page.nextSpotifyPlaylistOffset(0, 50))
        assertNull(SpotifyPaging<String>(limit = 50).nextSpotifyPlaylistOffset(0, 50))
    }
}
