/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.metadata

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object IsrcResolver {
    private const val TAG = "IsrcResolver"
    private const val SONG_LINK_API = "https://api.song.link/v1-alpha.1/links"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

    data class TrackMetadata(
        val isrc: String?,
        val title: String?,
        val artist: String?,
        val spotifyId: String?,
        val tidalId: String?,
        val deezerTrackId: String?,
        val qobuzTrackId: String?,
        val soundCloudUrl: String?,
    )

    fun resolve(url: String): TrackMetadata? {
        return try {
            val apiUrl =
                SONG_LINK_API.toHttpUrl().newBuilder()
                    .addQueryParameter("url", url)
                    .addQueryParameter("userCountry", "US")
                    .build()

            val request =
                Request.Builder()
                    .url(apiUrl)
                    .get()
                    .header("User-Agent", "ArchiveTune-Android")
                    .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.tag(TAG).w("song.link API returned ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val entityUniqueId =
                json.optString("entityUniqueId").takeIf { it.isNotBlank() }
                    ?: return null

            val entitiesByUniqueId = json.optJSONObject("entitiesByUniqueId") ?: return null
            val entity = entitiesByUniqueId.optJSONObject(entityUniqueId) ?: return null

            val isrc = entity.optString("isrc").takeIf { it.isNotBlank() }
            val title = entity.optString("title").takeIf { it.isNotBlank() }
            val artist = entity.optString("artistName").takeIf { it.isNotBlank() }

            val linksByPlatform = json.optJSONObject("linksByPlatform")
            val spotifyId =
                linksByPlatform?.optJSONObject("spotify")
                    ?.optString("entityId")
                    ?.takeIf { it.isNotBlank() }
            val tidalId =
                linksByPlatform?.optJSONObject("tidal")
                    ?.optString("entityId")
                    ?.takeIf { it.isNotBlank() }
            val deezerId =
                linksByPlatform?.optJSONObject("deezer")
                    ?.optString("entityId")
                    ?.takeIf { it.isNotBlank() }
            val qobuzId =
                linksByPlatform?.optJSONObject("qobuz")
                    ?.optString("entityId")
                    ?.takeIf { it.isNotBlank() }
            val soundCloudUrl =
                linksByPlatform?.optJSONObject("soundcloud")
                    ?.optString("url")
                    ?.takeIf { it.isNotBlank() }

            TrackMetadata(
                isrc = normalizeIsrc(isrc),
                title = title,
                artist = artist,
                spotifyId = spotifyId,
                tidalId = tidalId,
                deezerTrackId = deezerId,
                qobuzTrackId = qobuzId,
                soundCloudUrl = soundCloudUrl,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to resolve ISRC via song.link")
            null
        }
    }

    fun normalizeIsrc(isrc: String?): String? {
        if (isrc.isNullOrBlank()) return null
        val cleaned = isrc.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return if (cleaned.matches(Regex("^[A-Z]{2}[A-Z0-9]{3}\\d{7}$"))) cleaned else null
    }
}
