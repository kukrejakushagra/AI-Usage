package io.github.kukrejakushagra.claudeusage.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.kukrejakushagra.claudeusage.data.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
)

/** PKCE material for a manual sign-in that may outlive the process while the user is in Chrome. */
@Serializable
data class PendingAuth(
    val codeVerifier: String,
    val state: String,
    val redirectUri: String,
)

private val Context.authDataStore by preferencesDataStore("auth")

/** Encrypted-at-rest storage for OAuth tokens. */
class TokenStore(context: Context, private val cipher: KeystoreCipher = KeystoreCipher()) {
    private val store = context.applicationContext.authDataStore

    val tokens: Flow<Tokens?> = store.data.map { prefs ->
        prefs[TOKENS]?.let(cipher::decrypt)?.let { runCatching { AppJson.decodeFromString<Tokens>(it) }.getOrNull() }
    }

    suspend fun current(): Tokens? = tokens.first()

    suspend fun save(tokens: Tokens) {
        val sealed = cipher.encrypt(AppJson.encodeToString(Tokens.serializer(), tokens))
        store.edit { it[TOKENS] = sealed }
    }

    suspend fun pending(): PendingAuth? = store.data.first()[PENDING]
        ?.let(cipher::decrypt)
        ?.let { runCatching { AppJson.decodeFromString<PendingAuth>(it) }.getOrNull() }

    suspend fun savePending(pending: PendingAuth?) {
        store.edit { prefs ->
            if (pending == null) prefs.remove(PENDING)
            else prefs[PENDING] = cipher.encrypt(AppJson.encodeToString(PendingAuth.serializer(), pending))
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val TOKENS = stringPreferencesKey("tokens")
        val PENDING = stringPreferencesKey("pending")
    }
}
