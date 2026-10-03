package dev.maintracker.domain

import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.Season
import dev.maintracker.data.model.TemplateKind
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import dev.maintracker.domain.library.LibraryDefaults
import dev.maintracker.domain.library.Seasons
import dev.maintracker.domain.library.TestLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TaskSeederTest {
    private val zone = ZoneId.of("UTC")
    private val now = 1_790_000_000_000L

    private val day = 86_400_000L

    private fun vehicle(
        type: VehicleType, miles: Double? = null, eng: Double? = null, purchase: Double? = null, purchaseDate: Long? = null,
    ) = Vehicle(
        id = "v", name = "Test", vehicleType = type, currentMiles = miles, currentEngineHours = eng,
        purchaseMeter = purchase, purchaseDate = purchaseDate, createdAt = now, updatedAt = now,
    )

    /** The Phase 1 pool for [type], optionally of one [kind]. */
    private fun pool(type: VehicleType, kind: TemplateKind? = null): List<MaintenanceTemplate> =
        TestLibrary.templates.filter { it.vehicleType == type && it.phase == 1 && (kind == null || it.kind == kind) }

    private fun anyOf(kind: TemplateKind): MaintenanceTemplate =
        TestLibrary.templates.first { it.kind == kind && it.phase == 1 }

    @Test fun everyTypeSeedsAllRecurring_noneInstantlyOverdue() {
        for (type in VehicleType.entries) {
            val templates = pool(type, TemplateKind.RECURRING)
            assertTrue("$type has recurring templates", templates.isNotEmpty())
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
        val templates = pool(VehicleType.MOTORHOME, TemplateKind.RECURRING).filter { it.meterUnit != null }
        assertTrue(templates.isNotEmpty())
        TaskSeeder.seed(vehicle(VehicleType.MOTORHOME), templates, now, zone).forEach {
            assertNull(it.baselineMeter)
            assertNull(it.dueMeter)
        }
    }

    @Test fun meterBaseline_fallsBackToPurchaseMeterInPrimaryUnit() {
        val templates = pool(VehicleType.MOTORHOME, TemplateKind.RECURRING).filter { it.meterUnit == MeterUnit.MILES }
        TaskSeeder.seed(vehicle(VehicleType.MOTORHOME, purchase = 40_000.0), templates, now, zone).forEach {
            assertEquals(40_000.0, it.baselineMeter!!, 0.0)
        }
    }

    @Test fun fillMissingBaselines_startsMeterClockOnFirstReading() {
        val templates = pool(VehicleType.ATV_UTV, TemplateKind.RECURRING)
        val seeded = TaskSeeder.seed(vehicle(VehicleType.ATV_UTV), templates, now, zone)
        val filled = TaskSeeder.fillMissingBaselines(seeded, vehicle(VehicleType.ATV_UTV, eng = 120.0), now + 1)
        val hourTasks = seeded.filter { it.meterUnit == MeterUnit.ENGINE_HOURS && it.meterInterval != null }
        assertEquals(hourTasks.map { it.id }.toSet(), filled.map { it.id }.toSet())
        filled.forEach { assertEquals(120.0, it.baselineMeter!!, 0.0) }
    }

    @Test fun everyTypeSeedsItsWholePhase1Pool() {
        for (type in VehicleType.entries) {
            val templates = pool(type)
            val tasks = TaskSeeder.seed(vehicle(type, miles = 5_000.0, eng = 100.0), templates, now, zone)
            assertEquals(templates.map { it.id }, tasks.map { it.templateId })
            tasks.zip(templates).forEach { (task, t) ->
                assertEquals(t.kind, task.kind)
                assertEquals(t.safetyCritical, task.safetyCritical)
            }
        }
    }

    @Test fun expiry_ageClockFromGivenDate_elsePurchase_elseCreation() {
        val t = anyOf(TemplateKind.EXPIRY)
        val v = vehicle(t.vehicleType, purchaseDate = now - 100 * day)
        val made = now - 30 * day
        assertEquals(made, TaskSeeder.seed(v, listOf(t), now, zone, expiryDates = mapOf(t.id to made)).single().baselineDate)
        assertEquals(now - 100 * day, TaskSeeder.seed(v, listOf(t), now, zone).single().baselineDate)
        assertEquals(now, TaskSeeder.seed(vehicle(t.vehicleType), listOf(t), now, zone).single().baselineDate)
    }

    @Test fun expiry_pastItsLimitSeedsOverdue() {
        val t = TestLibrary.templates.first { it.kind == TemplateKind.EXPIRY && it.phase == 1 && it.timeIntervalDays != null }
        val old = now - (t.timeIntervalDays!! + 30) * day
        val task = TaskSeeder.seed(vehicle(t.vehicleType), listOf(t), now, zone, expiryDates = mapOf(t.id to old)).single()
        assertEquals(TaskStatus.OVERDUE, task.status)
    }

    @Test fun seasonal_firstDueAtNextAnchor() {
        val t = TestLibrary.templates.first { it.kind == TemplateKind.SEASONAL && it.phase == 1 && it.season != null }
        val task = TaskSeeder.seed(vehicle(t.vehicleType), listOf(t), now, zone).single()
        val anchor = Seasons.nextAnchor(t.season!!, now, zone, LibraryDefaults().anchors)
        assertEquals(anchor, DueCalculator.localDate(task.dueDate!!, zone))
    }

    @Test fun seasonal_customAnchorIsHonoured() {
        val t = TestLibrary.templates.first { it.kind == TemplateKind.SEASONAL && it.phase == 1 && it.season != null }
        val anchors = mapOf(Season.FALL to java.time.MonthDay.of(11, 15), Season.SPRING to java.time.MonthDay.of(3, 1))
        val task = TaskSeeder.seed(vehicle(t.vehicleType), listOf(t), now, zone, seasonAnchors = anchors).single()
        assertEquals(Seasons.nextAnchor(t.season!!, now, zone, anchors), DueCalculator.localDate(task.dueDate!!, zone))
    }

    @Test fun oneTime_measuredFromZeroAndPurchase() {
        val t = TestLibrary.templates.first { it.kind == TemplateKind.ONE_TIME && it.phase == 1 && it.meterUnit != null }
        val v = vehicle(t.vehicleType, miles = 12.0, eng = 2.0, purchaseDate = now - 5 * day)
        val task = TaskSeeder.seed(v, listOf(t), now, zone).single()
        assertEquals(0.0, task.baselineMeter!!, 0.0)
        assertEquals(now - 5 * day, task.baselineDate)
        assertTrue(task.isActive)
    }

    @Test fun oneTime_completionDeactivates_recurringStaysActive() {
        val oneTime = TestLibrary.templates.first { it.kind == TemplateKind.ONE_TIME && it.phase == 1 }
        val recurring = pool(oneTime.vehicleType, TemplateKind.RECURRING).first()
        val v = vehicle(oneTime.vehicleType, miles = 600.0, eng = 20.0)
        val (a, b) = TaskSeeder.seed(v, listOf(oneTime, recurring), now, zone)
        val readings = MeterReadings(miles = 600.0, engineHours = 20.0)
        val doneA = TaskCompletion.complete(a, v, "r1", now + day, readings, now + day, zone)
        val doneB = TaskCompletion.complete(b, v, "r1", now + day, readings, now + day, zone)
        assertFalse(doneA.isActive)
        assertFalse(doneA.notifyEnabled)
        assertTrue(doneB.isActive)
        assertEquals(1, doneA.completedCount)
    }
}
