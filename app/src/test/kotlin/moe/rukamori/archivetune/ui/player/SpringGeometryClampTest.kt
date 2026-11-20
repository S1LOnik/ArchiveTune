package moe.rukamori.archivetune.ui.player

import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.MINI_PLAYER_BAR_HEIGHT_DP
import moe.rukamori.archivetune.constants.MiniPlayerCompactHeight
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.NavigationBarHorizontalPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringGeometryClampTest {

    @Test
    fun `negative spring overshoot produces non-negative geometry and valid alphas for STANDARD and BAR`() {
        for (style in listOf(MiniPlayerStyle.STANDARD, MiniPlayerStyle.BAR)) {
            for (reserveEnd in listOf(true, false)) {
                val geometry =
                    resolveMiniPlayerSpringGeometry(
                        rawCompactFraction = -0.25f,
                        miniPlayerStyle = style,
                        compactHorizontalPadding = 16.dp,
                        compactReserveEndControl = reserveEnd,
                    )

                assertEquals(0f, geometry.compactFraction, 0.0001f)
                assertTrue("outerStartPadding must be >= 0.dp", geometry.outerStartPadding >= 0.dp)
                assertTrue("outerEndPadding must be >= 0.dp", geometry.outerEndPadding >= 0.dp)
                assertTrue("innerHorizontalPadding must be >= 0.dp", geometry.innerHorizontalPadding >= 0.dp)
                assertTrue("barTopCorner must be >= 0.dp", geometry.barTopCorner >= 0.dp)
                assertTrue("barBottomCorner must be >= 0.dp", geometry.barBottomCorner >= 0.dp)
                assertTrue("containerHeight must be >= 0.dp", geometry.containerHeight >= 0.dp)
                assertTrue(
                    "standardExpandedAlpha must be in 0f..1f",
                    geometry.standardExpandedAlpha in 0f..1f,
                )
                assertTrue(
                    "standardCompactAlpha must be in 0f..1f",
                    geometry.standardCompactAlpha in 0f..1f,
                )

                assertEquals(0.dp, geometry.outerStartPadding)
                assertEquals(0.dp, geometry.outerEndPadding)
                assertEquals(12.dp, geometry.barTopCorner)
                assertEquals(0.dp, geometry.barBottomCorner)
                assertEquals(1f, geometry.standardExpandedAlpha, 0.0001f)
                assertEquals(0f, geometry.standardCompactAlpha, 0.0001f)
                if (style == MiniPlayerStyle.STANDARD) {
                    assertEquals(NavigationBarHorizontalPadding, geometry.innerHorizontalPadding)
                    assertEquals(MiniPlayerHeight, geometry.containerHeight)
                } else {
                    assertEquals(0.dp, geometry.innerHorizontalPadding)
                    assertEquals(MINI_PLAYER_BAR_HEIGHT_DP.dp, geometry.containerHeight)
                }
            }
        }
    }

    @Test
    fun `above-one spring overshoot produces non-negative geometry and valid alphas for STANDARD and BAR`() {
        for (style in listOf(MiniPlayerStyle.STANDARD, MiniPlayerStyle.BAR)) {
            for (reserveEnd in listOf(true, false)) {
                val geometry =
                    resolveMiniPlayerSpringGeometry(
                        rawCompactFraction = 1.25f,
                        miniPlayerStyle = style,
                        compactHorizontalPadding = 16.dp,
                        compactReserveEndControl = reserveEnd,
                    )

                assertEquals(1f, geometry.compactFraction, 0.0001f)
                assertTrue("outerStartPadding must be >= 0.dp", geometry.outerStartPadding >= 0.dp)
                assertTrue("outerEndPadding must be >= 0.dp", geometry.outerEndPadding >= 0.dp)
                assertTrue("innerHorizontalPadding must be >= 0.dp", geometry.innerHorizontalPadding >= 0.dp)
                assertTrue("barTopCorner must be >= 0.dp", geometry.barTopCorner >= 0.dp)
                assertTrue("barBottomCorner must be >= 0.dp", geometry.barBottomCorner >= 0.dp)
                assertTrue("containerHeight must be >= 0.dp", geometry.containerHeight >= 0.dp)
                assertTrue(
                    "standardExpandedAlpha must be in 0f..1f",
                    geometry.standardExpandedAlpha in 0f..1f,
                )
                assertTrue(
                    "standardCompactAlpha must be in 0f..1f",
                    geometry.standardCompactAlpha in 0f..1f,
                )

                assertEquals(0.dp, geometry.innerHorizontalPadding)
                assertEquals(26.dp, geometry.barTopCorner)
                assertEquals(26.dp, geometry.barBottomCorner)
                assertEquals(0f, geometry.standardExpandedAlpha, 0.0001f)
                assertEquals(1f, geometry.standardCompactAlpha, 0.0001f)
                if (style == MiniPlayerStyle.STANDARD) {
                    assertEquals(MiniPlayerCompactHeight, geometry.containerHeight)
                } else {
                    assertEquals(MINI_PLAYER_BAR_HEIGHT_DP.dp, geometry.containerHeight)
                }
            }
        }
    }

    @Test
    fun `non-finite compactFraction falls back safely to zero`() {
        val nanGeometry =
            resolveMiniPlayerSpringGeometry(
                rawCompactFraction = Float.NaN,
                miniPlayerStyle = MiniPlayerStyle.STANDARD,
            )
        assertEquals(0f, nanGeometry.compactFraction, 0.0001f)
        assertTrue(nanGeometry.outerStartPadding >= 0.dp)
        assertTrue(nanGeometry.innerHorizontalPadding >= 0.dp)
        assertTrue(nanGeometry.standardExpandedAlpha in 0f..1f)
        assertTrue(nanGeometry.standardCompactAlpha in 0f..1f)
    }
}
