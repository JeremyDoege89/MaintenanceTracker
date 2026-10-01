package dev.maintracker.domain

import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Status rules, Build Guide §6.1–6.2. Pure; no Android imports.
 *
 * Time and meter axes are evaluated independently from their baselines; whichever comes first wins.
 * Dates are calendar arithmetic in the device zone so a DST change never shifts a due day.
 */
object DueCalculator {
    const val MAX_LEAD_DAYS = 14
    private const val MILES_LEAD_CAP = 500.0
    private const val HOURS_LEAD_CAP = 10.0

    /** Smaller of [cap] (14 days unless changed in Settings) and pct of the time interval, at least 1 day. */
    fun leadDays(timeIntervalDays: Int?, pct: Double, cap: Int = MAX_LEAD_DAYS): Int =
        timeIntervalDays?.let { min(cap, max(1, floor(it * pct).toInt())) } ?: cap

    /** Smaller of 500 mi (or 10 hrs) and pct of the meter interval. */
    fun meterLead(meterInterval: Int, unit: MeterUnit, pct: Double): Double {
        val cap = if (unit == MeterUnit.MILES) MILES_LEAD_CAP else HOURS_LEAD_CAP
        return min(cap, meterInterval * pct)
    }

    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** Start of the local due day. */
    fun dueDate(baselineDate: Long, timeIntervalDays: Int?, zone: ZoneId): Long? =
        timeIntervalDays?.let {
            localDate(baselineDate, zone).plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        }

    fun dueMeter(baselineMeter: Double?, meterInterval: Int?): Double? =
        if (baselineMeter != null && meterInterval != null) baselineMeter + meterInterval else null

    fun daysUntil(dueDate: Long, now: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(localDate(now, zone), localDate(dueDate, zone))

    fun status(task: MaintenanceTask, currentMeter: Double?, now: Long, zone: ZoneId): TaskStatus {
        if (task.timeIntervalDays == null && task.meterInterval == null) return TaskStatus.MANUAL

        var overdue = false
        var dueSoon = false

        task.dueDate?.let {
            val days = daysUntil(it, now, zone)
            if (days <= 0) overdue = true else if (days <= task.leadDays) dueSoon = true
        }
        val dueMeter = task.dueMeter
        val unit = task.meterUnit
        if (dueMeter != null && unit != null && currentMeter != null && task.meterInterval != null) {
            val remaining = dueMeter - currentMeter
            if (remaining <= 0) {
                overdue = true
            } else if (remaining <= meterLead(task.meterInterval, unit, task.dueSoonPct)) {
                dueSoon = true
            }
        }
        return when {
            overdue -> TaskStatus.OVERDUE
            dueSoon -> TaskStatus.DUE_SOON
            else -> TaskStatus.OK
        }
    }

    /** Recomputes the derived columns (dueDate, dueMeter, status). Persist the result. */
    fun recompute(task: MaintenanceTask, vehicle: Vehicle, now: Long, zone: ZoneId): MaintenanceTask {
        val withDue = task.copy(
            dueDate = dueDate(task.baselineDate, task.timeIntervalDays, zone),
            dueMeter = dueMeter(task.baselineMeter, task.meterInterval),
        )
        val current = task.meterUnit?.let(vehicle::meter)
        return withDue.copy(status = status(withDue, current, now, zone))
    }
}
