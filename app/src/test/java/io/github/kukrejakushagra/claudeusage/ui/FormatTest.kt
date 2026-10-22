package io.github.kukrejakushagra.claudeusage.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun countdown() {
        assertEquals("", formatCountdown(null, 0))
        assertEquals("now", formatCountdown(0, 10))
        assertEquals("1m", formatCountdown(10_000, 0))
        assertEquals("45m", formatCountdown(45 * minute, 0))
        assertEquals("2h 14m", formatCountdown(2 * hour + 14 * minute, 0))
        assertEquals("3d 4h", formatCountdown(76 * hour, 0))
    }

    @Test
    fun resetsIn() {
        assertEquals("Resets in 5m", formatResetsIn(5 * minute, 0))
        assertEquals("Resetting now", formatResetsIn(0, 1))
        assertEquals("", formatResetsIn(null, 1))
    }

    @Test
    fun percentAndAgo() {
        assertEquals("43%", formatPercent(42.6))
        assertEquals("just now", formatAgo(0, 30_000))
        assertEquals("5m ago", formatAgo(0, 5 * minute))
        assertEquals("3h ago", formatAgo(0, 3 * hour))
    }
}
