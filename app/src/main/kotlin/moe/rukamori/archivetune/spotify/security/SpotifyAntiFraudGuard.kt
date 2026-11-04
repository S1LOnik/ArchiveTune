/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Anti-fraud and rate-limiting guard for Spotify sessions:
 * 1. Emulation of official desktop client headers and User-Agent.
 * 2. Rate-limiter on audio decryption key requests to prevent HTTP 429 Too Many Requests.
 * 3. Play-state telemetry / Spirc heartbeat reporter.
 */
class SpotifyAntiFraudGuard(
    private val onSendPlayStateHeartbeat: suspend (positionMs: Long, isPlaying: Boolean) -> Unit = { _, _ -> },
) {
    companion object {
        const val OFFICIAL_DESKTOP_USER_AGENT = "Spotify/1.2.31.1205.g4d59ad7c (Linux; x86_64; desktop)"
        const val OFFICIAL_CLIENT_ID = "65b708073fc0480ea92a077233ca87bd"
        const val OFFICIAL_REDIRECT_URI = "http://127.0.0.1:8989/login"

        private const val MIN_KEY_REQUEST_INTERVAL_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 20_000L
    }

    private val lastKeyRequestTimestamp = AtomicLong(0L)
    private var heartbeatJob: Job? = null
    private val isMonitoring = AtomicBoolean(false)

    fun canRequestNextKey(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastKeyRequestTimestamp.get()
        if (now - last < MIN_KEY_REQUEST_INTERVAL_MS) {
            return false
        }
        return lastKeyRequestTimestamp.compareAndSet(last, now)
    }

    fun recordKeyRequested() {
        lastKeyRequestTimestamp.set(System.currentTimeMillis())
    }

    fun startTelemetryMonitoring(
        scope: CoroutineScope,
        getCurrentPositionMs: () -> Long,
        getIsPlaying: () -> Boolean,
    ) {
        if (isMonitoring.getAndSet(true)) return

        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && isMonitoring.get()) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val pos = getCurrentPositionMs()
                    val playing = getIsPlaying()
                    if (playing) {
                        onSendPlayStateHeartbeat(pos, true)
                    }
                } catch (_: Exception) {
                    // Suppress telemetry transport delays
                }
            }
        }
    }

    fun stopTelemetryMonitoring() {
        isMonitoring.set(false)
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
