/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import moe.rukamori.archivetune.spotify.session.SpotifySessionManager
import timber.log.Timber
import xyz.gianlu.librespot.audio.AbsChunkedInputStream
import xyz.gianlu.librespot.audio.NormalizationData
import xyz.gianlu.librespot.audio.PlayableContentFeeder
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.decoders.VorbisOnlyAudioQuality
import xyz.gianlu.librespot.metadata.PlayableId
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.min

/**
 * High-performance Media3/ExoPlayer DataSource for native Spotify playback:
 * 1. Directly receives decrypted Ogg Vorbis 320 kbps stream via Librespot ContentFeeder.
 * 2. Caches open stream handles to allow instant seeking without re-requesting AES keys from Spotify (anti-429).
 * 3. Extracts ReplayGain normalization parameters (track_gain_db, track_peak) for loudness matching.
 */
class LibrespotDataSource(
    private val sessionManager: SpotifySessionManager,
    private val onTrackResolved: ((trackUri: String, name: String, artist: String, norm: NormalizationData?, totalSize: Long) -> Unit)? = null,
) : BaseDataSource(/* isNetwork = */ true) {

    private var currentDataSpec: DataSpec? = null
    private var chunkedStream: AbsChunkedInputStream? = null
    private var bytesRemaining: Long = 0
    private var isOpened: Boolean = false
    private var currentTrackUri: String? = null
    private var currentCachedEntry: CachedStreamEntry? = null

    companion object {
        private const val TAG = "LibrespotDataSource"
        private const val MAX_CACHED_STREAMS = 5

        private class CachedStreamEntry(
            val loaded: PlayableContentFeeder.LoadedStream,
        ) {
            val activeReaders = java.util.concurrent.atomic.AtomicInteger(0)
        }

        // LRU stream cache to prevent excessive key requests during seek while bounding memory
        private val streamCache = Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedStreamEntry>(MAX_CACHED_STREAMS + 1, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStreamEntry>?): Boolean {
                    if (size > MAX_CACHED_STREAMS) {
                        val entry = eldest?.value
                        if (entry != null && entry.activeReaders.get() == 0) {
                            runCatching { entry.loaded.`in`.stream().close() }
                            return true
                        } else if (entry != null && entry.activeReaders.get() > 0) {
                            return false
                        }
                        return true
                    }
                    return false
                }
            }
        )

        // Dynamic quality picker - will be set per session based on account type
        private var qualityPicker = VorbisOnlyAudioQuality(AudioQuality.HIGH)

        fun clearCache() {
            synchronized(streamCache) {
                streamCache.values.forEach { entry ->
                    runCatching { entry.loaded.`in`.stream().close() }
                }
                streamCache.clear()
            }
        }

        /**
         * Updates quality picker based on detected account type.
         * Free accounts: 160 kbps max to avoid AudioKeyFailure 403
         * Premium accounts: 320 kbps full quality
         */
        fun setQualityForAccountType(session: xyz.gianlu.librespot.core.Session?) {
            val accountType = moe.rukamori.archivetune.spotify.utils.SpotifyAccountTypeDetector.detectAccountType(session)
            val targetQuality = moe.rukamori.archivetune.spotify.utils.SpotifyAccountTypeDetector.getMaxQualityForAccount(accountType)
            qualityPicker = VorbisOnlyAudioQuality(targetQuality)
            timber.log.Timber.tag(TAG).i(
                "Audio quality set to %s for account type %s",
                targetQuality,
                accountType
            )
        }

        fun parseCanonicalSpotifyUri(rawUriOrKey: String?): String? {
            if (rawUriOrKey.isNullOrBlank()) return null
            val clean = rawUriOrKey.substringBefore("?")
            return when {
                clean.startsWith("spotify:track:") -> clean
                clean.startsWith("spotify:episode:") -> clean
                clean.startsWith("spotify://track/") -> "spotify:track:" + clean.removePrefix("spotify://track/").substringBefore("/")
                clean.startsWith("spotify://episode/") -> "spotify:episode:" + clean.removePrefix("spotify://episode/").substringBefore("/")
                clean.startsWith("spotify://") -> "spotify:track:" + clean.removePrefix("spotify://").substringBefore("/")
                clean.contains("spotify.com/track/") -> "spotify:track:" + clean.substringAfter("spotify.com/track/").substringBefore("/").substringBefore("?")
                clean.contains("spotify.com/episode/") -> "spotify:episode:" + clean.substringAfter("spotify.com/episode/").substringBefore("/").substringBefore("?")
                clean.startsWith("spotify:") -> clean
                else -> null
            }
        }

        fun parseCanonicalSpotifyUri(uri: Uri?, customKey: String?): String? {
            parseCanonicalSpotifyUri(customKey)?.let { return it }
            parseCanonicalSpotifyUri(uri?.toString())?.let { return it }
            if (uri != null) {
                if (uri.scheme == "spotify" && !uri.host.isNullOrBlank()) {
                    return "spotify:track:" + uri.host
                }
                if (uri.path?.contains("/track/") == true) {
                    return "spotify:track:" + uri.path!!.substringAfter("/track/").substringBefore("/").substringBefore("?")
                }
                if (uri.path?.contains("/episode/") == true) {
                    return "spotify:episode:" + uri.path!!.substringAfter("/episode/").substringBefore("/").substringBefore("?")
                }
            }
            return null
        }
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)

        if (!sessionManager.hasActiveSession() && sessionManager.hasRestorableCredentials()) {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    sessionManager.awaitInitialRestoreIfNeeded()
                }
            }
        }

        val session = sessionManager.getActiveSession()
            ?: throw IOException("Spotify session is not active. Please authenticate in Settings.")

        val spotifyUri = parseCanonicalSpotifyUri(dataSpec.uri, dataSpec.key)
            ?: throw IOException("Invalid Spotify track URI: ${dataSpec.uri}")

        try {
            val stream: AbsChunkedInputStream
            val existingEntry = synchronized(streamCache) { streamCache[spotifyUri] }

            // FIX: When position == 0L (Previous track / Replay), always load fresh stream
            // to prevent ExoPlayer Error 3003 / UnrecognizedInputFormatException
            if (dataSpec.position == 0L) {
                // Evict any existing exhausted stream from cache
                synchronized(streamCache) { streamCache.remove(spotifyUri) }
                runCatching { existingEntry?.loaded?.`in`?.stream()?.close() }

                Timber.tag(TAG).d("Loading fresh Spotify stream (position=0): %s", spotifyUri)
                val playableId = PlayableId.fromUri(spotifyUri)
                val loaded = session.contentFeeder().load(playableId, qualityPicker, false, null)
                val newEntry = CachedStreamEntry(loaded)
                newEntry.activeReaders.set(1)
                synchronized(streamCache) {
                    streamCache[spotifyUri] = newEntry
                }
                currentCachedEntry = newEntry

                val totalStreamSize = loaded.`in`.stream().size().toLong()
                onTrackResolved?.invoke(
                    spotifyUri,
                    loaded.metadata.name,
                    loaded.metadata.artist,
                    loaded.normalizationData,
                    totalStreamSize,
                )

                stream = loaded.`in`.stream()
            } else if (existingEntry != null && !existingEntry.loaded.`in`.stream().isClosed && existingEntry.activeReaders.compareAndSet(0, 1)) {
                Timber.tag(TAG).d("Reusing cached stream for seek: %s @ pos=%d", spotifyUri, dataSpec.position)
                val s = existingEntry.loaded.`in`.stream()
                s.seek(dataSpec.position.toInt())
                stream = s
                currentCachedEntry = existingEntry
            } else {
                Timber.tag(TAG).d("Loading new Spotify track stream: %s", spotifyUri)
                val playableId = PlayableId.fromUri(spotifyUri)
                val loaded = session.contentFeeder().load(playableId, qualityPicker, false, null)
                val newEntry = CachedStreamEntry(loaded)
                newEntry.activeReaders.set(1)
                synchronized(streamCache) {
                    streamCache[spotifyUri] = newEntry
                }
                currentCachedEntry = newEntry

                val totalStreamSize = loaded.`in`.stream().size().toLong()
                onTrackResolved?.invoke(
                    spotifyUri,
                    loaded.metadata.name,
                    loaded.metadata.artist,
                    loaded.normalizationData,
                    totalStreamSize,
                )

                val s = loaded.`in`.stream()
                if (dataSpec.position > 0) {
                    s.seek(dataSpec.position.toInt())
                }
                stream = s
            }

            this.chunkedStream = stream
            this.currentTrackUri = spotifyUri

            val totalSize = stream.size().toLong()
            if (dataSpec.position > totalSize) {
                close()
                throw androidx.media3.datasource.DataSourceException(
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                )
            }

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                min(dataSpec.length, totalSize - dataSpec.position)
            } else {
                totalSize - dataSpec.position
            }

            isOpened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            close()
            // Clean up corrupted cache entry on failure
            synchronized(streamCache) { streamCache.remove(spotifyUri) }
            val msg = e.message.orEmpty()
            if (msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true)) {
                Timber.tag(TAG).w("Spotify HTTP 401 Unauthorized encountered. Clearing invalid stream cache...")
                clearCache()
                // [FIX-ZCODE] C2 (P1 из BOT_REPORTS): явная причина сбоя вместо generic IOException —
                // раньше пользователь видел только код 3003 (UnrecognizedInputFormat) без объяснения.
                throw IOException("Spotify authentication expired (HTTP 401). Please log in again in Settings.", e)
            }
            throw if (e is IOException) e else IOException("Failed to open Spotify stream ($spotifyUri): ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            min(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val stream = chunkedStream ?: throw IOException("Spotify stream is not ready for reading")
        val bytesRead: Int

        try {
            bytesRead = stream.read(buffer, offset, bytesToRead)
        } catch (e: InterruptedIOException) {
            return C.RESULT_END_OF_INPUT
        } catch (e: Exception) {
            throw IOException("Failed to read Spotify chunk: ${e.message}", e)
        }

        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentDataSpec?.uri

    override fun close() {
        if (isOpened) {
            isOpened = false
            transferEnded()
            currentCachedEntry?.activeReaders?.decrementAndGet()
            currentCachedEntry = null
            chunkedStream = null
            currentDataSpec = null
        }
    }
}
