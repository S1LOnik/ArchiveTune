/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsDisplayCharacter
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.RemovedPlayerDesignStyles
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.ThemePalettes
import moe.rukamori.archivetune.ui.theme.DefaultThemeColor
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.ui.theme.archiveTuneColorScheme
import moe.rukamori.archivetune.ui.theme.resolveCustomThemeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPreviewAndDraftTest {
    @Test
    fun resolveCustomThemeSelection_parsesHexSeedPaletteAndNamedPresets() {
        val (hex6Color, hex6Palette) = resolveCustomThemeSelection("#1A73E8")
        assertEquals(Color(0xFF1A73E8), hex6Color)
        assertNull(hex6Palette)

        val (hex8Color, hex8Palette) = resolveCustomThemeSelection("#80FF5722")
        assertEquals(Color(0x80FF5722), hex8Color)
        assertNull(hex8Palette)

        val customSeed =
            ThemeSeedPalette(
                primary = Color(0xFF2E7D32),
                secondary = Color(0xFF00838F),
                tertiary = Color(0xFFAD1457),
                neutral = Color(0xFF37474F),
            )
        val encoded = ThemeSeedPaletteCodec.encodeForPreference(customSeed, name = "Forest")
        val (seedColor, decodedSeed) = resolveCustomThemeSelection(encoded)
        assertEquals(customSeed.primary, seedColor)
        assertEquals(customSeed, decodedSeed)

        val (oceanColor, oceanSeed) = resolveCustomThemeSelection("ocean_blue")
        assertEquals(ThemePalettes.OceanBlue.primary, oceanColor)
        assertNotNull(oceanSeed)
        assertEquals(ThemePalettes.OceanBlue.primary, oceanSeed?.primary)

        val (emeraldColor, emeraldSeed) = resolveCustomThemeSelection("emerald_green")
        assertEquals(ThemePalettes.EmeraldGreen.primary, emeraldColor)
        assertNotNull(emeraldSeed)

        val (defaultColor, defaultSeed) = resolveCustomThemeSelection("default")
        assertEquals(ThemePalettes.Default.primary, defaultColor)
        assertNotNull(defaultSeed)

        val (blankColor, blankSeed) = resolveCustomThemeSelection("   ")
        assertEquals(DefaultThemeColor, blankColor)
        assertNull(blankSeed)

        val (invalidColor, invalidSeed) = resolveCustomThemeSelection("nonexistent_palette")
        assertEquals(DefaultThemeColor, invalidColor)
        assertNull(invalidSeed)

        val (badHexColor, badHexSeed) = resolveCustomThemeSelection("#ZZZZZZ")
        assertEquals(DefaultThemeColor, badHexColor)
        assertNull(badHexSeed)
    }

    @Test
    fun archiveTuneColorScheme_respectsDarkPureBlackAndAccentSeed() {
        val lightScheme =
            archiveTuneColorScheme(
                darkTheme = false,
                pureBlack = false,
                themeColor = ThemePalettes.OceanBlue.primary,
            )
        val darkScheme =
            archiveTuneColorScheme(
                darkTheme = true,
                pureBlack = false,
                themeColor = ThemePalettes.OceanBlue.primary,
            )
        val pureBlackDarkScheme =
            archiveTuneColorScheme(
                darkTheme = true,
                pureBlack = true,
                themeColor = ThemePalettes.OceanBlue.primary,
            )
        val pureBlackLightScheme =
            archiveTuneColorScheme(
                darkTheme = false,
                pureBlack = true,
                themeColor = ThemePalettes.OceanBlue.primary,
            )
        val crimsonDarkScheme =
            archiveTuneColorScheme(
                darkTheme = true,
                pureBlack = false,
                themeColor = ThemePalettes.CrimsonRed.primary,
            )

        assertTrue(lightScheme.background.luminance() > darkScheme.background.luminance())
        assertEquals(Color.Black, pureBlackDarkScheme.background)
        assertEquals(Color.Black, pureBlackDarkScheme.surface)
        assertNotEquals(Color.Black, pureBlackLightScheme.background)
        assertNotEquals(darkScheme.primary, crimsonDarkScheme.primary)
    }

    @Test
    fun appearanceProfileSnapshot_draftJsonRoundTripAndBackwardCompatibility() {
        val original =
            AppearanceProfileSnapshot(
                home = HomeProfile(homeFeedStyle = HomeFeedStyle.COVERS, showHomeCategoryChips = false),
                navigation =
                    NavigationProfile(
                        material = NavBarMaterial.LIQUID_GLASS,
                        compactBehavior = NavigationBarCompactBehavior.ALWAYS_COMPACT,
                        hideLabels = true,
                        widthFraction = 0.82f,
                        heightMultiplier = 1.15f,
                        glassGlow = true,
                        glassGlowIntensity = 1.45f,
                        miniPlayerStyle = MiniPlayerStyle.BAR,
                        miniPlayerBackground = MiniPlayerBackgroundStyle.LIQUID_GLASS,
                    ),
                player =
                    PlayerProfile(
                        designStyle = PlayerDesignStyle.BITCHORD,
                        backgroundStyle = PlayerBackgroundStyle.GLOW_ANIMATED,
                        sliderStyle = SliderStyle.Wavy,
                        showVolumeBar = true,
                        hideThumbnail = false,
                        cropToSquare = true,
                        thumbnailCornerRadius = 22f,
                    ),
                lyrics =
                    LyricsProfile(
                        mode = LyricsMode.SPOTIFY,
                        displayCharacter = LyricsDisplayCharacter.STAGE,
                        backgroundStyle = LyricsBackgroundStyle.MOVING_BLUR,
                        lineBlur = false,
                        textSize = 30f,
                        lineSpacing = 1.5f,
                    ),
                color =
                    ColorProfile(
                        darkMode = DarkMode.ON,
                        pureBlack = true,
                        dynamicTheme = false,
                        customThemeColor = "emerald_green",
                        disableBlur = true,
                    ),
                motion =
                    MotionProfileState(
                        profile = MotionProfile.SMOOTH,
                        disableAnimations = false,
                        screenTransitions = true,
                        artworkTransitions = false,
                        pressFeedback = true,
                        decorativeBackground = false,
                    ),
            )

        val encoded = AppearanceProfileSnapshot.encodeDraftJson(original)
        val decoded = AppearanceProfileSnapshot.decodeDraftJson(encoded)
        assertEquals(original.normalized(), decoded)

        // Backward compatibility: older draft JSON omitting fields still decodes cleanly
        val partialJson = """{"color":{"darkMode":"ON","pureBlack":true}}"""
        val partialDecoded = AppearanceProfileSnapshot.decodeDraftJson(partialJson)
        assertNotNull(partialDecoded)
        assertEquals(DarkMode.ON, partialDecoded?.color?.darkMode)
        assertEquals(true, partialDecoded?.color?.pureBlack)

        // Invalid JSON fails gracefully
        assertNull(AppearanceProfileSnapshot.decodeDraftJson("{not valid json"))
    }

    @Test
    fun appearanceProfileSnapshot_normalizedPreservesBackgroundStyleAndMigratesRemovedStyles() {
        for (fixedStyle in listOf(
            PlayerDesignStyle.APPLE_MUSIC,
            PlayerDesignStyle.V7,
            PlayerDesignStyle.V8,
            PlayerDesignStyle.BITCHORD,
            PlayerDesignStyle.SPATIALFLOW,
        )) {
            val snapshot =
                AppearanceProfileSnapshot(
                    player =
                        PlayerProfile(
                            designStyle = fixedStyle,
                            backgroundStyle = PlayerBackgroundStyle.GLOW_ANIMATED,
                        ),
                ).normalized()
            assertEquals(fixedStyle, snapshot.player.designStyle)
            assertEquals(PlayerBackgroundStyle.GLOW_ANIMATED, snapshot.player.backgroundStyle)
        }

        for (removed in RemovedPlayerDesignStyles) {
            val migrated =
                AppearanceProfileSnapshot(
                    player =
                        PlayerProfile(
                            designStyle = removed,
                            backgroundStyle = PlayerBackgroundStyle.COLORING,
                        ),
                ).normalized()
            assertEquals(PlayerDesignStyle.APPLE_MUSIC, migrated.player.designStyle)
            assertEquals(PlayerBackgroundStyle.COLORING, migrated.player.backgroundStyle)
        }
    }

    @Test
    fun playerStyleEligibilityHelpers_matchRealPlayerBehavior() {
        val customBgSupported =
            setOf(
                PlayerDesignStyle.V1,
                PlayerDesignStyle.V2,
                PlayerDesignStyle.V3,
                PlayerDesignStyle.V4,
                PlayerDesignStyle.V5,
                PlayerDesignStyle.V6,
            )
        val volumeBarSupported =
            setOf(
                PlayerDesignStyle.V7,
                PlayerDesignStyle.V8,
                PlayerDesignStyle.BITCHORD,
            )
        val ownLyricsStyles =
            setOf(
                PlayerDesignStyle.APPLE_MUSIC,
                PlayerDesignStyle.TIKTOK,
                PlayerDesignStyle.SPATIALFLOW,
            )

        for (style in PlayerDesignStyle.entries) {
            assertEquals(
                "custom bg eligibility for $style",
                style in customBgSupported,
                playerStyleSupportsCustomBackground(style),
            )
            assertEquals(
                "volume bar eligibility for $style",
                style in volumeBarSupported,
                playerStyleSupportsVolumeBar(style),
            )
            assertEquals(
                "own fullscreen lyrics eligibility for $style",
                style in ownLyricsStyles,
                playerStyleUsesOwnFullscreenLyrics(style),
            )
        }

        assertTrue(navBarMaterialSupportsGlassGlow(NavBarMaterial.LIQUID_GLASS))
        assertTrue(navBarMaterialSupportsGlassGlow(NavBarMaterial.SUKISU_GLASS))
        assertFalse(navBarMaterialSupportsGlassGlow(NavBarMaterial.STANDARD))
        assertFalse(navBarMaterialSupportsGlassGlow(NavBarMaterial.FROSTED))
        assertFalse(navBarMaterialSupportsGlassGlow(NavBarMaterial.TINTED))
        assertFalse(navBarMaterialSupportsGlassGlow(NavBarMaterial.BITCHORD))
    }

    @Test
    fun lyricsBackgroundStyle_resolveForFollowsCustomAndFallbackRules() {
        assertEquals(
            LyricsBackgroundStyle.CUSTOM,
            LyricsBackgroundStyle.DEFAULT.resolveFor(PlayerBackgroundStyle.CUSTOM),
        )
        assertEquals(
            LyricsBackgroundStyle.CUSTOM,
            LyricsBackgroundStyle.MOVING_BLUR.resolveFor(PlayerBackgroundStyle.CUSTOM),
        )
        assertEquals(
            LyricsBackgroundStyle.DEFAULT,
            LyricsBackgroundStyle.CUSTOM.resolveFor(PlayerBackgroundStyle.DEFAULT),
        )
        assertEquals(
            LyricsBackgroundStyle.DEFAULT,
            LyricsBackgroundStyle.CUSTOM.resolveFor(PlayerBackgroundStyle.GRADIENT),
        )
        assertEquals(
            LyricsBackgroundStyle.FOLLOW_THEME,
            LyricsBackgroundStyle.FOLLOW_THEME.resolveFor(PlayerBackgroundStyle.GRADIENT),
        )
        assertEquals(
            LyricsBackgroundStyle.COLORING,
            LyricsBackgroundStyle.COLORING.resolveFor(PlayerBackgroundStyle.DEFAULT),
        )
        assertEquals(
            LyricsBackgroundStyle.MOVING_BLUR,
            LyricsBackgroundStyle.MOVING_BLUR.resolveFor(PlayerBackgroundStyle.GLOW),
        )
    }
}
