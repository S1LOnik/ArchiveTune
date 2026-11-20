/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMultiSelectUndoTest {
    private val samplePlaylists =
        listOf(
            PlaylistVisibilityCandidate(id = "p1", name = "Workout Mix", isHidden = false),
            PlaylistVisibilityCandidate(id = "p2", name = "Chill Lo-Fi", isHidden = true),
            PlaylistVisibilityCandidate(id = "p3", name = "Rock Workout", isHidden = false),
            PlaylistVisibilityCandidate(id = "p4", name = "Jazz Late Night", isHidden = true),
        )

    @Test
    fun `filterPlaylistsByQuery matches case-insensitively and handles blanks`() {
        assertEquals(4, filterPlaylistsByQuery(samplePlaylists, "   ").size)
        val filtered = filterPlaylistsByQuery(samplePlaylists, " workout ")
        assertEquals(listOf("p1", "p3"), filtered.map { it.id })
    }

    @Test
    fun `toggleSelectAllVisible preserves out-of-filter selections`() {
        val initial = setOf("p2")
        val filteredIds = setOf("p1", "p3")

        val selectedAllMatches = toggleSelectAllVisible(initial, filteredIds)
        assertEquals(setOf("p1", "p2", "p3"), selectedAllMatches)

        val clearedMatches = toggleSelectAllVisible(selectedAllMatches, filteredIds)
        assertEquals(setOf("p2"), clearedMatches)
    }

    @Test
    fun `planPlaylistVisibilityChange excludes already-matching playlists so Undo never flips prior state`() {
        val selected = setOf("p1", "p2", "p3")

        val hidePlan = planPlaylistVisibilityChange(samplePlaylists, selected, targetHidden = true)
        assertEquals(listOf("p1", "p3"), hidePlan.changedIds)
        assertTrue(hidePlan.targetHidden)

        // Simulate applying hidePlan + then Undo restoring only hidePlan.changedIds
        val afterApply =
            samplePlaylists.map { item ->
                if (item.id in hidePlan.changedIds) item.copy(isHidden = hidePlan.targetHidden) else item
            }
        val afterUndo =
            afterApply.map { item ->
                if (item.id in hidePlan.changedIds) item.copy(isHidden = !hidePlan.targetHidden) else item
            }
        assertEquals(samplePlaylists, afterUndo)

        val showPlan = planPlaylistVisibilityChange(samplePlaylists, selected, targetHidden = false)
        assertEquals(listOf("p2"), showPlan.changedIds)
        assertFalse(showPlan.targetHidden)
    }

    @Test
    fun `togglePlaylistSelection adds and removes cleanly`() {
        val added = togglePlaylistSelection(emptySet(), "p1")
        assertEquals(setOf("p1"), added)
        val removed = togglePlaylistSelection(added, "p1")
        assertTrue(removed.isEmpty())
    }
}
