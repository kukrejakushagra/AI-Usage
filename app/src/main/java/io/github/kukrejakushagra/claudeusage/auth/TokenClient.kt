package io.github.kukrejakushagra.claudeusage.auth

import io.github.kukrejakushagra.claudeusage.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class TokenException(message: String, val isInvalidGrant: Boolean = false) : IOException(message)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
private data class TokenError(
    val error: String? = null,
    @SerialName("error_description") val description: String? = null,
)

/** Talks to Anthropic's OAuth token endpoint. */
class TokenClient(private val http: OkHttpClient, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun exchangeCode(code: String, state: String, verifier: String, redirectUri: String): Tokens =
        post(
            buildJsonObject {
                put("grant_type", "authorization_code")
                put("code", code)
                put("state", state)
                put("client_id", OAuthConfig.CLIENT_ID)
                put("redirect_uri", redirectUri)
                put("code_verifier", verifier)
            }.toString(),
            previousRefreshToken = null,
        )

    suspend fun refresh(refreshToken: String): Tokens =
        post(
            buildJsonObject {
                put("grant_type", "refresh_token")
                put("refresh_token", refreshToken)
                put("client_id", OAuthConfig.CLIENT_ID)
            }.toString(),
            previousRefreshToken = refreshToken,
        )

    private suspend fun post(body: String, previousRefreshToken: String?): Tokens = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(OAuthConfig.TOKEN_URL)
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val err = runCatching { AppJson.decodeFromString<TokenError>(text) }.getOrNull()
                throw TokenException(
                    "Token request failed (${response.code}): ${err?.description ?: err?.error ?: "unknown error"}",
                    isInvalidGrant = err?.error == "invalid_grant" || response.code == 401,
                )
            }
            val parsed = AppJson.decodeFromString<TokenResponse>(text)
            Tokens(
                accessToken = parsed.accessToken,
                refreshToken = parsed.refreshToken ?: previousRefreshToken,
                expiresAtMillis = clock() + (parsed.expiresIn ?: 3600L) * 1000L,
            )
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
