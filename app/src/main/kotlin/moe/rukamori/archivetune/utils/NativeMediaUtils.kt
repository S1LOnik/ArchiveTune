/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NativeAudioTagMetadataDto(
    val container: String = "",
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val durationMs: Long? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val bitsPerSample: Int? = null,
    val bitrateKbps: Int? = null,
    val lyrics: String? = null,
    val artworkOffset: Int? = null,
    val artworkLength: Int? = null,
    val artworkMime: String? = null,
)

@Serializable
data class NativeFilenameGuessDto(
    val title: String = "",
    val artist: String? = null,
    val trackNumber: Int? = null,
)

@Serializable
data class NativeM3uEntryDto(
    val uri: String = "",
    val title: String? = null,
    val artist: String? = null,
    val durationSeconds: Long? = null,
)

@Serializable
data class NativeCueTrackDto(
    val trackNumber: Int = 1,
    val startMs: Long = 0L,
    val title: String? = null,
    val performer: String? = null,
    val album: String? = null,
    val file: String? = null,
)

@Serializable
data class NativePlsEntryDto(
    val uri: String = "",
    val title: String? = null,
    val lengthSeconds: Long? = null,
)

@Serializable
data class NativeXspfTrackDto(
    val location: String = "",
    val title: String? = null,
    val creator: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
)

@Serializable
data class NativeContentRangeDto(
    val start: Long = 0L,
    val end: Long = 0L,
    val totalLength: Long? = null,
)

/** Native Rust utilities for container metadata probing, format selection, fuzzy search, playlists, and crypto. */
@Keep
object NativeMediaUtils {
    val available: Boolean =
        runCatching {
            System.loadLibrary("archivetune_automix")
        }.isSuccess

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseAudioTags(bytes: ByteArray): NativeAudioTagMetadataDto? {
        if (!available || bytes.isEmpty()) return null
        val raw = nativeParseAudioTagsJson(bytes) ?: return null
        return runCatching { json.decodeFromString<NativeAudioTagMetadataDto>(raw) }.getOrNull()
    }

    fun sniffMediaMime(bytes: ByteArray): String? =
        if (!available || bytes.isEmpty()) null else nativeSniffMediaMime(bytes)

    fun inferMetadataFromFilename(filename: String): NativeFilenameGuessDto? {
        if (!available || filename.isBlank()) return null
        val raw = nativeInferMetadataFromFilenameJson(filename)
        return runCatching { json.decodeFromString<NativeFilenameGuessDto>(raw) }.getOrNull()
    }

    fun rankAudioFormats(
        codecs: Array<String>,
        bitrates: IntArray,
        sampleRates: IntArray,
        isDirectFlags: IntArray,
        qualityOrdinal: Int,
        networkMetered: Boolean,
    ): IntArray =
        if (!available) {
            IntArray(0)
        } else {
            nativeRankAudioFormats(
                codecs,
                bitrates,
                sampleRates,
                isDirectFlags,
                qualityOrdinal,
                networkMetered,
            )
        }

    fun fuzzySimilarity(
        query: String,
        candidate: String,
    ): Double = if (!available) 0.0 else nativeFuzzySimilarity(query, candidate)

    fun extractYouTubeVideoId(urlOrId: String): String? =
        if (!available) null else nativeExtractYouTubeVideoId(urlOrId)

    fun extractYouTubePlaylistId(url: String): String? =
        if (!available) null else nativeExtractYouTubePlaylistId(url)

    fun stripTrackingParams(url: String): String =
        if (!available) url else nativeStripTrackingParams(url)

    fun deduplicateTracks(
        ids: Array<String>,
        titles: Array<String>,
        artists: Array<String>,
        durationsMs: LongArray,
    ): IntArray =
        if (!available) IntArray(0) else nativeDeduplicateTracks(ids, titles, artists, durationsMs)

    fun smartShuffle(
        artistKeys: Array<String>,
        seed: Long,
    ): IntArray = if (!available) IntArray(0) else nativeSmartShuffle(artistKeys, seed)

    fun parseM3u(content: String): List<NativeM3uEntryDto> {
        if (!available || content.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<NativeM3uEntryDto>>(nativeParseM3uJson(content))
        }.getOrDefault(emptyList())
    }

    fun parseCue(cue: String): List<NativeCueTrackDto> {
        if (!available || cue.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<NativeCueTrackDto>>(nativeParseCueJson(cue))
        }.getOrDefault(emptyList())
    }

    fun parsePls(content: String): List<NativePlsEntryDto> {
        if (!available || content.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<NativePlsEntryDto>>(nativeParsePlsJson(content))
        }.getOrDefault(emptyList())
    }

    fun parseXspf(xml: String): List<NativeXspfTrackDto> {
        if (!available || xml.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<NativeXspfTrackDto>>(nativeParseXspfJson(xml))
        }.getOrDefault(emptyList())
    }

    fun parseContentRange(header: String): NativeContentRangeDto? {
        if (!available || header.isBlank()) return null
        val raw = nativeParseContentRange(header) ?: return null
        return runCatching { json.decodeFromString<NativeContentRangeDto>(raw) }.getOrNull()
    }

    fun selectCacheEvictions(
        sizes: LongArray,
        lastAccessMs: LongArray,
        pinFlags: IntArray,
        maxBytes: Long,
    ): IntArray =
        if (!available) IntArray(0) else nativeSelectCacheEvictions(sizes, lastAccessMs, pinFlags, maxBytes)

    fun sha256Hex(bytes: ByteArray): String? =
        if (!available) null else nativeSha256Hex(bytes)

    @JvmStatic
    private external fun nativeParseAudioTagsJson(bytes: ByteArray): String?

    @JvmStatic
    private external fun nativeSniffMediaMime(bytes: ByteArray): String?

    @JvmStatic
    private external fun nativeInferMetadataFromFilenameJson(filename: String): String

    @JvmStatic
    private external fun nativeRankAudioFormats(
        codecs: Array<String>,
        bitrates: IntArray,
        sampleRates: IntArray,
        isDirectFlags: IntArray,
        qualityOrdinal: Int,
        networkMetered: Boolean,
    ): IntArray

    @JvmStatic
    private external fun nativeFuzzySimilarity(
        query: String,
        candidate: String,
    ): Double

    @JvmStatic
    private external fun nativeExtractYouTubeVideoId(urlOrId: String): String?

    @JvmStatic
    private external fun nativeExtractYouTubePlaylistId(url: String): String?

    @JvmStatic
    private external fun nativeStripTrackingParams(url: String): String

    @JvmStatic
    private external fun nativeDeduplicateTracks(
        ids: Array<String>,
        titles: Array<String>,
        artists: Array<String>,
        durationsMs: LongArray,
    ): IntArray

    @JvmStatic
    private external fun nativeSmartShuffle(
        artistKeys: Array<String>,
        seed: Long,
    ): IntArray

    @JvmStatic
    private external fun nativeParseM3uJson(content: String): String

    @JvmStatic
    private external fun nativeParseCueJson(cue: String): String

    @JvmStatic
    private external fun nativeSha256Hex(bytes: ByteArray): String?

    @JvmStatic
    private external fun nativeParsePlsJson(content: String): String

    @JvmStatic
    private external fun nativeParseContentRange(header: String): String?

    @JvmStatic
    private external fun nativeSelectCacheEvictions(
        sizes: LongArray,
        lastAccessMs: LongArray,
        pinFlags: IntArray,
        maxBytes: Long,
    ): IntArray

    @JvmStatic
    private external fun nativeParseXspfJson(xml: String): String
}
