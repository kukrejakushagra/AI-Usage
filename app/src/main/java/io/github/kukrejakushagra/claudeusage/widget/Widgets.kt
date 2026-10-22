package io.github.kukrejakushagra.claudeusage.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.kukrejakushagra.claudeusage.AppGraph
import io.github.kukrejakushagra.claudeusage.data.UsageCache
import io.github.kukrejakushagra.claudeusage.data.UsageLimit
import io.github.kukrejakushagra.claudeusage.ui.MainActivity
import io.github.kukrejakushagra.claudeusage.ui.formatAgo
import io.github.kukrejakushagra.claudeusage.ui.formatCountdown
import io.github.kukrejakushagra.claudeusage.ui.formatPercent
import io.github.kukrejakushagra.claudeusage.ui.formatResetsIn
import io.github.kukrejakushagra.claudeusage.work.RefreshScheduler

private val Warning = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))

/** Medium widget: every limit with a progress bar and reset countdown. */
class UsageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val usage = AppGraph.get(context).usage
        provideContent {
            val cache by usage.cache.collectAsState(initial = UsageCache())
            GlanceTheme { WidgetFrame { DetailedContent(cache) } }
        }
    }
}

/** Small widget: session and weekly percentages only. */
class CompactUsageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val usage = AppGraph.get(context).usage
        provideContent {
            val cache by usage.cache.collectAsState(initial = UsageCache())
            GlanceTheme { WidgetFrame { CompactContent(cache) } }
        }
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

class CompactUsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactUsageWidget()
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}

@Composable
private fun WidgetFrame(content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        content()
    }
}

@Composable
private fun DetailedContent(cache: UsageCache) {
    val now = System.currentTimeMillis()
    val snapshot = cache.snapshot
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Claude usage",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            "↻",
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 18.sp),
            modifier = GlanceModifier.padding(horizontal = 6.dp).clickable(actionRunCallback<RefreshAction>()),
        )
    }
    Spacer(GlanceModifier.height(6.dp))
    when {
        cache.signedOut || (snapshot == null && cache.error == null) -> Muted(if (cache.signedOut) "Tap to sign in" else "Tap to open the app")
        snapshot == null -> Muted(cache.error ?: "")
        else -> {
            val maxRows = ((LocalSize.current.height.value - 60) / 38).toInt().coerceAtLeast(1)
            snapshot.limits.take(maxRows).forEach { limit ->
                LimitRow(limit, now)
                Spacer(GlanceModifier.height(6.dp))
            }
            val status = cache.error ?: "Updated ${formatAgo(snapshot.fetchedAtMillis, now)}"
            Text(status, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
        }
    }
}

@Composable
private fun LimitRow(limit: UsageLimit, now: Long) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                limit.label,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            Text(
                formatPercent(limit.utilization),
                style = TextStyle(
                    color = if (limit.utilization >= 90) Warning else GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        LinearProgressIndicator(
            progress = (limit.utilization / 100.0).toFloat().coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = if (limit.utilization >= 90) Warning else GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        val reset = formatResetsIn(limit.resetsAtMillis, now)
        if (reset.isNotEmpty()) {
            Text(reset, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
        }
    }
}

@Composable
private fun CompactContent(cache: UsageCache) {
    val now = System.currentTimeMillis()
    val snapshot = cache.snapshot
    if (cache.signedOut || snapshot == null) {
        Muted(if (cache.signedOut || cache.error == null) "Claude: tap to set up" else cache.error)
        return
    }
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        listOfNotNull(snapshot.session?.let { "5h" to it }, snapshot.weekly?.let { "7d" to it }).forEach { (tag, limit) ->
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tag, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    formatPercent(limit.utilization),
                    style = TextStyle(
                        color = if (limit.utilization >= 90) Warning else GlanceTheme.colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    formatCountdown(limit.resetsAtMillis, now),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
}
