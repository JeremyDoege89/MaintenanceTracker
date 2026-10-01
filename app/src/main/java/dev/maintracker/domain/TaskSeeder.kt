package dev.maintracker.domain

import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import java.time.ZoneId

/** Clones templates into per-vehicle tasks (Build Guide §4, "Template seeding rule"). */
object TaskSeeder {

    /**
     * Baseline date = vehicle creation. Baseline meter = the vehicle's current reading for that
     * unit, else purchaseMeter when it is in that unit. If neither exists the baseline stays null
     * (meter axis idle) until a first reading arrives — see [fillMissingBaselines]. We never invent
     * a 0 baseline, which would make a 60,000-mile rig instantly overdue.
     */
    fun seed(
        vehicle: Vehicle,
        templates: List<MaintenanceTemplate>,
        now: Long,
        zone: ZoneId,
        leadCap: Int = DueCalculator.MAX_LEAD_DAYS,
    ): List<MaintenanceTask> =
        templates.map { t ->
            val baselineMeter = t.meterUnit?.let { unit ->
                vehicle.meter(unit)
                    ?: vehicle.purchaseMeter.takeIf { unit == vehicle.vehicleType.primaryMeter }
            }
            val task = MaintenanceTask(
                vehicleId = vehicle.id,
                templateId = t.id,
                area = t.area,
                title = t.title,
                description = t.description,
                timeIntervalDays = t.timeIntervalDays,
                meterInterval = t.meterInterval,
                meterUnit = t.meterUnit,
                dueSoonPct = t.defaultDueSoonPct,
                baselineDate = vehicle.createdAt,
                baselineMeter = baselineMeter,
                status = TaskStatus.OK,
                leadDays = DueCalculator.leadDays(t.timeIntervalDays, t.defaultDueSoonPct, leadCap),
                notes = t.notes,
                sortOrder = t.sortOrder,
                createdAt = now,
                updatedAt = now,
            )
            DueCalculator.recompute(task, vehicle, now, zone)
        }

    /** Starts the meter axis for tasks seeded before the vehicle had a reading in their unit. */
    fun fillMissingBaselines(tasks: List<MaintenanceTask>, vehicle: Vehicle, now: Long): List<MaintenanceTask> =
        tasks.mapNotNull { task ->
            val unit = task.meterUnit ?: return@mapNotNull null
            if (task.baselineMeter != null || task.meterInterval == null) return@mapNotNull null
            val reading = vehicle.meter(unit) ?: return@mapNotNull null
            task.copy(baselineMeter = reading, updatedAt = now)
        }
}
