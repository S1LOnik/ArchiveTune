package moe.rukamori.archivetune.ui.motion

import moe.rukamori.archivetune.constants.BackgroundMotionPolicy
import moe.rukamori.archivetune.constants.HapticIntensity
import moe.rukamori.archivetune.constants.MotionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionConfigTest {
    @Test
    fun legacyDisableAnimationsForcesReducedProfile() {
        val config =
            MotionConfig(
                profile = MotionProfile.FAST,
                legacyDisableAnimations = true,
                systemAnimatorScale = 1f,
            )
        assertFalse(config.animationsEnabled)
        assertEquals(MotionProfile.REDUCED, config.effectiveProfile)
        assertFalse(config.screenTransitionsEnabled)
        assertFalse(config.artworkMotionEnabled)
        assertFalse(config.pressFeedbackEnabled)
        assertFalse(config.decorativeBackgroundEnabled)
        assertEquals(0, config.tabTransitionDurationMs())
        assertTrue(config.highlightIsStatic)
    }

    @Test
    fun systemProfileRespectsSystemAnimatorZeroScale() {
        val systemZero =
            MotionConfig(
                profile = MotionProfile.SYSTEM,
                legacyDisableAnimations = false,
                systemAnimatorScale = 0f,
            )
        assertFalse(systemZero.animationsEnabled)
        assertEquals(MotionProfile.REDUCED, systemZero.effectiveProfile)

        val fastOverridesSystemZero =
            MotionConfig(
                profile = MotionProfile.FAST,
                legacyDisableAnimations = false,
                systemAnimatorScale = 0f,
            )
        assertTrue(fastOverridesSystemZero.animationsEnabled)
        assertEquals(MotionProfile.FAST, fastOverridesSystemZero.effectiveProfile)
    }

    @Test
    fun customDurationScalesAndClampsTheSelectedPreset() {
        val config = MotionConfig(profile = MotionProfile.CINEMATIC, durationPercent = 50)
        assertEquals(205, config.tabTransitionDurationMs())
        assertEquals(235, config.subScreenTransitionDurationMs())
        assertEquals(205, config.copy(durationPercent = -1).tabTransitionDurationMs())
        assertEquals(615, config.copy(durationPercent = 300).tabTransitionDurationMs())
        assertEquals(0, config.copy(profile = MotionProfile.REDUCED).tabTransitionDurationMs())
    }

    @Test
    fun perChannelFlagsOperateIndependently() {
        val config =
            MotionConfig(
                profile = MotionProfile.SMOOTH,
                legacyDisableAnimations = false,
                screenTransitionsChannel = true,
                artworkMotionChannel = true,
                pressFeedbackChannel = true,
                decorativeBackgroundChannel = false,
            )
        assertTrue(config.animationsEnabled)
        assertTrue(config.screenTransitionsEnabled)
        assertTrue(config.artworkMotionEnabled)
        assertFalse(config.decorativeBackgroundEnabled)
    }

    @Test
    fun decorativeMotionPolicyStopsOnScreenOffInvisibleOrPowerSave() {
        val motion = MotionConfig(profile = MotionProfile.SMOOTH)
        assertFalse(
            shouldRunDecorativeMotion(
                isForeground = false,
                isVisible = true,
                isPlaying = true,
                isPowerSaveMode = false,
                motionConfig = motion,
                policy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ),
        )
        assertFalse(
            shouldRunDecorativeMotion(
                isForeground = true,
                isVisible = false,
                isPlaying = true,
                isPowerSaveMode = false,
                motionConfig = motion,
                policy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ),
        )
        assertFalse(
            shouldRunDecorativeMotion(
                isForeground = true,
                isVisible = true,
                isPlaying = true,
                isPowerSaveMode = true,
                motionConfig = motion,
                policy = BackgroundMotionPolicy.RESPECT_POWER_SAVER,
            ),
        )
        assertTrue(
            shouldRunDecorativeMotion(
                isForeground = true,
                isVisible = true,
                isPlaying = true,
                isPowerSaveMode = true,
                motionConfig = motion,
                policy = BackgroundMotionPolicy.WHILE_PLAYING_OPEN,
            ),
        )
        assertFalse(
            shouldRunDecorativeMotion(
                isForeground = true,
                isVisible = true,
                isPlaying = true,
                isPowerSaveMode = false,
                motionConfig = motion,
                policy = BackgroundMotionPolicy.NEVER,
            ),
        )
    }

    @Test
    fun legacyHapticToggleFalseForcesOffIntensity() {
        assertEquals(
            HapticIntensity.OFF,
            effectiveHapticIntensity(legacyEnabled = false, intensity = HapticIntensity.STRONG),
        )
        assertEquals(
            HapticIntensity.LIGHT,
            effectiveHapticIntensity(legacyEnabled = true, intensity = HapticIntensity.LIGHT),
        )
    }
}
