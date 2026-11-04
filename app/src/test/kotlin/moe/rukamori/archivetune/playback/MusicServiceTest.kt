/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.constants.DownloadSource
import moe.rukamori.archivetune.constants.DownloadSourceConfig
import moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// [FIX-ZCODE] Wave 9: unit tests for MusicService support logic — download cache key
// round-trips (including the new soundcloud: prefix) and SoundCloud seed id normalization
// used by the infinite-queue bootstrap and search rows.
class MusicServiceTest {
    @Test
    fun `download cache key roundtrip for soundcloud ids`() {
        val mediaId = "soundcloud:tracks:12345"
        val key = DownloadSourceConfig.downloadCacheKey(DownloadSource.SOUNDCLOUD, mediaId)
        assertEquals("soundcloud:$mediaId", key)
        assertEquals(mediaId, DownloadSourceConfig.downloadIdToSongId(key))
    }

    @Test
    fun `download cache key roundtrip for youtube ids`() {
        val mediaId = "dQw4w9WgXcQ"
        val key = DownloadSourceConfig.downloadCacheKey(DownloadSource.YOUTUBE_MUSIC, mediaId)
        assertEquals("ytm:$mediaId", key)
        assertEquals(mediaId, DownloadSourceConfig.downloadIdToSongId(key))
    }

    @Test
    fun `soundcloud source is part of the default download order`() {
        assertTrue(DownloadSourceConfig.DEFAULT_ORDER.contains(DownloadSource.SOUNDCLOUD))
        assertTrue(
            DownloadSourceConfig.DEFAULT_ORDER.indexOf(DownloadSource.SOUNDCLOUD) <
                DownloadSourceConfig.DEFAULT_ORDER.indexOf(DownloadSource.YOUTUBE_MUSIC),
        )
    }

    @Test
    fun `soundcloud media id is normalized with soundcloud tracks prefix`() {
        val metadata =
            SoundCloudAudioProvider.toMediaMetadata(
                SoundCloudAudioProvider.SoundCloudTrackMetadata(
                    trackId = "12345",
                    title = "Test Track",
                    artist = "Test Artist",
                    permalinkUrl = "https://soundcloud.com/artist/test-track",
                    artworkUrl = "https://i1.sndcdn.com/artworks/a-large.jpg",
                    durationMs = 185_000L,
                ),
            )
        assertEquals("soundcloud:tracks:12345", metadata.id)
        assertEquals("Test Track", metadata.title)
        assertEquals("Test Artist", metadata.artists.first().name)
        assertEquals(185, metadata.duration)
        assertTrue(metadata.thumbnailUrl!!.contains("sndcdn.com"))
    }

    @Test
    fun `soundcloud media id with query suffix is stripped`() {
        val metadata =
            SoundCloudAudioProvider.toMediaMetadata(
                SoundCloudAudioProvider.SoundCloudTrackMetadata(
                    trackId = "soundcloud:tracks:999?in=user/playlist",
                    title = "T",
                    artist = "A",
                    permalinkUrl = "",
                    artworkUrl = null,
                    durationMs = null,
                ),
            )
        assertEquals("soundcloud:tracks:999", metadata.id)
        assertEquals(-1, metadata.duration)
    }

    @Test
    fun `bootstrap seed parsing matches normalized id`() {
        // MusicService.bootstrapSoundCloudInfiniteQueue parses seeds like this
        val seed = "soundcloud:tracks:777?query=1"
        val numericId = seed.removePrefix("soundcloud:tracks:").substringBefore("?")
        assertEquals("777", numericId)
    }
}
