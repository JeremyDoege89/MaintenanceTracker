package dev.maintracker.domain

import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.Reminder
import dev.maintracker.data.model.ReminderKind
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class ReminderPrefs(
    /** Local time of day date-based reminders go off. */
    val remindAtMinute: Int = 9 * 60,
    val quietEnabled: Boolean = true,
    val quietStartMinute: Int = 21 * 60,
    val quietEndMinute: Int = 7 * 60,
    /** Repeat overdue nudges at day 7 and day 30 (Build Guide §6.4). */
    val escalation: Boolean = true,
)

/** Quiet hours: anything landing inside the window is delivered when it ends (§6.3). */
object QuietHours {
    fun isQuiet(minuteOfDay: Int, p: ReminderPrefs): Boolean {
        if (!p.quietEnabled || p.quietStartMinute == p.quietEndMinute) return false
        val s = p.quietStartMinute
        val e = p.quietEndMinute
        return if (s < e) minuteOfDay in s until e else minuteOfDay >= s || minuteOfDay < e
    }

    /** [t] itself if allowed, else the next quiet-window end in local wall-clock time. */
    fun nextAllowed(t: Long, p: ReminderPrefs, zone: ZoneId): Long {
        val z = Instant.ofEpochMilli(t).atZone(zone)
        if (!isQuiet(z.hour * 60 + z.minute, p)) return t
        val end = LocalTime.of(p.quietEndMinute / 60, p.quietEndMinute % 60)
        var day = z.toLocalDate()
        if (z.toLocalTime() >= end) day = day.plusDays(1)
        return day.atTime(end).atZone(zone).toInstant().toEpochMilli()
    }
}

/** What reminders a task should have right now; one row per (kind, level). */
data class PlannedReminder(val kind: ReminderKind, val level: Int, val triggerAt: Long?, val meterTrigger: Double?)

data class ReminderChanges(val delete: List<Reminder>, val insert: List<Reminder>)

/** Pure planning for the reminder engine (Build Guide §6). The engine persists and schedules. */
object ReminderPlanner {
    val ESCALATION_DAYS = listOf(7L, 30L)

    fun plan(task: MaintenanceTask, vehicle: Vehicle, p: ReminderPrefs, zone: ZoneId): List<PlannedReminder> {
        if (!task.isActive || !task.notifyEnabled || vehicle.archived || task.status == TaskStatus.MANUAL) return emptyList()
        val at = LocalTime.of(p.remindAtMinute / 60, p.remindAtMinute % 60)
        fun on(day: java.time.LocalDate) = day.atTime(at).atZone(zone).toInstant().toEpochMilli()
        return buildList {
            task.dueDate?.let { due ->
                // Calendar arithmetic in the device zone: survives DST (§13 "Reminder DST change").
                val day = DueCalculator.localDate(due, zone)
                add(PlannedReminder(ReminderKind.DUE_SOON_TIME, 0, on(day.minusDays(task.leadDays.toLong())), null))
                add(PlannedReminder(ReminderKind.OVERDUE_TIME, 0, on(day), null))
                if (p.escalation) {
                    ESCALATION_DAYS.forEachIndexed { i, d ->
                        add(PlannedReminder(ReminderKind.OVERDUE_TIME, i + 1, on(day.plusDays(d)), null))
                    }
                }
            }
            val dueMeter = task.dueMeter
            val unit = task.meterUnit
            val interval = task.meterInterval
            if (dueMeter != null && unit != null && interval != null) {
                val lead = DueCalculator.meterLead(interval, unit, task.dueSoonPct)
                add(PlannedReminder(ReminderKind.DUE_SOON_METER, 0, null, dueMeter - lead))
                add(PlannedReminder(ReminderKind.OVERDUE_METER, 0, null, dueMeter))
            }
        }
    }

    /**
     * Diff stored rows against the plan. A row whose trigger is unchanged is kept as-is — including
     * its delivered/snoozed/dismissed state — so nothing fires twice. New rows whose moment has
     * already passed are created delivered: the app shows the status, and a fresh edit or seeding
     * never sets off a burst of stale notifications.
     */
    fun reconcile(
        taskId: String,
        existing: List<Reminder>,
        planned: List<PlannedReminder>,
        now: Long,
        currentMeter: Double?,
    ): ReminderChanges {
        fun same(r: Reminder, pl: PlannedReminder) =
            r.kind == pl.kind && r.escalationLevel == pl.level &&
                (if (pl.triggerAt != null) r.triggerAt == pl.triggerAt else r.meterTrigger == pl.meterTrigger)

        val keep = existing.filter { r -> planned.any { same(r, it) } }
        val delete = existing - keep.toSet()
        val insert = planned.filter { pl -> keep.none { same(it, pl) } }.map { pl ->
            val passed = if (pl.triggerAt != null) pl.triggerAt <= now
            else currentMeter != null && pl.meterTrigger != null && currentMeter >= pl.meterTrigger
            Reminder(
                taskId = taskId, kind = pl.kind, escalationLevel = pl.level, triggerAt = pl.triggerAt,
                meterTrigger = pl.meterTrigger, deliveredAt = now.takeIf { passed },
            )
        }
        return ReminderChanges(delete, insert)
    }

    /**
     * When an undelivered row should fire: its trigger or snooze, pushed out of quiet hours.
     * triggerAt is never rewritten (it is the row's identity); deferrals live in snoozedUntil.
     */
    fun fireAt(r: Reminder, p: ReminderPrefs, zone: ZoneId): Long? {
        val base = listOfNotNull(r.triggerAt, r.snoozedUntil).maxOrNull() ?: return null
        return QuietHours.nextAllowed(base, p, zone)
    }

    /** Meter rows crossed by the current reading and not yet delivered or snoozed. */
    fun meterRowsDue(rows: List<Reminder>, currentMeter: Double?, now: Long): List<Reminder> =
        if (currentMeter == null) emptyList()
        else rows.filter {
            it.meterTrigger != null && it.deliveredAt == null && currentMeter >= it.meterTrigger &&
                (it.snoozedUntil == null || it.snoozedUntil <= now)
        }
}
