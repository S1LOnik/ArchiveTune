package moe.rukamori.archivetune.constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDesignStyleTest {
    @Test
    fun removedStylesAreExactlyTheLegacyOnes() {
        assertEquals(
            setOf(
                PlayerDesignStyle.V1,
                PlayerDesignStyle.V2,
                PlayerDesignStyle.V3,
                PlayerDesignStyle.V5,
                PlayerDesignStyle.V6,
            ),
            RemovedPlayerDesignStyles,
        )
    }

    @Test
    fun appleMusicDefaultIsNeverRemoved() {
        assertFalse(PlayerDesignStyle.APPLE_MUSIC in RemovedPlayerDesignStyles)
    }

    @Test
    fun keptStylesRemainSelectable() {
        listOf(
            PlayerDesignStyle.V4,
            PlayerDesignStyle.V7,
            PlayerDesignStyle.V8,
            PlayerDesignStyle.V9,
            PlayerDesignStyle.V10,
            PlayerDesignStyle.APPLE_MUSIC,
            PlayerDesignStyle.BITCHORD,
            PlayerDesignStyle.TIKTOK,
            PlayerDesignStyle.SIMPMUSIC,
            PlayerDesignStyle.SPATIALFLOW,
            PlayerDesignStyle.LOOPER,
        ).forEach { assertFalse(it.name, it in RemovedPlayerDesignStyles) }
    }

    @Test
    fun migrationMatchesStoredNamesByName() {
        // App.kt migrates by comparing the stored string with enum names.
        assertTrue(RemovedPlayerDesignStyles.any { it.name == "V1" })
        assertFalse(RemovedPlayerDesignStyles.any { it.name == "APPLE_MUSIC" })
    }
}
