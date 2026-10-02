package dev.maintracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.Attachment
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.Reminder
import dev.maintracker.data.model.ServiceRecord
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import kotlinx.coroutines.flow.Flow

data class VehicleSummary(
    @Embedded val vehicle: Vehicle,
    val overdueCount: Int,
    val dueSoonCount: Int,
    val taskCount: Int,
)

data class DueItem(
    @Embedded val task: MaintenanceTask,
    val vehicleName: String,
    val vMiles: Double?,
    val vEngineHours: Double?,
    val vGeneratorHours: Double?,
) {
    fun currentMeter(unit: MeterUnit): Double? = when (unit) {
        MeterUnit.MILES -> vMiles
        MeterUnit.ENGINE_HOURS -> vEngineHours
        MeterUnit.GENERATOR_HOURS -> vGeneratorHours
    }
}

@Dao
interface VehicleDao {
    @Query(
        """
        SELECT v.*,
          (SELECT COUNT(*) FROM maintenance_task t
             WHERE t.vehicleId = v.id AND t.isActive = 1 AND t.status = 'OVERDUE') AS overdueCount,
          (SELECT COUNT(*) FROM maintenance_task t
             WHERE t.vehicleId = v.id AND t.isActive = 1 AND t.status = 'DUE_SOON') AS dueSoonCount,
          (SELECT COUNT(*) FROM maintenance_task t
             WHERE t.vehicleId = v.id AND t.isActive = 1) AS taskCount
        FROM vehicle v
        WHERE :includeArchived OR v.archived = 0
        ORDER BY v.archived, v.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(includeArchived: Boolean): Flow<List<VehicleSummary>>

    @Query("SELECT * FROM vehicle WHERE id = :id")
    fun observe(id: String): Flow<Vehicle?>

    @Query("SELECT * FROM vehicle WHERE id = :id")
    suspend fun get(id: String): Vehicle?

    @Query("SELECT * FROM vehicle")
    suspend fun all(): List<Vehicle>

    @Query("SELECT COUNT(*) FROM vehicle")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vehicle WHERE archived = 0")
    suspend fun activeCount(): Int

    @Insert
    suspend fun insert(vehicle: Vehicle)

    /** Everything goes with them: tasks, records, parts, attachments, reminders cascade. */
    @Query("DELETE FROM vehicle")
    suspend fun deleteAll()

    @Update
    suspend fun update(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)
}

@Dao
interface TemplateDao {
    /** IGNORE so shipped rows never clobber user edits to existing template ids. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(templates: List<MaintenanceTemplate>)

    @Query("SELECT id FROM maintenance_template")
    suspend fun ids(): List<String>

    @Query("SELECT * FROM maintenance_template WHERE vehicleType = :type ORDER BY sortOrder")
    suspend fun forType(type: VehicleType): List<MaintenanceTemplate>

    @Query("SELECT COUNT(*) FROM maintenance_template WHERE isSeeded = 1")
    suspend fun countSeeded(): Int
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insertAll(tasks: List<MaintenanceTask>)

    @Query("SELECT * FROM maintenance_task")
    suspend fun all(): List<MaintenanceTask>

    @Update
    suspend fun updateAll(tasks: List<MaintenanceTask>)

    @Delete
    suspend fun delete(task: MaintenanceTask)

    @Query(
        """
        SELECT * FROM maintenance_task WHERE vehicleId = :vehicleId
        ORDER BY isActive DESC,
          CASE status WHEN 'OVERDUE' THEN 0 WHEN 'DUE_SOON' THEN 1 WHEN 'OK' THEN 2 ELSE 3 END,
          area, sortOrder
        """,
    )
    fun observeForVehicle(vehicleId: String): Flow<List<MaintenanceTask>>

    @Query("SELECT * FROM maintenance_task WHERE vehicleId = :vehicleId")
    suspend fun forVehicle(vehicleId: String): List<MaintenanceTask>

    @Query("SELECT * FROM maintenance_task WHERE id = :id")
    suspend fun get(id: String): MaintenanceTask?

    @Query("SELECT * FROM maintenance_task WHERE id = :id")
    fun observe(id: String): Flow<MaintenanceTask?>

    @Insert
    suspend fun insert(task: MaintenanceTask)

    @Update
    suspend fun update(task: MaintenanceTask)

    @Query(
        """
        SELECT t.*, v.name AS vehicleName, v.currentMiles AS vMiles,
               v.currentEngineHours AS vEngineHours, v.currentGeneratorHours AS vGeneratorHours
        FROM maintenance_task t JOIN vehicle v ON v.id = t.vehicleId
        WHERE t.isActive = 1 AND v.archived = 0 AND t.status IN ('OVERDUE', 'DUE_SOON')
        ORDER BY CASE t.status WHEN 'OVERDUE' THEN 0 ELSE 1 END, t.dueDate
        """,
    )
    fun observeDue(): Flow<List<DueItem>>
}

@Dao
interface ServiceRecordDao {
    @Insert
    suspend fun insert(record: ServiceRecord)

    @Query("SELECT * FROM service_record ORDER BY date, createdAt")
    suspend fun all(): List<ServiceRecord>

    @Update
    suspend fun update(record: ServiceRecord)

    @Delete
    suspend fun delete(record: ServiceRecord)

    @Query("SELECT * FROM service_record WHERE vehicleId = :vehicleId ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(vehicleId: String, limit: Int): Flow<List<ServiceRecord>>

    @Query("SELECT * FROM service_record WHERE id = :id")
    suspend fun get(id: String): ServiceRecord?

    @Query("SELECT * FROM service_record WHERE id = :id")
    fun observe(id: String): Flow<ServiceRecord?>

    @Query("SELECT COUNT(*) FROM service_record WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: String): Int

    @Query("SELECT * FROM service_record WHERE vehicleId = :vehicleId ORDER BY date DESC, createdAt DESC")
    fun observeForVehicle(vehicleId: String): Flow<List<ServiceRecord>>

    /** History tab: filters are optional; [query] matches title, description, vendor, parts and notes. */
    @Query(
        """
        SELECT * FROM service_record
        WHERE vehicleId = :vehicleId
          AND (:area IS NULL OR area = :area)
          AND (:taskId IS NULL OR taskId = :taskId)
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'
               OR vendor LIKE '%' || :query || '%' OR partsText LIKE '%' || :query || '%'
               OR notes LIKE '%' || :query || '%')
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeHistory(vehicleId: String, area: Area?, taskId: String?, query: String): Flow<List<ServiceRecord>>

