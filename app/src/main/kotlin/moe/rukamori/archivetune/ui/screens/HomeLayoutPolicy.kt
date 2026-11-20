/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Song

enum class HomeSectionId(
    val key: String,
    @StringRes val titleRes: Int,
) {
    QUICK_PICKS("quick_picks", R.string.quick_picks),
    CONTINUE_LISTEN("continue_listen", R.string.keep_listening),
    PINNED_PLAYLISTS("pinned_playlists", R.string.home_section_pinned_playlists),
    MIXES("mix_pages", R.string.home_mix_pages_title),
    SPOTIFY_PLAYLISTS("spotify_playlists", R.string.your_daily_discover),
    SPOTIFY_TRENDING("spotify_trending", R.string.trending_songs_for_you),
    COMMUNITY("community", R.string.filter_community_playlists),
    SPEED_DIAL("speed_dial", R.string.speed_dial),
    ACCOUNT_PLAYLISTS("account_playlists", R.string.playlists),
    FORGOTTEN_FAVORITES("forgotten_favorites", R.string.forgotten_favorites),
    SIMILAR("similar_recommendations", R.string.home_section_similar_recommendations),
    REMOTE_SECTIONS("remote_sections", R.string.home_section_more_recommendations),
    ;

    companion object {
        val DefaultOrder: List<HomeSectionId> = entries.toList()

        fun fromKey(key: String): HomeSectionId? = entries.firstOrNull { it.key == key }
    }
}

object HomeLayoutPolicy {
    fun resolveSectionOrder(rawOrder: String?): List<HomeSectionId> {
        if (rawOrder.isNullOrBlank()) return HomeSectionId.DefaultOrder
        val parsed =
            rawOrder
                .split(',')
                .mapNotNull { HomeSectionId.fromKey(it.trim()) }
                .distinct()
        val missing = HomeSectionId.DefaultOrder.filterNot { it in parsed }
        return parsed + missing
    }

    fun serializeSectionOrder(order: List<HomeSectionId>): String =
        order.distinct().joinToString(",") { it.key }

    fun moveSectionUp(
        order: List<HomeSectionId>,
        id: HomeSectionId,
    ): List<HomeSectionId> {
        val idx = order.indexOf(id)
        if (idx <= 0) return order
        return reorderSection(order, idx, idx - 1)
    }

    fun moveSectionDown(
        order: List<HomeSectionId>,
        id: HomeSectionId,
    ): List<HomeSectionId> {
        val idx = order.indexOf(id)
        if (idx < 0 || idx >= order.lastIndex) return order
        return reorderSection(order, idx, idx + 1)
    }

    fun reorderSection(
        order: List<HomeSectionId>,
        fromIndex: Int,
        toIndex: Int,
    ): List<HomeSectionId> {
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
            return order
        }
        val mutable = order.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    fun visibleSections(
        rawOrder: String?,
        hiddenKeys: Set<String>,
    ): List<HomeSectionId> = resolveSectionOrder(rawOrder).filterNot { it.key in hiddenKeys }

    fun toggleSectionVisibility(
        hiddenKeys: Set<String>,
        id: HomeSectionId,
    ): Set<String> =
        if (id.key in hiddenKeys) {
            hiddenKeys - id.key
        } else {
            hiddenKeys + id.key
        }

    fun selectOfflineContinueSong(
        downloaded: List<Song>,
        keepListening: List<LocalItem>,
        quickPicks: List<Song>,
        forgottenFavorites: List<Song>,
    ): Song? =
        downloaded.firstOrNull()
            ?: keepListening.filterIsInstance<Song>().firstOrNull()
            ?: quickPicks.firstOrNull()
            ?: forgottenFavorites.firstOrNull()
}

@Immutable
data class CoversMosaicTileSpec(
    val index: Int,
    val isHero: Boolean,
)

@Immutable
sealed interface CoversMosaicRowSpec {
    @Immutable
    data class SingleHero(val tile: CoversMosaicTileSpec) : CoversMosaicRowSpec

