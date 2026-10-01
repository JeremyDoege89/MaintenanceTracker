package dev.maintracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// Ids are UUID strings everywhere (Build Guide §3: easier if sync ever lands).
// Timestamps are epoch millis. Money carries an ISO 4217 code.

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "vehicle")
data class Vehicle(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val vehicleType: VehicleType,
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val vinOrHin: String? = null,
    val purchaseDate: Long? = null,
    /** In [VehicleType.primaryMeter] units. */
    val purchaseMeter: Double? = null,
    val currentMiles: Double? = null,
    val currentEngineHours: Double? = null,
    val currentGeneratorHours: Double? = null,
    /** 'manual' or 'record' — provenance of the current* readings. */
    val metersSource: String? = null,
    /** Path relative to filesDir, never an external URI. */
    val photoUri: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
    /** When current* meters were last entered; drives "reading is N days old" nudges. */
    val metersUpdatedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun meter(unit: MeterUnit): Double? = when (unit) {
        MeterUnit.MILES -> currentMiles
        MeterUnit.ENGINE_HOURS -> currentEngineHours
        MeterUnit.GENERATOR_HOURS -> currentGeneratorHours
    }
}

@Entity(tableName = "maintenance_template", indices = [Index("vehicleType")])
data class MaintenanceTemplate(
    @PrimaryKey val id: String,
    val vehicleType: VehicleType,
    val area: Area,
    val title: String,
    val description: String,
    val timeIntervalDays: Int? = null,
    val meterInterval: Int? = null,
    val meterUnit: MeterUnit? = null,
    val defaultDueSoonPct: Double = 0.10,
    val notes: String? = null,
    val isSeeded: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "maintenance_task",
    foreignKeys = [
        ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = CASCADE),
        ForeignKey(MaintenanceTemplate::class, ["id"], ["templateId"], onDelete = SET_NULL),
        ForeignKey(ServiceRecord::class, ["id"], ["lastRecordId"], onDelete = SET_NULL),
    ],
    indices = [Index("vehicleId"), Index("templateId"), Index("lastRecordId"), Index("status")],
)
data class MaintenanceTask(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val templateId: String? = null,
    val area: Area,
    /** Free text when area == OTHER. */
    val areaDetail: String? = null,
    val title: String,
    val description: String? = null,
    val timeIntervalDays: Int? = null,
    val meterInterval: Int? = null,
    val meterUnit: MeterUnit? = null,
    val dueSoonPct: Double = 0.10,
    /** Last completed date. */
    val baselineDate: Long,
    /** Meter at last completion, in [meterUnit]. Null until a reading exists. */
    val baselineMeter: Double? = null,
    val lastRecordId: String? = null,
    val dueDate: Long? = null,
    val dueMeter: Double? = null,
    val status: TaskStatus,
    /** Due-soon window in days. */
    val leadDays: Int,
    val notifyEnabled: Boolean = true,
    val isActive: Boolean = true,
    val completedCount: Int = 0,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "service_record",
    foreignKeys = [
        ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = CASCADE),
        ForeignKey(MaintenanceTask::class, ["id"], ["taskId"], onDelete = SET_NULL),
    ],
    indices = [Index("vehicleId"), Index("taskId"), Index("date")],
)
data class ServiceRecord(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val taskId: String? = null,
    val area: Area,
    val title: String,
    val description: String? = null,
    val date: Long,
    val miles: Double? = null,
    val engineHours: Double? = null,
    val generatorHours: Double? = null,
    val cost: Double? = null,
    val currency: String,
    val vendor: String? = null,
    val partsText: String? = null,
    val notes: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "part",
    foreignKeys = [
        ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = CASCADE),
        ForeignKey(MaintenanceTask::class, ["id"], ["taskId"], onDelete = SET_NULL),
    ],
    indices = [Index("vehicleId"), Index("taskId")],
)
data class Part(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val taskId: String? = null,
    val name: String,
    val partNumber: String? = null,
    val manufacturer: String? = null,
    val torqueSpec: String? = null,
    val fluidSpec: String? = null,
    val quantity: String? = null,
    val notes: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "attachment",
    foreignKeys = [
        ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = CASCADE),
        ForeignKey(ServiceRecord::class, ["id"], ["serviceRecordId"], onDelete = CASCADE),
        ForeignKey(MaintenanceTask::class, ["id"], ["taskId"], onDelete = SET_NULL),
    ],
    indices = [Index("vehicleId"), Index("serviceRecordId"), Index("taskId")],
)
data class Attachment(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val serviceRecordId: String? = null,
    val taskId: String? = null,
    val kind: AttachmentKind,
    /** File name inside app-private attachments dir. */
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val caption: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "reminder",
    foreignKeys = [ForeignKey(MaintenanceTask::class, ["id"], ["taskId"], onDelete = CASCADE)],
    indices = [Index("taskId"), Index("triggerAt")],
)
data class Reminder(
    @PrimaryKey val id: String = newId(),
    val taskId: String,
    val kind: ReminderKind,
    val triggerAt: Long? = null,
    val meterTrigger: Double? = null,
    val deliveredAt: Long? = null,
    val dismissedAt: Long? = null,
    val snoozedUntil: Long? = null,
    val escalationLevel: Int = 0,
)
