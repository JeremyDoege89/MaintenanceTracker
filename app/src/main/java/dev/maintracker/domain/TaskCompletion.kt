package dev.maintracker.domain

import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.Vehicle
import java.time.ZoneId

/** Meter values typed into a record or a quick meter log; null = not entered. */
data class MeterReadings(
    val miles: Double? = null,
    val engineHours: Double? = null,
    val generatorHours: Double? = null,
) {
    operator fun get(unit: MeterUnit): Double? = when (unit) {
        MeterUnit.MILES -> miles
        MeterUnit.ENGINE_HOURS -> engineHours
        MeterUnit.GENERATOR_HOURS -> generatorHours
    }

    val isEmpty: Boolean get() = miles == null && engineHours == null && generatorHours == null
}

/** The "Mark complete" loop, Build Guide §5.5–5.6. Pure; the repository persists the results. */
object TaskCompletion {

    /**
     * Vehicle.current* is the largest reading seen (§3.1), so a back-dated record never winds the
     * odometer back. Any entered reading at or above the current one also refreshes metersUpdatedAt.
     */
    fun mergeMeters(v: Vehicle, r: MeterReadings, now: Long): Vehicle {
        if (r.isEmpty) return v
        fun pick(current: Double?, entered: Double?) =
            if (entered != null && (current == null || entered >= current)) entered else current
        val miles = pick(v.currentMiles, r.miles)
        val eng = pick(v.currentEngineHours, r.engineHours)
        val gen = pick(v.currentGeneratorHours, r.generatorHours)
        val fresh = MeterUnit.entries.any { u -> r[u] != null && r[u] == pick(v.meter(u), r[u]) }
        if (!fresh) return v
        return v.copy(
            currentMiles = miles,
            currentEngineHours = eng,
            currentGeneratorHours = gen,
            metersSource = "record",
            metersUpdatedAt = now,
            updatedAt = now,
        )
    }

    /**
     * Resets the task's baseline to this completion and recomputes due fields.
     *
     * A completion dated before the current baseline only counts toward history — unless the task
     * has never been completed, in which case the baseline is just the seeding guess and an older
     * real date is better information.
     *
     * Baseline meter: the reading entered on the record; else, for a completion logged today, the
     * vehicle's current reading; else the previous baseline (we never invent a reading).
     */
    fun complete(
        task: MaintenanceTask,
        vehicle: Vehicle,
        recordId: String,
        date: Long,
        readings: MeterReadings,
        now: Long,
        zone: ZoneId,
    ): MaintenanceTask {
        val counted = task.copy(completedCount = task.completedCount + 1, updatedAt = now)
        val rebase = task.lastRecordId == null || date >= task.baselineDate
        if (!rebase) return counted

        val unit = task.meterUnit
        val sameDay = DueCalculator.localDate(date, zone) == DueCalculator.localDate(now, zone)
        val meter = unit?.let { readings[it] ?: vehicle.meter(it).takeIf { sameDay } } ?: task.baselineMeter
        val rebased = counted.copy(baselineDate = date, baselineMeter = meter, lastRecordId = recordId)
        return DueCalculator.recompute(rebased, vehicle, now, zone)
    }
}
