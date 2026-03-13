package com.nexopos.erp.core.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexopos.erp.R
import com.nexopos.erp.core.repo.MobileSyncRepository
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CatalogWarmupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val mobileSyncRepository: MobileSyncRepository by lazy {
        KoinJavaComponent.get(MobileSyncRepository::class.java)
    }
    private val notificationManager = NotificationManagerCompat.from(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createNotificationChannel()
            setForeground(createForegroundInfo(0, 0, indeterminate = true))

            mobileSyncRepository.forceFullRefresh { processed, total ->
                updateNotification(processed, total)
            }.getOrThrow()

            showCompletion()
            Result.success()
        } catch (exception: Exception) {
            showFailure()
            Result.failure()
        }
    }

    private fun updateNotification(processed: Int, total: Int) {
        val indeterminate = total == 0
        val contentText = if (indeterminate) {
            applicationContext.getString(R.string.catalog_warmup_title)
        } else {
            applicationContext.getString(R.string.catalog_warmup_progress, processed, total)
        }
        val notification = buildNotification(contentText, processed, total, indeterminate, ongoing = true)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletion() {
        val notification = buildNotification(
            applicationContext.getString(R.string.catalog_warmup_complete),
            processed = 0,
            total = 0,
            indeterminate = false,
            ongoing = false
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFailure() {
        val notification = buildNotification(
            applicationContext.getString(R.string.catalog_warmup_failed),
            processed = 0,
            total = 0,
            indeterminate = false,
            ongoing = false
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        contentText: String,
        processed: Int,
        total: Int,
        indeterminate: Boolean,
        ongoing: Boolean
    ): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_catalog_sync)
            .setContentTitle(applicationContext.getString(R.string.catalog_warmup_title))
            .setContentText(contentText)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(total, processed, indeterminate)
            .build()
    }

    private fun createForegroundInfo(
        processed: Int,
        total: Int,
        indeterminate: Boolean
    ): ForegroundInfo {
        val notification = buildNotification(
            contentText = applicationContext.getString(R.string.catalog_warmup_title),
            processed = processed,
            total = total,
            indeterminate = indeterminate,
            ongoing = true
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.catalog_warmup_title),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "catalog_warmup"
        private const val NOTIFICATION_ID = 2001
        private const val WORK_NAME = "catalog-warmup"

        fun enqueue(context: Context, replaceExisting: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<CatalogWarmupWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, // 30 seconds initial backoff
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
