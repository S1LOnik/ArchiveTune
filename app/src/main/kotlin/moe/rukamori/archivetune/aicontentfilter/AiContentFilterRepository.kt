/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.aicontentfilter

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.AiContentFilterEnabledKey
import moe.rukamori.archivetune.constants.AiMusicAuthorsKey
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiContentFilterRepository
    @Inject
    constructor(@ApplicationContext context: Context) {
        private val store = context.dataStore

        fun observeSettings() = store.data.map { preferences ->
            AiContentFilterSettings(
                enabled = preferences[AiContentFilterEnabledKey] ?: false,
                authors = preferences[AiMusicAuthorsKey] ?: DefaultAiMusicAuthors,
            )
        }.distinctUntilChanged()

        suspend fun getSettings() = observeSettings().first()
    }

internal fun normalizeAuthorName(value: String) =
    value.trim().replace(authorWhitespace, " ").lowercase()

private val authorWhitespace = Regex("""\s+""")

private val artistIdPattern = Regex("""(?:UC|MPRE)[a-zA-Z0-9_-]+""")
private val artistUrlPattern = Regex(
    """(?:https?://)?(?:(?:www|music)\.)?youtube\.com/(?:channel|browse)/([^/?#]+)(?:[/?#].*)?""",
)

internal fun authorFilterKey(value: String): String {
    val trimmed = value.trim()
    val id = artistUrlPattern.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed
    return if (artistIdPattern.matches(id)) "id:$id" else "name:${normalizeAuthorName(trimmed)}"
}
