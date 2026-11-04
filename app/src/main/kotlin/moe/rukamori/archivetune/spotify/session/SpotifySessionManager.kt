/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.spotify.Authentication
import com.spotify.connectstate.Connect
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyAuth
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource
import moe.rukamori.archivetune.spotify.security.SpotifyAntiFraudGuard
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import xyz.gianlu.librespot.core.Session
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SpotifySessionState {
    data object Idle : SpotifySessionState
    data class Connecting(val method: String) : SpotifySessionState
    data class Discovering(val deviceName: String) : SpotifySessionState
    data class Connected(
        val username: String,
        val country: String?,
        val deviceId: String,
        val authMethod: String,
    ) : SpotifySessionState
    data class Error(val message: String) : SpotifySessionState
}

@Singleton
class SpotifySessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val credentialsStore = SpotifyCredentialsStore(context)

    private val _sessionState = MutableStateFlow<SpotifySessionState>(SpotifySessionState.Idle)
    val sessionState: StateFlow<SpotifySessionState> = _sessionState.asStateFlow()

    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow.asStateFlow()

    private val _isNativePlaybackEnabled = MutableStateFlow(true)
    val isNativePlaybackEnabled: StateFlow<Boolean> = _isNativePlaybackEnabled.asStateFlow()

    @Volatile
    private var activeSession: Session? = null

    val currentSession: Session?
        get() = activeSession

    fun getWebToken(
        scopes: String = "user-read-private,playlist-read-private,user-library-read,playlist-modify-public,playlist-modify-private",
    ): String? = runCatching { activeSession?.tokens()?.get(scopes) }.getOrNull()

    var playerPositionProvider: (() -> Long)? = null
    var playerIsPlayingProvider: (() -> Boolean)? = null

    val antiFraudGuard = SpotifyAntiFraudGuard { positionMs, isPlaying ->
        log("Heartbeat telemetry: pos=${positionMs}ms, playing=$isPlaying")
    }

    private val zeroConfService = SpotifyZeroConfService(
        context = context,
        credentialsStore = credentialsStore,
        onSessionReceived = { session ->
            handleSessionConnected(session, "ZeroConf")
        },
        onStatusLog = { msg ->
            log(msg)
        },
    )

    private var oauthServer: SpotifyOAuthLoopbackServer? = null

    private val initialRestoreJob: Deferred<Boolean> =
        scope.async(Dispatchers.IO) {
            tryRestoreSavedSession()
        }

    init {
        instance = this

        // Install uncaught exception handler to catch Librespot reconnect crashes (Vivo phone fix)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isLibrespot = generateSequence(throwable) { it.cause }.any { t ->
                t.stackTrace.any { it.className.startsWith("xyz.gianlu.librespot") }
            }
            val isMalformedLibrespotFrame =
                generateSequence(throwable) { it.cause }.any {
                    it is NegativeArraySizeException || it is OutOfMemoryError
                }
            if (isLibrespot && isMalformedLibrespotFrame) {
                Log.w(
                    TAG,
                    "Blocked Librespot reconnect crash on ${thread.name}: " +
                        "${throwable.javaClass.simpleName}: ${throwable.message}. " +
                        "Closing the corrupted session before a delayed restore.",
                )
                runCatching { activeSession?.close() }
                activeSession = null
                scope.launch(Dispatchers.IO) {
                    delay(2000)
                    tryRestoreSavedSession()
                }
            } else {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        scope.launch(Dispatchers.IO) {
            syncTokenFromCookiesIfNeeded()
        }
    }

    fun syncTokenFromCookiesBlocking(): String? {
        return runBlocking(Dispatchers.IO) {
            if (syncTokenFromCookiesIfNeeded()) {
                credentialsStore.getSavedAccessToken()
            } else {
                null
            }
        }
    }

    suspend fun syncTokenFromCookiesIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentToken = credentialsStore.getSavedAccessToken()
            val expiresAt = credentialsStore.getSavedTokenExpiresAt()
            if (!currentToken.isNullOrBlank() && expiresAt > 0L && System.currentTimeMillis() < (expiresAt - 60_000L)) {
                return@withContext true
            }

            // Check if sp_dc exists in DataStore
            val prefs = context.dataStore.data.first()
            val spDc = prefs[SpotifySpDcKey].orEmpty()
            val spKey = prefs[SpotifySpKeyKey].orEmpty()
            if (spDc.isBlank()) {
                Log.w(TAG, "syncTokenFromCookiesIfNeeded(): sp_dc is blank")
                return@withContext false
            }

            log("Fetching fresh Spotify access token using saved sp_dc cookie...")
            val result = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey)
            if (result.isSuccess) {
                val token = result.getOrThrow()
                val expiresInSec = (token.accessTokenExpirationTimestampMs - System.currentTimeMillis()) / 1000L
                val validSec = if (expiresInSec > 0) expiresInSec else 3600L
                credentialsStore.saveTokens(
                    accessToken = token.accessToken,
                    refreshToken = credentialsStore.getSavedRefreshToken().orEmpty(),
                    expiresInSeconds = validSec,
                )
                Spotify.accessToken = token.accessToken
                log("Successfully obtained and saved access token via sp_dc cookie! Expires in ${validSec}s")
                return@withContext true
            } else {
                log("Failed to fetch access token via sp_dc: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            log("Exception in syncTokenFromCookiesIfNeeded: ${e.message}")
        }
        false
    }

    fun forceRefreshTokenBlocking(): String? {
        return runBlocking(Dispatchers.IO) {
            forceRefreshToken()
        }
    }

    suspend fun forceRefreshToken(): String? = withContext(Dispatchers.IO) {
        try {
            val refreshToken = credentialsStore.getSavedRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                val refreshResult = SpotifyOAuthLoopbackServer.refreshAccessToken(refreshToken)
                if (refreshResult.isSuccess) {
                    val response = refreshResult.getOrThrow()
                    credentialsStore.saveTokens(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken ?: refreshToken,
                        expiresInSeconds = response.expiresIn,
                    )
                    Spotify.accessToken = response.accessToken
                    log("Force-refreshed Spotify token via OAuth refresh token")
                    return@withContext response.accessToken
                }
            }

            val prefs = context.dataStore.data.first()
            val spDc = prefs[SpotifySpDcKey].orEmpty()
            val spKey = prefs[SpotifySpKeyKey].orEmpty()
            if (spDc.isNotBlank()) {
                val result = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey)
                if (result.isSuccess) {
                    val token = result.getOrThrow()
                    val expiresInSec = (token.accessTokenExpirationTimestampMs - System.currentTimeMillis()) / 1000L
                    val validSec = if (expiresInSec > 0) expiresInSec else 3600L
                    credentialsStore.saveTokens(
                        accessToken = token.accessToken,
                        refreshToken = credentialsStore.getSavedRefreshToken().orEmpty(),
                        expiresInSeconds = validSec,
                    )
                    Spotify.accessToken = token.accessToken
                    log("Force-refreshed Spotify token via sp_dc cookie")
                    return@withContext token.accessToken
                }
            }
        } catch (e: Exception) {
            log("forceRefreshToken error: ${e.message}")
        }
        null
    }

    companion object {
        private const val TAG = "SpotifySessionManager"

        @Volatile
        var instance: SpotifySessionManager? = null
            private set

        fun get(): SpotifySessionManager? {
            val currentInstance = instance
            Log.i(TAG, "get(): instance available=${currentInstance != null}")
            return currentInstance
        }

        fun hasActiveSession(): Boolean {
            val hasActiveSession = instance?.hasActiveSession() == true
            Log.i(TAG, "hasActiveSession(): $hasActiveSession")
            return hasActiveSession
        }
    }

    fun hasActiveSession(): Boolean {
        val hasActiveSession = activeSession?.isValid == true &&
            _sessionState.value is SpotifySessionState.Connected
        Log.i(TAG, "hasActiveSession(): $hasActiveSession")
        return hasActiveSession
    }

    fun getActiveSession(): Session? = activeSession?.takeIf { it.isValid }

    fun getExternalCredentialsFile(): File? {
        return runCatching {
            val candidates = listOfNotNull(
                context.getExternalFilesDir(null)?.let { File(it, "spotify_credentials.json") },
                File("/sdcard/Android/data/moe.rukamori.archivetune.debug/files/spotify_credentials.json"),
                File("/sdcard/Android/data/moe.rukamori.archivetune/files/spotify_credentials.json"),
            )
            candidates.firstOrNull { it.exists() && it.length() > 0 }
        }.getOrNull()
    }

    fun hasSiblingCredentials(): Boolean {
        return runCatching {
            val siblingPkg = if (context.packageName.endsWith(".debug")) "moe.rukamori.archivetune" else "moe.rukamori.archivetune.debug"
            val siblingUri = Uri.parse("content://$siblingPkg.spotify_credentials_provider/credentials")
            context.contentResolver.query(siblingUri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } == true
        }.getOrDefault(false)
    }

    fun hasRestorableCredentials(): Boolean {
        val credentialsFile = File(context.filesDir, "spotify_credentials.json")
        if (credentialsFile.exists() && credentialsFile.length() > 0) return true
        if (credentialsStore.hasCredentials()) return true
        if (!credentialsStore.getSavedRefreshToken().isNullOrBlank()) return true
        if (!credentialsStore.getSavedAccessToken().isNullOrBlank()) return true
        if (getExternalCredentialsFile() != null) return true
        val prefs = runCatching { runBlocking(Dispatchers.IO) { context.dataStore.data.first() } }.getOrNull()
        if (prefs?.get(SpotifySpDcKey)?.isNotBlank() == true) return true
        return hasSiblingCredentials()
    }

    suspend fun awaitInitialRestoreIfNeeded(): Boolean {
        if (hasActiveSession()) return true
        if (initialRestoreJob.isCompleted) {
            return if (hasActiveSession()) true else tryRestoreSavedSession()
        }
        return initialRestoreJob.await() || tryRestoreSavedSession()
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$timestamp] $message"
        Log.i(TAG, "log(): $formatted")
        Timber.tag(TAG).d("%s", formatted)
        _logFlow.value = (_logFlow.value + formatted).takeLast(60)
    }

    private fun buildSessionConfig(): Session.Configuration {
        val cacheDir = File(context.cacheDir, "spotify_cache").apply { mkdirs() }
        val credentialsFile = File(context.filesDir, "spotify_credentials.json")
        return Session.Configuration.Builder()
            .setCacheEnabled(true)
            .setCacheDir(cacheDir)
            .setDoCacheCleanUp(true)
            .setRetryOnChunkError(true)
            .setStoreCredentials(true)
            .setStoredCredentialsFile(credentialsFile)
            .build()
    }

    private val restoreMutex = Mutex()

    suspend fun tryRestoreSavedSession(): Boolean = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            if (hasActiveSession()) return@withContext true

            val credentialsFile = File(context.filesDir, "spotify_credentials.json")
            if (!credentialsFile.exists() || credentialsFile.length() <= 0) {
                val external = getExternalCredentialsFile()
                if (external != null) {
                    runCatching {
                        external.copyTo(credentialsFile, overwrite = true)
                        log("Imported Spotify credentials from external storage backup (${external.absolutePath})")
                    }
                }
            }

            // Try restoring from sibling app (debug <-> release) via SpotifyCredentialsProvider
            val needTokensFromSibling = credentialsStore.getSavedRefreshToken().isNullOrBlank() && credentialsStore.getSavedAccessToken().isNullOrBlank()
            if (!credentialsFile.exists() || credentialsFile.length() <= 0 || needTokensFromSibling) {
                runCatching {
                    val siblingPkg = if (context.packageName.endsWith(".debug")) "moe.rukamori.archivetune" else "moe.rukamori.archivetune.debug"
                    val siblingUri = Uri.parse("content://$siblingPkg.spotify_credentials_provider/credentials")
                    context.contentResolver.query(siblingUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val creds = cursor.getString(cursor.getColumnIndexOrThrow("credentials"))
                            if (!creds.isNullOrBlank()) {
                                credentialsFile.writeText(creds)
                                log("Imported Spotify credentials from sibling app ($siblingPkg)")
                            }
                            val spDc = cursor.getString(cursor.getColumnIndexOrThrow("sp_dc"))
                            val spKey = cursor.getString(cursor.getColumnIndexOrThrow("sp_key"))
                            if (!spDc.isNullOrBlank()) {
                                runBlocking(Dispatchers.IO) {
                                    context.dataStore.edit {
                                        it[SpotifySpDcKey] = spDc
                                        if (!spKey.isNullOrBlank()) {
                                            it[SpotifySpKeyKey] = spKey
                                        }
                                    }
                                }
                                log("Imported Spotify sp_dc cookie from sibling app ($siblingPkg)")
                            }

                            val accessTokenCol = cursor.getColumnIndex("access_token")
                            if (accessTokenCol >= 0) {
                                val accessToken = cursor.getString(accessTokenCol)
                                val refreshToken = cursor.getString(cursor.getColumnIndexOrThrow("refresh_token"))
                                val expiresAtStr = cursor.getString(cursor.getColumnIndexOrThrow("expires_at"))
                                val expiresAt = expiresAtStr?.toLongOrNull() ?: 0L
                                val expiresInSec = if (expiresAt > System.currentTimeMillis()) (expiresAt - System.currentTimeMillis()) / 1000L else 3600L
                                if (!accessToken.isNullOrBlank()) {
                                    credentialsStore.saveTokens(accessToken, refreshToken, expiresInSec)
                                    log("Imported Spotify OAuth tokens from sibling app ($siblingPkg)")
                                }
                            }

                            val usernameCol = cursor.getColumnIndex("username")
                            if (usernameCol >= 0) {
                                val username = cursor.getString(usernameCol)
                                val authBlobBase64 = cursor.getString(cursor.getColumnIndexOrThrow("auth_blob"))
                                val authType = cursor.getString(cursor.getColumnIndexOrThrow("auth_type")) ?: "AUTHENTICATION_STORED_SPOTIFY_CREDENTIALS"
                                if (!username.isNullOrBlank() && !authBlobBase64.isNullOrBlank()) {
                                    runCatching {
                                        val blob = android.util.Base64.decode(authBlobBase64, android.util.Base64.NO_WRAP)
                                        credentialsStore.saveCredentials(username, blob, authType)
                                        log("Imported Spotify session credentials for '$username' from sibling app ($siblingPkg)")
                                    }
                                }
                            }
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "Sibling credentials query failed: ${it.message}")
                }
            }

            if (credentialsFile.exists() && credentialsFile.length() > 0) {
                // Self-healing: if file contains NUL bytes (e.g. from UTF-16) or encoding noise, sanitize it
                runCatching {
                    val rawText = credentialsFile.readText()
                    if (rawText.contains('\u0000') || !rawText.trim().startsWith("{")) {
                        val sanitized = rawText.replace("\u0000", "").trim()
                        if (sanitized.startsWith("{") && sanitized.endsWith("}")) {
                            credentialsFile.writeText(sanitized)
                            log("Sanitized corrupted credentials file encoding")
                        } else {
                            credentialsFile.delete()
                        }
                    }
                }
            }

            val credentialsFileExists = credentialsFile.exists()
            val credentialsFileSize = credentialsFile.length()
            Log.i(
                TAG,
                "tryRestoreSavedSession(): credentials file exists=$credentialsFileExists, " +
                    "size=$credentialsFileSize bytes",
            )
            val config = buildSessionConfig()

            // 1. Try restoring via Librespot stored credentials JSON file
            if (!credentialsFileExists || credentialsFileSize <= 0) {
                Log.w(TAG, "tryRestoreSavedSession(): saved credentials file is unavailable or empty")
            }
            if (credentialsFileExists && credentialsFileSize > 0) {
                try {
                    log("Restoring Spotify session from saved credentials file...")
                    _sessionState.value = SpotifySessionState.Connecting("StoredCredentials")
                    val session = Session.Builder(config)
                        .setDeviceId(credentialsStore.deviceId)
                        .setDeviceType(Connect.DeviceType.SMARTPHONE)
                        .stored(credentialsFile)
                        .create()
                    handleSessionConnected(session, "StoredFile")
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "tryRestoreSavedSession(): stored(credentialsFile) failed; " +
                            "message=${e.message}, cause=${e.cause}",
                        e,
                    )
                    Timber.tag(TAG).w(e, "Failed to restore from stored credentials file")
                }
            }

            // 2. Try restoring via Keystore saved credentials
            val username = credentialsStore.getSavedUsername()
            val blob = credentialsStore.getSavedAuthBlob()
            if (username != null && blob != null) {
                try {
                    log("Restoring Spotify session for '$username' from secure keystore...")
                    _sessionState.value = SpotifySessionState.Connecting("SecureKeystore")
                    val authTypeStr = credentialsStore.getSavedAuthType() ?: "AUTHENTICATION_STORED_SPOTIFY_CREDENTIALS"
                    val authType = runCatching { Authentication.AuthenticationType.valueOf(authTypeStr) }
                        .getOrDefault(Authentication.AuthenticationType.AUTHENTICATION_STORED_SPOTIFY_CREDENTIALS)
                    val creds = Authentication.LoginCredentials.newBuilder()
                        .setUsername(username)
                        .setTyp(authType)
                        .setAuthData(com.google.protobuf.ByteString.copyFrom(blob))
                        .build()
                    val session = Session.Builder(config)
                        .setDeviceId(credentialsStore.deviceId)
                        .setDeviceType(Connect.DeviceType.SMARTPHONE)
                        .credentials(creds)
                        .create()
                    handleSessionConnected(session, "KeystoreBlob")
                    return@withContext true
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to restore from secure keystore credentials")
                }
            }

            // 3. Try restoring via saved OAuth token / Refresh token
            val savedToken = credentialsStore.getSavedAccessToken()
            val refreshToken = credentialsStore.getSavedRefreshToken()
            val expiresAt = credentialsStore.getSavedTokenExpiresAt()
            val isTokenExpired = System.currentTimeMillis() >= (expiresAt - 60_000L)

            var effectiveToken = savedToken
            if ((effectiveToken.isNullOrBlank() || isTokenExpired) && !refreshToken.isNullOrBlank()) {
                log("OAuth token expired or missing, attempting refresh...")
                val refreshResult = SpotifyOAuthLoopbackServer.refreshAccessToken(refreshToken)
                refreshResult.fold(
                    onSuccess = { response ->
                        log("OAuth token successfully refreshed")
                        credentialsStore.saveTokens(
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken ?: refreshToken,
                            expiresInSeconds = response.expiresIn,
                        )
                        effectiveToken = response.accessToken
                    },
                    onFailure = { error ->
                        log("Failed to refresh OAuth token: ${error.message}")
                    }
                )
            }

            if (!effectiveToken.isNullOrBlank()) {
                try {
                    log("Restoring Spotify session using OAuth access token...")
                    _sessionState.value = SpotifySessionState.Connecting("OAuthToken")
                    val creds = Authentication.LoginCredentials.newBuilder()
                        .setTyp(Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
                        .setAuthData(com.google.protobuf.ByteString.copyFromUtf8(effectiveToken))
                        .build()
                    val session = Session.Builder(config)
                        .setDeviceId(credentialsStore.deviceId)
                        .setDeviceType(Connect.DeviceType.SMARTPHONE)
                        .credentials(creds)
                        .create()
                    handleSessionConnected(session, "OAuthToken")
                    return@withContext true
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to restore from saved OAuth token")
                    // If token failed with auth exception and we have a refresh token, try refreshing once
                    if (!refreshToken.isNullOrBlank() && effectiveToken == savedToken) {
                        try {
                            log("Retrying restore with refreshed token...")
                            val refreshResult = SpotifyOAuthLoopbackServer.refreshAccessToken(refreshToken)
                            if (refreshResult.isSuccess) {
                                val response = refreshResult.getOrThrow()
                                credentialsStore.saveTokens(
                                    accessToken = response.accessToken,
                                    refreshToken = response.refreshToken ?: refreshToken,
                                    expiresInSeconds = response.expiresIn,
                                )
                                val creds = Authentication.LoginCredentials.newBuilder()
                                    .setTyp(Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
                                    .setAuthData(com.google.protobuf.ByteString.copyFromUtf8(response.accessToken))
                                    .build()
                                val session = Session.Builder(config)
                                    .setDeviceId(credentialsStore.deviceId)
                                    .setDeviceType(Connect.DeviceType.SMARTPHONE)
                                    .credentials(creds)
                                    .create()
                                handleSessionConnected(session, "OAuthToken")
                                return@withContext true
                            }
                        } catch (re: Exception) {
                            Timber.tag(TAG).w(re, "Retry with refreshed token also failed")
                        }
                    }
                }
            }

            // 4. Try restoring via saved sp_dc cookie
            val prefs = context.dataStore.data.first()
            val spDc = prefs[SpotifySpDcKey].orEmpty()
            val spKey = prefs[SpotifySpKeyKey].orEmpty()
            if (spDc.isNotBlank()) {
                try {
                    log("Restoring Spotify session via saved sp_dc cookie...")
                    val result = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey)
                    if (result.isSuccess) {
                        val token = result.getOrThrow()
                        val expiresInSec = (token.accessTokenExpirationTimestampMs - System.currentTimeMillis()) / 1000L
                        val validSec = if (expiresInSec > 0) expiresInSec else 3600L
                        credentialsStore.saveTokens(
                            accessToken = token.accessToken,
                            refreshToken = credentialsStore.getSavedRefreshToken().orEmpty(),
                            expiresInSeconds = validSec,
                        )
                        Spotify.accessToken = token.accessToken
                        val creds = Authentication.LoginCredentials.newBuilder()
                            .setTyp(Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
                            .setAuthData(com.google.protobuf.ByteString.copyFromUtf8(token.accessToken))
                            .build()
                        val session = Session.Builder(config)
                            .setDeviceId(credentialsStore.deviceId)
                            .setDeviceType(Connect.DeviceType.SMARTPHONE)
                            .credentials(creds)
                            .create()
                        handleSessionConnected(session, "SpDcCookie")
                        return@withContext true
                    }
                } catch (e: Exception) {
                    log("Restore via sp_dc failed: ${e.message}")
                    Timber.tag(TAG).w(e, "Restore via sp_dc failed")
                }
            }

            log("No valid Spotify credentials found to restore")
            _sessionState.value = SpotifySessionState.Idle
            false
        }
    }

    /**
     * Spotifast on-device browser OAuth PKCE authentication.
     * Starts loopback server on 127.0.0.1:8989 and opens Spotify authorization in browser.
     */
    fun startOAuthBrowserLogin(launcherContext: Context = context) {
        scope.launch {
            try {
                _sessionState.value = SpotifySessionState.Connecting("Browser (OAuth PKCE)")
                log("Starting OAuth PKCE loopback server on port 8989...")

                oauthServer?.stop()
                val server = SpotifyOAuthLoopbackServer(
                    onCodeReceived = { code, verifier ->
                        scope.launch(Dispatchers.IO) {
                            handleOAuthCodeReceived(code, verifier)
                        }
                    },
                    onError = { err ->
                        log("OAuth error: $err")
                        _sessionState.value = SpotifySessionState.Error("OAuth error: $err")
                    },
                )
                oauthServer = server
                val (authUrl, _) = server.start(scope)

                log("Opening Spotify authorization in browser...")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launcherContext.startActivity(intent)
            } catch (e: Exception) {
                log("Failed to launch OAuth login: ${e.message}")
                _sessionState.value = SpotifySessionState.Error("Failed to launch browser: ${e.message}")
            }
        }
    }

    private suspend fun handleOAuthCodeReceived(code: String, verifier: String) {
        log("OAuth authorization code received, exchanging for access token...")
        val tokenResult = SpotifyOAuthLoopbackServer.exchangeCodeForToken(code, verifier)
        tokenResult.fold(
            onSuccess = { response ->
                log("Token received successfully (expires in ${response.expiresIn}s)")
                credentialsStore.saveTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresInSeconds = response.expiresIn,
                )

                // Initialize Librespot session with token
                try {
                    val config = buildSessionConfig()
                    val creds = Authentication.LoginCredentials.newBuilder()
                        .setTyp(Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
                        .setAuthData(com.google.protobuf.ByteString.copyFromUtf8(response.accessToken))
                        .build()

                    val session = Session.Builder(config)
                        .setDeviceId(credentialsStore.deviceId)
                        .setDeviceType(Connect.DeviceType.SMARTPHONE)
                        .credentials(creds)
                        .create()

                    handleSessionConnected(session, "OAuth")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to create Librespot session from OAuth token")
                    log("Session creation failed: ${e.message}")
                    _sessionState.value = SpotifySessionState.Error("Session error: ${e.message}")
                }
            },
            onFailure = { error ->
                log("Token exchange failed: ${error.message}")
                _sessionState.value = SpotifySessionState.Error("Token exchange failed: ${error.message}")
            },
        )
    }

    /**
     * Direct username and password authentication.
     */
    suspend fun loginWithUserPass(username: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _sessionState.value = SpotifySessionState.Connecting("Username/Password")
            log("Authenticating Spotify user '$username' with password...")

            val config = buildSessionConfig()
            val session = Session.Builder(config)
                .setDeviceId(credentialsStore.deviceId)
                .setDeviceType(Connect.DeviceType.SMARTPHONE)
                .userPass(username, pass)
                .create()

            handleSessionConnected(session, "UserPass")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Login with password failed")
            log("Password login failed: ${e.message}")
            _sessionState.value = SpotifySessionState.Error("Login failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * ZeroConf Spotify Connect discovery.
     */
    fun startZeroConfDiscovery(deviceName: String = SpotifyZeroConfService.DEFAULT_DEVICE_NAME) {
        scope.launch {
            _sessionState.value = SpotifySessionState.Discovering(deviceName)
            log("Starting Spotify Connect ZeroConf discovery as '$deviceName'...")
            val ok = zeroConfService.start(deviceName)
            if (!ok) {
                _sessionState.value = SpotifySessionState.Error("Failed to start ZeroConf service")
            }
        }
    }

    fun stopZeroConfDiscovery() {
        zeroConfService.stop()
        if (_sessionState.value is SpotifySessionState.Discovering) {
            _sessionState.value = SpotifySessionState.Idle
        }
    }

    /**
     * Connects session directly using a Spotify access token (e.g. from cookie session or OAuth).
     */
    suspend fun connectWithAccessToken(accessToken: String, username: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _sessionState.value = SpotifySessionState.Connecting("AccessToken")
            log("Connecting Spotify session with access token...")
            val config = buildSessionConfig()
            val creds = Authentication.LoginCredentials.newBuilder()
                .setTyp(Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
                .setAuthData(com.google.protobuf.ByteString.copyFromUtf8(accessToken))
                .apply {
                    if (!username.isNullOrBlank()) {
                        setUsername(username)
                    }
                }
                .build()

            val session = Session.Builder(config)
                .setDeviceId(credentialsStore.deviceId)
                .setDeviceType(Connect.DeviceType.SMARTPHONE)
                .credentials(creds)
                .create()

            handleSessionConnected(session, "AccessToken")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to connect with access token")
            log("Access token connection failed: ${e.message}")
            _sessionState.value = SpotifySessionState.Error("Connection failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun handleSessionConnected(session: Session, method: String) {
        activeSession = session
        val username = session.username()
        val country = session.countryCode()
        val deviceId = session.deviceId()
        Log.i(
            TAG,
            "handleSessionConnected(): successfully connected user=$username, " +
                "country=$country, deviceId=$deviceId, method=$method",
        )

        // Stop ZeroConf discovery now that a session is connected
        zeroConfService.stop()

        // Persist reusable credentials if provided by AP
        val apWelcome = session.apWelcome()
        if (apWelcome != null && apWelcome.hasReusableAuthCredentials()) {
            val reusableBytes = apWelcome.reusableAuthCredentials.toByteArray()
            val canonicalUser = apWelcome.canonicalUsername.ifEmpty { username }
            val authType = apWelcome.reusableAuthCredentialsType.name
            credentialsStore.saveCredentials(canonicalUser, reusableBytes, authType)
            log("Saved reusable session credentials for '$canonicalUser'")
        }

        // Export credentials to app-scoped external storage for cross-build persistence (debug <-> release)
        scope.launch(Dispatchers.IO) {
            delay(1200)
            runCatching {
                val internalFile = File(context.filesDir, "spotify_credentials.json")
                if (internalFile.exists() && internalFile.length() > 0) {
                    context.getExternalFilesDir(null)?.let { extDir ->
                        internalFile.copyTo(File(extDir, "spotify_credentials.json"), overwrite = true)
                        Log.i(TAG, "Exported Spotify credentials backup to app-scoped external storage")
                    }
                }
            }
        }

        _sessionState.value = SpotifySessionState.Connected(
            username = username,
            country = country,
            deviceId = deviceId,
            authMethod = method,
        )

        log("Connected: $username (country: $country, method: $method, 320 kbps Vorbis)")

        // Sync web access token for GraphQL / REST queries
        scope.launch(Dispatchers.IO) {
            syncTokenFromCookiesIfNeeded()
            runCatching {
                val webToken = session.tokens().get("user-read-private,playlist-read-private,user-library-read,playlist-modify-public,playlist-modify-private")
                if (!webToken.isNullOrBlank()) {
                    Spotify.accessToken = webToken
                    log("Synchronized Web API access token")
                }
            }
        }

        antiFraudGuard.startTelemetryMonitoring(
            scope = scope,
            getCurrentPositionMs = { playerPositionProvider?.invoke() ?: 0L },
            getIsPlaying = { playerIsPlayingProvider?.invoke() ?: false },
        )
        SpotifyPlaybackResolver.clearCache()

        // Configure audio quality based on account type to prevent 403 errors
        moe.rukamori.archivetune.spotify.datasource.LibrespotDataSource.setQualityForAccountType(session)
    }

    fun setNativePlaybackEnabled(enabled: Boolean) {
        _isNativePlaybackEnabled.value = enabled
        SpotifyPlaybackResolver.clearCache()
        log("Native Spotify playback set to: $enabled")
    }

    fun disconnect(clearStoredCredentials: Boolean = false) {
        antiFraudGuard.stopTelemetryMonitoring()
        oauthServer?.stop()
        oauthServer = null
        zeroConfService.stop()

        try {
            activeSession?.close()
        } catch (_: Exception) {}
        activeSession = null

        LibrespotDataSource.clearCache()
        SpotifyPlaybackResolver.clearCache()

        if (clearStoredCredentials) {
            credentialsStore.clearCredentials()
            val credentialsFile = File(context.filesDir, "spotify_credentials.json")
            if (credentialsFile.exists()) {
                runCatching { credentialsFile.delete() }
            }
        }

        _sessionState.value = SpotifySessionState.Idle
        log("Spotify session disconnected (credentials preserved: ${!clearStoredCredentials})")
    }
}
