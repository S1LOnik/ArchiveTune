/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DEFAULT_PLAYER_MENU_PINNED_ACTIONS
import moe.rukamori.archivetune.models.MediaMetadata

enum class PlayerMenuQuickAction(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    RADIO(R.string.start_radio, R.drawable.radio),
    ADD_TO_PLAYLIST(R.string.add_to_playlist, R.drawable.playlist_add),
    QUEUE(R.string.queue, R.drawable.queue_music),
    DOWNLOAD(R.string.action_download, R.drawable.download),
    SLEEP_TIMER(R.string.sleep_timer, R.drawable.bedtime),
    SHARE(R.string.share, R.drawable.share),
    CAST(R.string.cast, R.drawable.cast),
    OTHER_SOURCES(R.string.other_sources, R.drawable.manage_search),
    EQUALIZER(R.string.equalizer, R.drawable.equalizer),
    PITCH_TEMPO(R.string.tempo_and_pitch, R.drawable.speed),
    AOD(R.string.aod_mode, R.drawable.bedtime),
    SPEED_DIAL(R.string.pin_to_speed_dial, R.drawable.push_pin),
}

enum class ActiveTrackSource(
    @param:StringRes val labelRes: Int,
) {
    LOCAL(R.string.player_menu_source_local),
    SPOTIFY(R.string.player_menu_source_spotify),
    SOUNDCLOUD(R.string.player_menu_source_soundcloud),
    YOUTUBE(R.string.player_menu_source_youtube),
}

internal const val MAX_PINNED_PLAYER_MENU_ACTIONS = 4

internal fun parsePinnedPlayerMenuActions(
    raw: String?,
    maxCount: Int = MAX_PINNED_PLAYER_MENU_ACTIONS,
): List<PlayerMenuQuickAction> {
    val source = raw?.takeIf { it.isNotBlank() } ?: DEFAULT_PLAYER_MENU_PINNED_ACTIONS
    val parsed =
        source
            .split(',')
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                PlayerMenuQuickAction.entries.firstOrNull { it.name == trimmed }
            }.distinct()
            .take(maxCount.coerceAtLeast(1))
    if (parsed.isNotEmpty()) return parsed
    return DEFAULT_PLAYER_MENU_PINNED_ACTIONS
        .split(',')
        .mapNotNull { token -> PlayerMenuQuickAction.entries.firstOrNull { it.name == token } }
        .take(maxCount.coerceAtLeast(1))
}

internal fun serializePinnedPlayerMenuActions(
    actions: List<PlayerMenuQuickAction>,
    maxCount: Int = MAX_PINNED_PLAYER_MENU_ACTIONS,
): String = actions.distinct().take(maxCount.coerceAtLeast(1)).joinToString(",") { it.name }

internal fun togglePinnedPlayerMenuAction(
    current: List<PlayerMenuQuickAction>,
    action: PlayerMenuQuickAction,
    maxCount: Int = MAX_PINNED_PLAYER_MENU_ACTIONS,
): List<PlayerMenuQuickAction> {
    if (action in current) {
        return if (current.size > 1) current - action else current
    }
    val next = current + action
    return next.take(maxCount.coerceAtLeast(1))
}

internal fun movePinnedPlayerMenuAction(
    current: List<PlayerMenuQuickAction>,
    fromIndex: Int,
    toIndex: Int,
): List<PlayerMenuQuickAction> {
    if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
        return current
    }
    val mutable = current.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable
}

internal fun resolveVisiblePinnedActions(
    configured: List<PlayerMenuQuickAction>,
    available: Set<PlayerMenuQuickAction>,
    maxCount: Int = MAX_PINNED_PLAYER_MENU_ACTIONS,
): List<PlayerMenuQuickAction> {
    val active = configured.filter { it in available }
    if (active.size >= maxCount) return active.take(maxCount)
    val extras =
        listOf(
            PlayerMenuQuickAction.RADIO,
            PlayerMenuQuickAction.ADD_TO_PLAYLIST,
            PlayerMenuQuickAction.OTHER_SOURCES,
            PlayerMenuQuickAction.SHARE,
            PlayerMenuQuickAction.DOWNLOAD,
            PlayerMenuQuickAction.SLEEP_TIMER,
        ).filter { it in available && it !in active }
    return (active + extras).take(maxCount.coerceAtLeast(1))
}

internal fun detectActiveTrackSource(
    mediaMetadata: MediaMetadata,
    isLocal: Boolean,
): ActiveTrackSource {
    if (isLocal || mediaMetadata.id.startsWith("local:", ignoreCase = true)) {
        return ActiveTrackSource.LOCAL
    }
    if (mediaMetadata.id.startsWith("spotify:", ignoreCase = true) ||
        mediaMetadata.id.startsWith("sp:", ignoreCase = true) ||
        !mediaMetadata.spotifyTrackId.isNullOrBlank()
    ) {
        return ActiveTrackSource.SPOTIFY
    }
    if (mediaMetadata.id.startsWith("soundcloud:", ignoreCase = true) ||
        mediaMetadata.id.startsWith("sc:", ignoreCase = true)
    ) {
        return ActiveTrackSource.SOUNDCLOUD
    }
    return ActiveTrackSource.YOUTUBE
}
