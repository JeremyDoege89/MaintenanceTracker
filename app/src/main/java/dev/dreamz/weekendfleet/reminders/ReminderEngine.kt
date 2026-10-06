package dev.dreamz.weekendfleet.reminders

import androidx.room.withTransaction
import dev.dreamz.weekendfleet.data.db.AppDatabase
import dev.dreamz.weekendfleet.data.model.MaintenanceTask
import dev.dreamz.weekendfleet.data.model.Reminder
import dev.dreamz.weekendfleet.data.model.ReminderKind
import dev.dreamz.weekendfleet.data.model.TaskStatus
import dev.dreamz.weekendfleet.data.model.Vehicle
import dev.dreamz.weekendfleet.domain.DueCalculator
import dev.dreamz.weekendfleet.domain.DueText
import dev.dreamz.weekendfleet.domain.QuietHours
import dev.dreamz.weekendfleet.domain.ReminderPlanner
import dev.dreamz.weekendfleet.domain.ReminderPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** The one OS alarm the engine keeps armed. */
interface AlarmScheduler {
    /** Arms the wake-up for [at], replacing any earlier one; null cancels it. */
    fun scheduleNext(at: Long?)
}

data class ReminderNotification(
    val taskId: String,
    val kind: ReminderKind,
    val level: Int,
    /** Row whose snooze buttons this notification carries. */
    val reminderId: String,
    val title: String,
    val text: String,
)

interface Notifier {
    fun post(n: ReminderNotification)
    fun cancel(taskId: String)
}

/**
 * Reminder engine, Build Guide §6.3–6.5.
 *
 * Reminder rows are the source of truth (one per task × kind × escalation level), so everything can
 * be re-armed after a reboot. Only ONE OS alarm is kept: the earliest pending row. When it fires,
 * every row that is due is delivered and the alarm re-arms — no per-row alarms to leak or to hit
 * Android's per-app alarm cap.
 */
