/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
    onClearUpdateBadge: () -> Unit = {},
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()

    val storagePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            isStorageGranted = result[storagePermission] == true || isStorageGranted
            if (notificationPermission != null) {
                isNotificationGranted = result[notificationPermission] == true || isNotificationGranted
            }
        }

    val scrollBehavior = appBarScrollBehavior()
    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    val hasUpdate =
        BuildConfig.UPDATER_AVAILABLE &&
            Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
    var isUpdateDismissed by remember { mutableStateOf(false) }
    val settingsGroups = buildSettingsGroups(navController, isAndroid12OrLater, hasUpdate, context)

    // [FIX-ZCODE] Wave 9: settings search (ported from the donor). A query in the header
    // switches the list to ranked matches; tapping one opens the owning screen (with
    // scrollTo=<key> when the destination supports it) or falls back to the item action.
    var searchQuery by remember { mutableStateOf("") }
    val searchResults =
        remember(searchQuery, settingsGroups) {
            if (searchQuery.isBlank()) {
                emptyList()
            } else {
                SettingsSearch.search(
                    groups = settingsGroups,
                    rawQuery = searchQuery,
                    routeFor = { parentKey, scrollKey -> searchableSettingsRoute(parentKey, scrollKey) },
                )
            }
        }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = navController::navigateUp,
                            onLongClick = navController::backToMain,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back_button_desc),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    scrollBehavior = scrollBehavior,
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_settings),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, onLongClick = {}) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
        ) {
            if (hasUpdate && !isUpdateDismissed) {
                item(key = "update", contentType = "settings_banner") {
                    SettingsUpdateBanner(
                        latestVersion = latestVersionName,
                        onClick = { navController.navigate("settings/update") },
                        onDismiss = { isUpdateDismissed = true },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(bottom = SettingsDimensions.SectionSpacing),
                    )
                }
            }

            if (shouldShowPermissionHint) {
                item(key = "permission", contentType = "settings_banner") {
                    SettingsPermissionBanner(
                        onRequestPermission = {
                            val toRequest =
                                buildList {
                                    if (!isStorageGranted) add(storagePermission)
                                    if (!isNotificationGranted && notificationPermission != null) {
                                        add(notificationPermission)
                                    }
                                }
                            if (toRequest.isNotEmpty()) {
                                permissionLauncher.launch(toRequest.toTypedArray())
                            }
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(bottom = SettingsDimensions.SectionSpacing),
                    )
                }
            }

            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                itemsIndexed(
                    items = searchResults,
                    key = { index, result -> "${result.parentKey}:${result.title}:$index" },
                    contentType = { _, _ -> "settings_search_result" },
                ) { _, result ->
                    SettingsSearchResultItem(
                        result = result,
                        onClick = {
                            result.parentRoute?.let(navController::navigate) ?: result.onClick()
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(vertical = 4.dp),
                    )
                }
            } else if (searchQuery.isNotBlank()) {
                item(key = "settings_search_empty") {
                    Text(
                        text = stringResource(R.string.search_settings_empty, searchQuery),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 16.dp),
                    )
                }
            } else settingsGroups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    item(
                        key = "settings_group_spacing_$groupIndex",
                        contentType = "settings_group_spacing",
                    ) {
                        Spacer(modifier = Modifier.height(SettingsDimensions.SectionSpacing))
                    }
                }

                itemsIndexed(
                    items = group.items,
                    key = { _, item -> item.key },
                    contentType = { _, _ -> "settings_segment" },
                ) { index, settingsItem ->
                    SettingsSegmentedItem(
                        item = settingsItem,
                        index = index,
                        count = group.items.size,
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.SegmentedGroupHorizontalPadding)
                                .padding(
                                    bottom =
                                        if (index < group.items.lastIndex) {
                                            SettingsDimensions.SegmentedItemGap
                                        } else {
                                            0.dp
                                        },
                                ),
                    )
                }
            }
        }
    }
}

// [FIX-ZCODE] Wave 9: maps a settings item key (and optional scroll target key) to the
// navigation route of the owning settings screen. Unmapped keys return null so the search
// result falls back to the item's own onClick action.
private fun searchableSettingsRoute(
    parentKey: String,
    scrollKey: String?,
): String? {
    val route =
        when (parentKey) {
            "account" -> "settings/account"
            "stats" -> "settings/account"
            "appearance" -> "settings/appearance"
            "mascot" -> "settings/mascot"
            "appearance_extras" -> "settings/appearance/extras"
            "aod" -> "settings/appearance/aod_customized"
            "navigation_bar" -> "settings/appearance/navigation_bar"
            "playback" -> "settings/player"
            "canvas" -> "settings/canvas"
            "jiosaavn" -> "settings/jiosaavn"
            "deezer" -> "settings/deezer"
            "sources" -> "settings/sources"
            "lyrics" -> "settings/lyrics"
            "lyrics_providers" -> "settings/lyrics/providers"
            "lyrics_romanisation" -> "settings/lyrics/romanisation"
            "content" -> "settings/content"
            "language_packs" -> "settings/language_packs"
            "behavior" -> "settings/privacy"
            "android_auto" -> "settings/android_auto"
            "integration" -> "settings/integration"
            "discord" -> "settings/discord"
            "discord_experimental" -> "settings/discord/experimental"
            "tidal" -> "settings/tidal"
            "qobuz" -> "settings/qobuz"
            "telegram" -> "settings/telegram"
            "ai_integration" -> "settings/ai_integration"
            "internet" -> "settings/internet"
            "po_token" -> PO_TOKEN_ROUTE
            "storage" -> "settings/storage"
            "downloads" -> "settings/downloads"
            "backup_restore" -> "settings/backup_restore"
            "developer_options" -> "settings/misc"
            "updates" -> "settings/update"
            "about" -> "settings/about"
            else -> return null
        }

    // Only these destinations declare the optional scrollTo={scrollTo} nav argument;
    // appending it to any other route would crash NavController.navigate.
    val supportsScroll =
        parentKey in
            setOf(
                "appearance_extras",
                "lyrics_providers",
                "lyrics_romanisation",
                "downloads",
            )
    return if (!supportsScroll || scrollKey.isNullOrBlank()) route else "$route?scrollTo=$scrollKey"
}
