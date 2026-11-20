/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import moe.rukamori.archivetune.constants.DEFAULT_PLAYER_MENU_PINNED_ACTIONS
import moe.rukamori.archivetune.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerMenuPinnedActionsTest {
    @Test
    fun `parses defaults and enforces 4-action limit without duplicates`() {
        val defaults = parsePinnedPlayerMenuActions(null)
        assertEquals(
            listOf(
                PlayerMenuQuickAction.RADIO,
                PlayerMenuQuickAction.ADD_TO_PLAYLIST,
                PlayerMenuQuickAction.QUEUE,
                PlayerMenuQuickAction.DOWNLOAD,
            ),
            defaults,
        )

        val custom =
            parsePinnedPlayerMenuActions(
                "SHARE,RADIO,SHARE,CAST,SLEEP_TIMER,EQUALIZER",
            )
        assertEquals(
            listOf(
                PlayerMenuQuickAction.SHARE,
                PlayerMenuQuickAction.RADIO,
                PlayerMenuQuickAction.CAST,
                PlayerMenuQuickAction.SLEEP_TIMER,
            ),
            custom,
        )
    }

    @Test
    fun `hiding Cast reflows pinned bar with surviving actions and leaves no empty slot`() {
        val configured =
            listOf(
                PlayerMenuQuickAction.CAST,
                PlayerMenuQuickAction.RADIO,
                PlayerMenuQuickAction.SHARE,
                PlayerMenuQuickAction.DOWNLOAD,
            )
        val availableWithoutCast =
            setOf(
                PlayerMenuQuickAction.RADIO,
                PlayerMenuQuickAction.ADD_TO_PLAYLIST,
                PlayerMenuQuickAction.SHARE,
                PlayerMenuQuickAction.DOWNLOAD,
                PlayerMenuQuickAction.OTHER_SOURCES,
            )

        val visible = resolveVisiblePinnedActions(configured, availableWithoutCast)
        assertEquals(4, visible.size)
        assertFalse(PlayerMenuQuickAction.CAST in visible)
        assertEquals(
            listOf(
                PlayerMenuQuickAction.RADIO,
                PlayerMenuQuickAction.SHARE,
                PlayerMenuQuickAction.DOWNLOAD,
                PlayerMenuQuickAction.ADD_TO_PLAYLIST,
            ),
            visible,
        )
    }

    @Test
    fun `toggles and reorders pinned actions cleanly`() {
        val initial = parsePinnedPlayerMenuActions(DEFAULT_PLAYER_MENU_PINNED_ACTIONS)
        val removed = togglePinnedPlayerMenuAction(initial, PlayerMenuQuickAction.QUEUE)
        assertEquals(3, removed.size)

        val added = togglePinnedPlayerMenuAction(removed, PlayerMenuQuickAction.OTHER_SOURCES)
        assertEquals(4, added.size)
        assertEquals(PlayerMenuQuickAction.OTHER_SOURCES, added.last())

        val reordered = movePinnedPlayerMenuAction(added, fromIndex = 3, toIndex = 0)
        assertEquals(PlayerMenuQuickAction.OTHER_SOURCES, reordered.first())
    }

    @Test
    fun `detects active source accurately for YouTube Spotify SoundCloud and local`() {
        assertEquals(
            ActiveTrackSource.LOCAL,
            detectActiveTrackSource(MediaMetadata(id = "local:123", title = "T", artists = emptyList(), duration = 180), isLocal = true),
        )
        assertEquals(
            ActiveTrackSource.SPOTIFY,
            detectActiveTrackSource(MediaMetadata(id = "yt_abc", title = "T", artists = emptyList(), duration = 180, spotifyTrackId = "sp42"), isLocal = false),
        )
        assertEquals(
            ActiveTrackSource.SOUNDCLOUD,
            detectActiveTrackSource(MediaMetadata(id = "sc:998", title = "T", artists = emptyList(), duration = 180), isLocal = false),
        )
        assertEquals(
            ActiveTrackSource.YOUTUBE,
            detectActiveTrackSource(MediaMetadata(id = "dQw4w9WgXcQ", title = "T", artists = emptyList(), duration = 180), isLocal = false),
        )
    }
}
