package dev.maintracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalTest {
    @Test fun seedConstantsRoundTrip() {
        assertEquals(1 to IntervalUnit.MONTHS, IntervalUnit.split(30))
        assertEquals(3 to IntervalUnit.MONTHS, IntervalUnit.split(91))
        assertEquals(6 to IntervalUnit.MONTHS, IntervalUnit.split(182))
        assertEquals(1 to IntervalUnit.YEARS, IntervalUnit.split(365))
        assertEquals(2 to IntervalUnit.YEARS, IntervalUnit.split(730))
        assertEquals(2 to IntervalUnit.WEEKS, IntervalUnit.split(14))
        assertEquals(10 to IntervalUnit.DAYS, IntervalUnit.split(10))
    }

    @Test fun everyEditorValueRoundTrips() {
        for (unit in IntervalUnit.entries) for (n in 1..60) {
            val days = unit.toDays(n)
            val (m, u) = IntervalUnit.split(days)
            assertEquals("$n $unit", days, u.toDays(m))
        }
    }

    @Test fun formatReadsNaturally() {
        assertEquals("month", Format.days(30))
        assertEquals("6 months", Format.days(182))
        assertEquals("2 years", Format.days(730))
        assertEquals("Every 6 months or 5,000 mi — whichever first", Format.interval(182, 5000, dev.maintracker.data.model.MeterUnit.MILES))
    }
}
