/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPolicyTest {
    @Test
    fun `resolveSectionOrder appends missing defaults and ignores unknown keys`() {
        val resolved =
            HomeLayoutPolicy.resolveSectionOrder("speed_dial,unknown_section,quick_picks,speed_dial")
        assertEquals(HomeSectionId.SPEED_DIAL, resolved[0])
        assertEquals(HomeSectionId.QUICK_PICKS, resolved[1])
        assertEquals(HomeSectionId.DefaultOrder.size, resolved.size)
        assertEquals(resolved.distinct().size, resolved.size)
    }

    @Test
    fun `moveSectionUp and moveSectionDown respect bounds and serialize cleanly`() {
        val start = listOf(HomeSectionId.QUICK_PICKS, HomeSectionId.MIXES, HomeSectionId.SPEED_DIAL)
        assertEquals(start, HomeLayoutPolicy.moveSectionUp(start, HomeSectionId.QUICK_PICKS))
        val moved = HomeLayoutPolicy.moveSectionDown(start, HomeSectionId.QUICK_PICKS)
        assertEquals(HomeSectionId.MIXES, moved[0])
        assertEquals(HomeSectionId.QUICK_PICKS, moved[1])
        assertEquals("mix_pages,quick_picks,speed_dial", HomeLayoutPolicy.serializeSectionOrder(moved))
    }

    @Test
    fun `visibleSections filters out hidden keys`() {
        val visible =
            HomeLayoutPolicy.visibleSections(
                rawOrder = null,
                hiddenKeys = setOf(HomeSectionId.COMMUNITY.key, HomeSectionId.MIXES.key),
            )
        assertTrue(HomeSectionId.COMMUNITY !in visible)
        assertTrue(HomeSectionId.MIXES !in visible)
        assertEquals(HomeSectionId.DefaultOrder.size - 2, visible.size)
    }

    @Test
    fun `covers mosaic handles 0, 1, 2, 3, and 7 plus items without missing indices`() {
        assertTrue(CoversMosaicPlanner.planRows(0).isEmpty())

        val one = CoversMosaicPlanner.planRows(1)
        assertEquals(1, one.size)
        assertTrue(one.first() is CoversMosaicRowSpec.SingleHero)

        val two = CoversMosaicPlanner.planRows(2)
        assertEquals(1, two.size)
        assertTrue(two.first() is CoversMosaicRowSpec.AsymmetricPair)

        val three = CoversMosaicPlanner.planRows(3)
        assertEquals(1, three.size)
        assertTrue(three.first() is CoversMosaicRowSpec.HeroAndStackTwo)

        for (count in listOf(4, 5, 6, 7, 8, 9, 10, 13)) {
            val rows = CoversMosaicPlanner.planRows(count)
            val flattened =
                rows.flatMap { row ->
                    when (row) {
                        is CoversMosaicRowSpec.SingleHero -> listOf(row.tile.index)
                        is CoversMosaicRowSpec.AsymmetricPair -> listOf(row.primary.index, row.secondary.index)
                        is CoversMosaicRowSpec.HeroAndStackTwo ->
                            listOf(row.hero.index, row.top.index, row.bottom.index)
                        is CoversMosaicRowSpec.EqualRow -> row.tiles.map { it.index }
                    }
                }
            assertEquals((0 until count).toList(), flattened)
        }
    }

    @Test
    fun `selectOfflineContinueSong falls back cleanly across downloaded, keepListening, quickPicks, forgotten`() {
        val downloaded = fakeSong("dl_1", "Downloaded Track")
        val quick = fakeSong("qp_1", "Quick Track")
        val forgotten = fakeSong("fg_1", "Forgotten Track")

        assertEquals(
            downloaded,
            HomeLayoutPolicy.selectOfflineContinueSong(listOf(downloaded), emptyList(), listOf(quick), listOf(forgotten)),
        )
        assertEquals(
            quick,
            HomeLayoutPolicy.selectOfflineContinueSong(emptyList(), emptyList(), listOf(quick), listOf(forgotten)),
        )
        assertEquals(
            forgotten,
            HomeLayoutPolicy.selectOfflineContinueSong(emptyList(), emptyList(), emptyList(), listOf(forgotten)),
        )
        assertNull(HomeLayoutPolicy.selectOfflineContinueSong(emptyList(), emptyList(), emptyList(), emptyList()))
    }

    private fun fakeSong(
        id: String,
        title: String,
    ): Song =
        Song(
            song = SongEntity(id = id, title = title),
            artists = emptyList(),
        )
}
