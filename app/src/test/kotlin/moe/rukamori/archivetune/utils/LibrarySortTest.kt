/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator

class LibrarySortTest {
    @Test
    fun sortsUsingTheSameLocaleAndPreservesOrderOfEqualKeys() = runBlocking {
        val tracks = listOf("Édith", "Chvrches", "Молчат Дома", "EDITH", "", "中島みゆき", "Édith")
            .mapIndexed { index, artist -> index to artist }
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val expected = tracks.sortedWith(compareBy(collator) { it.second })

        assertEquals(expected, tracks.sortedByLocalizedArtist { it.second })
    }
}
