/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.LyricsDisplayCharacter

@Immutable
data class ResolvedLyricsCharacterStyle(
    val activeScale: Float,
    val inactiveScale: Float,
    val nearInactiveAlpha: Float,
    val midInactiveAlpha: Float,
    val farInactiveAlpha: Float,
    val blurMultiplier: Float,
)

internal fun resolveLyricsCharacterStyle(
    character: LyricsDisplayCharacter,
    animationsEnabled: Boolean = true,
): ResolvedLyricsCharacterStyle {
    if (!animationsEnabled) {
        return ResolvedLyricsCharacterStyle(
            activeScale = 1f,
            inactiveScale = 1f,
            nearInactiveAlpha = 0.62f,
            midInactiveAlpha = 0.48f,
            farInactiveAlpha = 0.36f,
            blurMultiplier = 0f,
        )
    }
    return when (character) {
        LyricsDisplayCharacter.READING ->
            ResolvedLyricsCharacterStyle(
                activeScale = 1f,
                inactiveScale = 0.98f,
                nearInactiveAlpha = 0.72f,
                midInactiveAlpha = 0.56f,
                farInactiveAlpha = 0.44f,
                blurMultiplier = 0.25f,
            )

        LyricsDisplayCharacter.FOCUS ->
            ResolvedLyricsCharacterStyle(
                activeScale = 1.03f,
                inactiveScale = 0.95f,
                nearInactiveAlpha = 0.52f,
                midInactiveAlpha = 0.30f,
                farInactiveAlpha = 0.16f,
                blurMultiplier = 1f,
            )

        LyricsDisplayCharacter.STAGE ->
            ResolvedLyricsCharacterStyle(
                activeScale = 1.08f,
                inactiveScale = 0.90f,
                nearInactiveAlpha = 0.38f,
                midInactiveAlpha = 0.20f,
                farInactiveAlpha = 0.10f,
                blurMultiplier = 1.35f,
            )
    }
}
