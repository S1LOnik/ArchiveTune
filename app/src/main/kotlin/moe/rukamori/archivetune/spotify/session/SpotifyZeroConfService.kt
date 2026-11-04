/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.session

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import xyz.gianlu.librespot.ZeroconfServer
import xyz.gianlu.librespot.core.Session
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Spotify Connect ZeroConf / mDNS discovery service.
 * Acquires WifiManager.MulticastLock to prevent network drops in Android Doze mode
 * and exposes ZeroconfServer for LAN pairing.
 */
class SpotifyZeroConfService(
    private val context: Context,
    private val credentialsStore: SpotifyCredentialsStore,
    private val onSessionReceived: (Session) -> Unit,
    private val onStatusLog: (String) -> Unit = {},
) {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var zeroconfServer: ZeroconfServer? = null

    companion object {
        private const val TAG = "SpotifyZeroConf"
        const val DEFAULT_DEVICE_NAME = "ArchiveTune Connect"
    }

    suspend fun start(deviceName: String = DEFAULT_DEVICE_NAME): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Acquire Wi-Fi Multicast Lock
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("ArchiveTuneSpotifyLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            onStatusLog("MulticastLock acquired")

            // 2. Configure session storage
            val cacheDir = File(context.cacheDir, "spotify_cache").apply { mkdirs() }
            val credentialsFile = File(context.filesDir, "spotify_credentials.json")
            val config = Session.Configuration.Builder()
                .setCacheEnabled(true)
                .setCacheDir(cacheDir)
                .setDoCacheCleanUp(true)
                .setRetryOnChunkError(true)
                .setStoreCredentials(true)
                .setStoredCredentialsFile(credentialsFile)
                .build()

            // 3. Build & start Zeroconf server
            val server = ZeroconfServer.Builder(config)
                .setDeviceName(deviceName)
                .setDeviceId(credentialsStore.deviceId)
                .setDeviceType(com.spotify.connectstate.Connect.DeviceType.SMARTPHONE)
                .setPreferredLocale(Locale.getDefault().language)
                .setListenAll(true)
                .create()

            server.addSessionListener(object : ZeroconfServer.SessionListener {
                override fun sessionClosing(session: Session) {
                    onStatusLog("Spotify session closing: ${session.username()}")
                }

                override fun sessionChanged(session: Session) {
                    onStatusLog("ZeroConf login successful! User: ${session.username()}")
                    onSessionReceived(session)
                }
            })

            zeroconfServer = server
            Timber.tag(TAG).i("Spotify Connect advertised as '%s'", deviceName)
            onStatusLog("Spotify Connect visible as '$deviceName'")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error starting ZeroConf server")
            onStatusLog("ZeroConf error: ${e.message}")
            stop()
            false
        }
    }

    fun stop() {
        try {
            zeroconfServer?.close()
            zeroconfServer = null
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Error closing ZeroconfServer")
        }

        multicastLock?.let {
            if (it.isHeld) {
                runCatching { it.release() }
            }
        }
        multicastLock = null
        onStatusLog("ZeroConf service stopped")
    }

    val isRunning: Boolean
        get() = zeroconfServer != null
}
