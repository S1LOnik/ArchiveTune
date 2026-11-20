/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import androidx.datastore.preferences.core.mutablePreferencesOf
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.LyricsDisplayCharacter
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MotionTransitionStyle
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceProfileCodecTest {
    @Test
    fun `export and import round trip preserves all six sections cleanly`() {
        val original =
            AppearanceProfileSnapshot(
                home = HomeProfile(homeFeedStyle = HomeFeedStyle.NOW, showHomeCategoryChips = false),
                navigation =
                    NavigationProfile(
                        material = NavBarMaterial.FROSTED,
                        hideLabels = true,
                        miniPlayerStyle = MiniPlayerStyle.BAR,
                        widthFraction = 0.82f,
                        heightMultiplier = 1.25f,
                    ),
                player =
                    PlayerProfile(
                        designStyle = PlayerDesignStyle.SPATIALFLOW,
                        backgroundStyle = PlayerBackgroundStyle.BLUR_GRADIENT,
                        cropToSquare = true,
                        thumbnailCornerRadius = 22f,
                    ),
                lyrics =
                    LyricsProfile(
                        displayCharacter = LyricsDisplayCharacter.STAGE,
                        lineBlur = false,
                        textSize = 28f,
                    ),
                motion =
                    MotionProfileState(
                        profile = MotionProfile.CINEMATIC,
                        durationPercent = 125,
                        screenStyle = MotionTransitionStyle.ZOOM,
                        menuStyle = MotionTransitionStyle.FADE,
                        motionBlur = true,
                        screenTransitions = true,
                        decorativeBackground = false,
                    ),
            )

        val encoded = AppearanceProfileCodec.encodeExport("Midnight Desk", original, exportedAtEpochMs = 1234L)
        val decoded = AppearanceProfileCodec.decodeImport(encoded).getOrThrow()

        assertEquals("Midnight Desk", decoded.name)
        assertEquals(ExportedAppearanceProfileV1.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(original.normalized(), decoded.snapshot)
        assertTrue(decoded.snapshot.diffSections(original).isEmpty())
    }

    @Test
    fun `import ignores unknown future fields and normalizes legacy removed player styles`() {
        val futureJson =
            """
            {
              "schemaVersion": 2,
              "appVersion": "17.0",
              "name": "Future Studio",
              "futureExperimentalFlag": true,
              "snapshot": {
                "player": {
                  "designStyle": "V3",
                  "backgroundStyle": "GRADIENT",
                  "futureSliderCurve": "cubic"
                },
                "lyrics": {
                  "displayCharacter": "READING"
                }
              }
            }
            """.trimIndent()

        val decoded = AppearanceProfileCodec.decodeImport(futureJson).getOrThrow()
        assertEquals("Future Studio", decoded.name)
        assertEquals(PlayerDesignStyle.APPLE_MUSIC, decoded.snapshot.player.designStyle)
        assertEquals(LyricsDisplayCharacter.READING, decoded.snapshot.lyrics.displayCharacter)
        assertEquals(MotionProfileState(), decoded.snapshot.motion)
    }

    @Test
    fun `resetting one section leaves all other sections untouched`() {
        val modified =
            AppearanceProfileSnapshot(
                home = HomeProfile(homeFeedStyle = HomeFeedStyle.COVERS, showHomeCategoryChips = false),
                navigation = NavigationProfile(hideLabels = true, miniPlayerStyle = MiniPlayerStyle.BAR),
                lyrics = LyricsProfile(displayCharacter = LyricsDisplayCharacter.STAGE, textSize = 34f),
            )

        val resetLyricsOnly = modified.resetSection(AppearanceSection.LYRICS, isLowRamDevice = false)
        assertEquals(LyricsProfile(), resetLyricsOnly.lyrics)
        assertEquals(modified.home, resetLyricsOnly.home)
        assertEquals(modified.navigation, resetLyricsOnly.navigation)
        assertEquals(modified.player, resetLyricsOnly.player)
        assertEquals(modified.color, resetLyricsOnly.color)
        assertEquals(modified.motion, resetLyricsOnly.motion)
    }

    @Test
    fun `atomic write and read round trip via datastore preferences`() {
        val starter = StudioStarterPresets.first().snapshot
        val snapshot = starter.copy(
            navigation = starter.navigation.copy(
                material = NavBarMaterial.GLOW,
                miniPlayerBackground = MiniPlayerBackgroundStyle.SUKISU_GLASS,
                miniPlayerGlassGlow = false,
                miniPlayerGlassGlowIntensity = 1.3f,
            ),
            motion = starter.motion.copy(
                profile = MotionProfile.SPRINGY,
                durationPercent = 70,
                screenStyle = MotionTransitionStyle.SLIDE,
                menuStyle = MotionTransitionStyle.ZOOM,
                motionBlur = true,
            ),
        )
        val prefs = mutablePreferencesOf()
        snapshot.writeToPreferences(prefs)

        val restored = AppearanceProfileSnapshot.fromPreferences(prefs, isLowRamDevice = false)
        assertEquals(snapshot.normalized(), restored)
        assertEquals(MiniPlayerStyle.STANDARD, restored.navigation.miniPlayerStyle)
        assertFalse(restored.motion.disableAnimations)
    }
}
