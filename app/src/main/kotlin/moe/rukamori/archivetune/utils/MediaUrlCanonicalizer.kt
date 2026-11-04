/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata

object MediaUrlCanonicalizer {

    /**
     * Resolves a canonical media URL from any raw ID or URL string for sharing and external downloaders.
     */
    fun canonicalUrl(rawId: String): String {
        val trimmed = rawId.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        // Spotify URI (spotify:track:..., spotify:episode:..., spotify:album:..., etc.)
        if (trimmed.startsWith("spotify:")) {
            val parts = trimmed.split(":")
            if (parts.size >= 3) {
                val entityType = parts[1]
                val entityId = parts[2]
                return "https://open.spotify.com/$entityType/$entityId"
            }
        }

        // Spotify base62 track ID (22 characters alphanumeric)
        if (trimmed.length == 22 && trimmed.matches(Regex("^[A-Za-z0-9]{22}$"))) {
            return "https://open.spotify.com/track/$trimmed"
        }

        // SoundCloud track URI (soundcloud:tracks:123456789)
        if (trimmed.startsWith("soundcloud:tracks:")) {
            val trackId = trimmed.removePrefix("soundcloud:tracks:")
            return "https://soundcloud.com/tracks/$trackId"
        }

        // YouTube playlist (starts with PL, OLAK, RD)
        if (trimmed.startsWith("PL") || trimmed.startsWith("OLAK") || trimmed.startsWith("RD")) {
            return "https://music.youtube.com/playlist?list=$trimmed"
        }

        // YouTube video ID (11 chars)
        if (trimmed.length == 11 && trimmed.matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
            return "https://music.youtube.com/watch?v=$trimmed"
        }

        // Default fallback to YouTube Music watch URL
        return "https://music.youtube.com/watch?v=$trimmed"
    }

    fun canonicalUrl(metadata: MediaMetadata): String {
        if (!metadata.spotifyTrackId.isNullOrBlank()) {
            val cleanId = metadata.spotifyTrackId.removePrefix("spotify:track:")
            return "https://open.spotify.com/track/$cleanId"
        }
        return canonicalUrl(metadata.id)
    }

    fun canonicalUrl(song: Song): String {
        return canonicalUrl(song.id)
    }

    fun canonicalUrl(songItem: SongItem): String {
        return canonicalUrl(songItem.id)
    }
}
