/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiModelOption
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiRecommendationsApiKeyKey
import moe.rukamori.archivetune.constants.AiRecommendationsCustomEndpointKey
import moe.rukamori.archivetune.constants.AiRecommendationsModelKey
import moe.rukamori.archivetune.constants.AiRecommendationsProviderKey
import moe.rukamori.archivetune.constants.AiRecommendationsSeparateApiKey
import moe.rukamori.archivetune.constants.MascotAiApiKeyKey
import moe.rukamori.archivetune.constants.MascotAiCustomEndpointKey
import moe.rukamori.archivetune.constants.MascotAiEnabledKey
import moe.rukamori.archivetune.constants.MascotAiModelKey
import moe.rukamori.archivetune.constants.MascotAiProviderKey
import moe.rukamori.archivetune.constants.MascotAiSeparateApiKey
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.AiIntegrationSettingsScreenState
import moe.rukamori.archivetune.viewmodels.AiIntegrationSettingsUiModel
import moe.rukamori.archivetune.viewmodels.AiIntegrationSettingsViewModel
import moe.rukamori.archivetune.viewmodels.AiSettingsEditorField
import moe.rukamori.archivetune.viewmodels.AiSettingsEditorUiModel

private enum class TestApiVisualState { Idle, Testing, Success, Failed }

private val AiProviderOptions =
    ImmutableList.of(
        AiProvider.GEMINI,
        AiProvider.CHATGPT,
        AiProvider.OPENROUTER,
        AiProvider.CUSTOM,
        AiProvider.NONE,
    )

@Composable
fun AiIntegrationSettings(
    navController: NavController,
    viewModel: AiIntegrationSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val screenState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { messageResId ->
            Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show()
        }
    }

    when (val state = screenState) {
        AiIntegrationSettingsScreenState.Loading -> AiIntegrationLoadingState()
        AiIntegrationSettingsScreenState.Empty -> Unit
        is AiIntegrationSettingsScreenState.Error -> AiIntegrationErrorState(state.messageResId)
        is AiIntegrationSettingsScreenState.Success -> {
            AiIntegrationSettingsContent(
                model = state.model,
                onProviderSelected = viewModel::selectProvider,
                onOpenEditor = viewModel::openEditor,
                onEditorValueChange = viewModel::updateEditorValue,
                onDismissEditor = viewModel::dismissEditor,
                onSaveEditor = viewModel::saveEditor,
                onOpenModelPicker = viewModel::openModelPicker,
                onDismissModelPicker = viewModel::dismissModelPicker,
                onModelSearchQueryChange = viewModel::updateModelSearchQuery,
                onModelSelected = viewModel::selectModel,
                onFetchModels = viewModel::fetchModels,
                onTestApi = viewModel::testApi,
                onDismissApiTestError = viewModel::dismissApiTestError,
            )
        }
    }

    AiIntegrationTopAppBar(navController)
}

