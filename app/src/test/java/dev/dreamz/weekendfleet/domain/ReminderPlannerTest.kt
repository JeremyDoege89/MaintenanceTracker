package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.data.model.Area
import dev.dreamz.weekendfleet.data.model.MaintenanceTask
import dev.dreamz.weekendfleet.data.model.MeterUnit
import dev.dreamz.weekendfleet.data.model.Reminder
import dev.dreamz.weekendfleet.data.model.ReminderKind
import dev.dreamz.weekendfleet.data.model.TaskStatus
import dev.dreamz.weekendfleet.data.model.Vehicle
import dev.dreamz.weekendfleet.data.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    private val prefs = ReminderPrefs() // 09:00, quiet 21:00–07:00, escalation on
    private val v = Vehicle(id = "v", name = "Rig", vehicleType = VehicleType.MOTORHOME, currentMiles = 10_000.0,
        createdAt = 0, updatedAt = 0)

    private fun task(days: Int? = 182, meter: Int? = null) = DueCalculator.recompute(
        MaintenanceTask(
            id = "t", vehicleId = "v", area = Area.ENGINE, title = "Oil", timeIntervalDays = days, meterInterval = meter,
            meterUnit = meter?.let { MeterUnit.MILES }, baselineDate = at(2026, 1, 10, 12), baselineMeter = 10_000.0,
            status = TaskStatus.OK, leadDays = DueCalculator.leadDays(days, 0.10), createdAt = 0, updatedAt = 0,
        ),
        v, at(2026, 1, 10, 12), zone,
    )

    // ---- Quiet hours ----

    @Test fun quiet_wrapsMidnight() {
        assertTrue(QuietHours.isQuiet(22 * 60, prefs))
        assertTrue(QuietHours.isQuiet(3 * 60, prefs))
        assertFalse(QuietHours.isQuiet(7 * 60, prefs))
        assertFalse(QuietHours.isQuiet(20 * 60 + 59, prefs))
        assertFalse(QuietHours.isQuiet(22 * 60, prefs.copy(quietEnabled = false)))
    }

    @Test fun quiet_sameDayWindow() {
        val p = prefs.copy(quietStartMinute = 12 * 60, quietEndMinute = 14 * 60)
        assertTrue(QuietHours.isQuiet(13 * 60, p))
        assertFalse(QuietHours.isQuiet(14 * 60, p))
    }

    @Test fun quiet_defersToWindowEnd() {
        assertEquals(at(2026, 3, 6, 7), QuietHours.nextAllowed(at(2026, 3, 5, 23, 15), prefs, zone)) // next morning
        assertEquals(at(2026, 3, 6, 7), QuietHours.nextAllowed(at(2026, 3, 6, 2), prefs, zone)) // same morning
        assertEquals(at(2026, 3, 6, 9), QuietHours.nextAllowed(at(2026, 3, 6, 9), prefs, zone)) // allowed
    }

    @Test fun quiet_overDstSpringForward_landsAt7LocalTime() {
        // US DST starts 2026-03-08 02:00; the night before is an hour short.
        val t = QuietHours.nextAllowed(at(2026, 3, 7, 23), prefs, zone)
        assertEquals(LocalDateTime.of(2026, 3, 8, 7, 0), local(t))
    }

    // ---- Planning ----

    @Test fun plan_timeTask_dueSoonOverdueAndEscalations_atRemindTime() {
        val t = task(182) // due 2026-07-11, lead 14
        val plan = ReminderPlanner.plan(t, v, prefs, zone)
        assertEquals(4, plan.size)
        val soon = plan.single { it.kind == ReminderKind.DUE_SOON_TIME }
        assertEquals(LocalDateTime.of(2026, 6, 27, 9, 0), local(soon.triggerAt!!))
        val overdue = plan.filter { it.kind == ReminderKind.OVERDUE_TIME }.sortedBy { it.level }
        assertEquals(listOf(0, 1, 2), overdue.map { it.level })
        assertEquals(
            listOf(LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 10)),
            overdue.map { local(it.triggerAt!!).toLocalDate() },
        )
    }

    @Test fun plan_acrossDstFallBack_staysAtNineLocal() {
        val t = task(300) // due 2026-11-06 — DST ends 2026-11-01
        val trig = ReminderPlanner.plan(t, v, prefs, zone).filter { it.triggerAt != null }
        trig.forEach { assertEquals(9, local(it.triggerAt!!).hour) }
    }

    @Test fun plan_noEscalationWhenOff() {
        assertEquals(2, ReminderPlanner.plan(task(182), v, prefs.copy(escalation = false), zone).size)
    }

    @Test fun plan_meterTask_dueSoonAndOverdueTriggers() {
        val plan = ReminderPlanner.plan(task(null, 5_000), v, prefs, zone)
        assertEquals(14_500.0, plan.single { it.kind == ReminderKind.DUE_SOON_METER }.meterTrigger!!, 0.0)
        assertEquals(15_000.0, plan.single { it.kind == ReminderKind.OVERDUE_METER }.meterTrigger!!, 0.0)
    }

    @Test fun plan_nothingForManualDisabledMutedArchived() {
        assertTrue(ReminderPlanner.plan(task(null, null), v, prefs, zone).isEmpty())
        assertTrue(ReminderPlanner.plan(task().copy(isActive = false), v, prefs, zone).isEmpty())
        assertTrue(ReminderPlanner.plan(task().copy(notifyEnabled = false), v, prefs, zone).isEmpty())
        assertTrue(ReminderPlanner.plan(task(), v.copy(archived = true), prefs, zone).isEmpty())
    }

    // ---- Reconcile ----

    @Test fun reconcile_keepsUnchangedRows_withTheirDeliveredState() {
        val plan = ReminderPlanner.plan(task(), v, prefs, zone)
        val first = ReminderPlanner.reconcile("t", emptyList(), plan, at(2026, 1, 10, 12), 10_000.0)
        assertEquals(4, first.insert.size)
        val delivered = first.insert.map { if (it.kind == ReminderKind.DUE_SOON_TIME) it.copy(deliveredAt = 5) else it }
        val again = ReminderPlanner.reconcile("t", delivered, plan, at(2026, 6, 28, 12), 10_000.0)
        assertTrue(again.insert.isEmpty())
        assertTrue(again.delete.isEmpty())
    }

    @Test fun reconcile_completionReplacesEveryRow() {
        val old = ReminderPlanner.reconcile("t", emptyList(), ReminderPlanner.plan(task(), v, prefs, zone), 0, null).insert
        val rebased = DueCalculator.recompute(task().copy(baselineDate = at(2026, 7, 1, 12)), v, at(2026, 7, 1, 12), zone)
        val ch = ReminderPlanner.reconcile("t", old, ReminderPlanner.plan(rebased, v, prefs, zone), at(2026, 7, 1, 12), null)
        assertEquals(4, ch.delete.size)
        assertEquals(4, ch.insert.size)
        assertTrue(ch.insert.all { it.deliveredAt == null })
    }

    @Test fun reconcile_rowsAlreadyPassed_areCreatedDelivered_soNoBurst() {
        val now = at(2026, 7, 20, 12) // past due-soon, due and +7 escalation
        val ch = ReminderPlanner.reconcile("t", emptyList(), ReminderPlanner.plan(task(), v, prefs, zone), now, null)
        assertEquals(3, ch.insert.count { it.deliveredAt != null })
        assertNull(ch.insert.single { it.escalationLevel == 2 }.deliveredAt)
        val meter = ReminderPlanner.reconcile("t", emptyList(), ReminderPlanner.plan(task(null, 5_000), v, prefs, zone),
            now, 14_600.0)
        assertNotNull(meter.insert.single { it.kind == ReminderKind.DUE_SOON_METER }.deliveredAt)
        assertNull(meter.insert.single { it.kind == ReminderKind.OVERDUE_METER }.deliveredAt)
    }

    // ---- Firing ----

    @Test fun fireAt_usesLaterOfTriggerAndSnooze_outsideQuietHours() {
        val r = Reminder(taskId = "t", kind = ReminderKind.OVERDUE_TIME, triggerAt = at(2026, 7, 11, 9))
        assertEquals(at(2026, 7, 11, 9), ReminderPlanner.fireAt(r, prefs, zone))
        assertEquals(at(2026, 7, 14, 10), ReminderPlanner.fireAt(r.copy(snoozedUntil = at(2026, 7, 14, 10)), prefs, zone))
        assertEquals(at(2026, 7, 15, 7), ReminderPlanner.fireAt(r.copy(snoozedUntil = at(2026, 7, 14, 22)), prefs, zone))
        assertNull(ReminderPlanner.fireAt(Reminder(taskId = "t", kind = ReminderKind.OVERDUE_METER, meterTrigger = 1.0), prefs, zone))
    }

    @Test fun meterRowsDue_respectsDeliveredAndSnooze() {
        val r = Reminder(taskId = "t", kind = ReminderKind.OVERDUE_METER, meterTrigger = 15_000.0)
        assertEquals(1, ReminderPlanner.meterRowsDue(listOf(r), 15_000.0, 0).size)
        assertTrue(ReminderPlanner.meterRowsDue(listOf(r), 14_999.0, 0).isEmpty())
        assertTrue(ReminderPlanner.meterRowsDue(listOf(r.copy(deliveredAt = 1)), 16_000.0, 0).isEmpty())
        assertTrue(ReminderPlanner.meterRowsDue(listOf(r.copy(snoozedUntil = 100)), 16_000.0, 50).isEmpty())
    }
}
