/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

enum class LibraryFilter {
    SONGS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    PODCASTS,
    SPOTIFY,
    SOUNDCLOUD,
    LIBRARY,
}

val DefaultLibraryFilterOrder =
    listOf(
        LibraryFilter.LIBRARY,
        LibraryFilter.PLAYLISTS,
        LibraryFilter.SPOTIFY,
        LibraryFilter.SOUNDCLOUD,
        LibraryFilter.SONGS,
        LibraryFilter.ARTISTS,
        LibraryFilter.ALBUMS,
    )

val DefaultLibraryFilterOrderPreference =
    DefaultLibraryFilterOrder.joinToString(",") { it.name }

fun String.toLibraryFilterOrder(): List<LibraryFilter> {
    val savedOrder =
        split(",")
            .mapNotNull { savedFilter ->
                DefaultLibraryFilterOrder.firstOrNull { it.name == savedFilter.trim() && it != LibraryFilter.PODCASTS }
            }.distinct()

    return (savedOrder + DefaultLibraryFilterOrder.filterNot(savedOrder::contains)).filter { it != LibraryFilter.PODCASTS }
}

fun List<LibraryFilter>.toLibraryFilterPreference(): String {
    val orderedFilters = distinct().filter { it != LibraryFilter.PODCASTS }
    return (orderedFilters + DefaultLibraryFilterOrder.filterNot(orderedFilters::contains))
        .filter { it != LibraryFilter.PODCASTS }
        .joinToString(",") { it.name }
}
