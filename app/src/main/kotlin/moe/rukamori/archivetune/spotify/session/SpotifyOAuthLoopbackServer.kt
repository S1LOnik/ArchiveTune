/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.session

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.spotify.security.SpotifyAntiFraudGuard
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device OAuth 2.0 PKCE authentication for Spotify (as used in Spotifast).
 * Runs a lightweight loopback server on 127.0.0.1:8989 to capture the authorization code
 * directly on the device without requiring external redirect servers or ZeroConf.
 */
class SpotifyOAuthLoopbackServer(
    private val onCodeReceived: (code: String, verifier: String) -> Unit,
    private val onError: (error: String) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var currentVerifier: String? = null

    companion object {
        private const val TAG = "SpotifyOAuthLoopback"
        const val CLIENT_ID = SpotifyAntiFraudGuard.OFFICIAL_CLIENT_ID
        const val REDIRECT_URI = SpotifyAntiFraudGuard.OFFICIAL_REDIRECT_URI
        const val PORT = 8989

        val SCOPES = listOf(
            "streaming",
            "app-remote-control",
            "user-read-playback-state",
            "user-modify-playback-state",
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-read-private",
            "user-read-email",
        ).joinToString(" ")

        fun generateCodeVerifier(): String {
            val random = SecureRandom()
            val bytes = ByteArray(64)
            random.nextBytes(bytes)
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun generateCodeChallenge(verifier: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        fun buildAuthUrl(codeChallenge: String): String {
            val encodedRedirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
            val encodedScopes = URLEncoder.encode(SCOPES, "UTF-8")
            val encodedChallenge = URLEncoder.encode(codeChallenge, "UTF-8")

            return "https://accounts.spotify.com/authorize?" +
                "response_type=code" +
                "&client_id=$CLIENT_ID" +
                "&redirect_uri=$encodedRedirect" +
                "&code_challenge=$encodedChallenge" +
                "&code_challenge_method=S256" +
                "&scope=$encodedScopes"
        }

        suspend fun exchangeCodeForToken(
            code: String,
            codeVerifier: String,
        ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
            runCatching {
                val formBody = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", CLIENT_ID)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code", code)
                    .add("code_verifier", codeVerifier)
                    .build()

                val request = Request.Builder()
                    .url("https://accounts.spotify.com/api/token")
                    .post(formBody)
                    .header("User-Agent", SpotifyAntiFraudGuard.OFFICIAL_DESKTOP_USER_AGENT)
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: $bodyStr")
                }

                val json = JSONObject(bodyStr)
                OAuthTokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresIn = json.optLong("expires_in", 3600L),
                )
            }
        }

        @JvmStatic
        fun refreshAccessTokenBlocking(
            refreshToken: String,
        ): OAuthTokenResponse? {
            return runCatching {
                val formBody = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("client_id", CLIENT_ID)
                    .add("refresh_token", refreshToken)
                    .build()

                val request = Request.Builder()
                    .url("https://accounts.spotify.com/api/token")
                    .post(formBody)
                    .header("User-Agent", SpotifyAntiFraudGuard.OFFICIAL_DESKTOP_USER_AGENT)
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    android.util.Log.e("SPOTIFY_AUTH", "refreshAccessTokenBlocking HTTP ${response.code}: $bodyStr")
                    return null
                }

                val json = JSONObject(bodyStr)
                OAuthTokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
                    expiresIn = json.optLong("expires_in", 3600L),
                )
            }.getOrElse { e ->
                android.util.Log.e("SPOTIFY_AUTH", "refreshAccessTokenBlocking failed: ${e.message}", e)
                null
            }
        }

        suspend fun refreshAccessToken(
            refreshToken: String,
        ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
            val resp = refreshAccessTokenBlocking(refreshToken)
            if (resp != null) {
                Result.success(resp)
            } else {
                Result.failure(IOException("Failed to refresh Spotify OAuth token"))
            }
        }
    }

    data class OAuthTokenResponse(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long,
    )

    fun start(scope: CoroutineScope): Pair<String, String> {
        stop()

        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        currentVerifier = verifier

        val server = ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 180_000 // 3 minutes timeout
        serverSocket = server
        isRunning.set(true)

        val authUrl = buildAuthUrl(challenge)

        scope.launch(Dispatchers.IO) {
            try {
                while (isRunning.get() && !server.isClosed) {
                    val socket = server.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val requestLine = reader.readLine() ?: continue

                    Timber.tag(TAG).d("Loopback request: %s", requestLine)

                    if (requestLine.startsWith("GET /login")) {
                        val pathAndQuery = requestLine.split(" ").getOrNull(1) ?: "/login"
                        val parsedUri = Uri.parse("http://127.0.0.1:$PORT$pathAndQuery")
                        val code = parsedUri.getQueryParameter("code")
                        val error = parsedUri.getQueryParameter("error")

                        val responseHtml = if (code != null) {
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                              <meta charset="utf-8">
                              <meta name="viewport" content="width=device-width, initial-scale=1.0">
                              <title>ArchiveTune</title>
                              <style>
                                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #121212; color: #fff; text-align: center; padding: 48px 20px; }
                                .card { max-width: 440px; margin: 0 auto; background: #1e1e1e; padding: 32px 24px; border-radius: 16px; border: 1px solid #2e2e2e; }
                                h1 { color: #1DB954; font-size: 22px; margin-bottom: 12px; }
                                p { color: #b3b3b3; font-size: 15px; line-height: 1.5; }
                              </style>
                            </head>
                            <body>
                              <div class="card">
                                <h1>Авторизация успешна!</h1>
                                <p>Вход в Spotify выполнен. Теперь вы можете закрыть эту вкладку и вернуться в ArchiveTune.</p>
                              </div>
                            </body>
                            </html>
                            """.trimIndent()
                        } else {
                            """
                            <!DOCTYPE html>
                            <html>
                            <head><meta charset="utf-8"><title>ArchiveTune</title></head>
                            <body style="background:#121212;color:#fff;text-align:center;padding:48px;">
                              <h2 style="color:#e91429;">Ошибка авторизации: ${error ?: "Отменено пользователем"}</h2>
                            </body>
                            </html>
                            """.trimIndent()
                        }

                        val bytes = responseHtml.toByteArray(Charsets.UTF_8)
                        val out = socket.getOutputStream()
                        val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        out.write(header.toByteArray(Charsets.UTF_8))
                        out.write(bytes)
                        out.flush()
                        socket.close()

                        stop()

                        if (code != null) {
                            onCodeReceived(code, verifier)
                        } else {
                            onError(error ?: "User cancelled authorization")
                        }
                        break
                    } else {
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Timber.tag(TAG).w(e, "Loopback server stopped with exception")
                    onError(e.message ?: "Authentication timed out")
                }
            } finally {
                stop()
            }
        }

        return Pair(authUrl, verifier)
    }

    fun stop() {
        if (isRunning.getAndSet(false)) {
            runCatching { serverSocket?.close() }
            serverSocket = null
        }
    }
}
