/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

internal const val supportArchiveTuneAvailable = true

@Composable
internal fun SupportArchiveTuneSection(
    modifier: Modifier = Modifier,
) {
    var showMeme by rememberSaveable { mutableStateOf(false) }
    if (showMeme) {
        AlertDialog(
            onDismissRequest = { showMeme = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painter = painterResource(R.drawable.free_internet_meme),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.free_internet_meme_caption))
                }
            },
            confirmButton = {
                TextButton(onClick = { showMeme = false }) {
                    Text(stringResource(R.string.close_dialog))
                }
            },
        )
    }

    SupportArchiveTuneCard(
        onClick = { showMeme = true },
        modifier = modifier,
    )
}

@Composable
private fun SupportArchiveTuneCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val iconContainerModifier =
        remember(iconContainerColor) {
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(iconContainerColor)
        }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor.copy(alpha = 0.7f),
            ),
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = iconContainerModifier,
            ) {
                Icon(
                    painter = painterResource(R.drawable.star),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(34.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.support_archivetune_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.support_easter_egg_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