class ReminderEngine(
    private val db: AppDatabase,
    private val prefs: suspend () -> ReminderPrefs,
    private val alarms: AlarmScheduler,
    private val notifier: Notifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()
    private val rows = db.reminders()

    /** Re-plan reminders for one vehicle's tasks (or all), deliver crossed meter rows, re-arm. */
    suspend fun sync(vehicleId: String?) = mutex.withLock {
        val p = prefs()
        val now = clock()
        val vehicles = if (vehicleId != null) listOfNotNull(db.vehicles().get(vehicleId)) else db.vehicles().all()
        for (v in vehicles) {
            for (t in db.tasks().forVehicle(v.id)) syncTask(t, v, p, now)
        }
        armNext(p)
    }

    private suspend fun syncTask(t: MaintenanceTask, v: Vehicle, p: ReminderPrefs, now: Long) {
        val current = t.meterUnit?.let(v::meter)
        val planned = ReminderPlanner.plan(t, v, p, zone())
        val changes = ReminderPlanner.reconcile(t.id, rows.forTask(t.id), planned, now, current)
        if (changes.delete.isNotEmpty() || changes.insert.isNotEmpty()) {
            db.withTransaction {
                if (changes.delete.isNotEmpty()) rows.deleteAll(changes.delete)
                if (changes.insert.isNotEmpty()) rows.insertAll(changes.insert)
            }
        }
        // Meter reminders are computed, not alarmed (§6.5): they go out when a reading crosses them.
        val crossed = ReminderPlanner.meterRowsDue(rows.forTask(t.id), current, now)
        if (crossed.isNotEmpty()) {
            val deliverAt = QuietHours.nextAllowed(now, p, zone())
            if (deliverAt > now) {
                crossed.forEach { rows.update(it.copy(snoozedUntil = deliverAt)) } // the alarm picks them up
            } else {
                deliver(t, v, crossed, now)
            }
        }
        // Completed, disabled or archived: an old "overdue" notification would now be wrong.
        if (planned.isEmpty() || t.status == TaskStatus.OK) notifier.cancel(t.id)
    }

    /** Alarm fired (or the daily check ran): deliver every row whose moment has come, then re-arm. */
    suspend fun deliverDue() = mutex.withLock {
        val p = prefs()
        val now = clock()
        val due = rows.pendingTimed().filter { r -> ReminderPlanner.fireAt(r, p, zone())?.let { it <= now + SLACK_MS } == true }
        for ((taskId, list) in due.groupBy { it.taskId }) {
            val t = db.tasks().get(taskId) ?: continue
            val v = db.vehicles().get(t.vehicleId) ?: continue
            if (!t.isActive || !t.notifyEnabled || v.archived) {
                list.forEach { rows.update(it.copy(deliveredAt = now)) }
                continue
            }
            deliver(t, v, list, now)
        }
        armNext(p)
    }

    /**
     * Posts ONE notification per task — the most severe of [due] — and marks them all delivered.
     * A phone that was off for a month gets "still overdue", not a stack of stale nudges.
     */
    private suspend fun deliver(t: MaintenanceTask, v: Vehicle, due: List<Reminder>, now: Long) {
        val top = due.maxWith(compareBy<Reminder>({ severity(it.kind) }, { it.escalationLevel }))
        val fresh = DueCalculator.recompute(t, v, now, zone())
        val overdue = top.kind == ReminderKind.OVERDUE_TIME || top.kind == ReminderKind.OVERDUE_METER
        val title = when {
            overdue && top.escalationLevel > 0 -> "Still overdue: ${t.title}"
            overdue -> "Overdue: ${t.title}"
            else -> "Due soon: ${t.title}"
        }
        val reason = DueText.reason(fresh, t.meterUnit?.let(v::meter), now, zone())
        notifier.post(
            ReminderNotification(t.id, top.kind, top.escalationLevel, top.id, title,
                listOf(v.name, reason).filter { it.isNotEmpty() }.joinToString(" · ")),
        )
        due.forEach { rows.update(it.copy(deliveredAt = now)) }
    }

    /** Notification action. Always finite (3 days / 1 week) — no indefinite snooze (§6.4). */
    suspend fun snooze(reminderId: String, days: Int) = mutex.withLock {
        val r = rows.get(reminderId) ?: return@withLock
        val until = clock() + TimeUnit.DAYS.toMillis(days.toLong().coerceIn(1, 7))
        rows.update(r.copy(snoozedUntil = until, deliveredAt = null, dismissedAt = null))
        notifier.cancel(r.taskId)
        armNext(prefs())
    }

    /** User swiped the notification away. */
    suspend fun dismiss(reminderId: String) = mutex.withLock {
        rows.get(reminderId)?.let { rows.update(it.copy(dismissedAt = clock())) }
    }

    /** After boot / clock change: just re-arm from the table. */
    suspend fun rearm() = mutex.withLock { armNext(prefs()) }

    /** Earliest pending reminder overall, or for one task. */
    suspend fun nextFireAt(taskId: String? = null): Long? {
        val p = prefs()
        return rows.pendingTimed().filter { taskId == null || it.taskId == taskId }
            .mapNotNull { ReminderPlanner.fireAt(it, p, zone()) }.minOrNull()
    }

    private suspend fun armNext(p: ReminderPrefs) {
        val now = clock()
        val next = rows.pendingTimed().mapNotNull { ReminderPlanner.fireAt(it, p, zone()) }.minOrNull()
        // Anything already overdue to fire (e.g. phone was off) goes out right away.
        alarms.scheduleNext(next?.coerceAtLeast(now + SLACK_MS))
    }

    private fun severity(k: ReminderKind) = when (k) {
        ReminderKind.OVERDUE_TIME, ReminderKind.OVERDUE_METER -> 1
        ReminderKind.DUE_SOON_TIME, ReminderKind.DUE_SOON_METER -> 0
    }

    companion object {
        /** Alarms can arrive a little early/late; treat anything within this as due. */
        const val SLACK_MS = 30_000L
    }
}
