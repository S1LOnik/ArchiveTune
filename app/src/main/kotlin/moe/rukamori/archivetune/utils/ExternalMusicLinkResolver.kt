package moe.rukamori.archivetune.utils

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Resolves Spotify / SoundCloud web links into something ArchiveTune can play:
 * a text query for a track (to be matched on YouTube Music) or a Spotify playlist id.
 */
object ExternalMusicLinkResolver {
    sealed interface Result {
        data class TrackQuery(
            val query: String,
        ) : Result

        data class SpotifyPlaylist(
            val playlistId: String,
        ) : Result

        data object Unsupported : Result
    }

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/135.0.0.0 Mobile Safari/537.36"

    private val spotifyHosts = setOf("open.spotify.com", "spotify.link")
    private val soundCloudHosts = setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com")

    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    fun isExternalMusicHost(host: String?): Boolean {
        val normalized = host?.lowercase(Locale.US) ?: return false
        return normalized in spotifyHosts || normalized in soundCloudHosts
    }

    suspend fun resolve(uri: Uri): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = uri.host?.lowercase(Locale.US).orEmpty()
                when {
                    host in spotifyHosts -> resolveSpotify(uri)
                    host in soundCloudHosts -> resolveSoundCloud(uri)
                    else -> Result.Unsupported
                }
            }.getOrDefault(Result.Unsupported)
        }

    private fun resolveSpotify(uri: Uri): Result {
        var url = uri.toString()
        if (uri.host.equals("spotify.link", ignoreCase = true)) {
            url = followRedirects(url) ?: return Result.Unsupported
        }
        val segments =
            Uri
                .parse(url)
                .pathSegments
                .filterNot { it.startsWith("intl-") }
        val kind = segments.getOrNull(0) ?: return Result.Unsupported
        val id = segments.getOrNull(1) ?: return Result.Unsupported

        return when (kind) {
            "playlist" -> Result.SpotifyPlaylist(id)

            "track" -> {
                val html = httpGet("https://open.spotify.com/track/$id") ?: return Result.Unsupported
                val title = metaContent(html, "og:title") ?: return Result.Unsupported
                val artist = spotifyArtistFromDescription(metaContent(html, "og:description"), title)
                Result.TrackQuery(listOfNotNull(title, artist).joinToString(" "))
            }

            else -> Result.Unsupported
        }
    }

    private fun resolveSoundCloud(uri: Uri): Result {
        var url = uri.toString()
        if (uri.host.equals("on.soundcloud.com", ignoreCase = true)) {
            url = followRedirects(url) ?: return Result.Unsupported
        }
        val segments = Uri.parse(url).pathSegments
        if (segments.size < 2 || segments.getOrNull(1) == "sets" || segments.getOrNull(0) == "discover") {
            return Result.Unsupported
        }
        val endpoint =
            "https://soundcloud.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
        val body = httpGet(endpoint) ?: return Result.Unsupported
        val json = JSONObject(body)
        val rawTitle = json.optString("title").takeIf { it.isNotBlank() } ?: return Result.Unsupported
        val author = json.optString("author_name").takeIf { it.isNotBlank() }
        val title = soundCloudTitleWithoutAuthor(rawTitle, author)
        return Result.TrackQuery(listOfNotNull(title, author).joinToString(" "))
    }

    /** Spotify og:description looks like "Artist · Album · Song · 2020"; the first part is the artist. */
    internal fun spotifyArtistFromDescription(
        description: String?,
        title: String,
    ): String? =
        description
            ?.split("·")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }

    /** SoundCloud oEmbed titles look like "Track by Author"; strip the author suffix. */
    internal fun soundCloudTitleWithoutAuthor(
        rawTitle: String,
        author: String?,
    ): String =
        if (author != null && rawTitle.endsWith(" by $author")) {
            rawTitle.removeSuffix(" by $author")
        } else {
            rawTitle.substringBeforeLast(" by ")
        }

    private fun httpGet(url: String): String? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept-Language", "en")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }

    private fun followRedirects(url: String): String? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
        client.newCall(request).execute().use { response ->
            return response.request.url.toString()
        }
    }

    internal fun metaContent(
        html: String,
        property: String,
    ): String? {
        val pattern =
            Regex(
                """<meta[^>]+property=["']${Regex.escape(property)}["'][^>]+content=["']([^"']*)["']""",
                RegexOption.IGNORE_CASE,
            )
        val raw = pattern.find(html)?.groupValues?.getOrNull(1) ?: return null
        return raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#x27;", "'")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
