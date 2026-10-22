package io.github.kukrejakushagra.claudeusage.ui

import kotlin.math.roundToInt

/** "2h 14m", "45m", "3d 4h", "now", or "" when unknown. */
fun formatCountdown(targetMillis: Long?, nowMillis: Long): String {
    if (targetMillis == null) return ""
    val totalMinutes = ((targetMillis - nowMillis) / 60_000.0).let { if (it <= 0) return "now" else kotlin.math.ceil(it).toLong() }
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatResetsIn(targetMillis: Long?, nowMillis: Long): String {
    val countdown = formatCountdown(targetMillis, nowMillis)
    return when (countdown) {
        "" -> ""
        "now" -> "Resetting now"
        else -> "Resets in $countdown"
    }
}

fun formatPercent(utilization: Double): String = "${utilization.roundToInt()}%"

/** "just now", "5m ago", "3h ago". */
fun formatAgo(thenMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - thenMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 48 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