    @Query("SELECT * FROM service_record WHERE taskId = :taskId ORDER BY date DESC, createdAt DESC")
    fun observeForTask(taskId: String): Flow<List<ServiceRecord>>

    /** Latest completion of a task other than [excludeId] — used to rebase after a delete. */
    @Query(
        """
        SELECT * FROM service_record WHERE taskId = :taskId AND id != :excludeId
        ORDER BY date DESC, createdAt DESC LIMIT 1
        """,
    )
    suspend fun latestForTask(taskId: String, excludeId: String): ServiceRecord?

    /** Recent distinct vendors for suggestion chips, newest first. */
    @Query(
        """
        SELECT vendor FROM service_record WHERE vendor IS NOT NULL AND vendor != ''
        GROUP BY vendor COLLATE NOCASE ORDER BY MAX(createdAt) DESC LIMIT :limit
        """,
    )
    fun observeRecentVendors(limit: Int): Flow<List<String>>
}

@Dao
interface PartDao {
    @Insert
    suspend fun insert(part: Part)

    @Query("SELECT * FROM part")
    suspend fun all(): List<Part>

    @Update
    suspend fun update(part: Part)

    @Delete
    suspend fun delete(part: Part)

    @Query("SELECT * FROM part WHERE id = :id")
    suspend fun get(id: String): Part?

    @Query("SELECT COUNT(*) FROM part WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: String): Int

    @Query("SELECT * FROM part WHERE vehicleId = :vehicleId ORDER BY name COLLATE NOCASE")
    fun observeForVehicle(vehicleId: String): Flow<List<Part>>

    @Query("SELECT * FROM part WHERE taskId = :taskId ORDER BY name COLLATE NOCASE")
    fun observeForTask(taskId: String): Flow<List<Part>>
}

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insert(attachment: Attachment)

    @Query("SELECT * FROM attachment WHERE vehicleId = :vehicleId")
    suspend fun forVehicle(vehicleId: String): List<Attachment>

    @Update
    suspend fun update(attachment: Attachment)

    @Delete
    suspend fun delete(attachment: Attachment)

    @Query("SELECT * FROM attachment WHERE vehicleId = :vehicleId ORDER BY createdAt DESC")
    fun observeForVehicle(vehicleId: String): Flow<List<Attachment>>

    @Query("SELECT * FROM attachment WHERE serviceRecordId = :recordId ORDER BY createdAt")
    fun observeForRecord(recordId: String): Flow<List<Attachment>>

    @Query("SELECT * FROM attachment WHERE serviceRecordId = :recordId")
    suspend fun forRecord(recordId: String): List<Attachment>

    @Query("UPDATE attachment SET serviceRecordId = :recordId WHERE id IN (:ids)")
    suspend fun relink(ids: List<String>, recordId: String?)

    @Query("SELECT * FROM attachment")
    suspend fun all(): List<Attachment>

    @Query("SELECT * FROM attachment WHERE id = :id")
    suspend fun get(id: String): Attachment?
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: Reminder)

    @Insert
    suspend fun insertAll(reminders: List<Reminder>)

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun deleteAll(reminders: List<Reminder>)

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: String): Reminder?

    @Query("SELECT * FROM reminder WHERE taskId = :taskId")
    suspend fun forTask(taskId: String): List<Reminder>

    @Query("SELECT * FROM reminder WHERE taskId = :taskId")
    fun observeForTask(taskId: String): Flow<List<Reminder>>

    /** Everything that still has a moment to fire at: date rows, plus snoozed/deferred meter rows. */
    @Query("SELECT * FROM reminder WHERE deliveredAt IS NULL AND (triggerAt IS NOT NULL OR snoozedUntil IS NOT NULL)")
    suspend fun pendingTimed(): List<Reminder>
}
