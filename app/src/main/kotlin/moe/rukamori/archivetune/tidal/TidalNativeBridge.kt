/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.tidal

import androidx.annotation.Keep

/** Native Rust bridge for TIDAL DASH MPD parsing, dfLa/FLAC extraction, and MP4 mdat remuxing. */
@Keep
internal object TidalNativeBridge {
    val available: Boolean =
        runCatching {
            System.loadLibrary("archivetune_automix")
        }.isSuccess

    fun extractDashSegmentUrls(manifestXml: String): List<String>? {
        if (!available) return null
        return nativeExtractDashSegmentUrls(manifestXml)?.toList()
    }

    fun extractFlacMetadataBlocks(initBytes: ByteArray): ByteArray? {
        if (!available) return null
        return nativeExtractFlacMetadataBlocks(initBytes)
    }

    fun extractMdatPayloads(segmentBytes: ByteArray): ByteArray? {
        if (!available) return null
        return nativeExtractMdatPayloads(segmentBytes)
    }

    fun normalizeIsrc(raw: String?): String? {
        if (!available) return null
        return nativeNormalizeIsrc(raw)
    }

    @JvmStatic
    private external fun nativeExtractDashSegmentUrls(manifestXml: String): Array<String>?

    @JvmStatic
    private external fun nativeExtractFlacMetadataBlocks(initBytes: ByteArray): ByteArray?

    @JvmStatic
    private external fun nativeExtractMdatPayloads(segmentBytes: ByteArray): ByteArray?

    @JvmStatic
    private external fun nativeNormalizeIsrc(raw: String?): String?
}
