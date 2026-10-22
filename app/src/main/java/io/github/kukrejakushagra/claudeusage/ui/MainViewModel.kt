package io.github.kukrejakushagra.claudeusage.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kukrejakushagra.claudeusage.AppGraph
import io.github.kukrejakushagra.claudeusage.auth.BrowserLauncher
import io.github.kukrejakushagra.claudeusage.auth.SignInService
import io.github.kukrejakushagra.claudeusage.auth.SignInStatus
import io.github.kukrejakushagra.claudeusage.data.UsageCache
import io.github.kukrejakushagra.claudeusage.work.RefreshScheduler
import io.github.kukrejakushagra.claudeusage.work.updateWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = AppGraph.get(app)

    /** null while loading. */
    val isSignedIn: StateFlow<Boolean?> = graph.auth.isSignedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val status: StateFlow<SignInStatus> = graph.auth.status
    val cache: StateFlow<UsageCache> = graph.usage.cache
        .stateIn(viewModelScope, SharingStarted.Eagerly, UsageCache())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun signIn() {
        val context = getApplication<Application>()
        val attempt = try {
            graph.auth.prepareLoopback()
        } catch (e: Exception) {
            graph.auth.setStatus(SignInStatus.Failed("Couldn't start local sign-in: ${e.message}. Use a code instead."))
            return
        }
        SignInService.start(context)
        BrowserLauncher.open(context, attempt.authorizeUrl)
    }

    fun cancelSignIn() {
        graph.auth.cancelLoopback()
    }

    fun startManualSignIn() {
        viewModelScope.launch {
            BrowserLauncher.open(getApplication(), graph.auth.prepareManual())
        }
    }

    fun submitCode(input: String) {
        viewModelScope.launch {
            if (graph.auth.submitPastedCode(input)) RefreshScheduler.onSignedIn(getApplication())
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            graph.usage.refresh()
            updateWidgets(getApplication())
            _refreshing.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            RefreshScheduler.cancelAll(getApplication())
            graph.auth.signOut()
            graph.usage.clear()
            updateWidgets(getApplication())
        }
    }
}
