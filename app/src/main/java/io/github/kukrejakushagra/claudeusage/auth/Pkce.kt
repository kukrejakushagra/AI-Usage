package io.github.kukrejakushagra.claudeusage.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** A single PKCE (RFC 7636) attempt: verifier, its S256 challenge, and an anti-CSRF state. */
data class PkceSession(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
) {
    companion object {
        private val random = SecureRandom()

        fun create(): PkceSession {
            val verifier = randomUrlSafe(32)
            return PkceSession(verifier, s256(verifier), randomUrlSafe(32))
        }

        fun s256(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return base64Url(digest)
        }

        private fun randomUrlSafe(bytes: Int): String =
            ByteArray(bytes).also(random::nextBytes).let(::base64Url)

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** Compares two strings in time independent of where they first differ. */
fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
