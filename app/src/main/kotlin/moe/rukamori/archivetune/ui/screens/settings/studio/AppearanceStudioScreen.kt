/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ActiveAppearanceProfileIdKey
import moe.rukamori.archivetune.constants.BackgroundMotionPolicy
import moe.rukamori.archivetune.constants.HapticIntensity
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsDisplayCharacter
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MotionDurationPercentRange
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.MotionTransitionStyle
import moe.rukamori.archivetune.constants.SkipAppearanceConfirmationKey
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.SavedAppearanceProfilesKey
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.player.supportsGlassGlow
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.SelectablePlayerDesignStyles
import moe.rukamori.archivetune.ui.screens.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.screens.settings.ThemePalettes
import moe.rukamori.archivetune.ui.screens.settings.rememberPreferencePositions
import moe.rukamori.archivetune.ui.theme.resolveCustomThemeSelection
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.UUID
import java.io.IOException
import kotlin.math.roundToInt

private fun sectionForScrollTo(scrollTo: String?): AppearanceSection? =
    when (scrollTo) {
        "home" -> AppearanceSection.HOME
        "navigation_mini_player" -> AppearanceSection.NAVIGATION
        "player" -> AppearanceSection.PLAYER
        "lyrics" -> AppearanceSection.LYRICS
        "color_background", "disable_blur" -> AppearanceSection.COLOR
        "motion_profile" -> AppearanceSection.MOTION
        else -> null
    }

