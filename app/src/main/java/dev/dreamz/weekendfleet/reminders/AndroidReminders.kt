package dev.dreamz.weekendfleet.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dev.dreamz.weekendfleet.R
import dev.dreamz.weekendfleet.data.model.ReminderKind
import dev.dreamz.weekendfleet.ui.MainActivity

/**
 * Exact while-idle alarm when the user allowed it; otherwise an inexact while-idle alarm that
 * Android may deliver some minutes late (§6.3: fall back, never crash, never nag). Reminders are
 * day-granular, so late-by-minutes is acceptable.
 */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    override fun scheduleNext(at: Long?) {
        val pi = ReminderReceiver.pending(context, ReminderReceiver.ACTION_FIRE)
        if (at == null) {
            am.cancel(pi)
            return
        }
        if (canScheduleExact()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class AndroidNotifier(private val context: Context) : Notifier {

    /** One channel per class so users can mute them independently (§6.3). */
    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_DUE_SOON, "Due soon", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Maintenance coming due by date" },
                NotificationChannel(CH_OVERDUE, "Overdue", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Maintenance past its due date, and repeat nudges" },
                NotificationChannel(CH_METER, "Mileage & hours", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Maintenance due by odometer or hour-meter reading" },
            ),
        )
    }

    override fun post(n: ReminderNotification) {
        val nm = NotificationManagerCompat.from(context)
        // Denied: in-app badges still work; reminders resume once granted (§6.3).
        if (!nm.areNotificationsEnabled()) return
        val channel = when (n.kind) {
            ReminderKind.DUE_SOON_TIME -> CH_DUE_SOON
            ReminderKind.OVERDUE_TIME -> CH_OVERDUE
            ReminderKind.DUE_SOON_METER, ReminderKind.OVERDUE_METER -> CH_METER
        }
        val open = PendingIntent.getActivity(
            context, n.taskId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TASK_ID, n.taskId)
                .setData("maintracker://task/${n.taskId}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setDeleteIntent(ReminderReceiver.pending(context, ReminderReceiver.ACTION_DISMISS, n.reminderId))
            .addAction(0, "Snooze 3 days", ReminderReceiver.pending(context, ReminderReceiver.ACTION_SNOOZE, n.reminderId, 3))
            .addAction(0, "Snooze 1 week", ReminderReceiver.pending(context, ReminderReceiver.ACTION_SNOOZE, n.reminderId, 7))
            .build()
        try {
            nm.notify(n.taskId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    override fun cancel(taskId: String) = NotificationManagerCompat.from(context).cancel(taskId.hashCode())

    companion object {
        const val CH_DUE_SOON = "due_soon"
        const val CH_OVERDUE = "overdue"
        const val CH_METER = "meter"
    }
}
