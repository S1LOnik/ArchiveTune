/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.playback.automix.SmartDjAutomixManager
import moe.rukamori.archivetune.playback.automix.TrackRhythm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDjAutomixTest {

    private val automixManager = SmartDjAutomixManager()

    @Test
    fun alignDurationToPhrase_selectsClosestMusicalBoundary() {
        // At 120 BPM: beat is 500ms, bar is 2000ms.
        // Candidates are 2 bars (4000ms), 4 bars (8000ms), 8 bars (16000ms).
        val phraseAt5s = automixManager.alignDurationToPhrase(120f, 5000L)
        assertEquals(4000L, phraseAt5s)

        val phraseAt7s = automixManager.alignDurationToPhrase(120f, 7500L)
        assertEquals(8000L, phraseAt7s)

        val phraseAt15s = automixManager.alignDurationToPhrase(120f, 15000L)
        assertEquals(16000L, phraseAt15s)
    }

    @Test
    fun automixGains_implementsDjCurveAndBassSwap() {
        val start = automixManager.calculateAutomixGains(0.0f)
        assertTrue(start.outgoingGain > 0.95f)
        assertEquals(0.0f, start.incomingGain, 0.001f)
        assertFalse(start.outgoingBassCut)
        assertTrue(start.incomingBassCut)

        val mid = automixManager.calculateAutomixGains(0.5f)
        assertTrue(mid.outgoingBassCut)
        assertFalse(mid.incomingBassCut)

        val end = automixManager.calculateAutomixGains(1.0f)
        assertTrue(end.outgoingGain < 0.2f)
        assertTrue(end.incomingGain > 0.9f)
        assertTrue(end.outgoingBassCut)
        assertFalse(end.incomingBassCut)
    }

    @Test
    fun tempoWarp_handlesProximityAndLimitsDelta() {
        // Outgoing 120, incoming 125 (within 15% delta)
        val warpStart = automixManager.calculateTempoWarpParameters(0.0f, 120f, 125f)
        assertEquals(1.0f, warpStart.outgoingSpeed, 0.001f)
        assertTrue(warpStart.incomingSpeed < 1.0f)

        val warpEnd = automixManager.calculateTempoWarpParameters(1.0f, 120f, 125f)
        assertTrue(warpEnd.outgoingSpeed > 1.0f)
        assertEquals(1.0f, warpEnd.incomingSpeed, 0.001f)

        // Large delta (120 vs 180) -> bypassed
        val warpLarge = automixManager.calculateTempoWarpParameters(0.5f, 120f, 180f)
        assertEquals(1.0f, warpLarge.outgoingSpeed, 0.001f)
        assertEquals(1.0f, warpLarge.incomingSpeed, 0.001f)
    }

    @Test
    fun downbeatSyncSeek_alignsPhaseToIncomingOnset() {
        val outgoingRhythm = TrackRhythm(bpm = 120f, firstDownbeatMs = 0L)
        val incomingRhythm = TrackRhythm(bpm = 120f, firstDownbeatMs = 0L)

        // Beat is 500ms. At outgoing position 10250ms, phase offset is 250ms.
        // Incoming onset is 200ms -> aligned seek should be 450ms.
        val alignedSeek = automixManager.calculateDownbeatSyncSeek(
            incomingOnsetMs = 200L,
            outgoingPositionMs = 10250L,
            outgoingRhythm = outgoingRhythm,
            incomingRhythm = incomingRhythm,
        )
        assertEquals(450L, alignedSeek)
    }
}
