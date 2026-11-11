/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiModelOption
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.MascotAiApiKeyKey
import moe.rukamori.archivetune.constants.MascotAiCustomEndpointKey
import moe.rukamori.archivetune.constants.MascotAiEnabledKey
import moe.rukamori.archivetune.constants.MascotAiModelKey
import moe.rukamori.archivetune.constants.MascotAiProviderKey
import moe.rukamori.archivetune.constants.MascotAiSeparateApiKey
import moe.rukamori.archivetune.constants.MascotCharacterKey
import moe.rukamori.archivetune.constants.MascotChatterFrequencyKey
import moe.rukamori.archivetune.constants.MascotEnabledKey
import moe.rukamori.archivetune.constants.MascotPlatformPhysicsKey
import moe.rukamori.archivetune.constants.MascotScaleKey
import moe.rukamori.archivetune.constants.MascotSpeechEnabledKey
import moe.rukamori.archivetune.constants.MascotUiReactionsKey
import moe.rukamori.archivetune.constants.MascotWanderEnabledKey
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SettingsPageTopBar
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.mascot.ClaudieMascot
import moe.rukamori.archivetune.ui.mascot.MascotAiCommentator
import moe.rukamori.archivetune.ui.mascot.MascotEvent
import moe.rukamori.archivetune.ui.mascot.MascotRegistry
import moe.rukamori.archivetune.ui.mascot.MascotState
import moe.rukamori.archivetune.ui.screens.ScreenHeaderHaze
import moe.rukamori.archivetune.ui.screens.rememberScreenHeaderHaze
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun MascotSettings(navController: NavController) {
    val (mascotEnabled, onMascotEnabledChange) =
        rememberPreference(MascotEnabledKey, defaultValue = false)
    val (mascotSpeechEnabled, onMascotSpeechEnabledChange) =
        rememberPreference(MascotSpeechEnabledKey, defaultValue = true)
    val (mascotScale, onMascotScaleChange) =
        rememberPreference(MascotScaleKey, defaultValue = 1.0f)
    val (mascotCharacterId) =
        rememberPreference(MascotCharacterKey, defaultValue = "claudie")
    val (mascotWanderEnabled, onMascotWanderEnabledChange) =
        rememberPreference(MascotWanderEnabledKey, defaultValue = true)
    val (mascotUiReactions, onMascotUiReactionsChange) =
        rememberPreference(MascotUiReactionsKey, defaultValue = true)
    val (mascotPlatformPhysics, onMascotPlatformPhysicsChange) =
        rememberPreference(MascotPlatformPhysicsKey, defaultValue = true)
    val (mascotChatterFrequency, onMascotChatterFrequencyChange) =
        rememberPreference(MascotChatterFrequencyKey, defaultValue = 1)
    val (mascotAiEnabled, onMascotAiEnabledChange) =
        rememberPreference(MascotAiEnabledKey, defaultValue = false)
    val (mascotAiSeparateApi, onMascotAiSeparateApiChange) =
        rememberPreference(MascotAiSeparateApiKey, defaultValue = false)
    val (mascotAiProvider, onMascotAiProviderChange) =
        rememberEnumPreference(MascotAiProviderKey, defaultValue = AiProvider.NONE)
    val (mascotAiApiKey, onMascotAiApiKeyChange) =
        rememberPreference(MascotAiApiKeyKey, defaultValue = "")
    val (mascotAiCustomEndpoint, onMascotAiCustomEndpointChange) =
        rememberPreference(MascotAiCustomEndpointKey, defaultValue = "")
    val (mascotAiModel, onMascotAiModelChange) =
        rememberPreference(MascotAiModelKey, defaultValue = "")

    var editingField by remember { mutableStateOf<String?>(null) }
    var editorText by remember { mutableStateOf("") }
    var fetchedModels by remember { mutableStateOf<List<AiModelOption>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }

    val character = remember(mascotCharacterId) {
        MascotRegistry.getCharacter(mascotCharacterId)
    }

    val headerHaze = rememberScreenHeaderHaze()
    val systemBarsTopPadding = LocalStableSystemBarsTopPadding.current
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    if (editingField != null) {
        val isPassword = editingField == "api_key"
        val dialogTitle =
            when (editingField) {
                "api_key" -> "API-ключ для Клодика"
                "endpoint" -> "Кастомный Endpoint Клодика"
                else -> "Модель ИИ для Клодика"
            }
        DefaultDialog(
            onDismiss = { editingField = null },
            icon = { Icon(painterResource(R.drawable.auto_awesome), contentDescription = null) },
            title = { Text(dialogTitle) },
            buttons = {
                TextButton(onClick = { editingField = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        when (editingField) {
                            "api_key" -> onMascotAiApiKeyChange(editorText.trim())
                            "endpoint" -> onMascotAiCustomEndpointChange(editorText.trim())
                            "model" -> onMascotAiModelChange(editorText.trim())
                        }
                        editingField = null
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
        ) {
            OutlinedTextField(
                value = editorText,
                onValueChange = { editorText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                label = { Text(dialogTitle) },
                supportingText =
                    if (editingField == "model") {
                        { Text("Например: gpt-4o-mini, gemini-2.0-flash-lite, mistral-small-latest") }
                    } else {
                        null
                    },
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsPageTopBar(
                titleText = stringResource(R.string.mascot_settings_title),
                onBack = navController::navigateUp,
                onBackLongClick = navController::backToMain,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val playerAwareBottomPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom)
                    .asPaddingValues()
                    .calculateBottomPadding()
            val topPadding = innerPadding.calculateTopPadding()
            val scrollState = rememberScrollState()

            Column(
                Modifier
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal,
                        ),
                    )
                    .verticalScroll(scrollState)
                    .hazeSource(headerHaze)
                    .padding(top = topPadding)
                    .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
            ) {
                // Interactive Mascot Preview Card
                MascotPreviewCard(
                    enabled = mascotEnabled,
                    scale = mascotScale,
                    character = character,
                    onTestSpeech = {
                        scope.launch {
                            val currentMeta = playerConnection?.mediaMetadata?.value
                            val aiLine =
                                if (mascotAiEnabled) {
                                    MascotAiCommentator.generateLiveLineOrNull(
                                        context = context,
                                        playerConnection = playerConnection,
                                        event = MascotEvent.TapMascot,
                                        currentTrackTitle = currentMeta?.title,
                                        currentTrackArtist = currentMeta?.artists?.joinToString { it.name },
                                        bypassCooldown = true,
                                    )
                                } else {
                                    null
                                }
                            val testLine = aiLine ?: character.getRandomLine(MascotEvent.TapMascot)
                            Toast.makeText(context, testLine, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )

                Spacer(Modifier.height(8.dp))

                PreferenceGroup(title = stringResource(R.string.mascot_settings_title)) {
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.mascot_enabled_title)) },
                            description = stringResource(R.string.mascot_enabled_desc),
                            icon = { Icon(painterResource(R.drawable.ic_mascot), null) },
                            checked = mascotEnabled,
                            onCheckedChange = onMascotEnabledChange,
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.mascot_speech_title)) },
                            description = stringResource(R.string.mascot_speech_desc),
                            icon = { Icon(painterResource(R.drawable.ic_mascot_speech), null) },
                            checked = mascotSpeechEnabled,
                            onCheckedChange = onMascotSpeechEnabledChange,
                            isEnabled = mascotEnabled,
                        )
                    }

                    item {
                        val chatterDescription = when (mascotChatterFrequency) {
                            0 -> stringResource(R.string.mascot_chatter_quiet)
                            2 -> stringResource(R.string.mascot_chatter_talkative)
                            else -> stringResource(R.string.mascot_chatter_normal)
                        }

                        PreferenceEntry(
                            title = { Text(stringResource(R.string.mascot_chatter_title)) },
                            description = chatterDescription,
                            icon = { Icon(painterResource(R.drawable.chat_msg), null) },
                            isEnabled = mascotEnabled && mascotSpeechEnabled,
                            onClick = {
                                val next = (mascotChatterFrequency + 1) % 3
                                onMascotChatterFrequencyChange(next)
                            },
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.mascot_wander_title)) },
                            description = stringResource(R.string.mascot_wander_desc),
                            icon = { Icon(painterResource(R.drawable.explore_outlined), null) },
                            checked = mascotWanderEnabled,
                            onCheckedChange = onMascotWanderEnabledChange,
                            isEnabled = mascotEnabled,
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.mascot_ui_reactions_title)) },
                            description = stringResource(R.string.mascot_ui_reactions_desc),
                            icon = { Icon(painterResource(R.drawable.discover_tune), null) },
                            checked = mascotUiReactions,
                            onCheckedChange = onMascotUiReactionsChange,
                            isEnabled = mascotEnabled,
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text("Физика платформ интерфейса") },
                            description = "Клодик ходит и прыгает строго по панелям, кнопкам и мини-плееру",
                            icon = { Icon(painterResource(R.drawable.grid_view), null) },
                            checked = mascotPlatformPhysics,
                            onCheckedChange = onMascotPlatformPhysicsChange,
                            isEnabled = mascotEnabled,
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.mascot_character_title)) },
                            description = character.displayName,
                            icon = { Icon(painterResource(R.drawable.person), null) },
                            isEnabled = mascotEnabled,
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "${character.displayName}: ${character.description}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }

                    item {
                        var localScale by remember(mascotScale) { mutableFloatStateOf(mascotScale) }

                        PreferenceEntry(
                            title = { Text(stringResource(R.string.mascot_size_title)) },
                            description = "${(localScale * 100).toInt()}%",
                            icon = { Icon(painterResource(R.drawable.tune), null) },
                            isEnabled = mascotEnabled,
                            content = {
                                Spacer(Modifier.height(8.dp))
                                Slider(
                                    value = localScale,
                                    onValueChange = { localScale = it },
                                    onValueChangeFinished = { onMascotScaleChange(localScale) },
                                    valueRange = 0.6f..1.6f,
                                    steps = 9,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = mascotEnabled,
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                PreferenceGroup(title = "ИИ-мозги Клодика (приоритет у музыки)") {
                    item {
                        SwitchPreference(
                            title = { Text("Живые ИИ-реплики Клодика") },
                            description = "Клодик комментирует играющий трек и действия через ИИ, только когда аудио уже забуферизовано",
                            icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                            checked = mascotAiEnabled,
                            onCheckedChange = onMascotAiEnabledChange,
                            isEnabled = mascotEnabled && mascotSpeechEnabled,
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text("Модель ИИ для Клодика (быстрая/легкая)") },
                            description =
                                mascotAiModel.ifBlank {
                                    "Авто (быстрая модель провайдера: gpt-4o-mini / gemini-2.0-flash-lite)"
                                },
                            icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                            isEnabled = mascotEnabled && mascotAiEnabled,
                            onClick = {
                                editorText = mascotAiModel
                                editingField = "model"
                            },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(if (isFetchingModels) "Загрузка списка моделей…" else "Загрузить модели из API") },
                            description =
                                if (fetchedModels.isNotEmpty()) {
                                    "Найдено моделей: ${fetchedModels.size} (выберите ниже)"
                                } else {
                                    "Подтянуть доступные модели от вашего провайдера"
                                },
                            icon = { Icon(painterResource(R.drawable.sync), null) },
                            isEnabled = mascotEnabled && mascotAiEnabled && !isFetchingModels,
                            onClick = {
                                scope.launch {
                                    isFetchingModels = true
                                    fetchedModels = MascotAiCommentator.fetchAvailableModels(context)
                                    isFetchingModels = false
                                    if (fetchedModels.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Не удалось получить список моделей — проверьте API-ключ",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                        )
                    }

                    if (fetchedModels.isNotEmpty()) {
                        item {
                            ListPreference(
                                title = { Text("Выбрать модель Клодика из списка") },
                                description = mascotAiModel.ifBlank { "Не выбрана" },
                                icon = { Icon(painterResource(R.drawable.tune), null) },
                                selectedValue = mascotAiModel.ifBlank { fetchedModels.first().id },
                                values = fetchedModels.map { it.id },
                                valueText = { id -> fetchedModels.firstOrNull { it.id == id }?.displayName ?: id },
                                onValueSelected = onMascotAiModelChange,
                                isEnabled = mascotEnabled && mascotAiEnabled,
                            )
                        }
                    }

                    item {
                        SwitchPreference(
                            title = { Text("Отдельный API-ключ / провайдер для Клодика") },
                            description = "Если выключено, используется основной API-ключ из настроек ИИ (но со своей быстрой моделью)",
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            checked = mascotAiSeparateApi,
                            onCheckedChange = onMascotAiSeparateApiChange,
                            isEnabled = mascotEnabled && mascotAiEnabled,
                        )
                    }

                    if (mascotAiSeparateApi) {
                        item {
                            val providers =
                                listOf(
                                    AiProvider.CHATGPT,
                                    AiProvider.GEMINI,
                                    AiProvider.OPENROUTER,
                                    AiProvider.MISTRAL,
                                    AiProvider.CUSTOM,
                                    AiProvider.NONE,
                                )
                            ListPreference(
                                title = { Text("Провайдер ИИ Клодика") },
                                description = mascotAiProvider.name,
                                icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                                selectedValue = mascotAiProvider,
                                values = providers,
                                valueText = { it.name },
                                onValueSelected = onMascotAiProviderChange,
                                isEnabled = mascotEnabled && mascotAiEnabled,
                            )
                        }

                        if (mascotAiProvider == AiProvider.CUSTOM) {
                            item {
                                PreferenceEntry(
                                    title = { Text("Custom Endpoint Клодика") },
                                    description = mascotAiCustomEndpoint.ifBlank { "Не задан" },
                                    icon = { Icon(painterResource(R.drawable.website), null) },
                                    isEnabled = mascotEnabled && mascotAiEnabled,
                                    onClick = {
                                        editorText = mascotAiCustomEndpoint
                                        editingField = "endpoint"
                                    },
                                )
                            }
                        }

                        item {
                            PreferenceEntry(
                                title = { Text("API-ключ Клодика") },
                                description = if (mascotAiApiKey.isBlank()) "Не задан" else "Настроен",
                                icon = { Icon(painterResource(R.drawable.token), null) },
                                isEnabled = mascotEnabled && mascotAiEnabled && mascotAiProvider != AiProvider.NONE,
                                onClick = {
                                    editorText = mascotAiApiKey
                                    editingField = "api_key"
                                },
                            )
                        }
                    }
                }
            }

            ScreenHeaderHaze(
                hazeState = headerHaze,
                systemBarsTopPadding = systemBarsTopPadding,
            )
        }
    }
}

@Composable
private fun MascotPreviewCard(
    enabled: Boolean,
    scale: Float,
    character: moe.rukamori.archivetune.ui.mascot.MascotCharacter,
    onTestSpeech: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animAsset = remember(enabled) {
        if (enabled) character.getAnimationAsset(MascotState.DANCE) else character.getAnimationAsset(MascotState.IDLE)
    }
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(animAsset),
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true,
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = character.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = character.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Lottie Animation Preview
            Box(
                modifier =
                    Modifier
                        .size((140 * scale.coerceIn(0.8f, 1.4f)).dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (composition != null) {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_mascot),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            FilledTonalButton(
                onClick = onTestSpeech,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painterResource(R.drawable.ic_mascot_speech),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.mascot_test_speech))
            }
        }
    }
}
