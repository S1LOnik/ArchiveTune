/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.constants.BackgroundMotionPolicy
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.ui.motion.MotionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMotionAndLandscapePolicyTest {
    @Test
    fun `never decorative motion policy freezes animation even when playing and visible`() {
        assertFalse(
            shouldRunDecorativeMotionCompat(
                isPlaying = true,
                isVisible = true,
                policy = BackgroundMotionPolicy.NEVER,
            ),
        )
    }

    @Test
    fun `respect power saver freezes decorative motion when battery saver is active`() {
        assertTrue(
            shouldRunDecorativeMotionCompat(
                isPlaying = true,
                isVisible = true,
                isPowerSaveMode = false,
                policy = BackgroundMotionPolicy.RESPECT_POWER_SAVER,
            ),
        )
        assertFalse(
            shouldRunDecorativeMotionCompat(
                isPlaying = true,
                isVisible = true,
                isPowerSaveMode = true,
                policy = BackgroundMotionPolicy.RESPECT_POWER_SAVER,
            ),
        )
    }

    @Test
    fun `paused or hidden player freezes decorative background motion`() {
        assertFalse(
            shouldRunDecorativeMotionCompat(
                isPlaying = false,
                isVisible = true,
                policy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ),
        )
        assertFalse(
            shouldRunDecorativeMotionCompat(
                isPlaying = true,
                isVisible = false,
                policy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ),
        )
        assertFalse(
            moe.rukamori.archivetune.ui.motion.shouldRunDecorativeMotion(
                isForeground = false,
                isVisible = true,
                isPlaying = true,
                isPowerSaveMode = false,
                motionConfig = MotionConfig(),
                policy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ),
        )
    }

    @Test
    fun `disabled decorative motion config overrides background policy`() {
        assertFalse(
            moe.rukamori.archivetune.ui.motion.shouldRunDecorativeMotion(
                isForeground = true,
                isVisible = true,
                isPlaying = true,
                isPowerSaveMode = false,
                motionConfig = MotionConfig(decorativeBackgroundChannel = false),
                policy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ),
        )
    }

    @Test
    fun `missing artwork on new track clears previous valid palette`() {
        val prevValid =
            PlayerPaletteTrackState(
                mediaId = "track-with-art",
                artworkUrl = "https://example.com/art.jpg",
                hasValidPalette = true,
            )

        val missingArtNext =
            resolvePaletteTransitionOnMissingOrFailedArtwork(
                previousState = prevValid,
                currentMediaId = "local-no-art",
                currentArtworkUrl = null,
            )
        assertFalse(missingArtNext.hasValidPalette)
        assertEquals(null, missingArtNext.artworkUrl)

        val blankArtNext =
            resolvePaletteTransitionOnMissingOrFailedArtwork(
                previousState = prevValid,
                currentMediaId = "local-blank-art",
                currentArtworkUrl = "   ",
            )
        assertFalse(blankArtNext.hasValidPalette)
    }

    @Test
    fun `failed artwork fetch clears stale palette on track change but preserves same track palette`() {
        val prevValid =
            PlayerPaletteTrackState(
                mediaId = "track-a",
                artworkUrl = "https://example.com/a.jpg",
                hasValidPalette = true,
            )

        val failedDifferentTrack =
            resolvePaletteTransitionOnMissingOrFailedArtwork(
                previousState = prevValid,
                currentMediaId = "track-b",
                currentArtworkUrl = "https://example.com/b.jpg",
                fetchFailed = true,
            )
        assertFalse(failedDifferentTrack.hasValidPalette)

        val failedSameTrack =
            resolvePaletteTransitionOnMissingOrFailedArtwork(
                previousState = prevValid,
                currentMediaId = "track-a",
                currentArtworkUrl = "https://example.com/a.jpg",
                fetchFailed = true,
            )
        assertTrue(failedSameTrack.hasValidPalette)
    }

    @Test
    fun `rapid pager programmatic scrolls never commit spurious track jumps`() {
        assertFalse(
            shouldCommitPagerTrackChange(
                settledPage = 1,
                currentPage = 1,
                isScrollInProgress = false,
                isProgrammaticScroll = true,
                activeWindowIndex = 3,
            ),
        )
        assertFalse(
            shouldCommitPagerTrackChange(
                settledPage = 2,
                currentPage = 3,
                isScrollInProgress = true,
                isProgrammaticScroll = false,
                activeWindowIndex = 0,
            ),
        )
        assertTrue(
            shouldCommitPagerTrackChange(
                settledPage = 4,
                currentPage = 4,
                isScrollInProgress = false,
                isProgrammaticScroll = false,
                activeWindowIndex = 3,
            ),
        )
    }

    @Test
    fun `landscape artwork sizing preserves chrome room on short 360dp phones`() {
        val artShort =
            resolveLandscapeArtworkSizeDp(
                paneWidthDp = 340f,
                paneHeightDp = 340f,
                chromeHeightDp = 154f,
            )
        assertTrue(artShort <= 186f)
        assertTrue(artShort >= 88f)

        val artFontScale13 =
            resolveLandscapeArtworkSizeDp(
                paneWidthDp = 340f,
                paneHeightDp = 320f,
                chromeHeightDp = 186f,
            )
        assertTrue(artFontScale13 >= 88f)
        assertTrue(artFontScale13 <= 134f)
    }

    @Test
    fun `shared landscape routing covers styles without bespoke multi-pane landscape`() {
        assertTrue(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.V7))
        assertTrue(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.V10))
        assertTrue(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.BITCHORD))
        assertTrue(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.SPATIALFLOW))
        assertTrue(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.LOOPER))
        assertTrue(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.SIMPMUSIC))
        assertTrue(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.TIKTOK))

        assertFalse(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.APPLE_MUSIC))
        assertFalse(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.V8))
        assertFalse(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.V9))
        assertFalse(playerStyleUsesSharedLandscapeLayout(PlayerDesignStyle.V5))
    }

    @Test
    fun `artwork fallback icon clamps cleanly for mini and full player bounds`() {
        assertEquals(18f, resolveArtworkFallbackIconDp(containerMinDp = 34f, minDp = 18f, maxDp = 88f), 0.01f)
        assertEquals(88f, resolveArtworkFallbackIconDp(containerMinDp = 300f, minDp = 18f, maxDp = 88f), 0.01f)
        assertEquals(44f, resolveArtworkFallbackIconDp(containerMinDp = 100f, fraction = 0.44f, minDp = 18f, maxDp = 88f), 0.01f)
    }

    private fun shouldRunDecorativeMotionCompat(
        isPlaying: Boolean,
        isVisible: Boolean,
        isPowerSaveMode: Boolean = false,
        policy: BackgroundMotionPolicy,
    ): Boolean =
        moe.rukamori.archivetune.ui.motion.shouldRunDecorativeMotion(
            isForeground = true,
            isVisible = isVisible,
            isPlaying = isPlaying,
            isPowerSaveMode = isPowerSaveMode,
            motionConfig = MotionConfig(),
            policy = policy,
        )
}
