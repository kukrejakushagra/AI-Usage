package io.github.kukrejakushagra.claudeusage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kukrejakushagra.claudeusage.auth.SignInStatus
import io.github.kukrejakushagra.claudeusage.data.UsageCache
import io.github.kukrejakushagra.claudeusage.data.UsageLimit
import kotlinx.coroutines.delay

@Composable
fun AppScreen(viewModel: MainViewModel, onSignIn: () -> Unit) {
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val cache by viewModel.cache.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    when (signedIn) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> SignInScreen(
            status = status,
            onSignIn = onSignIn,
            onCancel = viewModel::cancelSignIn,
            onStartManual = viewModel::startManualSignIn,
            onSubmitCode = viewModel::submitCode,
        )
        true -> DashboardScreen(
            cache = cache,
            refreshing = refreshing,
            onRefresh = viewModel::refresh,
            onSignOut = viewModel::signOut,
        )
    }
}

@Composable
private fun SignInScreen(
    status: SignInStatus,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onStartManual: () -> Unit,
    onSubmitCode: (String) -> Unit,
) {
    var showManual by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    val busy = status == SignInStatus.Exchanging

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Claude Usage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "See your Claude session and weekly limits on your home screen. You sign in on Anthropic's own " +
                    "page in your browser; this app never sees your password and only requests read-only profile access.",
                style = MaterialTheme.typography.bodyMedium,
            )

            when (status) {
                SignInStatus.WaitingForBrowser -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Waiting for you to finish signing in in the browser…")
                    }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
                SignInStatus.Exchanging -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Finishing sign-in…")
                }
                else -> Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in with Claude") }
            }

            if (status is SignInStatus.Failed) {
                Text(status.message, color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) "Hide manual sign-in" else "Having trouble? Sign in with a code instead")
            }
            if (showManual || status == SignInStatus.AwaitingPastedCode) {
                Text(
                    "1. Open the sign-in page. After approving, Anthropic shows a code.\n" +
                        "2. Copy the whole code (it contains a #) and paste it below.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onStartManual, enabled = !busy) { Text("Open sign-in page") }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Paste code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onSubmitCode(code) }, enabled = code.isNotBlank() && !busy) { Text("Submit code") }
            }

            Spacer(Modifier.height(16.dp))
            Disclaimer()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    cache: UsageCache,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude Usage") },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !refreshing) { Text(if (refreshing) "Refreshing…" else "Refresh") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            cache.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            val snapshot = cache.snapshot
            if (snapshot == null) {
                item {
                    Text(
                        if (refreshing || cache.error == null) "Loading usage…" else "No usage data yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(snapshot.limits, key = { it.key }) { limit -> LimitCard(limit, now) }
                item {
                    Text(
                        "Updated ${formatAgo(snapshot.fetchedAtMillis, now)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "Add the widgets from your launcher's widget picker. They refresh about every 15 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item { Disclaimer() }
        }
    }
}

@Composable
private fun LimitCard(limit: UsageLimit, now: Long) {
    val high = limit.utilization >= 90
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(limit.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    formatPercent(limit.utilization),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            LinearProgressIndicator(
                progress = { (limit.utilization / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            val reset = formatResetsIn(limit.resetsAtMillis, now)
            if (reset.isNotEmpty()) {
                Text(reset, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Disclaimer() {
    Text(
        "Unofficial app, not affiliated with or endorsed by Anthropic. It uses undocumented endpoints that may " +
            "change or stop working at any time.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}