@Composable
private fun AiIntegrationSettingsContent(
    model: AiIntegrationSettingsUiModel,
    onProviderSelected: (AiProvider) -> Unit,
    onOpenEditor: (AiSettingsEditorField) -> Unit,
    onEditorValueChange: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onSaveEditor: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onDismissModelPicker: () -> Unit,
    onModelSearchQueryChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onFetchModels: () -> Unit,
    onTestApi: () -> Unit,
    onDismissApiTestError: () -> Unit,
) {
    model.apiTestError?.let { details ->
        AiApiTestErrorDialog(
            details = details,
            onClose = onDismissApiTestError,
        )
    }

    if (model.editor.visible) {
        AiSettingsEditorDialog(
            editor = model.editor,
            onValueChange = onEditorValueChange,
            onDismiss = onDismissEditor,
            onSave = onSaveEditor,
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        PreferenceGroup(title = stringResource(R.string.ai_provider_settings)) {
            item {
                ListPreference(
                    title = { Text(stringResource(R.string.ai_provider)) },
                    description = stringResource(R.string.ai_provider_desc),
                    icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                    selectedValue = model.provider,
                    values = AiProviderOptions,
                    valueText = { it.label() },
                    onValueSelected = onProviderSelected,
                )
            }

            item(visible = model.provider == AiProvider.CUSTOM) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_custom_endpoint)) },
                    description = model.customEndpoint,
                    icon = { Icon(painterResource(R.drawable.website), null) },
                    onClick = { onOpenEditor(AiSettingsEditorField.CUSTOM_ENDPOINT) },
                )
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_api_key)) },
                    description =
                        if (model.apiKey.isBlank()) {
                            stringResource(R.string.ai_api_key_missing)
                        } else {
                            stringResource(R.string.ai_api_key_configured)
                        },
                    icon = { Icon(painterResource(R.drawable.token), null) },
                    onClick = { onOpenEditor(AiSettingsEditorField.API_KEY) },
                    isEnabled = model.provider != AiProvider.NONE,
                )
            }

            item(visible = model.provider != AiProvider.NONE && model.provider != AiProvider.CUSTOM) {
                ModelPickerPreference(
                    selectedModel = model.selectedModel,
                    availableModels = model.availableModels,
                    filteredModels = model.modelPicker.filteredModels,
                    searchQuery = model.modelPicker.searchQuery,
                    showSheet = model.modelPicker.visible,
                    isFetching = model.isFetchingModels,
                    isEnabled = model.canUseModelPicker,
                    canFetch = model.canFetchModels,
                    onShowSheet = onOpenModelPicker,
                    onDismissSheet = onDismissModelPicker,
                    onSearchQueryChange = onModelSearchQueryChange,
                    onModelSelected = onModelSelected,
                    onFetch = onFetchModels,
                )
            }

            item(visible = model.provider == AiProvider.CUSTOM) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_model)) },
                    description = model.customModel,
                    icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                    onClick = { onOpenEditor(AiSettingsEditorField.CUSTOM_MODEL) },
                )
            }

            item {
                val testVisualState =
                    when {
                        model.isTesting -> TestApiVisualState.Testing
                        model.validationStatus == AiApiValidationStatus.SUCCESS -> TestApiVisualState.Success
                        model.validationStatus == AiApiValidationStatus.FAILED -> TestApiVisualState.Failed
                        else -> TestApiVisualState.Idle
                    }
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_test_api)) },
                    icon = {
                        AnimatedContent(
                            targetState = testVisualState,
                            transitionSpec = {
                                (
                                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) +
                                        fadeIn(tween(200))
                                ) togetherWith
                                    (scaleOut(tween(100)) + fadeOut(tween(100)))
                            },
                            label = "testApiIcon",
                        ) { state ->
                            when (state) {
                                TestApiVisualState.Success -> {
                                    Icon(painterResource(R.drawable.done), null)
                                }

                                TestApiVisualState.Failed -> {
                                    Icon(painterResource(R.drawable.error), null, tint = MaterialTheme.colorScheme.error)
                                }

                                else -> {
                                    Icon(painterResource(R.drawable.sync), null)
                                }
                            }
                        }
                    },
                    content = {
                        Spacer(Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = testVisualState,
                            transitionSpec = {
                                (slideInVertically { -it } + fadeIn(tween(250))) togetherWith
                                    (slideOutVertically { it } + fadeOut(tween(150)))
                            },
                            label = "testApiDesc",
                        ) { state ->
                            Text(
                                text =
                                    when (state) {
                                        TestApiVisualState.Testing -> stringResource(R.string.ai_api_testing)
                                        else -> model.validationStatus.label()
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    when (state) {
                                        TestApiVisualState.Success -> MaterialTheme.colorScheme.primary
                                        TestApiVisualState.Failed -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        model.errorMessage?.let { message ->
                            Spacer(Modifier.height(10.dp))
                            AiErrorHintRow(message = message)
                        }
                    },
                    trailingContent = {
                        AnimatedContent(
                            targetState = model.isTesting,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(200))) togetherWith
                                    (scaleOut(tween(150)) + fadeOut(tween(150)))
                            },
                            label = "testApiTrailing",
                        ) { isTesting ->
                            if (isTesting) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    },
                    onClick = onTestApi,
                    isEnabled = model.canTestApi,
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.ai_translation_settings)) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_custom_prompt)) },
                    description =
                        if (model.customPrompt.isBlank()) {
                            stringResource(R.string.ai_custom_prompt_desc)
                        } else {
                            stringResource(R.string.ai_api_key_configured)
                        },
                    icon = { Icon(painterResource(R.drawable.edit), null) },
                    onClick = { onOpenEditor(AiSettingsEditorField.CUSTOM_PROMPT) },
                )
            }
        }

        AiRecommendationsAndMascotSection(availableModels = model.availableModels)
    }
}

