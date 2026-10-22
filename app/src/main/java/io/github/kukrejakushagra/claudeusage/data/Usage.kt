package io.github.kukrejakushagra.claudeusage.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.OffsetDateTime

enum class LimitKind { SESSION, WEEKLY, WEEKLY_MODEL }

@Serializable
data class UsageLimit(
    val key: String,
    val label: String,
    val kind: LimitKind,
    /** Percentage of the limit used, 0–100 (may exceed 100). */
    val utilization: Double,
    val resetsAtMillis: Long?,
)

@Serializable
data class UsageSnapshot(
    val limits: List<UsageLimit>,
    val fetchedAtMillis: Long,
) {
    val session: UsageLimit? get() = limits.firstOrNull { it.kind == LimitKind.SESSION }
    val weekly: UsageLimit? get() = limits.firstOrNull { it.kind == LimitKind.WEEKLY }
}

object UsageParser {
    private const val SESSION_KEY = "five_hour"
    private const val WEEKLY_KEY = "seven_day"

    /**
     * Parses the usage response leniently. Every `five_hour` / `seven_day*` object with a
     * `utilization` value becomes a limit, so newly added per-model limits appear automatically.
     */
    fun parse(json: String, fetchedAtMillis: Long): UsageSnapshot {
        val root = AppJson.parseToJsonElement(json).jsonObject
        val limits = root.entries.mapNotNull { (key, value) ->
            if (key != SESSION_KEY && !key.startsWith(WEEKLY_KEY)) return@mapNotNull null
            val obj = value as? JsonObject ?: return@mapNotNull null
            val utilization = (obj["utilization"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val resetsAt = (obj["resets_at"] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
            val kind = when (key) {
                SESSION_KEY -> LimitKind.SESSION
                WEEKLY_KEY -> LimitKind.WEEKLY
                else -> LimitKind.WEEKLY_MODEL
            }
            UsageLimit(key, labelFor(key), kind, utilization, resetsAt?.let(::parseInstant))
        }.sortedWith(compareBy<UsageLimit> { it.kind.ordinal }.thenBy { it.key })
        return UsageSnapshot(limits, fetchedAtMillis)
    }

    fun labelFor(key: String): String = when (key) {
        SESSION_KEY -> "Session (5h)"
        WEEKLY_KEY -> "Weekly (all models)"
        else -> {
            val suffix = key.removePrefix(WEEKLY_KEY).trim('_')
            val name = suffix.split('_').filter { it.isNotEmpty() }
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }
            if (name.isEmpty()) "Weekly" else "Weekly · $name"
        }
    }

    fun parseInstant(text: String): Long? =
        runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
}
