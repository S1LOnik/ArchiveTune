/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GithubAutoUpdateKey
import moe.rukamori.archivetune.updates.GithubAutoUpdateWorker
import moe.rukamori.archivetune.updates.GithubUpdateChecker
import moe.rukamori.archivetune.utils.AppUpdateInstaller
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    navController: NavController,
    onUpToDate: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (autoUpdateEnabled, onAutoUpdateEnabledChange) =
        rememberPreference(GithubAutoUpdateKey, defaultValue = false)
    var latestRelease by remember { mutableStateOf<GithubUpdateChecker.LatestRelease?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.updates),
                        fontWeight = FontWeight.Bold,
                    )
                },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.github),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = "Релизы и обновления",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = "Все официальные релизы ArchiveTune, APK-файлы с автосборкой и подробные списки изменений публикуются на GitHub:",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            text = "github.com/S1LOnik/ArchiveTune",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }

                    Button(
                        onClick = { uriHandler.openUri("https://github.com/S1LOnik/ArchiveTune/releases") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ButtonDefaults.shape,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.github),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "Релизы на GitHub")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Установленная версия: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Автообновление через GitHub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Скачивать новые релизы по Wi-Fi и присылать уведомление для установки",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = {
                                onAutoUpdateEnabledChange(it)
                                GithubAutoUpdateWorker.apply(context, it)
                            },
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isBusy = true
                                statusText = "Проверяем обновления…"
                                val latest = GithubUpdateChecker.fetchLatest()
                                latestRelease = null
                                statusText =
                                    when {
                                        latest == null -> "Не удалось проверить обновления"

                                        GithubUpdateChecker.isNewer(latest.version, BuildConfig.VERSION_NAME) -> {
                                            latestRelease = latest
                                            "Доступна версия ${latest.version}"
                                        }

                                        else -> "Установлена последняя версия"
                                    }
                                isBusy = false
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ButtonDefaults.shape,
                    ) {
                        Text(text = "Проверить обновления")
                    }

                    latestRelease?.let { release ->
                        Button(
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    val result =
                                        AppUpdateInstaller.downloadAndInstall(context, release.apkUrl) { progress ->
                                            val percent = progress.fraction?.let { (it * 100).toInt() }
                                            statusText = "Загрузка${percent?.let { " $it%" }.orEmpty()}…"
                                        }
                                    statusText =
                                        if (result.isSuccess) {
                                            "Откроется установщик Android"
                                        } else {
                                            "Не удалось скачать обновление"
                                        }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = ButtonDefaults.shape,
                        ) {
                            Text(text = "Скачать и установить ${release.version}")
                        }
                    }

                    statusText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
