/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.aicontentfilter

import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import javax.inject.Inject

class ObserveAiContentFilterUseCase
    @Inject
    constructor(private val repository: AiContentFilterRepository) {
        operator fun invoke() = repository.observeSettings()
    }

class LoadAiContentFilterPolicyUseCase
    @Inject
    constructor(private val repository: AiContentFilterRepository) {
        suspend operator fun invoke(): Set<String> {
            val settings = repository.getSettings()
            if (!settings.enabled) return emptySet()
            return settings.authors.mapTo(mutableSetOf(), ::authorFilterKey)
        }
    }

class FilterAiContentUseCase
    @Inject
    constructor() {
        operator fun <T : YTItem> invoke(items: List<T>, policy: Set<String>): List<T> {
            if (policy.isEmpty()) return items
            return items.filterNot { item ->
                when (item) {
                    is SongItem -> !item.isPodcast && item.artists.any { it.matches(policy) }
                    is AlbumItem -> item.artists.orEmpty().any { it.matches(policy) }
                    is ArtistItem -> "name:${normalizeAuthorName(item.title)}" in policy ||
                        "id:${item.id}" in policy || "id:${item.channelId}" in policy
                    else -> false
                }
            }
        }

        private fun Artist.matches(keys: Set<String>) =
            "name:${normalizeAuthorName(name)}" in keys || (id != null && "id:$id" in keys)
    }
