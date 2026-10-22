package io.github.kukrejakushagra.claudeusage.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.kukrejakushagra.claudeusage.AppGraph
import io.github.kukrejakushagra.claudeusage.data.RefreshResult
import io.github.kukrejakushagra.claudeusage.widget.CompactUsageWidget
import io.github.kukrejakushagra.claudeusage.widget.UsageWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = AppGraph.get(applicationContext).usage.refresh()
        updateWidgets(applicationContext)
        return when {
            result is RefreshResult.Failure && result.retryable && runAttemptCount < 3 -> Result.retry()
            else -> Result.success()
        }
    }
}

object RefreshScheduler {
    private const val PERIODIC = "usage-periodic"
    private const val ONE_SHOT = "usage-now"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun onSignedIn(context: Context) {
        schedulePeriodic(context)
        refreshNow(context)
    }

    /** WorkManager persists periodic work across reboots, so no boot receiver is needed. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(ONE_SHOT)
        }
    }
}

suspend fun updateWidgets(context: Context) {
    UsageWidget().updateAll(context)
    CompactUsageWidget().updateAll(context)
}
