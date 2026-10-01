package dev.maintracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.TaskStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Debug-only acceptance hook (Build Guide §12 M3: "set a reminder 2 min out -> fires").
 *
 *   adb shell am broadcast -n dev.maintracker/.DebugReminderReceiver -a remind_soon --ei minutes 2
 *   adb shell am broadcast -n dev.maintracker/.DebugReminderReceiver -a cleanup --ei remindAt 540
 *
 * remind_soon adds a 1-day task "last done yesterday" (so it's due today) to the first vehicle and
 * moves "Remind me at" to N minutes from now — the real planner, alarm and notifier do the rest.
 */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MaintTrackerApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val s = app.settings.current()
                when (intent.action) {
                    "remind_soon" -> {
                        val minutes = intent.getIntExtra("minutes", 2)
                        val at = LocalTime.now().plusMinutes(minutes.toLong())
                        Log.i(TAG, "previous remindAt=${s.reminders.remindAtMinute}")
                        app.settings.setReminders(s.reminders.copy(remindAtMinute = at.hour * 60 + at.minute))
                        val v = app.database.vehicles().all().first { !it.archived }
                        val yesterday = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        app.repository.saveTask(
                            MaintenanceTask(
                                vehicleId = v.id, area = Area.OTHER, title = TITLE, timeIntervalDays = 1,
                                baselineDate = yesterday, status = TaskStatus.OK, leadDays = 1, createdAt = 0, updatedAt = 0,
                            ),
                        )
                        app.repository.recomputeAll()
                        Log.i(TAG, "vehicle=${v.name} next=${app.reminders.nextFireAt()} exact=${app.alarms.canScheduleExact()}")
                    }
                    "cleanup" -> {
                        for (v in app.database.vehicles().all()) {
                            app.database.tasks().forVehicle(v.id).filter { it.title == TITLE }.forEach {
                                app.repository.deleteTask(it.id)
                            }
                        }
                        app.settings.setReminders(s.reminders.copy(remindAtMinute = intent.getIntExtra("remindAt", 9 * 60)))
                        app.repository.recomputeAll()
                        Log.i(TAG, "cleaned up; next=${app.reminders.nextFireAt()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DebugReminder"
        const val TITLE = "[debug] Test reminder"
    }
}
