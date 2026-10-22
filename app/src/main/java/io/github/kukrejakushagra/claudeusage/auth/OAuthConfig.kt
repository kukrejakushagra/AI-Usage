package io.github.kukrejakushagra.claudeusage.auth

/**
 * Endpoints and identifiers used for signing in.
 *
 * Anthropic does not offer third-party OAuth client registration, so this app borrows the public
 * client ID used by Claude Code. All of these endpoints are undocumented and may change without
 * notice.
 */
object OAuthConfig {
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val AUTHORIZE_URL = "https://claude.ai/oauth/authorize"
    const val TOKEN_URL = "https://console.anthropic.com/v1/oauth/token"

    /** Anthropic-hosted page that displays the authorization code as `code#state` for copy/paste. */
    const val MANUAL_REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"

    /** Read-only profile scope: no inference, no access to chats. */
    const val SCOPE = "user:profile"

    const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
    const val OAUTH_BETA_HEADER = "oauth-2025-04-20"

    const val CALLBACK_PATH = "/callback"

    fun loopbackRedirectUri(port: Int) = "http://localhost:$port$CALLBACK_PATH"
}
