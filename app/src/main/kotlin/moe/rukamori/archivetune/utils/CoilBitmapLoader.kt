/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import coil3.imageLoader
import coil3.network.HttpException
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.utils.YTThumbQuality
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.getNextFallbackUrl
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.ui.utils.resolveMaxresFallback
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToInt

internal const val NotificationArtworkSizePx = 1080
private const val LegacyMediaMetadataBitmapMaxSizeDp = 320
private const val ArtworkMemoryCacheEntries = 8
private const val FallbackGoogleCdnDimensionPx = 544
private val TransientRetryDelaysMs = longArrayOf(300L, 900L)

class CoilBitmapLoader(
    context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    private val applicationContext = context.applicationContext
    private val maximumArtworkDimensionPx = context.resolveMaximumArtworkDimensionPx()
    private val stateLock = Any()
    private val inFlightRequests = mutableMapOf<String, Deferred<Bitmap>>()
    private val bitmapCache =
        object : LinkedHashMap<String, Bitmap>(ArtworkMemoryCacheEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
                size > ArtworkMemoryCacheEntries
        }

    @Volatile
    private var lastFailedArtworkUrl: String? = null

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.Default) {
            require(data.isNotEmpty()) { "Empty image data" }
            val bitmap =
                checkNotNull(decodeSampledBitmap(data, maximumArtworkDimensionPx)) {
                    "Could not decode image data"
                }
            ensureActive()
            bitmap
                .scaleToNotificationArtwork(maximumArtworkDimensionPx)
                .toOwnedMediaSessionBitmap()
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            loadBitmapAsync(uri)
        }

    internal suspend fun loadBitmapAsync(uri: Uri): Bitmap {
        val rawUrl = uri.toString().trim()
        require(rawUrl.isNotEmpty()) { "Empty artwork URI" }

        cachedBitmapFor(rawUrl)?.let { return it }

        val deferred =
            synchronized(stateLock) {
                inFlightRequests[rawUrl]?.takeIf { it.isActive }
                    ?: scope.async(Dispatchers.IO) {
                        loadBitmapWithFallbackAndRetry(rawUrl)
                    }.also { created ->
                        inFlightRequests[rawUrl] = created
                        created.invokeOnCompletion {
                            synchronized(stateLock) {
                                if (inFlightRequests[rawUrl] === created) {
                                    inFlightRequests.remove(rawUrl)
                                }
                            }
                        }
                    }
            }

        return deferred.await().toOwnedMediaSessionBitmap()
    }

    internal fun consumeFailedArtworkUri(uri: Uri): Boolean {
        val rawUrl = uri.toString().trim()
        if (rawUrl.isEmpty()) return false
        synchronized(stateLock) {
            if (lastFailedArtworkUrl != rawUrl) return false
            lastFailedArtworkUrl = null
            return true
        }
    }

    private fun cachedBitmapFor(rawUrl: String): Bitmap? =
        synchronized(stateLock) {
            bitmapCache[rawUrl]?.takeUnless { it.isRecycled }?.toOwnedMediaSessionBitmap()
                ?: run {
                    bitmapCache.remove(rawUrl)
                    null
                }
        }

    private suspend fun loadBitmapWithFallbackAndRetry(rawUrl: String): Bitmap {
        cachedBitmapFor(rawUrl)?.let { return it }

        val candidates = buildArtworkCandidateUrls(rawUrl)
        var lastError: Throwable? = null

        for (candidateUrl in candidates) {
            var attempt = 0
            while (true) {
                try {
                    val bitmap = executeArtworkRequest(candidateUrl)
                    synchronized(stateLock) {
                        bitmapCache[rawUrl] = bitmap
                        if (lastFailedArtworkUrl == rawUrl) {
                            lastFailedArtworkUrl = null
                        }
                    }
                    return bitmap
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    lastError = throwable
                    val statusCode = (throwable as? HttpException)?.response?.code
                    if (isPermanentArtworkHttpError(statusCode) || attempt >= TransientRetryDelaysMs.size) {
                        Timber.tag("CoilBitmapLoader").d(
                            throwable,
                            "Artwork candidate failed (status=%s): %s",
                            statusCode,
                            candidateUrl,
                        )
                        break
                    }
                    delay(TransientRetryDelaysMs[attempt])
                    attempt++
                }
            }
        }

        synchronized(stateLock) {
            lastFailedArtworkUrl = rawUrl
        }
        throw lastError ?: IllegalStateException("Could not load artwork for $rawUrl")
    }

    private suspend fun executeArtworkRequest(candidateUrl: String): Bitmap {
        val request =
            ImageRequest
                .Builder(applicationContext)
                // Pass String so DownloadedArtworkRepository.coilMapper() (Mapper<String, Any>)
                // and offline disk cache keys match the in-app image requests.
                .data(candidateUrl)
                .memoryCacheKey(candidateUrl)
                .diskCacheKey(candidateUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .allowHardware(false)
                .size(maximumArtworkDimensionPx, maximumArtworkDimensionPx)
                .build()

        return when (val result = applicationContext.imageLoader.execute(request)) {
            is SuccessResult -> withContext(Dispatchers.Default) {
                ensureActive()
                result.image
                    .toBitmap()
                    .scaleToNotificationArtwork(maximumArtworkDimensionPx)
                    .toOwnedMediaSessionBitmap()
            }

            is ErrorResult -> throw result.throwable
        }
    }
}