@Composable
private fun AiRecommendationsAndMascotSection(
    availableModels: ImmutableList<AiModelOption>,
) {
    val (recModel, onRecModelChange) = rememberPreference(AiRecommendationsModelKey, defaultValue = "")
    val (recSeparateApi, onRecSeparateApiChange) = rememberPreference(AiRecommendationsSeparateApiKey, defaultValue = false)
    val (recProvider, onRecProviderChange) = rememberEnumPreference(AiRecommendationsProviderKey, defaultValue = AiProvider.NONE)
    val (recApiKey, onRecApiKeyChange) = rememberPreference(AiRecommendationsApiKeyKey, defaultValue = "")
    val (recEndpoint, onRecEndpointChange) = rememberPreference(AiRecommendationsCustomEndpointKey, defaultValue = "")

    val (mascotAiEnabled, onMascotAiEnabledChange) = rememberPreference(MascotAiEnabledKey, defaultValue = false)
    val (mascotAiModel, onMascotAiModelChange) = rememberPreference(MascotAiModelKey, defaultValue = "")
    val (mascotSeparateApi, onMascotSeparateApiChange) = rememberPreference(MascotAiSeparateApiKey, defaultValue = false)
    val (mascotProvider, onMascotProviderChange) = rememberEnumPreference(MascotAiProviderKey, defaultValue = AiProvider.NONE)
    val (mascotApiKey, onMascotApiKeyChange) = rememberPreference(MascotAiApiKeyKey, defaultValue = "")
    val (mascotEndpoint, onMascotEndpointChange) = rememberPreference(MascotAiCustomEndpointKey, defaultValue = "")

    var dialogKey by remember { mutableStateOf<String?>(null) }
    var dialogValue by remember { mutableStateOf("") }

    if (dialogKey != null) {
        val isPassword = dialogKey?.endsWith("_key") == true
        val title =
            when (dialogKey) {
                "rec_model" -> "Модель ИИ для рекомендаций (умнее)"
                "rec_key" -> "API-ключ для рекомендаций"
                "rec_endpoint" -> "Custom Endpoint для рекомендаций"
                "mascot_model" -> "Модель ИИ для Клодика (быстрее/меньше)"
                "mascot_key" -> "API-ключ для Клодика"
                else -> "Custom Endpoint для Клодика"
            }
        DefaultDialog(
            onDismiss = { dialogKey = null },
            icon = { Icon(painterResource(R.drawable.auto_awesome), contentDescription = null) },
            title = { Text(title) },
            buttons = {
                TextButton(onClick = { dialogKey = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        val trimmed = dialogValue.trim()
                        when (dialogKey) {
                            "rec_model" -> onRecModelChange(trimmed)
                            "rec_key" -> onRecApiKeyChange(trimmed)
                            "rec_endpoint" -> onRecEndpointChange(trimmed)
                            "mascot_model" -> onMascotAiModelChange(trimmed)
                            "mascot_key" -> onMascotApiKeyChange(trimmed)
                            "mascot_endpoint" -> onMascotEndpointChange(trimmed)
                        }
                        dialogKey = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
        ) {
            OutlinedTextField(
                value = dialogValue,
                onValueChange = { dialogValue = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                label = { Text(title) },
            )
        }
    }

    PreferenceGroup(title = "ИИ-рекомендации и персональные миксы") {
        item {
            PreferenceEntry(
                title = { Text("Модель для рекомендаций (умная)") },
                description = recModel.ifBlank { "Использовать основную модель (или укажите более мощную, например gpt-4o / gemini-2.5-pro)" },
                icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                onClick = {
                    dialogValue = recModel
                    dialogKey = "rec_model"
                },
            )
        }

        if (availableModels.isNotEmpty()) {
            item {
                ListPreference(
                    title = { Text("Выбрать модель рекомендаций из списка API") },
                    description = recModel.ifBlank { "По умолчанию (основная)" },
                    icon = { Icon(painterResource(R.drawable.tune), null) },
                    selectedValue = recModel.ifBlank { availableModels.first().id },
                    values = availableModels.map { it.id },
                    valueText = { id -> availableModels.firstOrNull { it.id == id }?.displayName ?: id },
                    onValueSelected = onRecModelChange,
                )
            }
        }

        item {
            SwitchPreference(
                title = { Text("Отдельный API-ключ / провайдер для рекомендаций") },
                description = "Позволяет подключить другой сервис специально для генерации миксов и рекомендаций",
                icon = { Icon(painterResource(R.drawable.token), null) },
                checked = recSeparateApi,
                onCheckedChange = onRecSeparateApiChange,
            )
        }

        if (recSeparateApi) {
            item {
                ListPreference(
                    title = { Text("Провайдер рекомендаций") },
                    description = recProvider.label(),
                    icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                    selectedValue = recProvider,
                    values = AiProviderOptions,
                    valueText = { it.label() },
                    onValueSelected = onRecProviderChange,
                )
            }

            if (recProvider == AiProvider.CUSTOM) {
                item {
                    PreferenceEntry(
                        title = { Text("Custom Endpoint рекомендаций") },
                        description = recEndpoint.ifBlank { "Не задан" },
                        icon = { Icon(painterResource(R.drawable.website), null) },
                        onClick = {
                            dialogValue = recEndpoint
                            dialogKey = "rec_endpoint"
                        },
                    )
                }
            }

            item {
                PreferenceEntry(
                    title = { Text("API-ключ рекомендаций") },
                    description = if (recApiKey.isBlank()) stringResource(R.string.ai_api_key_missing) else stringResource(R.string.ai_api_key_configured),
                    icon = { Icon(painterResource(R.drawable.token), null) },
                    onClick = {
                        dialogValue = recApiKey
                        dialogKey = "rec_key"
                    },
                )
            }
        }
    }

    PreferenceGroup(title = "Клодик — живые ИИ-комментарии (приоритет у музыки)") {
        item {
            SwitchPreference(
                title = { Text("Подключить ИИ к Клодику") },
                description = "Клодик живо комментирует треки и действия, но ждёт буферизации аудио, чтобы не мешать загрузке музыки",
                icon = { Icon(painterResource(R.drawable.ic_mascot), null) },
                checked = mascotAiEnabled,
                onCheckedChange = onMascotAiEnabledChange,
            )
        }

        item {
            PreferenceEntry(
                title = { Text("Модель для Клодика (быстрая и лёгкая)") },
                description = mascotAiModel.ifBlank { "Авто (gpt-4o-mini / gemini-2.0-flash-lite / mistral-small)" },
                icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                isEnabled = mascotAiEnabled,
                onClick = {
                    dialogValue = mascotAiModel
                    dialogKey = "mascot_model"
                },
            )
        }

        if (availableModels.isNotEmpty()) {
            item {
                ListPreference(
                    title = { Text("Выбрать модель Клодика из списка API") },
                    description = mascotAiModel.ifBlank { "Авто (быстрая)" },
                    icon = { Icon(painterResource(R.drawable.tune), null) },
                    selectedValue = mascotAiModel.ifBlank { availableModels.first().id },
                    values = availableModels.map { it.id },
                    valueText = { id -> availableModels.firstOrNull { it.id == id }?.displayName ?: id },
                    onValueSelected = onMascotAiModelChange,
                    isEnabled = mascotAiEnabled,
                )
            }
        }

        item {
            SwitchPreference(
                title = { Text("Отдельный API-ключ / провайдер для Клодика") },
                description = "Если выключено, используется основной API-ключ выше, но с отдельной лёгкой моделью",
                icon = { Icon(painterResource(R.drawable.token), null) },
                checked = mascotSeparateApi,
                onCheckedChange = onMascotSeparateApiChange,
                isEnabled = mascotAiEnabled,
            )
        }

        if (mascotSeparateApi) {
            item {
                ListPreference(
                    title = { Text("Провайдер Клодика") },
                    description = mascotProvider.label(),
                    icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                    selectedValue = mascotProvider,
                    values = AiProviderOptions,
                    valueText = { it.label() },
                    onValueSelected = onMascotProviderChange,
                    isEnabled = mascotAiEnabled,
                )
            }

            if (mascotProvider == AiProvider.CUSTOM) {
                item {
                    PreferenceEntry(
                        title = { Text("Custom Endpoint Клодика") },
                        description = mascotEndpoint.ifBlank { "Не задан" },
                        icon = { Icon(painterResource(R.drawable.website), null) },
                        isEnabled = mascotAiEnabled,
                        onClick = {
                            dialogValue = mascotEndpoint
                            dialogKey = "mascot_endpoint"
                        },
                    )
                }
            }

            item {
                PreferenceEntry(
                    title = { Text("API-ключ Клодика") },
                    description = if (mascotApiKey.isBlank()) stringResource(R.string.ai_api_key_missing) else stringResource(R.string.ai_api_key_configured),
                    icon = { Icon(painterResource(R.drawable.token), null) },
                    isEnabled = mascotAiEnabled,
                    onClick = {
                        dialogValue = mascotApiKey
                        dialogKey = "mascot_key"
                    },
                )
            }
        }
    }
}

@Composable
private fun AiIntegrationTopAppBar(navController: NavController) {
    TopAppBar(
        title = { Text(stringResource(R.string.ai_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
        },
    )
}

@Composable
private fun AiApiTestErrorDialog(
    details: String,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val dialogModifier = remember { Modifier.widthIn(max = 760.dp).fillMaxWidth() }
    val detailsModifier = remember { Modifier.fillMaxWidth() }
    val textModifier =
        remember(scrollState) {
            Modifier.verticalScroll(scrollState).padding(16.dp)
        }

    DefaultDialog(
        onDismiss = onClose,
        modifier = dialogModifier,
        constrainContentHeight = true,
        icon = {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text(stringResource(R.string.ai_api_test_failed)) },
        buttons = {
            TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Surface(
            modifier = detailsModifier.weight(1f, fill = false),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SelectionContainer(modifier = textModifier) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun AiSettingsEditorDialog(
    editor: AiSettingsEditorUiModel,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val field = editor.field ?: return
    val isCustomPrompt = field == AiSettingsEditorField.CUSTOM_PROMPT
    val titleResId =
        when (field) {
            AiSettingsEditorField.API_KEY -> R.string.ai_api_key
            AiSettingsEditorField.CUSTOM_ENDPOINT -> R.string.ai_custom_endpoint
            AiSettingsEditorField.CUSTOM_MODEL -> R.string.ai_model
            AiSettingsEditorField.CUSTOM_PROMPT -> R.string.ai_custom_prompt
        }
    val iconResId =
        when (field) {
            AiSettingsEditorField.API_KEY -> R.drawable.token
            AiSettingsEditorField.CUSTOM_ENDPOINT -> R.drawable.website
            AiSettingsEditorField.CUSTOM_MODEL -> R.drawable.auto_awesome
            AiSettingsEditorField.CUSTOM_PROMPT -> R.drawable.edit
        }
    val visualTransformation =
        remember(field) {
            if (field == AiSettingsEditorField.API_KEY) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            }
        }
    val keyboardOptions =
        remember(field) {
            KeyboardOptions(
                capitalization =
                    if (isCustomPrompt) {
                        KeyboardCapitalization.Sentences
                    } else {
                        KeyboardCapitalization.None
                    },
                imeAction = if (isCustomPrompt) ImeAction.Default else ImeAction.Done,
            )
        }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(iconResId), contentDescription = null) },
        title = { Text(stringResource(titleResId)) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                enabled = editor.canSave,
                onClick = onSave,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
    ) {
        OutlinedTextField(
            value = editor.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = !isCustomPrompt,
            minLines = if (isCustomPrompt) 4 else 1,
            maxLines = if (isCustomPrompt) 8 else 1,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            label = { Text(stringResource(titleResId)) },
            supportingText =
                if (isCustomPrompt) {
                    { Text(stringResource(R.string.ai_custom_prompt_desc)) }
                } else {
                    null
                },
        )
    }
}

@Composable
private fun AiIntegrationLoadingState() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator()
    }
}

@Composable
private fun AiIntegrationErrorState(messageResId: Int) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AiProvider.label(): String =
    when (this) {
        AiProvider.CHATGPT -> stringResource(R.string.ai_provider_openai)
        AiProvider.GEMINI -> stringResource(R.string.ai_provider_gemini)
        AiProvider.OPENROUTER -> stringResource(R.string.ai_provider_openrouter)
        AiProvider.CUSTOM -> stringResource(R.string.custom)
        AiProvider.DEEPL -> "DeepL"
        AiProvider.MISTRAL -> "Mistral AI"
        AiProvider.NONE -> stringResource(R.string.ai_provider_none)
    }

@Composable
private fun AiApiValidationStatus.label(): String =
    when (this) {
        AiApiValidationStatus.UNKNOWN -> stringResource(R.string.ai_api_status_unknown)
        AiApiValidationStatus.SUCCESS -> stringResource(R.string.ai_api_status_success)
        AiApiValidationStatus.FAILED -> stringResource(R.string.ai_api_status_failed)
    }

@Composable
private fun AiErrorHintRow(message: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModelPickerPreference(
    selectedModel: String,
    availableModels: ImmutableList<AiModelOption>,
    filteredModels: ImmutableList<AiModelOption>,
    searchQuery: String,
    showSheet: Boolean,
    isFetching: Boolean,
    isEnabled: Boolean,
    canFetch: Boolean,
    onShowSheet: () -> Unit,
    onDismissSheet: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onFetch: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val description =
        when {
            isFetching && availableModels.isEmpty() -> stringResource(R.string.ai_model_loading)
            availableModels.isEmpty() && !canFetch -> stringResource(R.string.ai_model_api_key_required)
            availableModels.isEmpty() -> stringResource(R.string.ai_model_fetch_hint)
            selectedModel.isBlank() -> stringResource(R.string.ai_model_not_selected)
            else -> availableModels.firstOrNull { it.id == selectedModel }?.displayName ?: selectedModel
        }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = stringResource(R.string.ai_model),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .padding(horizontal = 26.dp)
                        .padding(top = 18.dp, bottom = 22.dp),
            )
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.ai_model_search)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                            )
                        },
                        trailingIcon =
                            if (searchQuery.isNotBlank()) {
                                {
                                    androidx.compose.material3.IconButton(
                                        onClick = { onSearchQueryChange("") },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.clear),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp)
                        .padding(bottom = 18.dp),
            ) {}
            LazyColumn(
                contentPadding = PaddingValues(start = 26.dp, end = 26.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
            ) {
                if (filteredModels.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = stringResource(R.string.ai_model_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                        )
                    }
                }
                items(
                    items = filteredModels,
                    key = { it.id },
                    contentType = { "model" },
                ) { model ->
                    val id = model.id
                    val displayName = model.displayName
                    val selected = id == selectedModel
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ).selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onModelSelected(id) },
                                ).padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (displayName != id) {
                                Text(
                                    text = id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    PreferenceEntry(
        title = { Text(stringResource(R.string.ai_model)) },
        description = description,
        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
        trailingContent = {
            if (isFetching) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                FilledTonalIconButton(
                    onClick = onFetch,
                    enabled = canFetch,
                ) {
                    Icon(
                        painterResource(R.drawable.sync),
                        contentDescription = stringResource(R.string.ai_fetch_models),
                    )
                }
            }
        },
        onClick = if (isEnabled && availableModels.isNotEmpty()) onShowSheet else null,
        isEnabled = isEnabled,
    )
}
