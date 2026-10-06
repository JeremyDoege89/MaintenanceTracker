package dev.dreamz.weekendfleet.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.dreamz.weekendfleet.MaintTrackerApp
import kotlinx.coroutines.launch

/** Alarm fire, notification snooze and notification dismiss. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MaintTrackerApp
        val id = intent.getStringExtra(EXTRA_REMINDER_ID)
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        // Statuses are persisted; bring them up to today before choosing what to say.
                        app.repository.recomputeAll()
                        app.reminders.deliverDue()
                    }
                    ACTION_SNOOZE -> if (id != null) app.reminders.snooze(id, intent.getIntExtra(EXTRA_DAYS, 3))
                    ACTION_DISMISS -> if (id != null) app.reminders.dismiss(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "reminder ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_FIRE = "dev.dreamz.weekendfleet.reminder.FIRE"
        const val ACTION_SNOOZE = "dev.dreamz.weekendfleet.reminder.SNOOZE"
        const val ACTION_DISMISS = "dev.dreamz.weekendfleet.reminder.DISMISS"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_DAYS = "days"

        /** Distinct data URIs keep each (action, row, days) PendingIntent separate. */
        fun pending(context: Context, action: String, reminderId: String? = null, days: Int = 0): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData("maintracker://reminder/${reminderId ?: "next"}/$days".toUri())
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_DAYS, days)
            return PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

/**
 * Reboot, app update, clock/time-zone change, exact-alarm permission change: alarms are gone or
 * wrong, so re-plan from the Reminder table (Build Guide §6.3; tested per §13).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MaintTrackerApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.repository.recomputeAll() // re-syncs every task and re-arms the alarm
                app.reminders.deliverDue() // anything missed while the phone was off
            } catch (e: Exception) {
                Log.e("BootReceiver", "re-register after ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Daily safety net: rolls statuses over to today, re-plans, delivers anything due. Also covers
 * the weekly meter re-evaluation of §6.2.
 */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MaintTrackerApp
        app.repository.recomputeAll()
        app.reminders.deliverDue()
        return Result.success()
    }

    companion object {
        const val NAME = "daily-check"
    }
}
