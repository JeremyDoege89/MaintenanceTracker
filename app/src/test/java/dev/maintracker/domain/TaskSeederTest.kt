package dev.maintracker.domain

import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import dev.maintracker.data.seed.SeedTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TaskSeederTest {
    private val zone = ZoneId.of("UTC")
    private val now = 1_790_000_000_000L

    private fun vehicle(type: VehicleType, miles: Double? = null, eng: Double? = null, purchase: Double? = null) =
        Vehicle(
            id = "v", name = "Test", vehicleType = type, currentMiles = miles, currentEngineHours = eng,
            purchaseMeter = purchase, createdAt = now, updatedAt = now,
        )

    @Test fun everyTypeSeedsAllTemplates_noneInstantlyOverdue() {
        for (type in VehicleType.entries) {
            val templates = SeedTemplates.all.filter { it.vehicleType == type }
            val v = vehicle(type, miles = 61_230.0, eng = 812.0)
            val tasks = TaskSeeder.seed(v, templates, now, zone)
            assertEquals(templates.size, tasks.size)
            tasks.forEach {
                assertEquals(now, it.baselineDate)
                assertNotEquals("$type ${it.title}", TaskStatus.OVERDUE, it.status)
                assertNotEquals("$type ${it.title}", TaskStatus.DUE_SOON, it.status)
            }
        }
    }

    @Test fun meterBaseline_neverInventsZero() {
        val templates = SeedTemplates.all.filter { it.vehicleType == VehicleType.MOTORHOME && it.meterUnit != null }
        assertTrue(templates.isNotEmpty())
        TaskSeeder.seed(vehicle(VehicleType.MOTORHOME), templates, now, zone).forEach {
            assertNull(it.baselineMeter)
            assertNull(it.dueMeter)
        }
    }

    @Test fun meterBaseline_fallsBackToPurchaseMeterInPrimaryUnit() {
        val templates = SeedTemplates.all.filter { it.vehicleType == VehicleType.MOTORHOME && it.meterUnit == MeterUnit.MILES }
        TaskSeeder.seed(vehicle(VehicleType.MOTORHOME, purchase = 40_000.0), templates, now, zone).forEach {
            assertEquals(40_000.0, it.baselineMeter!!, 0.0)
        }
    }

    @Test fun fillMissingBaselines_startsMeterClockOnFirstReading() {
        val templates = SeedTemplates.all.filter { it.vehicleType == VehicleType.ATV_UTV }
        val seeded = TaskSeeder.seed(vehicle(VehicleType.ATV_UTV), templates, now, zone)
        val filled = TaskSeeder.fillMissingBaselines(seeded, vehicle(VehicleType.ATV_UTV, eng = 120.0), now + 1)
        val hourTasks = seeded.filter { it.meterUnit == MeterUnit.ENGINE_HOURS && it.meterInterval != null }
        assertEquals(hourTasks.map { it.id }.toSet(), filled.map { it.id }.toSet())
        filled.forEach { assertEquals(120.0, it.baselineMeter!!, 0.0) }
    }
}
