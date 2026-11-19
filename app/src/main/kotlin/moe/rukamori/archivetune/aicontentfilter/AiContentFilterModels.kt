/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.aicontentfilter

import androidx.compose.runtime.Immutable

@Immutable
data class AiContentFilterSettings(
    val enabled: Boolean,
    val authors: Set<String>,
)

// Each channel states that its music is generated with Suno in its channel description.
internal val DefaultAiMusicAuthors = setOf(
    "https://www.youtube.com/channel/UCCmPdCuqNXqoPjWkYRaWgwQ",
    "https://www.youtube.com/channel/UCs2kYL2gjrIniec7EDUPSZg",
)
