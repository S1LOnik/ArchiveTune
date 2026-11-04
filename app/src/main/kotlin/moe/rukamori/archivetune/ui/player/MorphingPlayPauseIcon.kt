/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// [FIX-ZCODE] Restored fork feature: true morphing Play/Pause icon. The original
// declaration was lost when PlayerComponents.kt got overwritten with the donor
// version during the v16 porting; consumers (ImmersivePlayerScreen, V9 controls)
// still reference it.
// Rework: pause target shape is two chunky rounded bars (pill ends), matching the
// rounded style of the track-switch arrows; the earlier version morphed into
// triangles which read as two little arrows instead of pause bars.
@Composable
fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    // 0f = play (triangle), 1f = pause (two bars)
    val morph by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "playPauseMorph",
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val t = morph

        // Play triangle vertices
        val pTop = Offset(cx - w * 0.20f, cy - h * 0.26f)
        val pBottom = Offset(cx - w * 0.20f, cy + h * 0.26f)
        val pTip = Offset(cx + w * 0.28f, cy)

        // Pause: two chunky vertical bars with pill-shaped ends
        val barWidth = w * 0.17f
        val barHeight = h * 0.54f
        val gap = w * 0.045f
        val leftBarLeft = cx - gap - barWidth
        val leftBarRight = cx - gap
        val rightBarLeft = cx + gap
        val rightBarRight = cx + gap + barWidth
        val barTop = cy - barHeight / 2f
        val barBottom = cy + barHeight / 2f

        fun lerp(a: Offset, b: Offset) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

        // Left shape: triangle left half (TL=pTop, TR=pTip, BR=pTip, BL=pBottom)
        // morphs into the left bar rectangle.
        val leftCorners = listOf(
            lerp(pTop, Offset(leftBarLeft, barTop)),
            lerp(pTip, Offset(leftBarRight, barTop)),
            lerp(pTip, Offset(leftBarRight, barBottom)),
            lerp(pBottom, Offset(leftBarLeft, barBottom)),
        )
        // Right shape: collapses to the triangle tip at t=0, grows into the right bar.
        val rightCorners = listOf(
            lerp(pTip, Offset(rightBarLeft, barTop)),
            lerp(pTip, Offset(rightBarRight, barTop)),
            lerp(pTip, Offset(rightBarRight, barBottom)),
            lerp(pTip, Offset(rightBarLeft, barBottom)),
        )

        // Rounded quad: quadratic curves through edge midpoints give every corner
        // a radius of half the shorter adjacent edge -> pill ends on the bars and
        // a softly rounded triangle in the play state.
        fun roundedQuadPath(corners: List<Offset>): Path {
            val path = Path()
            val n = corners.size
            fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            val start = mid(corners[n - 1], corners[0])
            path.moveTo(start.x, start.y)
            for (i in 0 until n) {
                val cur = corners[i]
                val nextMid = mid(cur, corners[(i + 1) % n])
                path.quadraticTo(cur.x, cur.y, nextMid.x, nextMid.y)
            }
            path.close()
            return path
        }

        drawPath(roundedQuadPath(leftCorners), color = tint, style = Fill)
        drawPath(roundedQuadPath(rightCorners), color = tint, style = Fill)
    }
}
