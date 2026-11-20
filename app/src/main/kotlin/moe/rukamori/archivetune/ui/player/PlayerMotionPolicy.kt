/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.PlayerDesignStyle

enum class LandscapeRightPane {
    QUEUE,
    LYRICS,
}

@Immutable
data class PlayerPaletteTrackState(
    val mediaId: String? = null,
    val artworkUrl: String? = null,
    val hasValidPalette: Boolean = false,
)

internal fun resolvePaletteTransitionOnMissingOrFailedArtwork(
    previousState: PlayerPaletteTrackState,
    currentMediaId: String?,
    currentArtworkUrl: String?,
    fetchFailed: Boolean = false,
): PlayerPaletteTrackState {
    if (currentMediaId.isNullOrBlank() || currentArtworkUrl.isNullOrBlank()) {
        return PlayerPaletteTrackState(
            mediaId = currentMediaId,
            artworkUrl = null,
            hasValidPalette = false,
        )
    }
    if (fetchFailed) {
        val sameTrackSameArt =
            previousState.hasValidPalette &&
                previousState.mediaId == currentMediaId &&
                previousState.artworkUrl == currentArtworkUrl
        return PlayerPaletteTrackState(
            mediaId = currentMediaId,
            artworkUrl = currentArtworkUrl,
            hasValidPalette = sameTrackSameArt,
        )
    }
    val retainExistingForSameTrack =
        previousState.hasValidPalette &&
            previousState.mediaId == currentMediaId &&
            previousState.artworkUrl == currentArtworkUrl
    return PlayerPaletteTrackState(
        mediaId = currentMediaId,
        artworkUrl = currentArtworkUrl,
        hasValidPalette = retainExistingForSameTrack,
    )
}

internal fun resolveLandscapeArtworkSizeDp(
    paneWidthDp: Float,
    paneHeightDp: Float,
    chromeHeightDp: Float = 152f,
    minArtworkDp: Float = 88f,
    maxArtworkDp: Float = 320f,
): Float {
    val availableHeight = (paneHeightDp - chromeHeightDp).coerceAtLeast(minArtworkDp)
    val availableWidth = (paneWidthDp - 32f).coerceAtLeast(minArtworkDp)
    return minOf(availableHeight, availableWidth).coerceIn(minArtworkDp, maxArtworkDp)
}

internal fun playerStyleUsesSharedLandscapeLayout(style: PlayerDesignStyle): Boolean =
    when (style) {
        PlayerDesignStyle.APPLE_MUSIC,
        PlayerDesignStyle.V8,
        PlayerDesignStyle.V9,
        PlayerDesignStyle.V5,
        -> false

        else -> true
    }

internal fun shouldCommitPagerTrackChange(
    settledPage: Int,
    currentPage: Int,
    isScrollInProgress: Boolean,
    isProgrammaticScroll: Boolean,
    activeWindowIndex: Int,
): Boolean =
    !isScrollInProgress &&
        !isProgrammaticScroll &&
        settledPage == currentPage &&
        settledPage >= 0 &&
        settledPage != activeWindowIndex
