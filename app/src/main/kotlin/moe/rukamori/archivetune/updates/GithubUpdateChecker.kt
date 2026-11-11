package moe.rukamori.archivetune.updates

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks the fork's GitHub releases (S1LOnik/ArchiveTune) for a newer APK.
 */
object GithubUpdateChecker {
    data class LatestRelease(
        val version: String,
        val apkUrl: String,
        val notes: String,
    )

    private const val LatestReleaseUrl = "https://api.github.com/repos/S1LOnik/ArchiveTune/releases/latest"

    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun fetchLatest(): LatestRelease? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(LatestReleaseUrl)
                        .header("Accept", "application/vnd.github+json")
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val json = JSONObject(response.body.string())
                    val version = json.optString("tag_name").removePrefix("v").trim()
                    if (version.isEmpty()) return@runCatching null
                    val assets = json.optJSONArray("assets") ?: return@runCatching null
                    val apks =
                        (0 until assets.length())
                            .map { assets.getJSONObject(it) }
                            .filter {
                                val name = it.optString("name")
                                name.endsWith(".apk", ignoreCase = true) &&
                                    !name.contains("cleaner", ignoreCase = true)
                            }
                    val prefersArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
                    val preferred =
                        (if (prefersArm64) apks.firstOrNull { it.optString("name").contains("arm64-v8a") } else null)
                            ?: apks.firstOrNull { it.optString("name").contains("universal") }
                            ?: apks.firstOrNull()
                    val url = preferred?.optString("browser_download_url").orEmpty()
                    if (url.isEmpty()) return@runCatching null
                    LatestRelease(version, url, json.optString("body"))
                }
            }.getOrNull()
        }

    fun isNewer(
        latestVersion: String,
        currentVersion: String,
    ): Boolean {
        val latest = numericParts(latestVersion)
        val current = numericParts(currentVersion)
        val size = maxOf(latest.size, current.size)
        for (i in 0 until size) {
            val l = latest.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    private fun numericParts(version: String): List<Int> =
        Regex("""\d+""").findAll(version.substringBefore('-')).map { it.value.toIntOrNull() ?: 0 }.toList()
}
