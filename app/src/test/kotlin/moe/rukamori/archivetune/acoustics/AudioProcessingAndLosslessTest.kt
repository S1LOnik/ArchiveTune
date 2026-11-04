/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.acoustics

import moe.rukamori.archivetune.metadata.IsrcResolver
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.MediaUrlCanonicalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioProcessingAndLosslessTest {

    @Test
    fun `test FFT single tone peak frequency localization`() {
        val n = 4096
        val sampleRate = 44100
        val targetFreq = 1000.0 // 1 kHz test tone
        val re = FloatArray(n) { i -> sin(2.0 * PI * targetFreq * i / sampleRate).toFloat() }
        val im = FloatArray(n)

        AcousticQualityAuditor.fft(re, im)

        var maxMag = 0f
        var maxBin = 0
        for (bin in 0 until n / 2) {
            val mag = kotlin.math.sqrt(re[bin] * re[bin] + im[bin] * im[bin])
            if (mag > maxMag) {
                maxMag = mag
                maxBin = bin
            }
        }

        val detectedFreq = maxBin.toDouble() * sampleRate / n
        // Should detect ~1000 Hz within bin resolution (44100 / 4096 ~= 10.76 Hz)
        assertEquals(1000.0, detectedFreq, 12.0)
    }

    @Test
    fun `test Spectral Audit identifies true lossless vs lowpass brickwall`() {
        val sampleRate = 44100
        val durationSamples = sampleRate * 5 // 5 seconds of audio

        // 1. Lossless signal with energy reaching up to 21 kHz
        val losslessPcm = FloatArray(durationSamples) { i ->
            var sample = 0f
            // Combine fundamental + rich harmonic content up to 21.5 kHz
            for (f in intArrayOf(440, 1000, 5000, 10000, 16000, 19000, 20800)) {
                sample += sin(2.0 * PI * f * i / sampleRate).toFloat() * 0.14f
            }
            sample
        }

        val losslessReport = AcousticQualityAuditor.analyzePcm(
            AcousticQualityAuditor.DecodedPcm(losslessPcm, sampleRate)
        )
        assertNotNull(losslessReport)
        assertTrue(
            "Lossless audio must have high cutoff (>= 20 kHz), was ${losslessReport.cutoffHz}",
            losslessReport.cutoffHz >= 20000f,
        )
        assertEquals(AcousticVerdict.TRUE_LOSSLESS, losslessReport.verdict)
        assertTrue(losslessReport.isTrueLossless)

        // 2. Brickwall low-pass signal cut off at 15 kHz (typical MP3 128 kbps upscale)
        val lossyPcm = FloatArray(durationSamples) { i ->
            var sample = 0f
            for (f in intArrayOf(440, 1000, 5000, 10000, 14500)) {
                sample += sin(2.0 * PI * f * i / sampleRate).toFloat() * 0.2f
            }
            sample
        }

        val lossyReport = AcousticQualityAuditor.analyzePcm(
            AcousticQualityAuditor.DecodedPcm(lossyPcm, sampleRate)
        )
        assertNotNull(lossyReport)
        assertTrue(
            "Low-pass audio cutoff must be <= 16 kHz, was ${lossyReport.cutoffHz}",
            lossyReport.cutoffHz <= 16000f,
        )
        assertEquals(AcousticVerdict.SUSPECTED_FAKE, lossyReport.verdict)
        assertFalse(lossyReport.isTrueLossless)
    }

    @Test
    fun `test MediaUrlCanonicalizer transforms platforms correctly`() {
        // Spotify track
        val spotifyMeta = MediaMetadata(
            id = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            title = "Never Gonna Give You Up",
            artists = emptyList(),
            duration = 213,
        )
        assertEquals(
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            MediaUrlCanonicalizer.canonicalUrl(spotifyMeta)
        )

        // Spotify episode
        val spotifyEpisodeMeta = MediaMetadata(
            id = "spotify:episode:512FacNeV157a4Qu26Ir7o",
            title = "Podcast Episode",
            artists = emptyList(),
            duration = 1800,
        )
        assertEquals(
            "https://open.spotify.com/episode/512FacNeV157a4Qu26Ir7o",
            MediaUrlCanonicalizer.canonicalUrl(spotifyEpisodeMeta)
        )

        // SoundCloud track ID
        val soundCloudMeta = MediaMetadata(
            id = "soundcloud:tracks:99887766",
            title = "SoundCloud Track",
            artists = emptyList(),
            duration = 180,
        )
        assertEquals(
            "https://soundcloud.com/tracks/99887766",
            MediaUrlCanonicalizer.canonicalUrl(soundCloudMeta)
        )

        // Standard YouTube track ID
        val ytMeta = MediaMetadata(
            id = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artists = emptyList(),
            duration = 213,
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            MediaUrlCanonicalizer.canonicalUrl(ytMeta)
        )

        // Direct web URL should remain intact
        val webMeta = MediaMetadata(
            id = "https://soundcloud.com/artist/song",
            title = "Direct URL",
            artists = emptyList(),
            duration = 120,
        )
        assertEquals(
            "https://soundcloud.com/artist/song",
            MediaUrlCanonicalizer.canonicalUrl(webMeta)
        )
    }

    @Test
    fun `test IsrcResolver normalizes valid and invalid ISRCs`() {
        // Valid ISRC with dashes
        assertEquals("USUM71703861", IsrcResolver.normalizeIsrc("US-UM7-17-03861"))

        // Valid ISRC raw
        assertEquals("GBAYE0601498", IsrcResolver.normalizeIsrc("gbaye0601498"))

        // Invalid ISRCs
        org.junit.Assert.assertNull(IsrcResolver.normalizeIsrc(""))
        org.junit.Assert.assertNull(IsrcResolver.normalizeIsrc(null))
        org.junit.Assert.assertNull(IsrcResolver.normalizeIsrc("INVALID123"))
        org.junit.Assert.assertNull(IsrcResolver.normalizeIsrc("123456789012"))
    }

    @Test
    fun `test SoundCloudAudioProvider HLS uri encoding and detection`() {
        val streamUrl = "https://cf-media.sndcdn.com/stream/sample.m3u8?token=xyz"
        val hlsUri = moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider.hlsStreamUri(streamUrl)
        assertTrue(hlsUri.startsWith("soundcloud-hls:"))

        assertTrue(moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider.isSoundCloudUrl("https://soundcloud.com/artist/track"))
        assertFalse(moe.rukamori.archivetune.soundcloud.SoundCloudAudioProvider.isSoundCloudUrl("https://spotify.com/track/123"))
    }
}
