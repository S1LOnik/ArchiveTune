/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExportedAppearanceProfileV1(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "16.5",
    val name: String = "ArchiveTune Style",
    val exportedAtEpochMs: Long = 0L,
    val snapshot: AppearanceProfileSnapshot = AppearanceProfileSnapshot(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class ImportedAppearanceProfile(
    val name: String,
    val schemaVersion: Int,
    val snapshot: AppearanceProfileSnapshot,
)

object AppearanceProfileCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            isLenient = true
        }

    fun encodeExport(
        name: String,
        snapshot: AppearanceProfileSnapshot,
        exportedAtEpochMs: Long = 0L,
    ): String =
        json.encodeToString(
            ExportedAppearanceProfileV1(
                schemaVersion = ExportedAppearanceProfileV1.CURRENT_SCHEMA_VERSION,
                name = name.trim().ifBlank { "ArchiveTune Style" },
                exportedAtEpochMs = exportedAtEpochMs,
                snapshot = snapshot.normalized(),
            ),
        )

    fun decodeImport(rawJson: String): Result<ImportedAppearanceProfile> =
        runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.startsWith("{")) { "Not a JSON object" }
            val payload = json.decodeFromString<ExportedAppearanceProfileV1>(trimmed)
            require(payload.schemaVersion >= 1) { "Unsupported schema version" }
            ImportedAppearanceProfile(
                name = payload.name.trim().ifBlank { "Imported style" },
                schemaVersion = payload.schemaVersion,
                snapshot = payload.snapshot.normalized(),
            )
        }
}
