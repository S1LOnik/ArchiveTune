/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.runtime.Immutable

/**
 * Lightweight descriptor of a playlist used for multi-select filtering and batch visibility updates.
 */
@Immutable
data class PlaylistVisibilityCandidate(
    val id: String,
    val name: String,
    val isHidden: Boolean,
)

/**
 * Describes the exact set of playlist IDs whose visibility actually changed in a batch operation.
 *
 * Undo applies `!targetHidden` strictly to [changedIds] so playlists that already matched [targetHidden]
 * prior to the batch action are never inadvertently toggled.
 */
@Immutable
data class PlaylistVisibilityBatchPlan(
    val changedIds: List<String>,
    val targetHidden: Boolean,
)

/**
 * Filters [playlists] by trimmed case-insensitive [query], returning all items when [query] is blank.
 */
internal fun filterPlaylistsByQuery(
    playlists: List<PlaylistVisibilityCandidate>,
    query: String,
): List<PlaylistVisibilityCandidate> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return playlists
    return playlists.filter { it.name.contains(trimmed, ignoreCase = true) }
}

/**
 * Toggles membership of [playlistId] inside [selectedIds].
 */
internal fun togglePlaylistSelection(
    selectedIds: Set<String>,
    playlistId: String,
): Set<String> =
    if (playlistId in selectedIds) {
        selectedIds - playlistId
    } else {
        selectedIds + playlistId
    }

/**
 * Selects every playlist in [visibleIds] unless all of them are already selected, in which case they are cleared.
 * Selections outside [visibleIds] are preserved across inline search filter changes.
 */
internal fun toggleSelectAllVisible(
    selectedIds: Set<String>,
    visibleIds: Set<String>,
): Set<String> {
    if (visibleIds.isEmpty()) return selectedIds
    return if (selectedIds.containsAll(visibleIds)) {
        selectedIds - visibleIds
    } else {
        selectedIds + visibleIds
    }
}

/**
 * Computes the subset of [selectedIds] whose `isHidden` flag differs from [targetHidden].
 */
internal fun planPlaylistVisibilityChange(
    playlists: List<PlaylistVisibilityCandidate>,
    selectedIds: Set<String>,
    targetHidden: Boolean,
): PlaylistVisibilityBatchPlan {
    val changedIds =
        playlists
            .asSequence()
            .filter { it.id in selectedIds && it.isHidden != targetHidden }
            .map { it.id }
            .toList()
    return PlaylistVisibilityBatchPlan(
        changedIds = changedIds,
        targetHidden = targetHidden,
    )
}