internal fun isPermanentArtworkHttpError(statusCode: Int?): Boolean =
    statusCode != null && statusCode in 400..499 && statusCode != 408 && statusCode != 429

internal fun buildArtworkCandidateUrls(rawUrl: String): List<String> {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return emptyList()

    val lower = trimmed.lowercase(Locale.ROOT)
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return listOf(trimmed)
    }

    val candidates = LinkedHashSet<String>()
    candidates.add(trimmed)

    if (lower.contains("i.ytimg.com/vi/")) {
        var current: String? = trimmed
        while (current != null) {
            val next = getNextFallbackUrl(current) ?: break
            if (next.contains("/mqdefault.", ignoreCase = true)) {
                resolveMaxresFallback(trimmed, YTThumbQuality.HQ)?.let(candidates::add)
            }
            candidates.add(next)
            current = next
        }
        resolveMaxresFallback(trimmed, YTThumbQuality.HQ)?.let(candidates::add)
        resolveMaxresFallback(trimmed, YTThumbQuality.MQ)?.let(candidates::add)
        resolveMaxresFallback(trimmed, YTThumbQuality.DEFAULT)?.let(candidates::add)
    } else if (lower.contains("sndcdn.com")) {
        if (trimmed.contains("-original.")) {
            candidates.add(trimmed.replace("-original.", "-t500x500."))
            candidates.add(trimmed.replace("-original.", "-large."))
        }
        if (trimmed.contains("-t500x500.")) {
            candidates.add(trimmed.replace("-t500x500.", "-t300x300."))
            candidates.add(trimmed.replace("-t500x500.", "-large."))
        }
    } else if (lower.contains("googleusercontent.com") || lower.contains("ggpht.com")) {
        val medium =
            trimmed.resize(
                width = FallbackGoogleCdnDimensionPx,
                height = FallbackGoogleCdnDimensionPx,
                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
            )
        if (medium != trimmed) {
            candidates.add(medium)
        }
    }

    return candidates.toList()
}

private fun decodeSampledBitmap(
    data: ByteArray,
    maximumDimensionPx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
    while (largestDimension / sampleSize / 2 >= maximumDimensionPx) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeByteArray(
        data,
        0,
        data.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}

private fun Bitmap.scaleToNotificationArtwork(maximumDimensionPx: Int): Bitmap {
    check(!isRecycled) { "Artwork bitmap has been recycled" }
    check(width > 0 && height > 0) { "Invalid artwork dimensions" }
    if (width <= maximumDimensionPx && height <= maximumDimensionPx) return this

    val scale =
        minOf(
            maximumDimensionPx.toFloat() / width.toFloat(),
            maximumDimensionPx.toFloat() / height.toFloat(),
        )
    val targetWidth = (width * scale).roundToInt().coerceIn(1, maximumDimensionPx)
    val targetHeight = (height * scale).roundToInt().coerceIn(1, maximumDimensionPx)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun Bitmap.toOwnedMediaSessionBitmap(): Bitmap {
    check(!isRecycled) { "Artwork bitmap has been recycled" }
    return checkNotNull(copy(Bitmap.Config.ARGB_8888, false)) {
        "Could not copy artwork bitmap"
    }
}

@Suppress("DiscouragedApi")
private fun Context.resolveMaximumArtworkDimensionPx(): Int {
    val dimensionResourceId =
        resources.getIdentifier(
            "config_mediaMetadataBitmapMaxSize",
            "dimen",
            "android",
        )
    val frameworkLimitPx =
        if (dimensionResourceId != 0) {
            resources.getDimensionPixelSize(dimensionResourceId)
        } else {
            (LegacyMediaMetadataBitmapMaxSizeDp * resources.displayMetrics.density).roundToInt()
        }
    val pixelLimitResourceId =
        resources.getIdentifier(
            "config_maxBitmapSizePx",
            "integer",
            "android",
        )
    val pixelLimitPx =
        if (pixelLimitResourceId != 0) {
            resources.getInteger(pixelLimitResourceId).takeIf { it > 0 } ?: frameworkLimitPx
        } else {
            frameworkLimitPx
        }
    return minOf(NotificationArtworkSizePx, frameworkLimitPx, pixelLimitPx).coerceAtLeast(1)
}
