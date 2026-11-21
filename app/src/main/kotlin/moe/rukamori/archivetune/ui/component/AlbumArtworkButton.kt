/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LargeAlbumCoverKey
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun AlbumArtworkButton(thumbnailUrl: String?, title: String) {
    if (thumbnailUrl.isNullOrBlank()) return
    val coverLabel = stringResource(R.string.album_view_cover)
    var showArtwork by rememberSaveable(thumbnailUrl) { mutableStateOf(false) }
    IconButton(onClick = { showArtwork = true }) {
        Icon(painterResource(R.drawable.image), contentDescription = coverLabel)
    }

    if (showArtwork) {
        val (fullCover, setFullCover) = rememberPreference(LargeAlbumCoverKey, false)
        val artwork = remember(thumbnailUrl) {
            thumbnailUrl.resize(1200, 1200, ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal)
        }
        AlertDialog(
            onDismissRequest = { showArtwork = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = coverLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    SwitchPreference(
                        title = { Text(stringResource(R.string.album_large_cover)) },
                        checked = fullCover,
                        onCheckedChange = setFullCover,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showArtwork = false }) {
                    Text(stringResource(R.string.close_dialog))
                }
            },
        )
    }
}