    @Immutable
    data class AsymmetricPair(
        val primary: CoversMosaicTileSpec,
        val secondary: CoversMosaicTileSpec,
    ) : CoversMosaicRowSpec

    @Immutable
    data class HeroAndStackTwo(
        val hero: CoversMosaicTileSpec,
        val top: CoversMosaicTileSpec,
        val bottom: CoversMosaicTileSpec,
    ) : CoversMosaicRowSpec

    @Immutable
    data class EqualRow(val tiles: List<CoversMosaicTileSpec>) : CoversMosaicRowSpec
}

object CoversMosaicPlanner {
    fun planRows(itemCount: Int): List<CoversMosaicRowSpec> {
        if (itemCount <= 0) return emptyList()
        return when (itemCount) {
            1 -> listOf(CoversMosaicRowSpec.SingleHero(CoversMosaicTileSpec(0, isHero = true)))
            2 ->
                listOf(
                    CoversMosaicRowSpec.AsymmetricPair(
                        primary = CoversMosaicTileSpec(0, isHero = true),
                        secondary = CoversMosaicTileSpec(1, isHero = false),
                    ),
                )
            3 ->
                listOf(
                    CoversMosaicRowSpec.HeroAndStackTwo(
                        hero = CoversMosaicTileSpec(0, isHero = true),
                        top = CoversMosaicTileSpec(1, isHero = false),
                        bottom = CoversMosaicTileSpec(2, isHero = false),
                    ),
                )
            4 ->
                listOf(
                    CoversMosaicRowSpec.HeroAndStackTwo(
                        hero = CoversMosaicTileSpec(0, isHero = true),
                        top = CoversMosaicTileSpec(1, isHero = false),
                        bottom = CoversMosaicTileSpec(2, isHero = false),
                    ),
                    CoversMosaicRowSpec.SingleHero(CoversMosaicTileSpec(3, isHero = false)),
                )
            5 ->
                listOf(
                    CoversMosaicRowSpec.HeroAndStackTwo(
                        hero = CoversMosaicTileSpec(0, isHero = true),
                        top = CoversMosaicTileSpec(1, isHero = false),
                        bottom = CoversMosaicTileSpec(2, isHero = false),
                    ),
                    CoversMosaicRowSpec.EqualRow(
                        listOf(
                            CoversMosaicTileSpec(3, isHero = false),
                            CoversMosaicTileSpec(4, isHero = false),
                        ),
                    ),
                )
            6 ->
                listOf(
                    CoversMosaicRowSpec.HeroAndStackTwo(
                        hero = CoversMosaicTileSpec(0, isHero = true),
                        top = CoversMosaicTileSpec(1, isHero = false),
                        bottom = CoversMosaicTileSpec(2, isHero = false),
                    ),
                    CoversMosaicRowSpec.EqualRow(
                        listOf(
                            CoversMosaicTileSpec(3, isHero = false),
                            CoversMosaicTileSpec(4, isHero = false),
                            CoversMosaicTileSpec(5, isHero = false),
                        ),
                    ),
                )
            else -> {
                val rows = mutableListOf<CoversMosaicRowSpec>()
                rows +=
                    CoversMosaicRowSpec.HeroAndStackTwo(
                        hero = CoversMosaicTileSpec(0, isHero = true),
                        top = CoversMosaicTileSpec(1, isHero = false),
                        bottom = CoversMosaicTileSpec(2, isHero = false),
                    )
                rows +=
                    CoversMosaicRowSpec.AsymmetricPair(
                        primary = CoversMosaicTileSpec(3, isHero = false),
                        secondary = CoversMosaicTileSpec(4, isHero = false),
                    )
                var cursor = 5
                while (cursor < itemCount) {
                    val remaining = itemCount - cursor
                    val takeCount =
                        when {
                            remaining == 4 -> 2
                            remaining >= 3 -> 3
                            else -> remaining
                        }
                    rows +=
                        CoversMosaicRowSpec.EqualRow(
                            (0 until takeCount).map { offset ->
                                CoversMosaicTileSpec(cursor + offset, isHero = false)
                            },
                        )
                    cursor += takeCount
                }
                rows
            }
        }
    }
}
