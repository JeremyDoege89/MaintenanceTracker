package dev.maintracker.domain

import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TaskCompletionTest {
    private val zone = ZoneId.of("America/Denver")
    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 9, 0).atZone(zone).toInstant().toEpochMilli()
    private fun day(ms: Long) = DueCalculator.localDate(ms, zone)

    private val created = at(2026, 1, 1)
    private fun vehicle(miles: Double? = null, eng: Double? = null) = Vehicle(
        id = "v", name = "Rig", vehicleType = VehicleType.MOTORHOME, currentMiles = miles,
        currentEngineHours = eng, createdAt = created, updatedAt = created,
    )

    private fun task(days: Int?, meter: Int?, unit: MeterUnit?, baselineMeter: Double?, now: Long = created) =
        DueCalculator.recompute(
            MaintenanceTask(
                vehicleId = "v", area = Area.ENGINE, title = "Oil", timeIntervalDays = days, meterInterval = meter,
                meterUnit = unit, baselineDate = created, baselineMeter = baselineMeter, status = TaskStatus.OK,
                leadDays = DueCalculator.leadDays(days, 0.10), createdAt = created, updatedAt = created,
            ),
            vehicle(baselineMeter), now, zone,
        )

    @Test fun timeOnly_nextDueFromCompletionDate() {
        val done = at(2026, 6, 1)
        val r = TaskCompletion.complete(task(365, null, null, null), vehicle(), "r1", done, MeterReadings(), done, zone)
        assertEquals(LocalDate.of(2027, 6, 1), day(r.dueDate!!))
        assertNull(r.dueMeter)
        assertEquals(TaskStatus.OK, r.status)
        assertEquals("r1", r.lastRecordId)
        assertEquals(1, r.completedCount)
    }

    @Test fun meterOnly_nextDueFromEnteredReading() {
        val now = at(2026, 6, 1)
        val v = vehicle(miles = 52_000.0)
        val r = TaskCompletion.complete(task(null, 5_000, MeterUnit.MILES, 41_000.0), v, "r1", now,
            MeterReadings(miles = 52_000.0), now, zone)
        assertEquals(57_000.0, r.dueMeter!!, 0.0)
        assertNull(r.dueDate)
        assertEquals(TaskStatus.OK, r.status)
    }

    @Test fun both_overdueTaskBecomesOkWithBothAxesReset() {
        val now = at(2026, 9, 1)
        val v = vehicle(miles = 47_000.0)
        val overdue = task(182, 5_000, MeterUnit.MILES, 41_000.0, now)
        assertEquals(TaskStatus.OVERDUE, overdue.status)
        val r = TaskCompletion.complete(overdue, v, "r1", now, MeterReadings(miles = 47_000.0), now, zone)
        assertEquals(LocalDate.of(2027, 3, 2), day(r.dueDate!!))
        assertEquals(52_000.0, r.dueMeter!!, 0.0)
        assertEquals(TaskStatus.OK, r.status)
    }

    @Test fun completedToday_withoutReading_usesVehicleCurrent() {
        val now = at(2026, 6, 1)
        val r = TaskCompletion.complete(task(null, 100, MeterUnit.ENGINE_HOURS, 10.0), vehicle(eng = 95.0), "r1",
            now, MeterReadings(), now, zone)
        assertEquals(95.0, r.baselineMeter!!, 0.0)
    }

    @Test fun backdated_withoutReading_keepsOldMeterBaseline() {
        val now = at(2026, 6, 1)
        val r = TaskCompletion.complete(task(365, 100, MeterUnit.ENGINE_HOURS, 10.0), vehicle(eng = 95.0), "r1",
            at(2026, 5, 1), MeterReadings(), now, zone)
        assertEquals(10.0, r.baselineMeter!!, 0.0)
        assertEquals(LocalDate.of(2026, 5, 1), day(r.baselineDate))
    }

    @Test fun olderThanLastCompletion_onlyCountsHistory() {
        val now = at(2026, 6, 1)
        val done = task(365, null, null, null).copy(lastRecordId = "r0", baselineDate = at(2026, 5, 1), completedCount = 1)
        val r = TaskCompletion.complete(done, vehicle(), "r1", at(2025, 5, 1), MeterReadings(), now, zone)
        assertEquals(done.baselineDate, r.baselineDate)
        assertEquals("r0", r.lastRecordId)
        assertEquals(2, r.completedCount)
    }

    @Test fun neverCompleted_olderRealDateReplacesSeedGuess() {
        val now = at(2026, 6, 1)
        val r = TaskCompletion.complete(task(365, null, null, null), vehicle(), "r1", at(2025, 11, 1),
            MeterReadings(), now, zone)
        assertEquals(LocalDate.of(2026, 11, 1), day(r.dueDate!!))
    }

    @Test fun mergeMeters_neverWindsBack_andMarksSource() {
        val v = vehicle(miles = 50_000.0, eng = 300.0)
        assertSame(v, TaskCompletion.mergeMeters(v, MeterReadings(miles = 49_000.0), 5))
        val m = TaskCompletion.mergeMeters(v, MeterReadings(miles = 49_000.0, engineHours = 310.0), 5)
        assertEquals(50_000.0, m.currentMiles!!, 0.0)
        assertEquals(310.0, m.currentEngineHours!!, 0.0)
        assertEquals("record", m.metersSource)
        assertEquals(5L, m.metersUpdatedAt)
    }

    @Test fun meterLogPastDue_flipsDueSoonToOverdue() {
        val t = task(null, 3_000, MeterUnit.MILES, 10_000.0)
        val soon = DueCalculator.recompute(t, TaskCompletion.mergeMeters(vehicle(10_000.0), MeterReadings(12_800.0), 1), created, zone)
        assertEquals(TaskStatus.DUE_SOON, soon.status)
        val over = DueCalculator.recompute(soon, TaskCompletion.mergeMeters(vehicle(12_800.0), MeterReadings(13_000.0), 2), created, zone)
        assertEquals(TaskStatus.OVERDUE, over.status)
    }
}
