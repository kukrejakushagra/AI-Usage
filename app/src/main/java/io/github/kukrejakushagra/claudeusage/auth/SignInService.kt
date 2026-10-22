package io.github.kukrejakushagra.claudeusage.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.kukrejakushagra.claudeusage.AppGraph
import io.github.kukrejakushagra.claudeusage.R
import io.github.kukrejakushagra.claudeusage.ui.MainActivity
import io.github.kukrejakushagra.claudeusage.work.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Keeps the process un-frozen while the loopback server waits for the browser redirect.
 * Shows a visible "Waiting for sign in" notification and gives up after five minutes.
 */
class SignInService : LifecycleService() {

    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val graph = AppGraph.get(this)
        if (intent?.action == ACTION_CANCEL) {
            graph.auth.cancelLoopback()
            stopSelf()
            return START_NOT_STICKY
        }

        val attempt = graph.auth.activeLoopback
        ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            waitingNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (attempt == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        job?.cancel()
        job = lifecycleScope.launch {
            val result = runInterruptible(Dispatchers.IO) { attempt.server.await(TIMEOUT_MILLIS) }
            graph.auth.clearLoopback(attempt)
            when (result) {
                is LoopbackServer.Result.Code -> {
                    val ok = graph.auth.exchange(
                        result.code,
                        attempt.session.state,
                        attempt.session.codeVerifier,
                        attempt.redirectUri,
                    )
                    if (ok) {
                        RefreshScheduler.onSignedIn(this@SignInService)
                        notifyResult(getString(R.string.notif_signed_in))
                    } else {
                        notifyResult(getString(R.string.notif_sign_in_failed))
                    }
                }
                is LoopbackServer.Result.Denied ->
                    graph.auth.setStatus(SignInStatus.Failed("Sign-in was denied: ${result.error}"))
                null -> if (graph.auth.status.value == SignInStatus.WaitingForBrowser) {
                    graph.auth.setStatus(SignInStatus.Failed("Sign-in timed out. Try again, or use a code."))
                }
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun waitingNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.notif_waiting_title))
        .setContentText(getString(R.string.notif_waiting_text))
        .setOngoing(true)
        .setContentIntent(openAppIntent())
        .addAction(
            0,
            getString(R.string.cancel),
            PendingIntent.getService(
                this,
                1,
                Intent(this, SignInService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun notifyResult(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(RESULT_NOTIFICATION_ID, notification) }
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "sign_in"
        private const val NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        private const val ACTION_CANCEL = "cancel"
        private const val TIMEOUT_MILLIS = 5 * 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SignInService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}
