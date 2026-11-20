/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.HomeFeedStyleKey
import moe.rukamori.archivetune.constants.HomeHiddenSectionsKey
import moe.rukamori.archivetune.constants.HomeSectionOrderKey
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyleKey
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.MotionProfileKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.ShowCastInPlayerMenuKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifiedSettingsRegistryTest {
    @Test
    fun `empty preferences report zero modifications`() {
        assertTrue(ModifiedSettingsRegistry.collectModified(preferencesOf(), isLowRamDevice = false).isEmpty())
        assertTrue(ModifiedSettingsRegistry.collectModified(null, isLowRamDevice = false).isEmpty())
    }

    @Test
    fun `disable animations respects low ram device default`() {
        val truePrefs = preferencesOf(DisableAnimationsKey to true)
        val falsePrefs = preferencesOf(DisableAnimationsKey to false)

        assertTrue(ModifiedSettingsRegistry.collectModified(truePrefs, isLowRamDevice = true).isEmpty())
        assertEquals(1, ModifiedSettingsRegistry.collectModified(truePrefs, isLowRamDevice = false).size)

        assertTrue(ModifiedSettingsRegistry.collectModified(falsePrefs, isLowRamDevice = false).isEmpty())
        assertEquals(1, ModifiedSettingsRegistry.collectModified(falsePrefs, isLowRamDevice = true).size)
    }

    @Test
    fun `removed player design styles normalize to apple music default`() {
        val legacyPrefs = preferencesOf(PlayerDesignStyleKey to PlayerDesignStyle.V2.name)
        assertTrue(ModifiedSettingsRegistry.collectModified(legacyPrefs, isLowRamDevice = false).isEmpty())

        val modernPrefs = preferencesOf(PlayerDesignStyleKey to PlayerDesignStyle.SPATIALFLOW.name)
        val entries = ModifiedSettingsRegistry.collectModified(modernPrefs, isLowRamDevice = false)
        assertEquals(1, entries.size)
        assertEquals("settings/appearance?scrollTo=player_design_style", entries.first().route)
    }

    @Test
    fun `reset single item and reset all restore default state`() {
        val mutablePrefs =
            mutablePreferencesOf(
                HomeFeedStyleKey to HomeFeedStyle.NOW.name,
                MiniPlayerStyleKey to MiniPlayerStyle.BAR.name,
                MotionProfileKey to MotionProfile.FAST.name,
                ShowCastInPlayerMenuKey to false,
                HomeSectionOrderKey to "quick_picks,pinned",
                HomeHiddenSectionsKey to setOf("moods"),
            )

        val entries = ModifiedSettingsRegistry.collectModified(mutablePrefs, isLowRamDevice = false)
        assertEquals(6, entries.size)

        entries.first { it.id == ShowCastInPlayerMenuKey.name }.reset(mutablePrefs)
        assertEquals(5, ModifiedSettingsRegistry.collectModified(mutablePrefs, isLowRamDevice = false).size)

        ModifiedSettingsRegistry.resetAll(mutablePrefs)
        assertTrue(ModifiedSettingsRegistry.collectModified(mutablePrefs, isLowRamDevice = false).isEmpty())
    }
}
