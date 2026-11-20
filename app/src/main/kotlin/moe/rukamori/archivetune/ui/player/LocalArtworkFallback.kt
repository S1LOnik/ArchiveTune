/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import moe.rukamori.archivetune.R

internal fun resolveArtworkFallbackIconDp(
    containerMinDp: Float,
    fraction: Float = 0.44f,
    minDp: Float = 18f,
    maxDp: Float = 88f,
): Float {
    if (containerMinDp <= 0f) return minDp
    return (containerMinDp * fraction).coerceIn(minDp, maxDp)
}

@Composable
fun LocalArtworkFallback(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
    primaryTint: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    secondaryTint: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
    surfaceTint: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    minIconSize: Dp = 18.dp,
    maxIconSize: Dp = 88.dp,
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier.background(
                Brush.linearGradient(
                    colors = listOf(surfaceTint, primaryTint, secondaryTint),
                ),
            ),
    ) {
        val containerMinDp = min(maxWidth.value, maxHeight.value)
        val iconSize =
            resolveArtworkFallbackIconDp(
                containerMinDp = containerMinDp,
                minDp = minIconSize.value,
                maxDp = maxIconSize.value,
            ).dp
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}
