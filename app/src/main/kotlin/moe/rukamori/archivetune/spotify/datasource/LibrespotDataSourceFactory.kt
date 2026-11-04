/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.datasource

import androidx.media3.datasource.DataSource
import moe.rukamori.archivetune.spotify.session.SpotifySessionManager
import xyz.gianlu.librespot.audio.NormalizationData

class LibrespotDataSourceFactory(
    private val sessionManager: SpotifySessionManager,
    private val onTrackResolved: ((trackUri: String, name: String, artist: String, norm: NormalizationData?, totalSize: Long) -> Unit)? = null,
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        LibrespotDataSource(
            sessionManager = sessionManager,
            onTrackResolved = onTrackResolved,
        )
}
