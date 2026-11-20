/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.aicontentfilter

import androidx.compose.runtime.Immutable

private val canonicalArtistIdRegex = Regex("""(?:UC|MPRE)[a-zA-Z0-9_-]+""")
private val artistUrlDisplayRegex =
    Regex("""(?:https?://)?(?:(?:www|music)\.)?youtube\.com/(?:channel|browse)/([^/?#]+)(?:[/?#].*)?""")

/**
 * Result of a 1-tap toggle for adding or removing an artist from [AiMusicAuthorsKey].
 */
@Immutable
data class AiArtistFilterToggleResult(
    val updatedAuthors: Set<String>,
    val added: Boolean,
    val entry: String?,
    val previousAuthors: Set<String>,
)

/**
 * Builds all filter keys (`id:...` and `name:...`) that identify the given artist metadata.
 */
fun candidateArtistFilterKeys(
    artistName: String?,
    artistId: String? = null,
    channelId: String? = null,
): Set<String> =
    buildSet {
        artistId
            ?.trim()
            ?.takeIf { canonicalArtistIdRegex.matches(it) }
            ?.let { add("id:$it") }
        channelId
            ?.trim()
            ?.takeIf { canonicalArtistIdRegex.matches(it) }
            ?.let { add("id:$it") }
        artistName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { add("name:${normalizeAuthorName(it)}") }
    }

/**
 * Returns `true` if any stored author entry in [authors] matches this artist's ID, channel ID, or name.
 */
fun isArtistInAiFilter(
    authors: Set<String>,
    artistName: String?,
    artistId: String? = null,
    channelId: String? = null,
): Boolean {
    val candidateKeys = candidateArtistFilterKeys(artistName = artistName, artistId = artistId, channelId = channelId)
    if (candidateKeys.isEmpty()) return false
    val activeKeys = authors.mapTo(HashSet(authors.size), ::authorFilterKey)
    return candidateKeys.any { it in activeKeys }
}

/**
 * Formats the canonical string persisted in [AiMusicAuthorsKey], preferring a full YouTube channel URL
 * when a canonical channel ID is available, or falling back to the trimmed artist display name.
 */
fun formatAiFilterEntry(
    artistName: String?,
    artistId: String? = null,
    channelId: String? = null,
): String? {
    val canonicalId =
        channelId?.trim()?.takeIf { canonicalArtistIdRegex.matches(it) }
            ?: artistId?.trim()?.takeIf { canonicalArtistIdRegex.matches(it) }
    if (canonicalId != null) {
        return "https://www.youtube.com/channel/$canonicalId"
    }
    return artistName?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Adds or removes the artist from [authors] without duplicates while preserving unrelated entries.
 */
fun toggleArtistInAiFilter(
    authors: Set<String>,
    artistName: String?,
    artistId: String? = null,
    channelId: String? = null,
): AiArtistFilterToggleResult {
    val candidateKeys = candidateArtistFilterKeys(artistName = artistName, artistId = artistId, channelId = channelId)
    if (candidateKeys.isEmpty()) {
        return AiArtistFilterToggleResult(
            updatedAuthors = authors,
            added = false,
            entry = null,
            previousAuthors = authors,
        )
    }
    val matchingKeys = authors.filter { authorFilterKey(it) in candidateKeys }
    if (matchingKeys.isNotEmpty()) {
        val updated = authors.filterNot { authorFilterKey(it) in candidateKeys }.toSet()
        return AiArtistFilterToggleResult(
            updatedAuthors = updated,
            added = false,
            entry = matchingKeys.firstOrNull(),
            previousAuthors = authors,
        )
    }

    val entry = formatAiFilterEntry(artistName = artistName, artistId = artistId, channelId = channelId)
        ?: return AiArtistFilterToggleResult(
            updatedAuthors = authors,
            added = false,
            entry = null,
            previousAuthors = authors,
        )
    val entryKey = authorFilterKey(entry)
    val deduplicated =
        buildSet {
            addAll(authors.filterNot { authorFilterKey(it) == entryKey })
            add(entry)
        }
    return AiArtistFilterToggleResult(
        updatedAuthors = deduplicated,
        added = true,
        entry = entry,
        previousAuthors = authors,
    )
}

/**
 * Produces a concise human-readable label for removable author chips in ContentSettings.
 */
fun formatAiAuthorChipLabel(entry: String): String {
    val trimmed = entry.trim()
    val channelId = artistUrlDisplayRegex.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed
    return if (artistUrlDisplayRegex.matches(trimmed) && channelId.length > 16) {
        "${channelId.take(8)}…${channelId.takeLast(4)}"
    } else {
        channelId
    }
}
