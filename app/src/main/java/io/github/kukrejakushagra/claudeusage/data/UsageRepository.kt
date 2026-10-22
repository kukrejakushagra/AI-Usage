package io.github.kukrejakushagra.claudeusage.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.kukrejakushagra.claudeusage.auth.AuthRepository
import io.github.kukrejakushagra.claudeusage.auth.OAuthConfig
import io.github.kukrejakushagra.claudeusage.auth.TokenException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What widgets and the app render: the last good snapshot plus the most recent error, if any. */
@Serializable
data class UsageCache(
    val snapshot: UsageSnapshot? = null,
    val error: String? = null,
    val signedOut: Boolean = false,
)

sealed interface RefreshResult {
    data object Success : RefreshResult
    data object SignedOut : RefreshResult
    data class Failure(val message: String, val retryable: Boolean) : RefreshResult
}

private val Context.usageDataStore by preferencesDataStore("usage")

class UsageRepository(
    context: Context,
    private val http: OkHttpClient,
    private val auth: AuthRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = context.applicationContext.usageDataStore

    val cache: Flow<UsageCache> = store.data.map { prefs ->
        prefs[CACHE]?.let { runCatching { AppJson.decodeFromString<UsageCache>(it) }.getOrNull() } ?: UsageCache()
    }

    suspend fun refresh(): RefreshResult {
        val result = try {
            val token = auth.validAccessToken() ?: return save { UsageCache(signedOut = true) }.let { RefreshResult.SignedOut }
            var response = fetch(token)
            if (response.code == 401) {
                val renewed = auth.validAccessToken(forceRefresh = true)
                    ?: return save { UsageCache(signedOut = true) }.let { RefreshResult.SignedOut }
                response = fetch(renewed)
            }
            when {
                response.code in 200..299 -> {
                    val snapshot = UsageParser.parse(response.body, clock())
                    save { UsageCache(snapshot = snapshot) }
                    RefreshResult.Success
                }
                response.code == 401 || response.code == 403 ->
                    RefreshResult.Failure("Not authorized (${response.code}). Try signing in again.", retryable = false)
                response.code == 429 -> RefreshResult.Failure("Rate limited, will retry later.", retryable = true)
                else -> RefreshResult.Failure("Server error (${response.code}).", retryable = response.code >= 500)
            }
        } catch (e: TokenException) {
            RefreshResult.Failure(
                if (e.isInvalidGrant) "Session expired. Sign in again." else e.message ?: "Token refresh failed.",
                retryable = !e.isInvalidGrant,
            )
        } catch (e: IOException) {
            RefreshResult.Failure("Network error: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        } catch (e: Exception) {
            RefreshResult.Failure("Unexpected response: ${e.message ?: e.javaClass.simpleName}", retryable = false)
        }
        if (result is RefreshResult.Failure) save { it.copy(error = result.message, signedOut = false) }
        return result
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private class HttpResult(val code: Int, val body: String)

    private suspend fun fetch(token: String): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(OAuthConfig.USAGE_URL)
            .header("Authorization", "Bearer $token")
            .header("anthropic-beta", OAuthConfig.OAUTH_BETA_HEADER)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(request).execute().use { HttpResult(it.code, it.body.string()) }
    }

    private suspend fun save(transform: (UsageCache) -> UsageCache) {
        store.edit { prefs ->
            val current = prefs[CACHE]?.let { runCatching { AppJson.decodeFromString<UsageCache>(it) }.getOrNull() }
                ?: UsageCache()
            prefs[CACHE] = AppJson.encodeToString(UsageCache.serializer(), transform(current))
        }
    }

    private companion object {
        val CACHE = stringPreferencesKey("cache")
    }
}
