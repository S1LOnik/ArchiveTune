/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMixPlannerTest {

    private val track1 = AutoMixTrackInfo(
        id = "1",
        durationMs = 200_000L,
        title = "Track 1",
        artist = "Artist A",
        album = "Album 1",
    )

    private val track2 = AutoMixTrackInfo(
        id = "2",
        durationMs = 210_000L,
        title = "Track 2",
        artist = "Artist B",
        album = "Album 1",
    )

    private val analysis1 = AutoMixAnalysis(
        durationMs = 200_000L,
        bpm = 124.0,
        beatIntervalMs = 483.87,
        beatConfidence = 0.85,
        downbeatPhaseMs = 120.0,
        audibleStartMs = 500L,
        introEndMs = 8_000L,
        outroStartMs = 180_000L,
        contentEndMs = 195_000L,
        finalFadeOnsetMs = 188_000L,
        mixInCandidatesMs = listOf(8_000L, 16_000L),
        mixOutCandidatesMs = listOf(188_000L),
        energyCurve = List(800) { 0.5f },
        vocalActivity = List(800) { 0.1f },
    )

    private val analysis2 = AutoMixAnalysis(
        durationMs = 210_000L,
        bpm = 125.0,
        beatIntervalMs = 480.0,
        beatConfidence = 0.88,
        downbeatPhaseMs = 100.0,
        audibleStartMs = 300L,
        introEndMs = 7_500L,
        outroStartMs = 190_000L,
        contentEndMs = 205_000L,
        finalFadeOnsetMs = 198_000L,
        mixInCandidatesMs = listOf(7_500L, 15_000L),
        mixOutCandidatesMs = listOf(198_000L),
        energyCurve = List(840) { 0.6f },
        vocalActivity = List(840) { 0.1f },
    )

    @Test
    fun planGaplessAlbumReturnsGaplessStyle() {
        val plan = AutoMixPlanner.plan(
            current = analysis1,
            next = analysis2,
            currentTrack = track1,
            nextTrack = track2,
            positionMs = 100_000L,
            fallbackFadeMs = 6_000L,
            minFadeMs = 2_000L,
            gaplessAlbum = true,
        )

        assertEquals(AutoMixStyle.GAPLESS, plan.style)
        assertEquals(0L, plan.fadeMs)
        assertEquals(track1.durationMs, plan.transitionStartMs)
        assertEquals("same-album sequence", plan.reason)
        assertFalse(plan.bassSwap)
    }

    @Test
    fun planNullAnalysisReturnsFallback() {
        val plan = AutoMixPlanner.plan(
            current = null,
            next = null,
            currentTrack = track1,
            nextTrack = track2,
            positionMs = 100_000L,
            fallbackFadeMs = 6_000L,
            minFadeMs = 2_000L,
            gaplessAlbum = false,
        )

        assertEquals(AutoMixStyle.EQUAL_POWER, plan.style)
        assertEquals(6_000L, plan.fadeMs)
        assertEquals("no analysis", plan.reason)
        assertFalse(plan.markerVisible)
    }

    @Test
    fun planBeatMatchedCreatesDjBlend() {
        val plan = AutoMixPlanner.plan(
            current = analysis1,
            next = analysis2,
            currentTrack = track1,
            nextTrack = track2,
            positionMs = 100_000L,
            fallbackFadeMs = 6_000L,
            minFadeMs = 2_000L,
            gaplessAlbum = false,
        )

        assertEquals(AutoMixStyle.DJ_BLEND, plan.style)
        assertTrue(plan.bassSwap)
        assertTrue(plan.markerVisible)
        assertTrue(plan.incomingPlaybackRate in 0.96..1.04)
        assertTrue(plan.fadeMs >= 2_000L)
    }

    @Test
    fun planFilterRideWhenConfidenceMedium() {
        val mediumAnalysis1 = analysis1.copy(beatConfidence = 0.35, bpm = 120.0)
        val mediumAnalysis2 = analysis2.copy(beatConfidence = 0.35, bpm = 150.0) // 25% tempo difference -> not beat-matchable

        val plan = AutoMixPlanner.plan(
            current = mediumAnalysis1,
            next = mediumAnalysis2,
            currentTrack = track1,
            nextTrack = track2,
            positionMs = 100_000L,
            fallbackFadeMs = 6_000L,
            minFadeMs = 2_000L,
            gaplessAlbum = false,
        )

        assertEquals(AutoMixStyle.DJ_FILTER, plan.style)
        assertFalse(plan.bassSwap)
        assertEquals(1.0, plan.filterSweep, 0.001)
        assertEquals("filter ride", plan.reason)
    }

    @Test
    fun planLowConfidenceReturnsFallback() {
        val lowAnalysis1 = analysis1.copy(beatConfidence = 0.10)
        val lowAnalysis2 = analysis2.copy(beatConfidence = 0.10)

        val plan = AutoMixPlanner.plan(
            current = lowAnalysis1,
            next = lowAnalysis2,
            currentTrack = track1,
            nextTrack = track2,
            positionMs = 100_000L,
            fallbackFadeMs = 6_000L,
            minFadeMs = 2_000L,
            gaplessAlbum = false,
        )

        assertEquals(AutoMixStyle.EQUAL_POWER, plan.style)
        assertEquals("low beat confidence", plan.reason)
    }
}
