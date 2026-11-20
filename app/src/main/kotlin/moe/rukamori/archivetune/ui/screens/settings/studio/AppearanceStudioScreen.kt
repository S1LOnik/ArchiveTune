/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.studio

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import moe.rukamori.archivetune.constants.LyricsDisplayCharacter
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.MiniPlayerStyle
import moe.rukamori.archivetune.constants.MotionProfile
import moe.rukamori.archivetune.constants.NavigationBarCompactBehavior
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.SavedAppearanceProfilesKey
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.SelectablePlayerDesignStyles
import moe.rukamori.archivetune.ui.screens.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.screens.settings.rememberPreferencePositions
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.UUID
import kotlin.math.roundToInt

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

    var draft by remember { mutableStateOf(savedSnapshot) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(savedSnapshot) {
        if (!initialized) {
            draft = savedSnapshot
            initialized = true
        }
    }

    val hasUnsavedChanges = remember(draft, savedSnapshot) { draft.normalized() != savedSnapshot }
    var isComparingSaved by remember { mutableStateOf(false) }
    var coverMode by remember { mutableStateOf(StudioCoverPreviewMode.LIGHT) }
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }

    var savedProfilesJson by rememberPreference(SavedAppearanceProfilesKey, "")
    var activeProfileId by rememberPreference(ActiveAppearanceProfileIdKey, "")
    val savedProfiles =
        remember(savedProfilesJson) {
            SavedProfilesStore.decode(savedProfilesJson)
        }

    var showSaveStyleDialog by rememberSaveable { mutableStateOf(false) }
    var saveStyleNameDraft by rememberSaveable { mutableStateOf("") }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<ImportedAppearanceProfile?>(null) }

    val scrollState = rememberScrollState()
    val positions = rememberPreferencePositions()
    var expandedSection by rememberSaveable {
        mutableStateOf(
            when (scrollTo) {
                "motion_profile" -> AppearanceSection.MOTION
                "navigation_mini_player" -> AppearanceSection.NAVIGATION
                "color_background" -> AppearanceSection.COLOR
                "home" -> AppearanceSection.HOME
                else -> AppearanceSection.HOME
            },
        )
    }

    LaunchedEffect(scrollTo) {
        if (!scrollTo.isNullOrBlank()) {
            expandedSection =
                when (scrollTo) {
                    "motion_profile" -> AppearanceSection.MOTION
                    "navigation_mini_player" -> AppearanceSection.NAVIGATION
                    "color_background" -> AppearanceSection.COLOR
                    "lyrics" -> AppearanceSection.LYRICS
                    "player" -> AppearanceSection.PLAYER
                    else -> AppearanceSection.HOME
                }
            positions.scrollToKey(scrollTo, scrollState)
        }
    }

    val applyDraft: () -> Unit = {
        val target = draft.normalized()
        haptic(ArchiveTuneHapticKind.SELECTION)
        scope.launch {
            context.dataStore.edit { mutable ->
                target.writeToPreferences(mutable)
            }
            draft = target
            snackbarHostState.showSnackbar(context.getString(R.string.studio_applied))
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val activeName =
                    savedProfiles.firstOrNull { it.id == activeProfileId }?.name ?: "ArchiveTune Style"
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

    val attemptNavigateUp: () -> Unit = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            navController.navigateUp()
        }
    }

    BackHandler(enabled = hasUnsavedChanges && !showUnsavedDialog) {
        showUnsavedDialog = true
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
                        val target = draft.normalized()
                        scope.launch {
                            context.dataStore.edit { mutable -> target.writeToPreferences(mutable) }
                            navController.navigateUp()
                        }
                    },
                ) {
                    Text(stringResource(R.string.studio_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        draft = savedSnapshot
                        navController.navigateUp()
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
                        activeProfileId = id
                        showSaveStyleDialog = false
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
                        draft = imported.snapshot.normalized()
                        pendingImport = null
                    },
                ) {
                    Text(stringResource(R.string.studio_apply))
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
                        onClick = attemptNavigateUp,
                        onLongClick = navController::backToMain,
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
                            draft = savedSnapshot
                        },
                    ) {
                        Text(stringResource(R.string.studio_discard))
                    }
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
                            draft =
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
                                draft = preset.snapshot.normalized()
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
                            val isActive = profile.id == activeProfileId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color =
                                    if (isActive) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
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
                                            activeProfileId = profile.id
                                        },
                                    ) {
                                        Text(stringResource(R.string.studio_apply))
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
                        if (expandedSection == AppearanceSection.HOME) AppearanceSection.NAVIGATION else AppearanceSection.HOME
                },
                onResetSection = { draft = draft.resetSection(AppearanceSection.HOME, isLowRam) },
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
                                draft = draft.copy(home = draft.home.copy(homeFeedStyle = style))
                            },
                            label = { Text(homeLayoutLabel(style)) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.show_home_category_chips),
                    checked = draft.home.showHomeCategoryChips,
                    onCheckedChange = {
                        draft = draft.copy(home = draft.home.copy(showHomeCategoryChips = it))
                    },
                )
            }

            // Section 2: Navigation & MiniPlayer
            StudioSectionCard(
                section = AppearanceSection.NAVIGATION,
                iconRes = R.drawable.nav_bar,
                summary = sectionSummaryNavigation(draft.navigation),
                expanded = expandedSection == AppearanceSection.NAVIGATION,
                onToggleExpand = { expandedSection = AppearanceSection.NAVIGATION },
                onResetSection = { draft = draft.resetSection(AppearanceSection.NAVIGATION, isLowRam) },
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
                                draft = draft.copy(navigation = preset.navigation)
                            },
                            label = { Text(stringResource(preset.titleRes)) },
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
                                draft =
                                    draft.copy(
                                        navigation = draft.navigation.copy(miniPlayerStyle = style),
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
                StudioToggleRow(
                    title = stringResource(R.string.hide_navigation_bar_labels),
                    checked = draft.navigation.hideLabels,
                    onCheckedChange = {
                        draft = draft.copy(navigation = draft.navigation.copy(hideLabels = it))
                    },
                )
                StudioFineTuningBlock {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        NavBarMaterial.entries.forEach { material ->
                            FilterChip(
                                selected = draft.navigation.material == material,
                                onClick = {
                                    draft = draft.copy(navigation = draft.navigation.copy(material = material))
                                },
                                label = { Text(stringResource(material.labelRes)) },
                            )
                        }
                    }
                    StudioSliderRow(
                        label = stringResource(R.string.navigation_bar_height),
                        value = draft.navigation.heightMultiplier,
                        valueText = "${(draft.navigation.heightMultiplier * 100).roundToInt()}%",
                        range = 0.8f..1.4f,
                        onValueChange = {
                            draft = draft.copy(navigation = draft.navigation.copy(heightMultiplier = it))
                        },
                    )
                    StudioSliderRow(
                        label = stringResource(R.string.navigation_bar_width),
                        value = draft.navigation.widthFraction,
                        valueText = "${(draft.navigation.widthFraction * 100).roundToInt()}%",
                        range = 0.5f..1.0f,
                        onValueChange = {
                            draft = draft.copy(navigation = draft.navigation.copy(widthFraction = it))
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NavigationBarCompactBehavior.entries.forEach { behavior ->
                            FilterChip(
                                selected = draft.navigation.compactBehavior == behavior,
                                onClick = {
                                    draft = draft.copy(navigation = draft.navigation.copy(compactBehavior = behavior))
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
                }
            }

            // Section 3: Player
            StudioSectionCard(
                section = AppearanceSection.PLAYER,
                iconRes = R.drawable.music_note,
                summary = "${draft.player.designStyle.name} · ${sliderLabel(draft.player.sliderStyle)}",
                expanded = expandedSection == AppearanceSection.PLAYER,
                onToggleExpand = { expandedSection = AppearanceSection.PLAYER },
                onResetSection = { draft = draft.resetSection(AppearanceSection.PLAYER, isLowRam) },
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
                                draft =
                                    draft.copy(
                                        player = draft.player.copy(designStyle = style),
                                    ).normalized()
                            },
                            label = { Text(style.name) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.crop_thumbnail_to_square),
                    checked = draft.player.cropToSquare,
                    onCheckedChange = {
                        draft = draft.copy(player = draft.player.copy(cropToSquare = it))
                    },
                )
                StudioFineTuningBlock {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SliderStyle.entries.forEach { sStyle ->
                            FilterChip(
                                selected = draft.player.sliderStyle == sStyle,
                                onClick = {
                                    draft = draft.copy(player = draft.player.copy(sliderStyle = sStyle))
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
                            draft = draft.copy(player = draft.player.copy(thumbnailCornerRadius = it))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.hide_player_thumbnail),
                        checked = draft.player.hideThumbnail,
                        onCheckedChange = {
                            draft = draft.copy(player = draft.player.copy(hideThumbnail = it))
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
                onToggleExpand = { expandedSection = AppearanceSection.LYRICS },
                onResetSection = { draft = draft.resetSection(AppearanceSection.LYRICS, isLowRam) },
                onOpenFullSettings = { navController.navigate("settings/lyrics?scrollTo=lyrics_mode") },
                modifier = positions.modifierFor("lyrics"),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsDisplayCharacter.entries.forEach { ch ->
                        FilterChip(
                            selected = draft.lyrics.displayCharacter == ch,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                draft = draft.copy(lyrics = draft.lyrics.copy(displayCharacter = ch))
                            },
                            label = { Text(lyricsCharacterLabel(ch)) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.lyrics_line_blur),
                    checked = draft.lyrics.lineBlur,
                    onCheckedChange = {
                        draft = draft.copy(lyrics = draft.lyrics.copy(lineBlur = it))
                    },
                )
                StudioFineTuningBlock {
                    StudioSliderRow(
                        label = stringResource(R.string.lyrics_text_size),
                        value = draft.lyrics.textSize,
                        valueText = "${draft.lyrics.textSize.roundToInt()} sp",
                        range = 16f..38f,
                        onValueChange = {
                            draft = draft.copy(lyrics = draft.lyrics.copy(textSize = it))
                        },
                    )
                    StudioSliderRow(
                        label = stringResource(R.string.lyrics_line_spacing),
                        value = draft.lyrics.lineSpacing,
                        valueText = "${(draft.lyrics.lineSpacing * 10f).roundToInt() / 10f}x",
                        range = 1.0f..1.8f,
                        onValueChange = {
                            draft = draft.copy(lyrics = draft.lyrics.copy(lineSpacing = it))
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LyricsMode.entries.forEach { mode ->
                            FilterChip(
                                selected = draft.lyrics.mode == mode,
                                onClick = { draft = draft.copy(lyrics = draft.lyrics.copy(mode = mode)) },
                                label = { Text(mode.name) },
                            )
                        }
                    }
                }
            }

            // Section 5: Color & Background
            StudioSectionCard(
                section = AppearanceSection.COLOR,
                iconRes = R.drawable.palette,
                summary = darkModeLabel(draft.color.darkMode) +
                    if (draft.color.pureBlack) " · AMOLED" else "",
                expanded = expandedSection == AppearanceSection.COLOR,
                onToggleExpand = { expandedSection = AppearanceSection.COLOR },
                onResetSection = { draft = draft.resetSection(AppearanceSection.COLOR, isLowRam) },
                onOpenFullSettings = { navController.navigate("settings/appearance/palette_picker") },
                modifier = positions.modifierFor("color_background"),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draft.color.darkMode == mode,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                draft = draft.copy(color = draft.color.copy(darkMode = mode))
                            },
                            label = { Text(darkModeLabel(mode)) },
                        )
                    }
                }
                StudioToggleRow(
                    title = stringResource(R.string.pure_black),
                    checked = draft.color.pureBlack,
                    onCheckedChange = { draft = draft.copy(color = draft.color.copy(pureBlack = it)) },
                )
                StudioToggleRow(
                    title = stringResource(R.string.enable_dynamic_theme),
                    checked = draft.color.dynamicTheme,
                    onCheckedChange = { draft = draft.copy(color = draft.color.copy(dynamicTheme = it)) },
                )
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
                                    draft =
                                        draft.copy(
                                            color = draft.color.copy(backgroundMotionPolicy = policy),
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
                            draft = draft.copy(color = draft.color.copy(backdropEnabled = it))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.disable_blur),
                        checked = draft.color.disableBlur,
                        onCheckedChange = {
                            draft = draft.copy(color = draft.color.copy(disableBlur = it))
                        },
                    )
                }
            }

            // Section 6: Motion
            StudioSectionCard(
                section = AppearanceSection.MOTION,
                iconRes = R.drawable.animation,
                summary = "${motionProfileLabel(draft.motion.profile)} · ${hapticIntensityLabel(draft.motion.hapticIntensity)}",
                expanded = expandedSection == AppearanceSection.MOTION,
                onToggleExpand = { expandedSection = AppearanceSection.MOTION },
                onResetSection = { draft = draft.resetSection(AppearanceSection.MOTION, isLowRam) },
                onOpenFullSettings = { navController.navigate("settings/appearance?scrollTo=disable_animations") },
                modifier = positions.modifierFor("motion_profile"),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MotionProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = draft.motion.profile == profile,
                            onClick = {
                                haptic(ArchiveTuneHapticKind.SELECTION)
                                draft =
                                    draft.copy(
                                        motion =
                                            draft.motion.copy(
                                                profile = profile,
                                                disableAnimations = profile == MotionProfile.REDUCED,
                                            ),
                                    )
                            },
                            label = { Text(motionProfileLabel(profile)) },
                        )
                    }
                }
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
                                draft =
                                    draft.copy(
                                        motion =
                                            draft.motion.copy(
                                                hapticIntensity = intensity,
                                                hapticsEnabled = intensity != HapticIntensity.OFF,
                                            ),
                                    )
                            },
                            label = { Text(hapticIntensityLabel(intensity)) },
                        )
                    }
                }
                StudioFineTuningBlock {
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_screen),
                        checked = draft.motion.screenTransitions,
                        onCheckedChange = {
                            draft = draft.copy(motion = draft.motion.copy(screenTransitions = it))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_artwork),
                        checked = draft.motion.artworkTransitions,
                        onCheckedChange = {
                            draft = draft.copy(motion = draft.motion.copy(artworkTransitions = it))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_press),
                        checked = draft.motion.pressFeedback,
                        onCheckedChange = {
                            draft = draft.copy(motion = draft.motion.copy(pressFeedback = it))
                        },
                    )
                    StudioToggleRow(
                        title = stringResource(R.string.motion_channel_decorative),
                        checked = draft.motion.decorativeBackground,
                        onCheckedChange = {
                            draft = draft.copy(motion = draft.motion.copy(decorativeBackground = it))
                        },
                    )
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(value = value, valueRange = range, onValueChange = onValueChange)
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

@Composable
private fun motionProfileLabel(profile: MotionProfile): String =
    stringResource(
        when (profile) {
            MotionProfile.SYSTEM -> R.string.motion_profile_system
            MotionProfile.FAST -> R.string.motion_profile_fast
            MotionProfile.SMOOTH -> R.string.motion_profile_smooth
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
