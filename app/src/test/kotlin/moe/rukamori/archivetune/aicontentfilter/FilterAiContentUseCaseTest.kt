package moe.rukamori.archivetune.aicontentfilter

import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import org.junit.Assert.assertEquals
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

    private fun song(id: String, artist: Artist) =
        SongItem(id = id, title = "Shared title", artists = listOf(artist), thumbnail = "shared-cover")
}
