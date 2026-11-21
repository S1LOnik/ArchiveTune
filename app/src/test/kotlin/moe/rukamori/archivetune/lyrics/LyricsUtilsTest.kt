package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {

    @Test
    fun testParseStandardLrc() {
        val lrc = """
            [00:10.50]Hello world
            [00:15.00]Second line
        """.trimIndent()

        val entries = LyricsUtils.parseLyrics(lrc)
        assertEquals(2, entries.size)
        assertEquals(10500L, entries[0].time)
        assertEquals("Hello world", entries[0].text)
        assertEquals(15000L, entries[1].time)
        assertEquals("Second line", entries[1].text)
    }

    @Test
    fun testParseMultiTimestampLrc() {
        val lrc = "[00:10.00][00:20.00]Chorus line"
        val entries = LyricsUtils.parseLyrics(lrc)
        assertEquals(2, entries.size)
        assertEquals(10000L, entries[0].time)
        assertEquals("Chorus line", entries[0].text)
        assertEquals(20000L, entries[1].time)
        assertEquals("Chorus line", entries[1].text)
    }

    @Test
    fun testParseEnhancedLrcAndClamping() {
        val lrc = """
            [00:01.00]<00:01.00>One <00:01.50>Two <00:02.00>Three
            [00:05.00]Next line
        """.trimIndent()

        val entries = LyricsUtils.parseLyrics(lrc)
        assertEquals(2, entries.size)
        val words = entries[0].words
        assertNotNull(words)
        assertEquals(3, words!!.size)
        assertEquals("One ", words[0].text)
        assertEquals(1.0, words[0].startTime, 0.001)
        assertEquals(1.5, words[0].endTime, 0.001)
        assertEquals("Two ", words[1].text)
        assertEquals(1.5, words[1].startTime, 0.001)
        assertEquals(2.0, words[1].endTime, 0.001)
        assertEquals("Three", words[2].text)
        assertEquals(2.0, words[2].startTime, 0.001)
        // Last word duration clamped: default was 2.0 + 0.6 = 2.6s, which is < next line (5.0s),
        // but since words.size > 1, lastEndMs remains 2.6s (2.6 <= 5.0)
        assertEquals(2.6, words[2].endTime, 0.001)
    }

    @Test
    fun testTranslationMerging() {
        val lrc = """
            [00:10.00]Original line
            [00:10.00]Переведенная строка
            [00:20.00]Second original
        """.trimIndent()

        val entries = LyricsUtils.parseLyrics(lrc)
        assertEquals(2, entries.size)
        assertEquals(10000L, entries[0].time)
        assertEquals("Original line", entries[0].text)
        assertEquals("Переведенная строка", entries[0].providerTranslationText)
        assertEquals(20000L, entries[1].time)
        assertNull(entries[1].providerTranslationText)
    }

    @Test
    fun testFindCurrentLineIndex() {
        val entries = listOf(
            LyricsEntry(time = 1000L, text = "Line 1", durationMs = 1000L),
            LyricsEntry(time = 2500L, text = "Line 2", durationMs = 1000L),
            LyricsEntry(time = 4000L, text = "Line 3", durationMs = 2000L),
        )

        assertEquals(0, LyricsUtils.findCurrentLineIndex(entries, 500L))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(entries, 1500L)) // within gap (1000..2500), but before lead threshold
        // At position 2300ms + 300ms = 2600ms >= 2500ms -> jumps to line 2 (index 1)
        assertEquals(1, LyricsUtils.findCurrentLineIndex(entries, 2300L, leadMs = 300L))
        assertEquals(1, LyricsUtils.findCurrentLineIndex(entries, 3000L))
        assertEquals(2, LyricsUtils.findCurrentLineIndex(entries, 5000L))
    }

    @Test
    fun testParseQrcFormat() {
        val qrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [1000,2000]Hello(1000,1000) World(2000,1000)
            [1000,2000]Привет Мир
            [4000,1500]Solo(4000,1500)
        """.trimIndent()

        val entries = LyricsUtils.parseLyrics(qrc)
        assertEquals(2, entries.size)
        assertEquals(1000L, entries[0].time)
        assertEquals("Hello World", entries[0].text)
        assertEquals("Привет Мир", entries[0].providerTranslationText)
        assertEquals(2, entries[0].words?.size)
        assertEquals(4000L, entries[1].time)
        assertEquals("Solo", entries[1].text)
    }

    @Test
    fun testParseYrcInlineWordTiming() {
        val yrc = "[1000,2000]Hello(1000,500) world(1500,500)"
        val entries = LyricsUtils.parseLyrics(yrc)
        assertEquals(1, entries.size)
        assertEquals(1000L, entries[0].time)
        assertEquals("Hello world", entries[0].text)
    }
}
