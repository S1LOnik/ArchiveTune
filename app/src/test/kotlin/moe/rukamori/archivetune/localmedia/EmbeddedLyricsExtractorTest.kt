/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class EmbeddedLyricsExtractorTest {

    @Test
    fun extractsId3UsltLyrics() {
        val tag = buildId3UsltTag("Hello embedded lyrics!")
        val result = EmbeddedLyricsExtractor.extractFromBytes(tag, "mp3", "audio/mpeg")
        assertEquals("Hello embedded lyrics!", result)
    }

    @Test
    fun extractsId3SyltSyncedLyrics() {
        val tag = buildId3SyltTag(listOf(1000L to "Line 1", 3500L to "Line 2"))
        val result = EmbeddedLyricsExtractor.extractFromBytes(tag, "mp3", "audio/mpeg")
        assertEquals("[00:01.00]Line 1\n[00:03.50]Line 2", result)
    }

    @Test
    fun extractsFlacVorbisCommentLyrics() {
        val flac = buildFlacWithLyrics("Synced or plain flac lyrics")
        val result = EmbeddedLyricsExtractor.extractFromBytes(flac, "flac", "audio/flac")
        assertEquals("Synced or plain flac lyrics", result)
    }

    @Test
    fun extractsMp4Lyrics() {
        val mp4 = buildMp4WithLyrics("MP4 embedded lyrics")
        val result = EmbeddedLyricsExtractor.extractFromBytes(mp4, "m4a", "audio/mp4")
        assertEquals("MP4 embedded lyrics", result)
    }

    @Test
    fun emptyOrInvalidReturnsNull() {
        assertNull(EmbeddedLyricsExtractor.extractFromBytes(ByteArray(0), null, null))
        assertNull(EmbeddedLyricsExtractor.extractFromBytes(byteArrayOf(1, 2, 3), "mp3", "audio/mpeg"))
    }

    private fun buildId3UsltTag(lyrics: String): ByteArray {
        val payload = byteArrayOf(0, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0) +
            lyrics.toByteArray(Charsets.ISO_8859_1)
        val frameHeader = "USLT".toByteArray(Charsets.US_ASCII) +
            intToBytes(payload.size) +
            byteArrayOf(0, 0)
        val frame = frameHeader + payload
        val synchsafeSize = toSynchsafe(frame.size)
        return "ID3".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(3, 0, 0) +
            synchsafeSize +
            frame
    }

    private fun buildId3SyltTag(lines: List<Pair<Long, String>>): ByteArray {
        var payload = byteArrayOf(0, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 2, 1, 0)
        for ((ms, text) in lines) {
            payload += text.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + intToBytes(ms.toInt())
        }
        val frameHeader = "SYLT".toByteArray(Charsets.US_ASCII) +
            intToBytes(payload.size) +
            byteArrayOf(0, 0)
        val frame = frameHeader + payload
        val synchsafeSize = toSynchsafe(frame.size)
        return "ID3".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(3, 0, 0) +
            synchsafeSize +
            frame
    }

    private fun buildFlacWithLyrics(lyrics: String): ByteArray {
        val vendor = "test-flac".toByteArray(Charsets.UTF_8)
        val comment = "LYRICS=$lyrics".toByteArray(Charsets.UTF_8)
        val vorbisPayload = intToLeBytes(vendor.size) +
            vendor +
            intToLeBytes(1) +
            intToLeBytes(comment.size) +
            comment

        val flacHeader = "fLaC".toByteArray(Charsets.US_ASCII)
        val blockHeader = byteArrayOf(
            0x84.toByte(),
            ((vorbisPayload.size shr 16) and 0xFF).toByte(),
            ((vorbisPayload.size shr 8) and 0xFF).toByte(),
            (vorbisPayload.size and 0xFF).toByte(),
        )
        return flacHeader + blockHeader + vorbisPayload
    }

    private fun buildMp4WithLyrics(lyrics: String): ByteArray {
        val textBytes = lyrics.toByteArray(Charsets.UTF_8)
        val dataBox = intToBytes(8 + 8 + textBytes.size) +
            "data".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) +
            textBytes
        val lyrBox = intToBytes(8 + dataBox.size) +
            byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()) +
            dataBox
        val ilstBox = intToBytes(8 + lyrBox.size) +
            "ilst".toByteArray(Charsets.US_ASCII) +
            lyrBox
        val moovBox = intToBytes(8 + ilstBox.size) +
            "moov".toByteArray(Charsets.US_ASCII) +
            ilstBox
        return moovBox
    }

    private fun intToBytes(value: Int): ByteArray =
        byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )

    private fun intToLeBytes(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    private fun toSynchsafe(value: Int): ByteArray =
        byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte(),
        )
}
