package io.github.kukrejakushagra.claudeusage.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizeUrlTest {
    private val session = PkceSession("verifier", "challenge", "state123")

    @Test
    fun buildsExpectedParameters() {
        val url = AuthorizeUrl.build(session, "http://localhost:4242/callback")
        assertTrue(url.startsWith(OAuthConfig.AUTHORIZE_URL + "?"))
        val params = parseQuery(url.substringAfter('?'))
        assertEquals("true", params["code"])
        assertEquals(OAuthConfig.CLIENT_ID, params["client_id"])
        assertEquals("code", params["response_type"])
        assertEquals("http://localhost:4242/callback", params["redirect_uri"])
        assertEquals("user:profile", params["scope"])
        assertEquals("challenge", params["code_challenge"])
        assertEquals("S256", params["code_challenge_method"])
        assertEquals("state123", params["state"])
        assertTrue("redirect must be encoded", url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A4242%2Fcallback"))
    }

    @Test
    fun parsesCodeHashState() {
        assertEquals(PastedCode("abc", "xyz"), parsePastedCode("  abc#xyz \n"))
    }

    @Test
    fun parsesBareCode() {
        assertEquals(PastedCode("abc", null), parsePastedCode("abc"))
    }

    @Test
    fun parsesCallbackUrl() {
        assertEquals(
            PastedCode("abc", "xyz"),
            parsePastedCode("https://console.anthropic.com/oauth/code/callback?code=abc&state=xyz"),
        )
    }

    @Test
    fun rejectsGarbage() {
        assertNull(parsePastedCode(""))
        assertNull(parsePastedCode("two words"))
        assertNull(parsePastedCode("https://example.com/?nope=1"))
    }
}
