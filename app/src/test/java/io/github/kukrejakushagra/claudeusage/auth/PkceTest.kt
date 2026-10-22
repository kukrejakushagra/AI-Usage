package io.github.kukrejakushagra.claudeusage.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun s256MatchesRfc7636Vector() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PkceSession.s256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun sessionsAreRandomAndUrlSafe() {
        val a = PkceSession.create()
        val b = PkceSession.create()
        assertNotEquals(a.codeVerifier, b.codeVerifier)
        assertNotEquals(a.state, b.state)
        assertEquals(43, a.codeVerifier.length)
        assertTrue(a.codeVerifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(PkceSession.s256(a.codeVerifier), a.codeChallenge)
    }
}
