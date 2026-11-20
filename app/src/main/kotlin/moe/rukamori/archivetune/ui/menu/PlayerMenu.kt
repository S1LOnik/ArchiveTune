/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import moe.rukamori.archivetune.ui.component.OtherSourcesSheet

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.AodModeEnabledKey
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.DEFAULT_PLAYER_MENU_PINNED_ACTIONS
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PlayerMenuPinnedActionsKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.ui.player.LocalArtworkFallback
import moe.rukamori.archivetune.ui.mascot.MascotEvent
import moe.rukamori.archivetune.ui.mascot.MascotEventBus
import moe.rukamori.archivetune.playback.CanvasArtworkRefetchResult
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.player.rememberDeviceMusicVolumeController
import moe.rukamori.archivetune.utils.ExternalDownloaderLaunchResult
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.MediaUrlCanonicalizer
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.openExternalDownloader
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    sleepTimerEnabled: Boolean = false,
    onSleepTimerClick: (() -> Unit)? = null,
    onPlayNextFromQueue: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onPlayerVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float -> deviceMusicVolumeController.setVolumeFraction(volume) }
        }
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        MascotEventBus.emit(MascotEvent.PlayerMenuOpened)
    }

    val downloadUtil = LocalDownloadUtil.current
    val download by downloadUtil
        .getDownload(mediaMetadata.id)
        .collectAsState(initial = null)

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    // Artist separators for splitting artist names
    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (aodFeatureEnabled, onAodFeatureEnabledChange) = rememberPreference(AodModeEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (archiveTuneCanvasEnabled) = rememberPreference(ArchiveTuneCanvasKey, defaultValue = false)
    val playerDesignStyle by rememberEnumPreference(PlayerDesignStyleKey, defaultValue = PlayerDesignStyle.APPLE_MUSIC)
    val lowDataModeActive = rememberLowDataModeActive()
    val canvasNetworkAllowed by playerConnection.canvasNetworkAllowed.collectAsStateWithLifecycle()
    val isCanvasArtworkRefetching by playerConnection.isCanvasArtworkRefetching.collectAsStateWithLifecycle()
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(mediaMetadata.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = mediaMetadata.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }
    val isLocalMedia =
        remember(librarySong?.song?.isLocal, mediaMetadata.id) {
            librarySong?.song?.isLocal == true || mediaMetadata.id.isLocalMediaId()
        }
    val castPlayerMenuAction = rememberCastPlayerMenuAction()
    val triggerHaptic = rememberArchiveTuneHapticTrigger()
    val (pinnedActionsRaw, onPinnedActionsRawChange) =
        rememberPreference(
            key = PlayerMenuPinnedActionsKey,
            defaultValue = DEFAULT_PLAYER_MENU_PINNED_ACTIONS,
        )
    val configuredPinnedActions =
        remember(pinnedActionsRaw) {
            parsePinnedPlayerMenuActions(pinnedActionsRaw)
        }
    var showCustomizePinnedDialog by rememberSaveable { mutableStateOf(false) }

    // Split artists by configured separators
    data class SplitArtist(
        val name: String,
        val originalArtist: MediaMetadata.Artist?,
    )

    val splitArtists =
        remember(artists, artistSeparators) {
            if (artistSeparators.isEmpty()) {
                artists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                artists.flatMap { artist ->
                    val parts =
                        artist.name
                            .split(separatorRegex)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    if (parts.size > 1) {
                        parts.mapIndexed { index, name ->
                            SplitArtist(name, if (index == 0) artist else null)
                        }
                    } else {
                        listOf(SplitArtist(artist.name, artist))
                    }
                }
            }
        }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.withTransaction {
                insert(mediaMetadata)
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    var showAddToSpotifyPlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToSpotifyPlaylistDialog(
        isVisible = showAddToSpotifyPlaylistDialog,
        mediaMetadata = mediaMetadata,
        onDismiss = { showAddToSpotifyPlaylistDialog = false },
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(splitArtists.distinctBy { it.name }) { splitArtist ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = splitArtist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        val thumbUrl = splitArtist.originalArtist?.thumbnailUrl
                        if (thumbUrl.isNullOrBlank()) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                splitArtist.originalArtist?.let { artist ->
                                    navController.navigate("artist/${artist.id}")
                                    showSelectArtistDialog = false
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(
            onDismiss = { showPitchTempoDialog = false },
        )
    }

    var showOtherSourcesSheet by rememberSaveable {
        mutableStateOf(false)
    }

    if (showOtherSourcesSheet) {
        OtherSourcesSheet(
            mediaMetadata = mediaMetadata,
            onDismiss = { showOtherSourcesSheet = false },
        )
    }

    var showEqualizerDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            openSystemEqualizer = {
                val intent =
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(
                            AudioEffect.EXTRA_AUDIO_SESSION,
                            playerConnection.localPlayer.audioSessionId,
                        )
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                if (intent.resolveActivity(context.packageManager) != null) {
                    activityResultLauncher.launch(intent)
                }
            },
        )
    }

    val nowPlayingTitle =
        remember(mediaMetadata.title) {
            mediaMetadata.title.ifBlank { context.getString(R.string.no_title) }
        }

    val nowPlayingSubtitle =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(separator = " • ") { it.name }
        }
    val activeTrackSource =
        remember(mediaMetadata.id, mediaMetadata.spotifyTrackId, isLocalMedia) {
            detectActiveTrackSource(mediaMetadata, isLocalMedia)
        }

    val shareTrackAction: () -> Unit = {
        if (isLocalMedia) {
            shareLocalAudio(context, mediaMetadata.id, librarySong?.format?.mimeType)
        } else {
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, MediaUrlCanonicalizer.canonicalUrl(mediaMetadata))
                }
            context.startActivity(Intent.createChooser(shareIntent, null))
        }
        onDismiss()
    }

    val startDownloadAction: () -> Unit = {
        when (download?.state) {
            Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    download?.request?.id ?: downloadUtil.currentSourceDownloadTarget(mediaMetadata.id).key,
                    false,
                )
            }

            else -> {
                database.transaction {
                    insert(mediaMetadata)
                }
                val downloadTarget = downloadUtil.currentSourceDownloadTarget(mediaMetadata.id)
                downloadUtil.clearCurrentTargetCacheSpans(mediaMetadata.id)
                val downloadRequest =
                    DownloadRequest
                        .Builder(downloadTarget.key, mediaMetadata.id.toUri())
                        .setCustomCacheKey(downloadTarget.key)
                        .setData(mediaMetadata.title.toByteArray())
                        .build()
                DownloadService.sendAddDownload(
                    context,
                    ExoDownloadService::class.java,
                    downloadRequest,
                    false,
                )
            }
        }
    }

    val availableQuickActions =
        remember(
            isLocalMedia,
            mediaMetadata.isPodcast,
            castPlayerMenuAction,
            onSleepTimerClick,
            isQueueTrigger,
        ) {
            buildSet {
                if (!isLocalMedia && !mediaMetadata.isPodcast) add(PlayerMenuQuickAction.RADIO)
                add(PlayerMenuQuickAction.ADD_TO_PLAYLIST)
                add(PlayerMenuQuickAction.QUEUE)
                if (!isLocalMedia) add(PlayerMenuQuickAction.DOWNLOAD)
                if (onSleepTimerClick != null) add(PlayerMenuQuickAction.SLEEP_TIMER)
                add(PlayerMenuQuickAction.SHARE)
                if (castPlayerMenuAction != null) add(PlayerMenuQuickAction.CAST)
                if (!isLocalMedia) add(PlayerMenuQuickAction.OTHER_SOURCES)
                if (isQueueTrigger != true) {
                    add(PlayerMenuQuickAction.EQUALIZER)
                    add(PlayerMenuQuickAction.PITCH_TEMPO)
                    add(PlayerMenuQuickAction.AOD)
                }
                add(PlayerMenuQuickAction.SPEED_DIAL)
            }
        }
    val visiblePinnedActions =
        remember(configuredPinnedActions, availableQuickActions) {
            resolveVisiblePinnedActions(configuredPinnedActions, availableQuickActions)
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun buildQuickAction(action: PlayerMenuQuickAction): NewAction =
        when (action) {
            PlayerMenuQuickAction.RADIO ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.radio),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.start_radio),
                    onClick = {
                        triggerHaptic(ArchiveTuneHapticKind.SELECTION)
                        playerConnection.startRadioSeamlessly()
                        onDismiss()
                    },
                )

            PlayerMenuQuickAction.ADD_TO_PLAYLIST ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.playlist_add),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.add_to_playlist),
                    onClick = { showChoosePlaylistDialog = true },
                )

            PlayerMenuQuickAction.QUEUE ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.queue),
                    onClick = {
                        onDismiss()
                    },
                )

            PlayerMenuQuickAction.DOWNLOAD ->
                NewAction(
                    icon = {
                        if (download?.state == Download.STATE_QUEUED || download?.state == Download.STATE_DOWNLOADING) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                painter = painterResource(if (download?.state == Download.STATE_COMPLETED) R.drawable.offline else R.drawable.download),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint =
                                    if (download?.state == Download.STATE_COMPLETED) {
                                        primaryColor
                                    } else {
                                        onSurfaceVariantColor
                                    },
                            )
                        }
                    },
                    text =
                        context.getString(
                            if (download?.state == Download.STATE_COMPLETED) R.string.remove_download else R.string.action_download,
                        ),
                    onClick = startDownloadAction,
                )

            PlayerMenuQuickAction.SLEEP_TIMER ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.bedtime),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (sleepTimerEnabled) primaryColor else onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.sleep_timer),
                    onClick = {
                        onDismiss()
                        onSleepTimerClick?.invoke()
                    },
                    backgroundColor = if (sleepTimerEnabled) primaryContainerColor else Color.Unspecified,
                    contentColor = if (sleepTimerEnabled) onPrimaryContainerColor else onSurfaceVariantColor,
                )

            PlayerMenuQuickAction.SHARE ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.share),
                    onClick = shareTrackAction,
                )

            PlayerMenuQuickAction.CAST ->
                castPlayerMenuAction ?: NewAction(
                    icon = {},
                    text = context.getString(R.string.cast),
                    onClick = {},
                    enabled = false,
                )

            PlayerMenuQuickAction.OTHER_SOURCES ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.manage_search),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.other_sources),
                    onClick = { showOtherSourcesSheet = true },
                )

            PlayerMenuQuickAction.EQUALIZER ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.equalizer),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.equalizer),
                    onClick = { showEqualizerDialog = true },
                )

            PlayerMenuQuickAction.PITCH_TEMPO ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.speed),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.tempo_and_pitch),
                    onClick = { showPitchTempoDialog = true },
                )

            PlayerMenuQuickAction.AOD ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.bedtime),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (aodFeatureEnabled) onPrimaryColor else onSurfaceVariantColor,
                        )
                    },
                    text = context.getString(R.string.aod_mode),
                    onClick = {
                        if (!aodFeatureEnabled) onAodFeatureEnabledChange(true)
                        playerConnection.aodModeEnabled.value = true
                        onDismiss()
                    },
                    backgroundColor = if (aodFeatureEnabled) primaryColor else Color.Unspecified,
                    contentColor = if (aodFeatureEnabled) onPrimaryColor else onSurfaceVariantColor,
                )

            PlayerMenuQuickAction.SPEED_DIAL ->
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceVariantColor,
                        )
                    },
                    text =
                        context.getString(
                            if (isInSpeedDial) R.string.remove_from_speed_dial else R.string.pin_to_speed_dial,
                        ),
                    onClick = {
                        val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                        onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                        onDismiss()
                    },
                )
        }

    if (showCustomizePinnedDialog) {
        ListDialog(onDismiss = { showCustomizePinnedDialog = false }) {
            item(key = "customize_header") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.player_menu_quick_actions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.player_menu_customize_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(PlayerMenuQuickAction.entries) { candidate ->
                val pinnedIdx = configuredPinnedActions.indexOf(candidate)
                val isPinned = pinnedIdx >= 0
                val canToggleOn = isPinned || configuredPinnedActions.size < MAX_PINNED_PLAYER_MENU_ACTIONS
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canToggleOn) {
                                triggerHaptic(ArchiveTuneHapticKind.SELECTION)
                                val next = togglePinnedPlayerMenuAction(configuredPinnedActions, candidate)
                                onPinnedActionsRawChange(serializePinnedPlayerMenuActions(next))
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(candidate.iconRes),
                        contentDescription = null,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(candidate.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isPinned) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isPinned) {
                        IconButton(
                            enabled = pinnedIdx > 0,
                            onClick = {
                                val next = movePinnedPlayerMenuAction(configuredPinnedActions, pinnedIdx, pinnedIdx - 1)
                                onPinnedActionsRawChange(serializePinnedPlayerMenuActions(next))
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(painterResource(R.drawable.arrow_upward), contentDescription = stringResource(R.string.home_move_up), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            enabled = pinnedIdx < configuredPinnedActions.lastIndex,
                            onClick = {
                                val next = movePinnedPlayerMenuAction(configuredPinnedActions, pinnedIdx, pinnedIdx + 1)
                                onPinnedActionsRawChange(serializePinnedPlayerMenuActions(next))
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(painterResource(R.drawable.arrow_downward), contentDescription = stringResource(R.string.home_move_down), modifier = Modifier.size(18.dp))
                        }
                    }
                    Icon(
                        painter = painterResource(if (isPinned) R.drawable.check else R.drawable.add),
                        contentDescription = null,
                        tint =
                            when {
                                isPinned -> MaterialTheme.colorScheme.primary
                                canToggleOn -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                    )
                }
            }
            item(key = "customize_footer") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { onPinnedActionsRawChange(DEFAULT_PLAYER_MENU_PINNED_ACTIONS) },
                    ) {
                        Text(stringResource(R.string.player_menu_reset_actions))
                    }
                    TextButton(onClick = { showCustomizePinnedDialog = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ZONE 1: Compact Track Header
        item(key = "zone1_now_playing") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    val thumb = mediaMetadata.thumbnailUrl
                    Box(
                        modifier =
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp)),
                    ) {
                        if (thumb.isNullOrBlank()) {
                            LocalArtworkFallback(
                                minIconSize = 20.dp,
                                maxIconSize = 28.dp,
                                modifier = Modifier.size(54.dp),
                            )
                        } else {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(54.dp),
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.now_playing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = nowPlayingTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                        if (nowPlayingSubtitle.isNotBlank()) {
                            Text(
                                text = nowPlayingSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }

                    IconButton(onClick = shareTrackAction, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = stringResource(R.string.share),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            onShowDetailsDialog()
                            onDismiss()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.info),
                            contentDescription = stringResource(R.string.details),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // ZONE 2: Pinned Quick Actions (up to 4 customizable & reorderable slots, reflows when Cast hidden)
        item(key = "zone2_quick_actions") {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_menu_quick_actions),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape,
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .clickable { showCustomizePinnedDialog = true },
                        ) {
                            Text(
                                text = stringResource(R.string.player_menu_customize_actions),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    val quickGridActions = visiblePinnedActions.map(::buildQuickAction)
                    NewActionGrid(
                        actions = quickGridActions,
                        columns = quickGridActions.size.coerceIn(1, 4),
                    )
                }
            }
        }

        // ZONE 3: Grouped Controls — Audio & Sources + Track & Library
        if (isQueueTrigger != true) {
            item(key = "zone3_audio_sources_header") {
                Text(
                    text = stringResource(R.string.player_menu_section_audio_sources),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            item(key = "volume") {
                PlayerVolumeCard(
                    volume = deviceMusicVolumeController.volumeFraction,
                    onVolumeChange = onPlayerVolumeChange,
                )
            }
        }
        item(key = "other_sources") {
            Surface(
                onClick = { showOtherSourcesSheet = true },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.manage_search), contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.other_sources),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.player_menu_active_source_fmt,
                                    stringResource(activeTrackSource.labelRes),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(painterResource(R.drawable.chevron_right), contentDescription = null)
                }
            }
        }
        item(key = "zone3_audio_tools") {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 4.dp)) {
                Column {
                    if (isQueueTrigger != true) {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.equalizer)) },
                            leadingContent = {
                                Icon(painterResource(R.drawable.equalizer), contentDescription = null)
                            },
                            modifier = Modifier.clickable { showEqualizerDialog = true },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.tempo_and_pitch)) },
                            leadingContent = {
                                Icon(painterResource(R.drawable.speed), contentDescription = null)
                            },
                            supportingContent = {
                                val playbackParameters by playerConnection.playbackParameters.collectAsStateWithLifecycle()
                                Text(
                                    text = "x${formatMultiplier(playbackParameters.speed)} • x${formatMultiplier(playbackParameters.pitch)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.clickable { showPitchTempoDialog = true },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    if (!isLocalMedia && isQueueTrigger != true && archiveTuneCanvasEnabled && !lowDataModeActive && canvasNetworkAllowed && playerDesignStyle != PlayerDesignStyle.V5) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.refetch_canvas)) },
                            leadingContent = {
                                if (isCanvasArtworkRefetching) {
                                    CircularWavyProgressIndicator(modifier = Modifier.size(22.dp))
                                } else {
                                    Icon(painterResource(R.drawable.sync), contentDescription = null)
                                }
                            },
                            modifier =
                                Modifier.clickable(enabled = !isCanvasArtworkRefetching) {
                                    coroutineScope.launch {
                                        when (
                                            playerConnection.refetchCanvasArtwork(
                                                metadata = mediaMetadata,
                                                requireVertical = playerDesignStyle == PlayerDesignStyle.V7,
                                            )
                                        ) {
                                            CanvasArtworkRefetchResult.Success -> onDismiss()
                                            CanvasArtworkRefetchResult.Failure ->
                                                Toast.makeText(context, R.string.canvas_refetch_failed, Toast.LENGTH_SHORT).show()
                                            CanvasArtworkRefetchResult.AlreadyRunning -> Unit
                                        }
                                    }
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
        item(key = "zone3_track_library_header") {
            Text(
                text = stringResource(R.string.player_menu_section_track_library),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        item(key = "zone3_secondary_actions") {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 4.dp)) {
                val secondaryGrid =
                    buildList {
                        add(buildQuickAction(PlayerMenuQuickAction.ADD_TO_PLAYLIST))
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.spotify_icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified,
                                    )
                                },
                                text = stringResource(R.string.add_to_spotify_playlist),
                                onClick = { showAddToSpotifyPlaylistDialog = true },
                            ),
                        )
                        add(buildQuickAction(PlayerMenuQuickAction.SPEED_DIAL))
                        if (castPlayerMenuAction != null && PlayerMenuQuickAction.CAST !in visiblePinnedActions) {
                            add(castPlayerMenuAction)
                        }
                        if (onSleepTimerClick != null && PlayerMenuQuickAction.SLEEP_TIMER !in visiblePinnedActions) {
                            add(buildQuickAction(PlayerMenuQuickAction.SLEEP_TIMER))
                        }
                        if (!isLocalMedia && !mediaMetadata.isPodcast && PlayerMenuQuickAction.RADIO !in visiblePinnedActions) {
                            add(buildQuickAction(PlayerMenuQuickAction.RADIO))
                        }
                    }
                NewActionGrid(
                    actions = secondaryGrid,
                    columns = 3,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        if (splitArtists.isNotEmpty() || mediaMetadata.album != null) {
            item(key = "zone3_artist_album") {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 4.dp)) {
                    Column {
                        if (splitArtists.isNotEmpty()) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                                leadingContent = {
                                    Icon(painter = painterResource(R.drawable.artist), contentDescription = null)
                                },
                                modifier =
                                    Modifier.clickable {
                                        if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                                            onDismiss()
                                            playerBottomSheetState.collapseSoft()
                                            navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                                        } else {
                                            showSelectArtistDialog = true
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        if (splitArtists.isNotEmpty() && mediaMetadata.album != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        if (mediaMetadata.album != null) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_album)) },
                                leadingContent = {
                                    Icon(painter = painterResource(R.drawable.album), contentDescription = null)
                                },
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        playerBottomSheetState.collapseSoft()
                                        navController.navigate("album/${mediaMetadata.album.id}")
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
        if (!isLocalMedia) {
            item(key = "zone3_download") {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 4.dp)) {
                    Column {
                        when (download?.state) {
                            Download.STATE_COMPLETED -> {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = stringResource(R.string.remove_download),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.offline),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    modifier = Modifier.clickable(onClick = startDownloadAction),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }

                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                ListItem(
                                    headlineContent = { Text(text = stringResource(R.string.downloading)) },
                                    leadingContent = {
                                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                    },
                                    modifier = Modifier.clickable(onClick = startDownloadAction),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }

                            else -> {
                                ListItem(
                                    headlineContent = { Text(text = stringResource(R.string.action_download)) },
                                    leadingContent = {
                                        Icon(painter = painterResource(R.drawable.download), contentDescription = null)
                                    },
                                    modifier = Modifier.clickable(onClick = startDownloadAction),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                        if (externalDownloaderEnabled) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                                leadingContent = {
                                    Icon(painter = painterResource(R.drawable.download), contentDescription = null)
                                },
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        val url = MediaUrlCanonicalizer.canonicalUrl(mediaMetadata)
                                        when (context.openExternalDownloader(externalDownloaderPackage, url)) {
                                            ExternalDownloaderLaunchResult.STARTED -> Unit
                                            ExternalDownloaderLaunchResult.NOT_CONFIGURED ->
                                                Toast.makeText(context, context.getString(R.string.external_downloader_not_configured), Toast.LENGTH_LONG).show()
                                            ExternalDownloaderLaunchResult.NOT_INSTALLED ->
                                                Toast.makeText(context, context.getString(R.string.external_downloader_not_installed), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
        if (isQueueTrigger == true && (onPlayNextFromQueue != null || onRemoveFromQueue != null)) {
            item(key = "zone3_queue_item_actions") {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 4.dp)) {
                    Column {
                        if (onPlayNextFromQueue != null) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.play_next)) },
                                leadingContent = {
                                    Icon(painter = painterResource(R.drawable.playlist_play), contentDescription = null)
                                },
                                modifier =
                                    Modifier.clickable {
                                        onPlayNextFromQueue()
                                        onDismiss()
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        if (onPlayNextFromQueue != null && onRemoveFromQueue != null) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (onRemoveFromQueue != null) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.remove_from_queue),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        onRemoveFromQueue()
                                        onDismiss()
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerVolumeCard(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeVolume = volume.coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.volume),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "${(safeVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.volume_off),
                        contentDescription = stringResource(R.string.minimum_volume),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )

                    VolumeSliderL(
                        value = safeVolume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f),
                    )

                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = stringResource(R.string.maximum_volume),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VolumeSliderL(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeValue = value.coerceIn(0f, 1f)
    var sliderValue by remember { mutableFloatStateOf(safeValue) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(safeValue) {
        if (!isDragging) sliderValue = safeValue
    }

    Slider(
        value = sliderValue,
        onValueChange = { updated ->
            isDragging = true
            val coerced = updated.coerceIn(0f, 1f)
            sliderValue = coerced
            onValueChange(coerced)
        },
        onValueChangeFinished = { isDragging = false },
        valueRange = 0f..1f,
        modifier = modifier.height(36.dp),
        thumb = {
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
        },
        colors =
            SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
    )
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val initialSpeed = remember { playerConnection.player.playbackParameters.speed }
    val initialPitch = remember { playerConnection.player.playbackParameters.pitch }

    var tempo by remember {
        mutableFloatStateOf(initialSpeed.safeCoerceIn(TempoMin, TempoMax, fallback = 1f))
    }

    var pitch by remember {
        mutableFloatStateOf(initialPitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f))
    }

    var pitchMode by rememberSaveable {
        mutableStateOf(
            if (isPitchSemitoneAligned(pitch)) PitchMode.Semitones else PitchMode.Multiplier,
        )
    }

    val applyPlaybackParameters: (Float, Float) -> Unit = { speed, pitchMultiplier ->
        playerConnection.player.playbackParameters =
            PlaybackParameters(
                speed.coerceIn(TempoMin, TempoMax),
                pitchMultiplier.coerceIn(PitchMin, PitchMax),
            )
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.tempo_and_pitch))
        },
        dismissButton = {
            TextButton(
                onClick = {
                    tempo = 1f
                    pitch = 1f
                    applyPlaybackParameters(tempo, pitch)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.speed),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )

                    Text(
                        text = stringResource(R.string.tempo),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = "x${formatMultiplier(tempo)}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        enabled = tempo > TempoMin,
                        onClick = {
                            tempo = (tempo - 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.remove),
                            contentDescription = null,
                        )
                    }

                    Slider(
                        value = multiplierToSlider(tempo),
                        onValueChange = { slider ->
                            val updated = sliderToMultiplier(slider).quantize(0.01f)
                            if (abs(updated - tempo) >= 0.005f) {
                                tempo = updated
                                applyPlaybackParameters(tempo, pitch)
                            }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(),
                    )

                    IconButton(
                        enabled = tempo < TempoMax,
                        onClick = {
                            tempo = (tempo + 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                ) {
                    val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                    presets.forEach { preset ->
                        val selected = abs(tempo - preset) < 0.005f
                        FilterChip(
                            selected = selected,
                            onClick = {
                                tempo = preset
                                applyPlaybackParameters(tempo, pitch)
                            },
                            label = { Text("x${formatMultiplier(preset)}") },
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.discover_tune),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )

                    Text(
                        text = stringResource(R.string.pitch),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text =
                            when (pitchMode) {
                                PitchMode.Semitones -> {
                                    val semitones = pitchToSemitones(pitch)
                                    "${if (semitones > 0) "+" else ""}$semitones"
                                }

                                PitchMode.Multiplier -> {
                                    "x${formatMultiplier(pitch)}"
                                }
                            },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = pitchMode == PitchMode.Semitones,
                        onClick = { pitchMode = PitchMode.Semitones },
                        label = { Text(stringResource(R.string.pitch_mode_semitones_short)) },
                    )
                    FilterChip(
                        selected = pitchMode == PitchMode.Multiplier,
                        onClick = { pitchMode = PitchMode.Multiplier },
                        label = { Text(stringResource(R.string.pitch_mode_multiplier_short)) },
                    )
                }

                when (pitchMode) {
                    PitchMode.Semitones -> {
                        val currentSemitones = pitchToSemitones(pitch)
                        Slider(
                            value = currentSemitones.toFloat(),
                            onValueChange = { slider ->
                                val semitones = slider.roundToInt().coerceIn(-12, 12)
                                val updated = semitonesToPitch(semitones)
                                if (abs(updated - pitch) >= 0.0005f) {
                                    pitch = updated
                                    applyPlaybackParameters(tempo, pitch)
                                }
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                        ) {
                            val presets = listOf(-12, -7, -5, 0, 5, 7, 12)
                            presets.forEach { preset ->
                                val selected = currentSemitones == preset
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        pitch = semitonesToPitch(preset)
                                        applyPlaybackParameters(tempo, pitch)
                                    },
                                    label = { Text("${if (preset > 0) "+" else ""}$preset") },
                                )
                            }
                        }
                    }

                    PitchMode.Multiplier -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                enabled = pitch > PitchMin,
                                onClick = {
                                    pitch = (pitch - 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                                    applyPlaybackParameters(tempo, pitch)
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.remove),
                                    contentDescription = null,
                                )
                            }

                            Slider(
                                value = multiplierToSlider(pitch),
                                onValueChange = { slider ->
                                    val updated = sliderToMultiplier(slider).quantize(0.01f)
                                    if (abs(updated - pitch) >= 0.005f) {
                                        pitch = updated
                                        applyPlaybackParameters(tempo, pitch)
                                    }
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(),
                            )

                            IconButton(
                                enabled = pitch < PitchMax,
                                onClick = {
                                    pitch = (pitch + 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                                    applyPlaybackParameters(tempo, pitch)
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.add),
                                    contentDescription = null,
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                        ) {
                            val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                            presets.forEach { preset ->
                                val selected = abs(pitch - preset) < 0.005f
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        pitch = preset
                                        applyPlaybackParameters(tempo, pitch)
                                    },
                                    label = { Text("x${formatMultiplier(preset)}") },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

private enum class PitchMode {
    Semitones,
    Multiplier,
}

private const val TempoMin = 0.25f
private const val TempoMax = 2f
private const val PitchMin = 0.25f
private const val PitchMax = 2f

private fun Float.safeCoerceIn(
    min: Float,
    max: Float,
    fallback: Float,
): Float {
    val safe = if (this.isFinite()) this else fallback
    return safe.coerceIn(min, max)
}

private fun Float.quantize(step: Float): Float {
    if (step <= 0f) return this
    return (round(this / step) * step).coerceAtLeast(0f)
}

private fun pitchToSemitones(pitch: Float): Int {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    return (12f * log2(safePitch)).roundToInt().coerceIn(-12, 12)
}

private fun semitonesToPitch(semitones: Int): Float = 2f.pow(semitones.toFloat() / 12f).coerceIn(PitchMin, PitchMax)

private fun isPitchSemitoneAligned(pitch: Float): Boolean {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    val semitones = (12f * log2(safePitch)).roundToInt()
    val reconstructed = 2f.pow(semitones.toFloat() / 12f)
    return abs(reconstructed - pitch) < 0.0015f
}

private fun formatMultiplier(multiplier: Float): String = String.format("%.2f", multiplier)

private fun sliderToMultiplier(slider: Float): Float {
    val t = slider.coerceIn(0f, 1f)
    val y = (t - 0.5f) * 2f
    val curve = 2.2f
    val absY = abs(y).pow(curve)
    val shaped =
        when {
            y > 0f -> absY
            y < 0f -> -absY
            else -> 0f
        }
    val exponent = if (y < 0f) 2f * shaped else shaped
    return 2f.pow(exponent).coerceIn(TempoMin, TempoMax)
}

private fun multiplierToSlider(multiplier: Float): Float {
    val m = multiplier.coerceIn(TempoMin, TempoMax)
    val log = log2(m)
    val curve = 2.2f
    val shaped = if (m < 1f) (log / 2f) else log
    val absShaped = abs(shaped).pow(1f / curve)
    val y =
        when {
            shaped > 0f -> absShaped
            shaped < 0f -> -absShaped
            else -> 0f
        }
    return (0.5f + y / 2f).coerceIn(0f, 1f)
}