private val CuratedStudioAccentPalettes =
    listOf(
        ThemePalettes.Default,
        ThemePalettes.OceanBlue,
        ThemePalettes.EmeraldGreen,
        ThemePalettes.SunsetOrange,
        ThemePalettes.RoyalPurple,
        ThemePalettes.CrimsonRed,
        ThemePalettes.Graphite,
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceStudioScreen(
    navController: NavController,
    scrollTo: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = rememberArchiveTuneHapticTrigger()
    val isLowRam = remember(context) { context.isLowRamDevice() }
    val configuration = LocalConfiguration.current
    val isRussian =
        remember(configuration) {
            @Suppress("DEPRECATION")
            val locale = configuration.locale
            locale.language.equals("ru", ignoreCase = true)
        }

    val initialPrefs = remember { PreferenceStore.currentPreferences() }
    val prefs by context.dataStore.data.collectAsStateWithLifecycle(initialValue = initialPrefs)
    val savedSnapshot =
        remember(prefs, isLowRam) {
            AppearanceProfileSnapshot.fromPreferences(prefs, isLowRam)
        }

    var savedProfilesJson by rememberPreference(SavedAppearanceProfilesKey, "")
    var activeProfileId by rememberPreference(ActiveAppearanceProfileIdKey, "")
    val savedProfiles =
        remember(savedProfilesJson) {
            SavedProfilesStore.decode(savedProfilesJson)
        }

    val snapshotSaver =
        remember(isLowRam) {
            Saver<AppearanceProfileSnapshot, String>(
                save = { AppearanceProfileSnapshot.encodeDraftJson(it) },
                restore = { AppearanceProfileSnapshot.decodeDraftJson(it) ?: AppearanceProfileSnapshot.defaultSnapshot(isLowRam) },
            )
        }
    var draft by rememberSaveable(stateSaver = snapshotSaver) { mutableStateOf(savedSnapshot) }
    var hasUserEditedDraft by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedSnapshot, hasUserEditedDraft) {
        if (!hasUserEditedDraft) {
            draft = savedSnapshot
        }
    }
    var draftProfileId by rememberSaveable { mutableStateOf<String?>(activeProfileId.ifBlank { null }) }
    var savedToLibraryNotice by rememberSaveable { mutableStateOf(false) }

    val updateDraft: (AppearanceProfileSnapshot) -> Unit = { next ->
        draft = next
        draftProfileId = null
        hasUserEditedDraft = true
        savedToLibraryNotice = false
    }

    val discardDraft: () -> Unit = {
        draft = savedSnapshot
        draftProfileId = activeProfileId.ifBlank { null }
        hasUserEditedDraft = false
        savedToLibraryNotice = false
    }

    val hasUnsavedChanges = remember(draft, savedSnapshot) { draft.normalized() != savedSnapshot }
    var isComparingSaved by remember { mutableStateOf(false) }
    var coverMode by rememberSaveable { mutableStateOf(StudioCoverPreviewMode.CURRENT) }
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var returnToMainAfterDraft by rememberSaveable { mutableStateOf(false) }
    val skipAppearanceConfirmation by rememberPreference(SkipAppearanceConfirmationKey, false)
    var isSavingDraft by remember { mutableStateOf(false) }

    var showSaveStyleDialog by rememberSaveable { mutableStateOf(false) }
    var saveStyleNameDraft by rememberSaveable { mutableStateOf("") }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<ImportedAppearanceProfile?>(null) }

    val scrollState = rememberScrollState()
    val positions = rememberPreferencePositions()
    var expandedSection by rememberSaveable {
        mutableStateOf<AppearanceSection?>(sectionForScrollTo(scrollTo))
    }

    LaunchedEffect(scrollTo) {
        if (!scrollTo.isNullOrBlank()) {
            sectionForScrollTo(scrollTo)?.let { expandedSection = it }
            positions.scrollToKey(scrollTo, scrollState)
        }
    }

    fun saveDraft(onSaved: (() -> Unit)? = null) {
        if (isSavingDraft) return
        val target = draft.normalized()
        val targetProfileId = draftProfileId.orEmpty()
        haptic(ArchiveTuneHapticKind.SELECTION)
        isSavingDraft = true
        scope.launch {
            val saved = try {
                context.dataStore.edit { mutable ->
                    target.writeToPreferences(mutable)
                    mutable[ActiveAppearanceProfileIdKey] = targetProfileId
                }
                true
            } catch (_: IOException) {
                false
            } finally {
                isSavingDraft = false
            }
            if (!saved) {
                snackbarHostState.showSnackbar(context.getString(R.string.studio_save_failed))
                return@launch
            }
            if (draft.normalized() == target && draftProfileId.orEmpty() == targetProfileId) {
                draft = target
                hasUserEditedDraft = false
                savedToLibraryNotice = false
                if (onSaved != null) {
                    onSaved()
                    return@launch
                }
            }
            snackbarHostState.showSnackbar(context.getString(R.string.studio_applied))
        }
    }

    val applyDraft: () -> Unit = { saveDraft() }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val activeName =
                    savedProfiles.firstOrNull { it.id == (draftProfileId ?: activeProfileId) }?.name
                        ?: "ArchiveTune Style"
                val jsonText = AppearanceProfileCodec.encodeExport(activeName, draft, System.currentTimeMillis())
                val success =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(jsonText.toByteArray(Charsets.UTF_8))
                            } ?: error("Null output stream")
                        }.isSuccess
                    }
                if (success) {
                    snackbarHostState.showSnackbar(context.getString(R.string.studio_export_success))
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val rawText =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.readBytes().toString(Charsets.UTF_8)
                            }
                        }.getOrNull()
                    }
                val parsed = rawText?.let { AppearanceProfileCodec.decodeImport(it) }
                if (parsed != null && parsed.isSuccess) {
                    pendingImport = parsed.getOrThrow()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.studio_import_invalid),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    fun exitStudio(returnToMain: Boolean) {
        if (returnToMain) navController.backToMain() else navController.navigateUp()
    }

    fun attemptExit(returnToMain: Boolean = false) {
        if (isSavingDraft) return
        when {
            !hasUnsavedChanges -> exitStudio(returnToMain)
            skipAppearanceConfirmation -> saveDraft { exitStudio(returnToMain) }
            else -> {
                returnToMainAfterDraft = returnToMain
                showUnsavedDialog = true
            }
        }
    }

    BackHandler(enabled = hasUnsavedChanges && !showUnsavedDialog) {
        attemptExit()
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.studio_unsaved_title)) },
            text = { Text(stringResource(R.string.studio_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        saveDraft { exitStudio(returnToMainAfterDraft) }
                    },
                ) {
                    Text(stringResource(R.string.studio_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        discardDraft()
                        exitStudio(returnToMainAfterDraft)
                    },
                ) {
                    Text(stringResource(R.string.studio_discard))
                }
            },
        )
    }

    if (showSaveStyleDialog) {
        AlertDialog(
            onDismissRequest = { showSaveStyleDialog = false },
            title = { Text(stringResource(R.string.studio_save_as_my_style)) },
            text = {
                OutlinedTextField(
                    value = saveStyleNameDraft,
                    onValueChange = { saveStyleNameDraft = it },
                    label = { Text(stringResource(R.string.studio_style_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = UUID.randomUUID().toString()
                        val updated =
                            SavedProfilesStore.saveNew(
                                existing = savedProfiles,
                                name = saveStyleNameDraft,
                                snapshot = draft,
                                id = id,
                                nowMs = System.currentTimeMillis(),
                            )
                        savedProfilesJson = SavedProfilesStore.encode(updated)
                        draftProfileId = id
                        savedToLibraryNotice = true
                        showSaveStyleDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.studio_saved_to_library_hint),
                            )
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveStyleDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    renameTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            title = { Text(stringResource(R.string.studio_rename_style)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text(stringResource(R.string.studio_style_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        savedProfilesJson =
                            SavedProfilesStore.encode(
                                SavedProfilesStore.rename(
                                    savedProfiles,
                                    targetId,
                                    renameDraft,
                                    System.currentTimeMillis(),
                                ),
                            )
                        renameTargetId = null
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    pendingImport?.let { imported ->
        val affected = remember(imported, draft) { imported.snapshot.diffSections(draft) }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.studio_import_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = imported.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (affected.isEmpty()) {
                        Text(
                            text = stringResource(R.string.studio_import_no_changes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.studio_import_affected_sections),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        affected.forEach { section ->
                            Text(
                                text = "• ${stringResource(section.titleRes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateDraft(imported.snapshot.normalized())
                        pendingImport = null
                    },
                ) {
                    Text(stringResource(R.string.studio_import_load_to_draft))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.appearance_studio))
                        Text(
                            text =
                                stringResource(
                                    if (hasUnsavedChanges) {
                                        R.string.studio_subtitle_unsaved
                                    } else {
                                        R.string.studio_subtitle_saved
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (hasUnsavedChanges) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { attemptExit() },
                        onLongClick = { attemptExit(returnToMain = true) },
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val previewSnapshot = if (isComparingSaved) savedSnapshot else draft

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .then(positions.containerModifier())
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = SettingsDimensions.ScreenBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Interactive preview
            StudioInteractivePreview(
                snapshot = previewSnapshot,
                isComparingSaved = isComparingSaved,
                coverMode = coverMode,
                onCoverModeChange = { coverMode = it },
            )

            // Compare + Apply action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color =
                        if (isComparingSaved) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    modifier =
                        Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isComparingSaved = true
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            isComparingSaved = false
                                        }
                                    },
                                )
                            },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.restore),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.studio_hold_to_compare),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Button(
                    onClick = applyDraft,
                    enabled = hasUnsavedChanges,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.studio_apply))
                }
            }

            if (hasUnsavedChanges) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            haptic(ArchiveTuneHapticKind.SELECTION)
                            discardDraft()
                        },
                    ) {
                        Text(stringResource(R.string.studio_discard))
                    }
                }
            }

            if (savedToLibraryNotice && hasUnsavedChanges) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.studio_saved_to_library_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            // Immediately accessible global Animations toggle
            val animationsEnabled =
                !draft.motion.disableAnimations && draft.motion.profile != MotionProfile.REDUCED
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.studio_global_animations),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.studio_global_animations_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = animationsEnabled,
                        onCheckedChange = { checked ->
                            haptic(ArchiveTuneHapticKind.PLAY_PAUSE)
                            updateDraft(
                                draft.copy(
                                    motion =
                                        if (checked) {
                                            draft.motion.copy(
                                                disableAnimations = false,
                                                profile =
                                                    if (draft.motion.profile == MotionProfile.REDUCED) {
                                                        MotionProfile.SYSTEM
                                                    } else {
                                                        draft.motion.profile
                                                    },
                                            )
                                        } else {
                                            draft.motion.copy(
                                                disableAnimations = true,
                                                profile = MotionProfile.REDUCED,
                                            )
                                        },
                                ),
                            )
                        },
                    )
                }
            }

            // Starter presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.studio_starter_presets),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StudioStarterPresets, key = { it.id }) { preset ->
                        val selected = draft.normalized() == preset.snapshot.normalized()
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(preset.snapshot.normalized())
                            },
                            label = { Text(preset.localizedTitle(isRussian)) },
                        )
                    }
                }
            }

            // My Styles & Export / Import
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.studio_my_styles),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FilledTonalButton(
                            onClick = {
                                saveStyleNameDraft = ""
                                showSaveStyleDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(painterResource(R.drawable.add), null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.studio_save_as_my_style))
                        }
                    }

                    if (savedProfiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.studio_my_styles_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        savedProfiles.forEach { profile ->
                            val isActive = profile.id == activeProfileId && activeProfileId.isNotBlank()
                            val isDraftSelected = profile.id == draftProfileId && !isActive
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color =
                                    when {
                                        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        isDraftSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f)
                                        else -> MaterialTheme.colorScheme.surfaceContainer
                                    },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (isActive || isDraftSelected) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color =
                                                        if (isActive) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.secondary
                                                        },
                                                ) {
                                                    Text(
                                                        text =
                                                            stringResource(
                                                                if (isActive) {
                                                                    R.string.studio_active_badge
                                                                } else {
                                                                    R.string.studio_draft_badge
                                                                },
                                                            ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color =
                                                            if (isActive) {
                                                                MaterialTheme.colorScheme.onPrimary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSecondary
                                                            },
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = sectionSummaryNavigation(profile.snapshot.navigation),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            haptic(ArchiveTuneHapticKind.SELECTION)
                                            draft = profile.snapshot.normalized()
                                            draftProfileId = profile.id
                                            hasUserEditedDraft = true
                                            savedToLibraryNotice = false
                                        },
                                    ) {
                                        Text(stringResource(R.string.studio_load_to_draft))
                                    }
                                    MaterialIconButton(
                                        onClick = {
                                            renameTargetId = profile.id
                                            renameDraft = profile.name
                                        },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.edit),
                                            contentDescription = stringResource(R.string.studio_rename_style),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    MaterialIconButton(
                                        onClick = {
                                            val updated =
                                                SavedProfilesStore.duplicate(
                                                    existing = savedProfiles,
                                                    sourceId = profile.id,
                                                    newId = UUID.randomUUID().toString(),
                                                    copySuffix = if (isRussian) "(копия)" else "(Copy)",
                                                    nowMs = System.currentTimeMillis(),
                                                )
                                            savedProfilesJson = SavedProfilesStore.encode(updated)
                                        },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.content_copy),
                                            contentDescription = stringResource(R.string.studio_duplicate_style),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    MaterialIconButton(
                                        onClick = {
                                            savedProfilesJson =
                                                SavedProfilesStore.encode(
                                                    SavedProfilesStore.delete(savedProfiles, profile.id),
                                                )
                                            if (draftProfileId == profile.id) {
                                                draftProfileId = null
                                            }
                                            if (activeProfileId == profile.id) {
                                                activeProfileId = ""
                                            }
                                        },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.delete),
                                            contentDescription = stringResource(R.string.studio_delete_style),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("archivetune-style.json") },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.studio_export_profile),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.studio_import_profile),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Section 1: Home
            StudioSectionCard(
                section = AppearanceSection.HOME,
                iconRes = R.drawable.home_outlined,
                summary =
                    homeLayoutLabel(draft.home.homeFeedStyle) +
                        if (draft.home.showHomeCategoryChips) "" else " · ${stringResource(R.string.disabled_state)}",
                expanded = expandedSection == AppearanceSection.HOME,
                onToggleExpand = {
                    expandedSection =
                        if (expandedSection == AppearanceSection.HOME) null else AppearanceSection.HOME
                },
                onResetSection = { updateDraft(draft.resetSection(AppearanceSection.HOME, isLowRam)) },
                onOpenFullSettings = { navController.navigate("settings/appearance?scrollTo=home_feed_style") },
                modifier = positions.modifierFor("home"),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HomeFeedStyle.entries.forEach { style ->
                        FilterChip(
                            selected = draft.home.homeFeedStyle == style,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(draft.copy(home = draft.home.copy(homeFeedStyle = style)))
                            },
                            label = { Text(homeLayoutLabel(style)) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.show_home_category_chips),
                    checked = draft.home.showHomeCategoryChips,
                    onCheckedChange = {
                        updateDraft(draft.copy(home = draft.home.copy(showHomeCategoryChips = it)))
                    },
                )
            }

            // Section 2: Navigation & MiniPlayer
            StudioSectionCard(
                section = AppearanceSection.NAVIGATION,
                iconRes = R.drawable.nav_bar,
                summary = sectionSummaryNavigation(draft.navigation),
                expanded = expandedSection == AppearanceSection.NAVIGATION,
                onToggleExpand = {
                    expandedSection =
                        if (expandedSection == AppearanceSection.NAVIGATION) null else AppearanceSection.NAVIGATION
                },
                onResetSection = { updateDraft(draft.resetSection(AppearanceSection.NAVIGATION, isLowRam)) },
                onOpenFullSettings = { navController.navigate("settings/appearance/navigation_bar") },
                modifier = positions.modifierFor("navigation_mini_player"),
            ) {
                Text(
                    text = stringResource(R.string.studio_starter_presets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NavMiniPlayerStarterPresets.forEach { preset ->
                        FilterChip(
                            selected =
                                draft.navigation.material == preset.navigation.material &&
                                    draft.navigation.miniPlayerStyle == preset.navigation.miniPlayerStyle &&
                                    draft.navigation.hideLabels == preset.navigation.hideLabels,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(draft.copy(navigation = preset.navigation))
                            },
                            label = { Text(stringResource(preset.titleRes)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.studio_nav_background),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NavBarMaterial.entries.forEach { material ->
                        FilterChip(
                            selected = draft.navigation.material == material,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(draft.copy(navigation = draft.navigation.copy(material = material)))
                            },
                            label = { Text(stringResource(material.labelRes)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.mini_player_style_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniPlayerStyle.entries.forEach { style ->
                        FilterChip(
                            selected = draft.navigation.miniPlayerStyle == style,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(
                                    draft.copy(
                                        navigation = draft.navigation.copy(miniPlayerStyle = style),
                                    ),
                                )
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (style == MiniPlayerStyle.BAR) {
                                            R.string.mini_player_style_bar
                                        } else {
                                            R.string.mini_player_style_pill
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.mini_player_background_style),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MiniPlayerBackgroundStyle.entries.forEach { bgStyle ->
                        FilterChip(
                            selected = draft.navigation.miniPlayerBackground == bgStyle,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(
                                    draft.copy(
                                        navigation = draft.navigation.copy(miniPlayerBackground = bgStyle),
                                    ),
                                )
                            },
                            label = { Text(stringResource(miniPlayerBackgroundStyleLabelRes(bgStyle))) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.hide_navigation_bar_labels),
                    checked = draft.navigation.hideLabels,
                    onCheckedChange = {
                        updateDraft(draft.copy(navigation = draft.navigation.copy(hideLabels = it)))
                    },
                )
                StudioFineTuningBlock {
                    val supportsGlassGlow = navBarMaterialSupportsGlassGlow(draft.navigation.material)
                    val widthLocked = navBarMaterialLocksWidth(draft.navigation.material)
                    val heightLocked = navBarMaterialLocksHeight(draft.navigation.material)

                    StudioToggleRow(
                        title = stringResource(R.string.navigation_bar_glass_glow),
                        subtitle =
                            if (supportsGlassGlow) {
                                stringResource(R.string.navigation_bar_glass_glow_desc)
                            } else {
                                stringResource(R.string.dependency_nav_material_glow_bar)
                            },
                        checked = draft.navigation.glassGlow,
                        enabled = supportsGlassGlow,
                        onCheckedChange = {
                            updateDraft(draft.copy(navigation = draft.navigation.copy(glassGlow = it)))
                        },
                    )
                    StudioSliderRow(
                        label = stringResource(R.string.navigation_bar_glass_glow_intensity),
                        subtitle =
                            if (!supportsGlassGlow || !draft.navigation.glassGlow) {
                                stringResource(R.string.dependency_nav_material_glow)
                            } else {
                                null
                            },
                        value = draft.navigation.glassGlowIntensity,
                        valueText = "${(draft.navigation.glassGlowIntensity * 100).roundToInt()}%",
                        range = 0.2f..2.0f,
                        enabled = supportsGlassGlow && draft.navigation.glassGlow,
                        onValueChange = {
                            updateDraft(draft.copy(navigation = draft.navigation.copy(glassGlowIntensity = it)))
                        },
                    )
                    val supportsMiniGlassGlow = draft.navigation.miniPlayerBackground.supportsGlassGlow &&
                        draft.color.liquidGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    StudioToggleRow(
                        title = stringResource(R.string.mini_player_glass_glow),
                        subtitle = if (supportsMiniGlassGlow) null else stringResource(R.string.dependency_nav_material_glow_bar),
                        checked = draft.navigation.miniPlayerGlassGlow,
                        enabled = supportsMiniGlassGlow,
                        onCheckedChange = {
                            updateDraft(draft.copy(navigation = draft.navigation.copy(miniPlayerGlassGlow = it)))
                        },
                    )
                    StudioSliderRow(
                        label = stringResource(R.string.mini_player_glass_glow_intensity),
                        value = draft.navigation.miniPlayerGlassGlowIntensity,
                        valueText = "${(draft.navigation.miniPlayerGlassGlowIntensity * 100).roundToInt()}%",
                        range = 0.2f..2.0f,
                        enabled = supportsMiniGlassGlow && draft.navigation.miniPlayerGlassGlow,
                        onValueChange = {
                            updateDraft(draft.copy(navigation = draft.navigation.copy(miniPlayerGlassGlowIntensity = it)))
                        },
                    )
                    StudioSliderRow(
                        label = stringResource(R.string.navigation_bar_height),
                        subtitle = if (heightLocked) stringResource(R.string.dependency_nav_height) else null,
                        value = draft.navigation.heightMultiplier,
                        valueText = "${(draft.navigation.heightMultiplier * 100).roundToInt()}%",
                        range = 0.8f..1.4f,
                        enabled = !heightLocked,
                        onValueChange = {
                            updateDraft(draft.copy(navigation = draft.navigation.copy(heightMultiplier = it)))
                        },
                    )
                    StudioSliderRow(
                        label = stringResource(R.string.navigation_bar_width),
                        subtitle = if (widthLocked) stringResource(R.string.dependency_nav_width) else null,
                        value = draft.navigation.widthFraction,
                        valueText = "${(draft.navigation.widthFraction * 100).roundToInt()}%",
                        range = 0.5f..1.0f,
                        enabled = !widthLocked,
                        onValueChange = {
                            updateDraft(draft.copy(navigation = draft.navigation.copy(widthFraction = it)))
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NavigationBarCompactBehavior.entries.forEach { behavior ->
                            FilterChip(
                                selected = draft.navigation.compactBehavior == behavior,
                                onClick = {
                                    updateDraft(
                                        draft.copy(navigation = draft.navigation.copy(compactBehavior = behavior)),
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            when (behavior) {
                                                NavigationBarCompactBehavior.ADAPTIVE ->
                                                    R.string.navigation_bar_compact_adaptive
                                                NavigationBarCompactBehavior.ALWAYS_EXPANDED ->
                                                    R.string.navigation_bar_compact_always_expanded
                                                NavigationBarCompactBehavior.ALWAYS_COMPACT ->
                                                    R.string.navigation_bar_compact_always_compact
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    if (draft.navigation.compactBehavior == NavigationBarCompactBehavior.ALWAYS_COMPACT) {
                        Text(
                            text = stringResource(R.string.studio_nav_always_compact_geometry_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Section 3: Player
            val playerStyleTitle = stringResource(playerDesignStyleLabelRes(draft.player.designStyle))
            val supportsCustomPlayerBg = playerStyleSupportsCustomBackground(draft.player.designStyle)
            val supportsPlayerVolumeBar = playerStyleSupportsVolumeBar(draft.player.designStyle)
            val availablePlayerBackgrounds =
                remember {
                    PlayerBackgroundStyle.entries.filter {
                        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    }
                }

            StudioSectionCard(
                section = AppearanceSection.PLAYER,
                iconRes = R.drawable.music_note,
                summary = "$playerStyleTitle · ${sliderLabel(draft.player.sliderStyle)}",
                expanded = expandedSection == AppearanceSection.PLAYER,
                onToggleExpand = {
                    expandedSection =
                        if (expandedSection == AppearanceSection.PLAYER) null else AppearanceSection.PLAYER
                },
                onResetSection = { updateDraft(draft.resetSection(AppearanceSection.PLAYER, isLowRam)) },
                onOpenFullSettings = { navController.navigate("settings/appearance?scrollTo=player_design_style") },
                modifier = positions.modifierFor("player"),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SelectablePlayerDesignStyles.forEach { style ->
                        FilterChip(
                            selected = draft.player.designStyle == style,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(
                                    draft.copy(
                                        player = draft.player.copy(designStyle = style),
                                    ).normalized(),
                                )
                            },
                            label = { Text(stringResource(playerDesignStyleLabelRes(style))) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.player_background_style),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!supportsCustomPlayerBg) {
                    Text(
                        text =
                            stringResource(
                                R.string.dependency_player_style_custom_locked,
                                playerStyleTitle,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availablePlayerBackgrounds.forEach { bgStyle ->
                        FilterChip(
                            selected = draft.player.backgroundStyle == bgStyle,
                            enabled = supportsCustomPlayerBg,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(
                                    draft.copy(
                                        player = draft.player.copy(backgroundStyle = bgStyle),
                                    ),
                                )
                            },
                            label = { Text(stringResource(playerBackgroundStyleLabelRes(bgStyle))) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.show_player_volume_bar),
                    subtitle =
                        if (supportsPlayerVolumeBar) {
                            null
                        } else {
                            stringResource(R.string.dependency_player_volume_bar)
                        },
                    checked = draft.player.showVolumeBar,
                    enabled = supportsPlayerVolumeBar,
                    onCheckedChange = {
                        updateDraft(draft.copy(player = draft.player.copy(showVolumeBar = it)))
                    },
                )
                StudioToggleRow(
                    title = stringResource(R.string.crop_thumbnail_to_square),
                    checked = draft.player.cropToSquare,
                    onCheckedChange = {
                        updateDraft(draft.copy(player = draft.player.copy(cropToSquare = it)))
                    },
                )
                StudioFineTuningBlock {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SliderStyle.entries.forEach { sStyle ->
                            FilterChip(
                                selected = draft.player.sliderStyle == sStyle,
                                onClick = {
                                    updateDraft(draft.copy(player = draft.player.copy(sliderStyle = sStyle)))
                                },
                                label = { Text(sliderLabel(sStyle)) },
                            )
                        }
                    }
                    StudioSliderRow(
                        label = stringResource(R.string.custom_radius),
                        value = draft.player.thumbnailCornerRadius,
                        valueText = "${draft.player.thumbnailCornerRadius.roundToInt()} dp",
                        range = 0f..36f,
                        onValueChange = {
                            updateDraft(draft.copy(player = draft.player.copy(thumbnailCornerRadius = it)))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.hide_player_thumbnail),
                        checked = draft.player.hideThumbnail,
                        onCheckedChange = {
                            updateDraft(draft.copy(player = draft.player.copy(hideThumbnail = it)))
                        },
                    )
                }
            }

            // Section 4: Lyrics
            StudioSectionCard(
                section = AppearanceSection.LYRICS,
                iconRes = R.drawable.lyrics,
                summary = "${lyricsCharacterLabel(draft.lyrics.displayCharacter)} · ${draft.lyrics.textSize.roundToInt()} sp",
                expanded = expandedSection == AppearanceSection.LYRICS,
                onToggleExpand = {
                    expandedSection =
                        if (expandedSection == AppearanceSection.LYRICS) null else AppearanceSection.LYRICS
                },
                onResetSection = { updateDraft(draft.resetSection(AppearanceSection.LYRICS, isLowRam)) },
                onOpenFullSettings = { navController.navigate("settings/lyrics?scrollTo=lyrics_mode") },
                modifier = positions.modifierFor("lyrics"),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsDisplayCharacter.entries.forEach { ch ->
                        FilterChip(
                            selected = draft.lyrics.displayCharacter == ch,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(draft.copy(lyrics = draft.lyrics.copy(displayCharacter = ch)))
                            },
                            label = { Text(lyricsCharacterLabel(ch)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.lyrics_background_style),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        LyricsBackgroundStyle.DEFAULT,
                        LyricsBackgroundStyle.FOLLOW_THEME,
                        LyricsBackgroundStyle.COLORING,
                        LyricsBackgroundStyle.MOVING_BLUR,
                    ).forEach { bgStyle ->
                        FilterChip(
                            selected = draft.lyrics.backgroundStyle == bgStyle,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(draft.copy(lyrics = draft.lyrics.copy(backgroundStyle = bgStyle)))
                            },
                            label = { Text(stringResource(lyricsBackgroundStyleLabelRes(bgStyle))) },
                        )
                    }
                }
                if (playerStyleUsesOwnFullscreenLyrics(draft.player.designStyle)) {
                    Text(
                        text = stringResource(R.string.lyrics_background_style_own_player_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StudioToggleRow(
                    title = stringResource(R.string.lyrics_line_blur),
                    checked = draft.lyrics.lineBlur,
                    onCheckedChange = {
                        updateDraft(draft.copy(lyrics = draft.lyrics.copy(lineBlur = it)))
                    },
                )
                StudioFineTuningBlock {
                    StudioSliderRow(
                        label = stringResource(R.string.lyrics_text_size),
                        value = draft.lyrics.textSize,
                        valueText = "${draft.lyrics.textSize.roundToInt()} sp",
                        range = 16f..38f,
                        onValueChange = {
                            updateDraft(draft.copy(lyrics = draft.lyrics.copy(textSize = it)))
                        },
                    )
                    StudioSliderRow(
                        label = stringResource(R.string.lyrics_line_spacing),
                        value = draft.lyrics.lineSpacing,
                        valueText = "${(draft.lyrics.lineSpacing * 10f).roundToInt() / 10f}x",
                        range = 1.0f..1.8f,
                        onValueChange = {
                            updateDraft(draft.copy(lyrics = draft.lyrics.copy(lineSpacing = it)))
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LyricsMode.entries.forEach { mode ->
                            FilterChip(
                                selected = draft.lyrics.mode == mode,
                                onClick = {
                                    updateDraft(draft.copy(lyrics = draft.lyrics.copy(mode = mode)))
                                },
                                label = { Text(stringResource(lyricsModeLabelRes(mode))) },
                            )
                        }
                    }
                }
            }

            // Section 5: Color & Background
            val (resolvedCustomColor, _) =
                remember(draft.color.customThemeColor) {
                    resolveCustomThemeSelection(draft.color.customThemeColor)
                }
            val isCuratedAccent =
                CuratedStudioAccentPalettes.any { it.id == draft.color.customThemeColor }

            StudioSectionCard(
                section = AppearanceSection.COLOR,
                iconRes = R.drawable.palette,
                summary = darkModeLabel(draft.color.darkMode) +
                    if (draft.color.pureBlack) " · AMOLED" else "",
                expanded = expandedSection == AppearanceSection.COLOR,
                onToggleExpand = {
                    expandedSection =
                        if (expandedSection == AppearanceSection.COLOR) null else AppearanceSection.COLOR
                },
                onResetSection = { updateDraft(draft.resetSection(AppearanceSection.COLOR, isLowRam)) },
                onOpenFullSettings = { navController.navigate("settings/appearance/palette_picker") },
                modifier = positions.modifierFor("color_background"),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draft.color.darkMode == mode,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(draft.copy(color = draft.color.copy(darkMode = mode)))
                            },
                            label = { Text(darkModeLabel(mode)) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.pure_black),
                    checked = draft.color.pureBlack,
                    onCheckedChange = {
                        updateDraft(draft.copy(color = draft.color.copy(pureBlack = it)))
                    },
                )
                StudioToggleRow(
                    title = stringResource(R.string.enable_dynamic_theme),
                    checked = draft.color.dynamicTheme,
                    onCheckedChange = {
                        updateDraft(draft.copy(color = draft.color.copy(dynamicTheme = it)))
                    },
                )
                Text(
                    text = stringResource(R.string.studio_accent_swatches_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft.color.dynamicTheme) {
                    Text(
                        text = stringResource(R.string.studio_dynamic_overrides_accent_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CuratedStudioAccentPalettes.forEach { palette ->
                        val selected = !draft.color.dynamicTheme && draft.color.customThemeColor == palette.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(
                                    draft.copy(
                                        color =
                                            draft.color.copy(
                                                customThemeColor = palette.id,
                                                dynamicTheme = false,
                                            ),
                                    ),
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(palette.primary)
                                            .border(
                                                width = if (selected) 1.5.dp else 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                shape = CircleShape,
                                            ),
                                )
                            },
                            label = { Text(stringResource(palette.nameResId)) },
                        )
                    }
                    if (!isCuratedAccent) {
                        FilterChip(
                            selected = !draft.color.dynamicTheme,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(
                                    draft.copy(color = draft.color.copy(dynamicTheme = false)),
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(resolvedCustomColor)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                CircleShape,
                                            ),
                                )
                            },
                            label = { Text(stringResource(R.string.studio_accent_custom_badge)) },
                        )
                    }
                }
                StudioFineTuningBlock {
                    Text(
                        text = stringResource(R.string.player_background_motion_policy_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BackgroundMotionPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = draft.color.backgroundMotionPolicy == policy,
                                onClick = {
                                    updateDraft(
                                        draft.copy(
                                            color = draft.color.copy(backgroundMotionPolicy = policy),
                                        ),
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            when (policy) {
                                                BackgroundMotionPolicy.WHILE_PLAYING_OPEN ->
                                                    R.string.player_background_motion_while_open
                                                BackgroundMotionPolicy.RESPECT_POWER_SAVER ->
                                                    R.string.player_background_motion_power_saver
                                                BackgroundMotionPolicy.NEVER ->
                                                    R.string.player_background_motion_never
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    StudioToggleRow(
                        title = stringResource(R.string.album_backdrop),
                        checked = draft.color.backdropEnabled,
                        onCheckedChange = {
                            updateDraft(draft.copy(color = draft.color.copy(backdropEnabled = it)))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.disable_blur),
                        subtitle = stringResource(R.string.disable_blur_desc),
                        checked = draft.color.disableBlur,
                        onCheckedChange = {
                            updateDraft(draft.copy(color = draft.color.copy(disableBlur = it)))
                        },
                        modifier = positions.modifierFor("disable_blur"),
                    )
                }
            }

            // Section 6: Motion
            StudioSectionCard(
                section = AppearanceSection.MOTION,
                iconRes = R.drawable.animation,
                summary = "${motionProfileLabel(draft.motion.profile)} · ${hapticIntensityLabel(draft.motion.hapticIntensity)}",
                expanded = expandedSection == AppearanceSection.MOTION,
                onToggleExpand = {
                    expandedSection =
                        if (expandedSection == AppearanceSection.MOTION) null else AppearanceSection.MOTION
                },
                onResetSection = { updateDraft(draft.resetSection(AppearanceSection.MOTION, isLowRam)) },
                onOpenFullSettings = { navController.navigate("settings/appearance?scrollTo=disable_animations") },
                modifier = positions.modifierFor("motion_profile"),
            ) {
                Text(
                    text = stringResource(R.string.studio_motion_presets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MotionProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = draft.motion.profile == profile,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                updateDraft(
                                    draft.copy(
                                        motion =
                                            draft.motion.copy(
                                                profile = profile,
                                                disableAnimations = profile == MotionProfile.REDUCED,
                                            ),
                                    ),
                                )
                            },
                            label = { Text(motionProfileLabel(profile)) },
                        )
                    }
                }
                val supportsMotionBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                StudioToggleRow(
                    title = stringResource(R.string.studio_motion_blur),
                    subtitle = stringResource(
                        if (supportsMotionBlur) R.string.studio_motion_blur_hint else R.string.studio_motion_blur_android_12,
                    ),
                    enabled = supportsMotionBlur,
                    checked = draft.motion.motionBlur && supportsMotionBlur,
                    onCheckedChange = { updateDraft(draft.copy(motion = draft.motion.copy(motionBlur = it))) },
                )
                StudioMotionDemoCard(motion = draft.motion)
                Text(
                    text = stringResource(R.string.haptic_intensity_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticIntensity.entries.forEach { intensity ->
                        FilterChip(
                            selected = draft.motion.hapticIntensity == intensity,
                            onClick = {
                                updateDraft(
                                    draft.copy(
                                        motion =
                                            draft.motion.copy(
                                                hapticIntensity = intensity,
                                                hapticsEnabled = intensity != HapticIntensity.OFF,
                                            ),
                                    ),
                                )
                            },
                            label = { Text(hapticIntensityLabel(intensity)) },
                        )
                    }
                }
                StudioFineTuningBlock {
                    val duration = draft.motion.durationPercent.coerceIn(MotionDurationPercentRange)
                    StudioSliderRow(
                        label = stringResource(R.string.studio_motion_duration),
                        value = duration.toFloat(),
                        valueText = stringResource(R.string.studio_motion_duration_value, duration),
                        range = MotionDurationPercentRange.first.toFloat()..MotionDurationPercentRange.last.toFloat(),
                        onValueChange = { updateDraft(draft.copy(motion = draft.motion.copy(durationPercent = it.roundToInt()))) },
                    )
                    MotionStylePicker(
                        title = stringResource(R.string.studio_motion_screen_style),
                        style = draft.motion.screenStyle,
                        onSelect = { updateDraft(draft.copy(motion = draft.motion.copy(screenStyle = it))) },
                    )
                    MotionStylePicker(
                        title = stringResource(R.string.studio_motion_menu_style),
                        style = draft.motion.menuStyle,
                        onSelect = { updateDraft(draft.copy(motion = draft.motion.copy(menuStyle = it))) },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_screen),
                        checked = draft.motion.screenTransitions,
                        onCheckedChange = {
                            updateDraft(draft.copy(motion = draft.motion.copy(screenTransitions = it)))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_artwork),
                        checked = draft.motion.artworkTransitions,
                        onCheckedChange = {
                            updateDraft(draft.copy(motion = draft.motion.copy(artworkTransitions = it)))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_press),
                        checked = draft.motion.pressFeedback,
                        onCheckedChange = {
                            updateDraft(draft.copy(motion = draft.motion.copy(pressFeedback = it)))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_decorative),
                        checked = draft.motion.decorativeBackground,
                        onCheckedChange = {
                            updateDraft(draft.copy(motion = draft.motion.copy(decorativeBackground = it)))
                        },
                    )
                }
            }

            // Bottom Apply / Discard convenience bar when scrolled down with unsaved edits
            if (hasUnsavedChanges) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                discardDraft()
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.studio_discard))
                        }
                        Button(
                            onClick = applyDraft,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.studio_apply))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSectionCard(
    section: AppearanceSection,
    iconRes: Int,
    summary: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onResetSection: () -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = onToggleExpand,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(section.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                        contentDescription = null,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    content()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = onResetSection) {
                            Icon(painterResource(R.drawable.restore), null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.studio_reset_section))
                        }
                        TextButton(onClick = onOpenFullSettings) {
                            Text(stringResource(R.string.studio_open_full_settings))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioFineTuningBlock(
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(stringResource(R.string.studio_fine_tuning))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun StudioToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                fontWeight = FontWeight.Medium,
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            valueRange = range,
            enabled = enabled,
            onValueChange = onValueChange,
        )
    }
}

@Composable
private fun sectionSummaryNavigation(nav: NavigationProfile): String {
    val material = stringResource(nav.material.labelRes)
    val labels = stringResource(if (nav.hideLabels) R.string.studio_no_labels else R.string.studio_with_labels)
    val mini =
        stringResource(
            if (nav.miniPlayerStyle == MiniPlayerStyle.BAR) {
                R.string.mini_player_style_bar
            } else {
                R.string.mini_player_style_pill
            },
        )
    return "$material · $labels · $mini"
}

@Composable
private fun homeLayoutLabel(style: HomeFeedStyle): String =
    stringResource(
        when (style) {
            HomeFeedStyle.STANDARD -> R.string.home_feed_style_standard
            HomeFeedStyle.COMPACT -> R.string.home_feed_style_compact
            HomeFeedStyle.GALLERY -> R.string.home_feed_style_gallery
            HomeFeedStyle.NOW -> R.string.home_feed_style_now
            HomeFeedStyle.COVERS -> R.string.home_feed_style_covers
        },
    )

@Composable
private fun sliderLabel(style: SliderStyle): String =
    stringResource(
        when (style) {
            SliderStyle.Standard -> R.string.slider_style_standard
            SliderStyle.Wavy -> R.string.slider_style_wavy
            SliderStyle.Thick -> R.string.slider_style_thick
            SliderStyle.Circular -> R.string.slider_style_circular
            SliderStyle.Simple -> R.string.slider_style_simple
        },
    )

@Composable
private fun lyricsCharacterLabel(character: LyricsDisplayCharacter): String =
    stringResource(
        when (character) {
            LyricsDisplayCharacter.READING -> R.string.lyrics_character_reading
            LyricsDisplayCharacter.FOCUS -> R.string.lyrics_character_focus
            LyricsDisplayCharacter.STAGE -> R.string.lyrics_character_stage
        },
    )

@Composable
private fun darkModeLabel(mode: DarkMode): String =
    stringResource(
        when (mode) {
            DarkMode.ON -> R.string.dark_theme_on
            DarkMode.OFF -> R.string.dark_theme_off
            DarkMode.AUTO -> R.string.dark_theme_follow_system
        },
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotionStylePicker(
    title: String,
    style: MotionTransitionStyle,
    onSelect: (MotionTransitionStyle) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MotionTransitionStyle.entries.forEach { choice ->
            FilterChip(
                selected = style == choice,
                onClick = { onSelect(choice) },
                label = {
                    Text(
                        stringResource(
                            when (choice) {
                                MotionTransitionStyle.DEFAULT -> R.string.studio_motion_style_default
                                MotionTransitionStyle.SLIDE -> R.string.studio_motion_style_slide
                                MotionTransitionStyle.FADE -> R.string.studio_motion_style_fade
                                MotionTransitionStyle.ZOOM -> R.string.studio_motion_style_zoom
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun motionProfileLabel(profile: MotionProfile): String =
    stringResource(
        when (profile) {
            MotionProfile.SYSTEM -> R.string.motion_profile_system
            MotionProfile.FAST -> R.string.motion_profile_fast
            MotionProfile.SMOOTH -> R.string.motion_profile_smooth
            MotionProfile.SPRINGY -> R.string.motion_profile_springy
            MotionProfile.CINEMATIC -> R.string.motion_profile_cinematic
            MotionProfile.REDUCED -> R.string.motion_profile_reduced
        },
    )

@Composable
private fun hapticIntensityLabel(intensity: HapticIntensity): String =
    stringResource(
        when (intensity) {
            HapticIntensity.OFF -> R.string.haptic_intensity_off
            HapticIntensity.LIGHT -> R.string.haptic_intensity_light
            HapticIntensity.STANDARD -> R.string.haptic_intensity_standard
            HapticIntensity.STRONG -> R.string.haptic_intensity_strong
        },
    )
