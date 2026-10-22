package io.github.kukrejakushagra.claudeusage

import android.app.Application
import android.content.Context
import io.github.kukrejakushagra.claudeusage.auth.AuthRepository
import io.github.kukrejakushagra.claudeusage.auth.TokenClient
import io.github.kukrejakushagra.claudeusage.auth.TokenStore
import io.github.kukrejakushagra.claudeusage.data.UsageRepository
import io.github.kukrejakushagra.claudeusage.data.createHttpClient

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppGraph(context: Context) {
    private val http = createHttpClient()
    val tokenStore = TokenStore(context)
    val auth = AuthRepository(tokenStore, TokenClient(http), context.packageName)
    val usage = UsageRepository(context, http, auth)

    companion object {
        fun get(context: Context): AppGraph = (context.applicationContext as ClaudeUsageApp).graph
    }
}

class ClaudeUsageApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
