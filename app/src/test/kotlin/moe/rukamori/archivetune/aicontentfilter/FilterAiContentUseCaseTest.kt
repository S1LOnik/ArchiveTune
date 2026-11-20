package moe.rukamori.archivetune.aicontentfilter

import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAiContentUseCaseTest {
    @Test
    fun matchesChannelIdFromLinkAndIgnoresTheCoverAndTitle() {
        val channel = "UCCmPdCuqNXqoPjWkYRaWgwQ"
        val policy = setOf(authorFilterKey("https://www.youtube.com/channel/$channel"))
        val listed = song("listed", Artist(name = "Listed", id = channel))
        val other = song("other", Artist(name = "Listed", id = channel.lowercase()))
        val podcasts = listed.copy(id = "podcast", isPodcast = true)

        assertEquals(listOf(other, podcasts), FilterAiContentUseCase()(listOf(listed, other, podcasts), policy))
    }

    @Test
    fun matchesExactNamesWithoutCollapsingPunctuation() {
        val policy = setOf(authorFilterKey("  AI  Project  "))
        val listed = song("listed", Artist(name = "Ai Project", id = null))
        val similar = song("similar", Artist(name = "AI-Project", id = null))

        assertEquals(listOf(similar), FilterAiContentUseCase()(listOf(listed, similar), policy))
    }

    @Test
    fun `1-tap toggle adds canonical channel URL, detects matches by name or ID, and removes cleanly`() {
        val channelId = "UC1234567890abcdefghij"
        val initial = setOf("Existing Author")

        assertFalse(isArtistInAiFilter(initial, artistName = "Synth Bot", artistId = channelId, channelId = channelId))

        val added = toggleArtistInAiFilter(initial, artistName = "Synth Bot", artistId = channelId, channelId = channelId)
        assertTrue(added.added)
        assertEquals("https://www.youtube.com/channel/$channelId", added.entry)
        assertTrue("Existing Author" in added.updatedAuthors)
        assertTrue(isArtistInAiFilter(added.updatedAuthors, artistName = "Different Alias", artistId = channelId))

        val removed = toggleArtistInAiFilter(added.updatedAuthors, artistName = "Synth Bot", artistId = channelId, channelId = channelId)
        assertFalse(removed.added)
        assertEquals(initial, removed.updatedAuthors)
    }

    @Test
    fun `toggle by display name removes duplicate forms and formats readable chip labels`() {
        val initial = setOf("  Synth   Bot ", "Other Artist")
        assertTrue(isArtistInAiFilter(initial, artistName = "synth bot", artistId = "local_id"))

        val removed = toggleArtistInAiFilter(initial, artistName = "synth bot", artistId = "local_id")
        assertFalse(removed.added)
        assertEquals(setOf("Other Artist"), removed.updatedAuthors)

        assertEquals(
            "UC123456…ghij",
            formatAiAuthorChipLabel("https://www.youtube.com/channel/UC1234567890abcdefghij"),
        )
        assertEquals("Synth Bot", formatAiAuthorChipLabel("  Synth Bot  "))
    }

    private fun song(id: String, artist: Artist) =
        SongItem(id = id, title = "Shared title", artists = listOf(artist), thumbnail = "shared-cover")
}
