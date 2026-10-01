package dev.maintracker.domain

import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DueCalculatorTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private val base = at(2026, 3, 14)

    private fun vehicle(miles: Double? = null, eng: Double? = null) = Vehicle(
        id = "v", name = "Big Blue", vehicleType = VehicleType.MOTORHOME,
        currentMiles = miles, currentEngineHours = eng, createdAt = base, updatedAt = base,
    )

    private fun task(days: Int? = null, meter: Int? = null, unit: MeterUnit? = null, baselineMeter: Double? = null) =
        MaintenanceTask(
            vehicleId = "v", area = Area.ENGINE, title = "Oil",
            timeIntervalDays = days, meterInterval = meter, meterUnit = unit,
            baselineDate = base, baselineMeter = baselineMeter, status = TaskStatus.OK,
            leadDays = DueCalculator.leadDays(days, 0.10), createdAt = base, updatedAt = base,
        )

    private fun eval(t: MaintenanceTask, v: Vehicle, now: Long) = DueCalculator.recompute(t, v, now, zone)

    @Test fun leadDays_isSmallerOf14AndTenPercent() {
        assertEquals(14, DueCalculator.leadDays(182, 0.10)) // 18 capped at 14
        assertEquals(3, DueCalculator.leadDays(30, 0.10))
        assertEquals(1, DueCalculator.leadDays(5, 0.10)) // never 0
        assertEquals(14, DueCalculator.leadDays(null, 0.10))
    }

    @Test fun meterLead_capsAt500MilesOr10Hours() {
        assertEquals(500.0, DueCalculator.meterLead(10_000, MeterUnit.MILES, 0.10), 0.0)
        assertEquals(300.0, DueCalculator.meterLead(3_000, MeterUnit.MILES, 0.10), 0.0)
        assertEquals(10.0, DueCalculator.meterLead(200, MeterUnit.ENGINE_HOURS, 0.10), 0.0)
        assertEquals(5.0, DueCalculator.meterLead(50, MeterUnit.ENGINE_HOURS, 0.10), 0.0)
    }

    @Test fun noIntervals_isManual_neverAutoDue() {
        val r = eval(task(), vehicle(), at(2030, 1, 1))
        assertEquals(TaskStatus.MANUAL, r.status)
        assertNull(r.dueDate)
        assertNull(r.dueMeter)
    }

    @Test fun timeOnly_okThenDueSoonThenOverdue() {
        val t = task(days = 182) // due 2026-09-12, lead 14
        assertEquals(LocalDate.of(2026, 9, 12), DueCalculator.localDate(eval(t, vehicle(), base).dueDate!!, zone))
        assertEquals(TaskStatus.OK, eval(t, vehicle(), at(2026, 8, 28)).status) // 15 days out
        assertEquals(TaskStatus.DUE_SOON, eval(t, vehicle(), at(2026, 8, 29)).status) // 14 days out
        assertEquals(TaskStatus.OVERDUE, eval(t, vehicle(), at(2026, 9, 12, 0)).status) // due day itself
    }

    @Test fun meterOnly_flipsOnReadings() {
        val t = task(meter = 5_000, unit = MeterUnit.MILES, baselineMeter = 41_220.0) // due 46,220, lead 500
        assertEquals(46_220.0, eval(t, vehicle(41_220.0), base).dueMeter!!, 0.0)
        assertEquals(TaskStatus.OK, eval(t, vehicle(45_719.0), base).status)
        assertEquals(TaskStatus.DUE_SOON, eval(t, vehicle(45_720.0), base).status)
        assertEquals(TaskStatus.OVERDUE, eval(t, vehicle(46_220.0), base).status)
    }

    @Test fun meterAxisIdle_withoutBaselineOrReading() {
        val t = task(meter = 5_000, unit = MeterUnit.MILES)
        val r = eval(t, vehicle(), at(2030, 1, 1))
        assertNull(r.dueMeter)
        assertEquals(TaskStatus.OK, r.status)
    }

    @Test fun both_whicheverFirstWins() {
        val t = task(days = 182, meter = 5_000, unit = MeterUnit.MILES, baselineMeter = 41_220.0)
        // Miles past due while calendar still fine.
        assertEquals(TaskStatus.OVERDUE, eval(t, vehicle(46_300.0), at(2026, 4, 1)).status)
        // Calendar past due while miles fine.
        assertEquals(TaskStatus.OVERDUE, eval(t, vehicle(41_500.0), at(2026, 10, 1)).status)
        // One axis due-soon, other fine.
        assertEquals(TaskStatus.DUE_SOON, eval(t, vehicle(45_900.0), at(2026, 4, 1)).status)
        assertEquals(TaskStatus.OK, eval(t, vehicle(41_500.0), at(2026, 4, 1)).status)
    }

    @Test fun meterDrivePastDue_dueSoonToOverdue() {
        val t = task(meter = 100, unit = MeterUnit.ENGINE_HOURS, baselineMeter = 200.0)
        val soon = eval(t, vehicle(eng = 292.0), base)
        assertEquals(TaskStatus.DUE_SOON, soon.status)
        assertEquals(TaskStatus.OVERDUE, eval(soon, vehicle(eng = 301.0), base).status)
    }

    @Test fun dstBoundary_keepsCalendarDay() {
        // Baseline the day before US spring-forward; a 1-day interval must land on the next local day.
        val before = LocalDateTime.of(2026, 3, 7, 23, 30).atZone(zone).toInstant().toEpochMilli()
        val due = DueCalculator.dueDate(before, 1, zone)!!
        assertEquals(LocalDate.of(2026, 3, 8), DueCalculator.localDate(due, zone))
        val fallBack = LocalDateTime.of(2026, 10, 31, 23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 1), DueCalculator.localDate(DueCalculator.dueDate(fallBack, 1, zone)!!, zone))
    }
}
