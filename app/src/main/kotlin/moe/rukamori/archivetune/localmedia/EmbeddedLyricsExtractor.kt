/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.Keep
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.InputStream
import java.util.Locale

@Keep
class EmbeddedLyricsExtractor(
    private val contentResolver: ContentResolver,
) {
    init {
        System.loadLibrary("archivetune_automix")
    }

    fun extract(
        contentUri: Uri,
        displayName: String?,
        mimeType: String?,
    ): String? =
        try {
            contentResolver.openInputStream(contentUri)?.buffered()?.use { input ->
                val bytes = input.readUpTo(MaxMetadataBytes)
                if (bytes.isEmpty()) return null
                extractFromBytes(
                    bytes = bytes,
                    displayName = displayName,
                    mimeType = mimeType,
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Timber.tag(LogTag).w(error, "Failed to extract embedded lyrics for %s", contentUri)
            null
        }

    companion object {
        init {
            System.loadLibrary("archivetune_automix")
        }

        private const val LogTag = "EmbeddedLyricsExtractor"
        private const val MaxMetadataBytes = 2 * 1024 * 1024
        private const val DefaultBufferSize = 8 * 1024

        fun extractFromBytes(
            bytes: ByteArray,
            displayName: String?,
            mimeType: String?,
        ): String? {
            if (bytes.isEmpty()) return null
            val ext = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            return nativeExtractLyrics(bytes, ext, mimeType)
        }

        private fun InputStream.readUpTo(maxBytes: Int): ByteArray {
            val buffer = ByteArray(DefaultBufferSize)
            val output = ByteArray(maxBytes)
            var total = 0
            while (total < maxBytes) {
                val read = read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (read < 0) break
                buffer.copyInto(output, destinationOffset = total, startIndex = 0, endIndex = read)
                total += read
            }
            return output.copyOf(total)
        }

        @JvmStatic
        private external fun nativeExtractLyrics(
            bytes: ByteArray,
            extension: String?,
            mimeType: String?,
        ): String?
    }
}
