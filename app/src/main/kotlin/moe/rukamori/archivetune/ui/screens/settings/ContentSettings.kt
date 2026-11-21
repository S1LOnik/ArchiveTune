/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.aicontentfilter.DefaultAiMusicAuthors
import moe.rukamori.archivetune.aicontentfilter.authorFilterKey
import moe.rukamori.archivetune.aicontentfilter.formatAiAuthorChipLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.component.EditTextPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.setAppLocale
import java.util.Locale

@Composable
fun ContentSettings(
    navController: NavController,
    scrollTo: String? = null,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val preferencePositions = rememberPreferencePositions()
    androidx.compose.runtime.LaunchedEffect(scrollTo) {
        preferencePositions.scrollToKey(scrollTo, scrollState)
    }
    val (useSystemLanguage, onUseSystemLanguageChange) = rememberPreference(key = UseSystemLanguageKey, defaultValue = true)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (playlistSuggestionSource, onPlaylistSuggestionSourceChange) =
        rememberEnumPreference(
            key = PlaylistSuggestionSourceKey,
            defaultValue = PlaylistSuggestionSource.BOTH,
        )
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideo, onHideVideoChange) = rememberPreference(key = HideVideoKey, defaultValue = false)
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .then(preferencePositions.containerModifier())
            .verticalScroll(scrollState)
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        PreferenceGroup(title = stringResource(R.string.general)) {
            item {
                ListPreference(
                    title = { Text(stringResource(R.string.content_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    selectedValue = contentLanguage,
                    values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                    valueText = {
                        LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()
                        val languageTag = locale.toLanguageTag().replace("-Hant", "")

                        YouTube.locale =
                            YouTube.locale.copy(
                                hl =
                                    newValue.takeIf { it != SYSTEM_DEFAULT }
                                        ?: locale.language.takeIf { it in LanguageCodeToName }
                                        ?: languageTag.takeIf { it in LanguageCodeToName }
                                        ?: "en",
                            )

                        onContentLanguageChange(newValue)
                    },
                )
            }

            item {
                ListPreference(
                    title = { Text(stringResource(R.string.content_country)) },
                    icon = { Icon(painterResource(R.drawable.location_on), null) },
                    selectedValue = contentCountry,
                    values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                    valueText = {
                        CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()

                        YouTube.locale =
                            YouTube.locale.copy(
                                gl =
                                    newValue.takeIf { it != SYSTEM_DEFAULT }
                                        ?: locale.country.takeIf { it in CountryCodeToName }
                                        ?: "US",
                            )

                        onContentCountryChange(newValue)
                    },
                )
            }

            item {
                ListPreference(
                    title = { Text(stringResource(R.string.you_might_like_source)) },
                    icon = { Icon(painterResource(R.drawable.playlist_play), null) },
                    selectedValue = playlistSuggestionSource,
                    values =
                        listOf(
                            PlaylistSuggestionSource.PLAYLIST_TITLE,
                            PlaylistSuggestionSource.PLAYLIST_CONTENT,
                            PlaylistSuggestionSource.BOTH,
                        ),
                    valueText = {
                        when (it) {
                            PlaylistSuggestionSource.PLAYLIST_TITLE -> stringResource(R.string.playlist_suggestion_source_title)
                            PlaylistSuggestionSource.PLAYLIST_CONTENT -> stringResource(R.string.playlist_suggestion_source_content)
                            PlaylistSuggestionSource.BOTH -> stringResource(R.string.playlist_suggestion_source_both)
                        }
                    },
                    onValueSelected = onPlaylistSuggestionSourceChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    icon = { Icon(painterResource(R.drawable.explicit), null) },
                    checked = hideExplicit,
                    onCheckedChange = onHideExplicitChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.hide_video)) },
                    icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
                    checked = hideVideo,
                    onCheckedChange = onHideVideoChange,
                )
            }
        }

        AiContentFilterPreferences(preferencePositions)

        PreferenceGroup(
            title = stringResource(R.string.app_language),
            modifier = preferencePositions.modifierFor("app_language"),
        ) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.use_system_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    checked = useSystemLanguage,
                    onCheckedChange = { checked ->
                        onUseSystemLanguageChange(checked)
                        val newLocale = if (checked) Locale.getDefault() else Locale.ENGLISH
                        setAppLocale(context, newLocale)
                        (context as? android.app.Activity)?.recreate()
                    },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.misc)) {
            item {
                EditTextPreference(
                    title = { Text(stringResource(R.string.top_length)) },
                    icon = { Icon(painterResource(R.drawable.trending_up), null) },
                    value = lengthTop,
                    isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                    onValueChange = onLengthTopChange,
                )
            }

            item {
                ListPreference(
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                    selectedValue = quickPicks,
                    values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN, QuickPicks.DONT_SHOW),
                    valueText = {
                        when (it) {
                            QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                            QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                            QuickPicks.DONT_SHOW -> stringResource(R.string.dont_show)
                        }
                    },
                    onValueSelected = onQuickPicksChange,
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )

}

@Composable
private fun AiContentFilterPreferences(preferencePositions: PreferencePositions) {
    val (enabled, setEnabled) = rememberPreference(AiContentFilterEnabledKey, false)
    val (authors, setAuthors) = rememberPreference(AiMusicAuthorsKey, DefaultAiMusicAuthors)
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    editor?.let { draft ->
        AlertDialog(
            onDismissRequest = { editor = null },
            title = { Text(stringResource(R.string.ai_music_authors)) },
            text = {
                Column {
                    Text(stringResource(R.string.ai_music_authors_help))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { editor = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 7,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    setAuthors(draft.lineSequence().map(String::trim).filter(String::isNotEmpty)
                        .distinctBy(::authorFilterKey).toSet())
                    editor = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editor = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    PreferenceGroup(title = stringResource(R.string.ai_content_filter)) {
        item {
            androidx.compose.foundation.layout.Box(
                modifier = preferencePositions.modifierFor("ai_content_filter"),
            ) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.ai_music_filter_enable)) },
                    description = stringResource(R.string.ai_music_filter_summary),
                    icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                    checked = enabled,
                    onCheckedChange = setEnabled,
                )
            }
        }
        item {
            androidx.compose.foundation.layout.Box(
                modifier = preferencePositions.modifierFor("ai_music_authors"),
            ) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_music_authors)) },
                    description = stringResource(R.string.ai_music_authors_count, authors.size),
                    icon = { Icon(painterResource(R.drawable.person), null) },
                    onClick = { editor = authors.sorted().joinToString("\n") },
                )
            }
        }
        if (authors.isNotEmpty()) {
            item {
                val sortedAuthors = remember(authors) { authors.sorted() }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sortedAuthors.forEach { entry ->
                        InputChip(
                            selected = true,
                            onClick = { setAuthors(authors - entry) },
                            label = {
                                Text(
                                    text = formatAiAuthorChipLabel(entry),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = stringResource(R.string.remove_from_ai_filter),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
