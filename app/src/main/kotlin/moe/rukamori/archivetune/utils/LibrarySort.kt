/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.CollationKey
import java.text.Collator
import java.util.Locale

internal suspend fun <T> List<T>.sortedByLocalizedArtist(selector: (T) -> String): List<T> =
    withContext(Dispatchers.Default) {
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        val keys = HashMap<String, CollationKey>()
        map { item ->
            val name = selector(item)
            item to keys.getOrPut(name) { collator.getCollationKey(name) }
        }.sortedBy { it.second }.map { it.first }
    }
