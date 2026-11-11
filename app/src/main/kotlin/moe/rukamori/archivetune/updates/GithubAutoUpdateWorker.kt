package moe.rukamori.archivetune.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GithubAutoUpdateKey
import moe.rukamori.archivetune.constants.GithubAutoUpdateLastVersionKey
import moe.rukamori.archivetune.utils.AppUpdateInstaller
import moe.rukamori.archivetune.utils.dataStore
import java.util.concurrent.TimeUnit

/**
 * Downloads a newer GitHub release in the background (Wi-Fi only) and posts a notification;
 * tapping it opens the system installer.
 */
class GithubAutoUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.dataStore.data.first()
        if (preferences[GithubAutoUpdateKey] != true) return Result.success()

        val latest = GithubUpdateChecker.fetchLatest() ?: return Result.retry()
        if (!GithubUpdateChecker.isNewer(latest.version, BuildConfig.VERSION_NAME)) return Result.success()
        if (preferences[GithubAutoUpdateLastVersionKey] == latest.version) return Result.success()

        val download = AppUpdateInstaller.download(applicationContext, latest.apkUrl) { }
        val apkFile = download.getOrNull()
        if (apkFile == null || !apkFile.isFile) return Result.retry()

        applicationContext.dataStore.edit { it[GithubAutoUpdateLastVersionKey] = latest.version }
        showReadyNotification(applicationContext, latest.version, apkFile)
        return Result.success()
    }

    private fun showReadyNotification(
        context: Context,
        version: String,
        apkFile: java.io.File,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Обновления ArchiveTune",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle("Обновление ArchiveTune $version готово")
                .setContentText("Нажмите, чтобы установить")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(AppUpdateInstaller.installPendingIntent(context, apkFile))
                .setAutoCancel(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    companion object {
        private const val CHANNEL_ID = "github_auto_update_channel"
        private const val NOTIFICATION_ID = 9998
        private const val PERIODIC_WORK_NAME = "github_auto_update_work"
        private const val IMMEDIATE_WORK_NAME = "github_auto_update_now"

        fun apply(
            context: Context,
            enabled: Boolean,
        ) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                return
            }
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GithubAutoUpdateWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GithubAutoUpdateWorker>()
                    .setConstraints(constraints)
                    .build(),
            )
        }
    }
}
