package io.github.kukrejakushagra.claudeusage.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Opens a URL in the full Chrome app when installed, otherwise the default browser.
 * Deliberately not a WebView or Custom Tab: the app never hosts the login page.
 */
object BrowserLauncher {
    private const val CHROME = "com.android.chrome"

    fun open(context: Context, url: String) {
        val base = Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent(base).setPackage(CHROME))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(base)
        }
    }
}
