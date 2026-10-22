package io.github.kukrejakushagra.claudeusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageParserTest {
    @Test
    fun parsesAllLimitsInOrder() {
        val json = """
            {
              "five_hour": {"utilization": 42.0, "resets_at": "2026-10-22T15:00:00.123456+00:00"},
              "seven_day": {"utilization": 12, "resets_at": "2026-10-27T09:00:00Z"},
              "seven_day_sonnet": {"utilization": 3.5, "resets_at": null},
              "seven_day_opus": {"utilization": 80, "resets_at": "2026-10-27T09:00:00+00:00"},
              "seven_day_oauth_apps": null,
              "extra_usage": {"is_enabled": false, "monthly_limit": null},
              "something_new": {"utilization": 1}
            }
        """.trimIndent()
        val snapshot = UsageParser.parse(json, fetchedAtMillis = 1L)

        assertEquals(listOf("five_hour", "seven_day", "seven_day_opus", "seven_day_sonnet"), snapshot.limits.map { it.key })
        assertEquals(42.0, snapshot.session!!.utilization, 0.0)
        assertEquals(1_792_681_200_123L, snapshot.session!!.resetsAtMillis)
        assertEquals(LimitKind.WEEKLY, snapshot.weekly!!.kind)
        assertNull(snapshot.limits.first { it.key == "seven_day_sonnet" }.resetsAtMillis)
        assertEquals("Weekly · Opus", snapshot.limits.first { it.key == "seven_day_opus" }.label)
    }

    @Test
    fun labels() {
        assertEquals("Session (5h)", UsageParser.labelFor("five_hour"))
        assertEquals("Weekly (all models)", UsageParser.labelFor("seven_day"))
        assertEquals("Weekly · Claude Fable", UsageParser.labelFor("seven_day_claude_fable"))
    }

    @Test
    fun emptyObjectGivesNoLimits() {
        assertEquals(0, UsageParser.parse("{}", 0).limits.size)
    }
}
