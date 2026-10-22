package io.github.kukrejakushagra.claudeusage.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SignInStatus {
    data object Idle : SignInStatus
    data object WaitingForBrowser : SignInStatus
    data object AwaitingPastedCode : SignInStatus
    data object Exchanging : SignInStatus
    data class Failed(val message: String) : SignInStatus
}

/** A loopback sign-in that has been prepared but whose browser has not necessarily opened yet. */
class LoopbackAttempt(val session: PkceSession, val server: LoopbackServer) {
    val redirectUri: String = OAuthConfig.loopbackRedirectUri(server.port)
    val authorizeUrl: String = AuthorizeUrl.build(session, redirectUri)
}

class AuthRepository(
    private val store: TokenStore,
    private val client: TokenClient,
    private val appPackage: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _status = MutableStateFlow<SignInStatus>(SignInStatus.Idle)
    val status: StateFlow<SignInStatus> = _status.asStateFlow()

    val isSignedIn: Flow<Boolean> = store.tokens.map { it != null }

    private val refreshLock = Mutex()

    @Volatile
    var activeLoopback: LoopbackAttempt? = null
        private set

    fun setStatus(status: SignInStatus) {
        _status.value = status
    }

    /** Binds the loopback server; the caller then starts the foreground service and opens the browser. */
    fun prepareLoopback(): LoopbackAttempt {
        cancelLoopback()
        val session = PkceSession.create()
        val server = LoopbackServer(session.state, LoopbackServer.successPage(appPackage))
        return LoopbackAttempt(session, server).also {
            activeLoopback = it
            _status.value = SignInStatus.WaitingForBrowser
        }
    }

    fun cancelLoopback() {
        activeLoopback?.server?.close()
        activeLoopback = null
        if (_status.value == SignInStatus.WaitingForBrowser) _status.value = SignInStatus.Idle
    }

    fun clearLoopback(attempt: LoopbackAttempt) {
        attempt.server.close()
        if (activeLoopback === attempt) activeLoopback = null
    }

    /** Starts the manual flow; returns the authorize URL to open. */
    suspend fun prepareManual(): String {
        cancelLoopback()
        val session = PkceSession.create()
        store.savePending(PendingAuth(session.codeVerifier, session.state, OAuthConfig.MANUAL_REDIRECT_URI))
        _status.value = SignInStatus.AwaitingPastedCode
        return AuthorizeUrl.build(session, OAuthConfig.MANUAL_REDIRECT_URI)
    }

    /** Completes the manual flow with what the user pasted. */
    suspend fun submitPastedCode(input: String): Boolean {
        val pending = store.pending()
        if (pending == null) {
            _status.value = SignInStatus.Failed("Start the manual sign-in first.")
            return false
        }
        val pasted = parsePastedCode(input)
        if (pasted == null) {
            _status.value = SignInStatus.Failed("That doesn't look like a sign-in code.")
            return false
        }
        if (pasted.state != null && !constantTimeEquals(pasted.state, pending.state)) {
            _status.value = SignInStatus.Failed("This code belongs to a different sign-in attempt. Start again.")
            return false
        }
        val ok = exchange(pasted.code, pending.state, pending.codeVerifier, pending.redirectUri)
        if (ok) store.savePending(null)
        return ok
    }

    suspend fun exchange(code: String, state: String, verifier: String, redirectUri: String): Boolean {
        _status.value = SignInStatus.Exchanging
        return try {
            store.save(client.exchangeCode(code, state, verifier, redirectUri))
            _status.value = SignInStatus.Idle
            true
        } catch (e: Exception) {
            _status.value = SignInStatus.Failed(e.message ?: "Sign-in failed.")
            false
        }
    }

    /**
     * Returns a usable access token, refreshing it if it expires within five minutes, or null when
     * signed out. Throws [TokenException] with [TokenException.isInvalidGrant] if the session is dead.
     */
    suspend fun validAccessToken(forceRefresh: Boolean = false): String? = refreshLock.withLock {
        val tokens = store.current() ?: return null
        val fresh = tokens.expiresAtMillis - clock() > 5 * 60_000L
        if (fresh && !forceRefresh) return tokens.accessToken
        val refreshToken = tokens.refreshToken
            ?: return if (fresh) tokens.accessToken else throw TokenException("Session expired.", isInvalidGrant = true)
        val renewed = client.refresh(refreshToken)
        store.save(renewed)
        renewed.accessToken
    }

    suspend fun signOut() {
        cancelLoopback()
        store.clear()
        _status.value = SignInStatus.Idle
    }
}
