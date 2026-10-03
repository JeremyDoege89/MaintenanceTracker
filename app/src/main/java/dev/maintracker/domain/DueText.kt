package dev.maintracker.domain

import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.TemplateKind
import java.time.ZoneId
import kotlin.math.abs

/** Human due lines for cards and rows. */
object DueText {

    /** "Due 2026-09-14 / 51,220 mi". Null for checklist tasks and finished one-time tasks. */
    fun dueLine(t: MaintenanceTask, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (t.isFinishedOneTime) return null
        val parts = listOfNotNull(
            t.dueDate?.let { Format.date(it, zone) },
            if (t.dueMeter != null && t.meterUnit != null) Format.meter(t.dueMeter, t.meterUnit) else null,
        )
        if (parts.isEmpty()) {
            return if (t.meterInterval != null && t.baselineMeter == null) "Enter a reading to start the meter clock" else null
        }
        return "Due " + parts.joinToString(" / ")
    }

    /** "2 days / 380 mi over" or "due in 5 days". */
    fun reason(t: MaintenanceTask, currentMeter: Double?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = t.dueDate?.let { DueCalculator.daysUntil(it, now, zone) }
        val meter = if (t.dueMeter != null && currentMeter != null) t.dueMeter - currentMeter else null
        val over = buildList {
            if (time != null && time <= 0) add(if (time == 0L) "due today" else "${abs(time)} ${plural(abs(time), "day")}")
            if (meter != null && meter <= 0 && t.meterUnit != null) add(Format.meter(abs(meter), t.meterUnit))
        }
        if (over.isNotEmpty()) {
            return if (over == listOf("due today")) "Due today" else over.joinToString(" / ") + " over"
        }
        val soon = buildList {
            if (time != null) add("$time ${plural(time, "day")}")
            if (meter != null && t.meterUnit != null) add(Format.meter(meter, t.meterUnit))
        }
        return if (soon.isEmpty()) "" else "Due in " + soon.joinToString(" or ")
    }

    private fun plural(n: Long, word: String) = if (n == 1L) word else "${word}s"
}

/** A break-in / first service that has been logged: retired, nothing more comes due (§5 step 8). */
val MaintenanceTask.isFinishedOneTime: Boolean
    get() = kind == TemplateKind.ONE_TIME && !isActive && completedCount > 0
