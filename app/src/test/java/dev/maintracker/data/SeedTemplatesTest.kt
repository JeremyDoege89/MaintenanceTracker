package dev.maintracker.data

import dev.maintracker.data.model.VehicleType
import dev.maintracker.data.seed.SeedTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedTemplatesTest {
    private val all = SeedTemplates.all

    @Test fun idsAreUniqueAndStable() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { assertTrue(it.id, it.id.startsWith("seed:${it.vehicleType.name}:")) }
    }

    @Test fun everyTypeHasASchedule() {
        VehicleType.entries.forEach { type -> assertTrue("$type", all.count { it.vehicleType == type } >= 10) }
    }

    @Test fun meterUnitPresentIffMeterInterval() {
        all.forEach { assertEquals(it.title, it.meterInterval != null, it.meterUnit != null) }
    }

    @Test fun meterUnitsAreOnesTheTypePromptsFor() {
        all.filter { it.meterUnit != null }.forEach {
            assertTrue("${it.vehicleType} ${it.title} uses ${it.meterUnit}", it.meterUnit in it.vehicleType.meters)
        }
    }

    @Test fun allSeeded_sortOrderUniquePerType() {
        all.forEach { assertTrue(it.isSeeded) }
        all.groupBy { it.vehicleType }.forEach { (type, list) ->
            assertEquals("$type", list.size, list.map { it.sortOrder }.toSet().size)
        }
    }
}
