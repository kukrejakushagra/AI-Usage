package io.github.kukrejakushagra.claudeusage.auth

import java.net.URLDecoder
import java.net.URLEncoder

object AuthorizeUrl {
    fun build(session: PkceSession, redirectUri: String): String {
        val params = linkedMapOf(
            "code" to "true",
            "client_id" to OAuthConfig.CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "scope" to OAuthConfig.SCOPE,
            "code_challenge" to session.codeChallenge,
            "code_challenge_method" to "S256",
            "state" to session.state,
        )
        return OAuthConfig.AUTHORIZE_URL + "?" + params.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** Parses an `a=b&c=d` query string. Later duplicates win. */
fun parseQuery(query: String): Map<String, String> =
    query.split('&').filter { it.isNotEmpty() }.associate { part ->
        val i = part.indexOf('=')
        val key = if (i < 0) part else part.substring(0, i)
        val value = if (i < 0) "" else part.substring(i + 1)
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }

/** Code and state as pasted by the user in the manual fallback flow. */
data class PastedCode(val code: String, val state: String?)

/**
 * Accepts what Anthropic's callback page shows (`code#state`), a bare code, or a full callback URL
 * containing `code` and `state` query parameters.
 */
fun parsePastedCode(input: String): PastedCode? {
    val text = input.trim()
    if (text.isEmpty()) return null
    if (text.startsWith("http://") || text.startsWith("https://")) {
        val query = text.substringAfter('?', "").substringBefore('#')
        val params = parseQuery(query)
        val code = params["code"]?.takeIf { it.isNotBlank() } ?: return null
        return PastedCode(code, params["state"])
    }
    val code = text.substringBefore('#').trim()
    if (code.isEmpty() || code.any { it.isWhitespace() }) return null
    val state = text.substringAfter('#', "").trim().ifEmpty { null }
    return PastedCode(code, state)
}
