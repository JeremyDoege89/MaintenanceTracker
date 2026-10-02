package dev.maintracker.data

import androidx.room.withTransaction
import dev.maintracker.data.db.AppDatabase
import dev.maintracker.data.db.DueItem
import dev.maintracker.data.db.VehicleSummary
import dev.maintracker.data.model.AttachmentKind
import dev.maintracker.data.model.Attachment
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.ServiceRecord
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VendorType
import dev.maintracker.data.model.VehicleType
import dev.maintracker.data.seed.SeedTemplates
import dev.maintracker.domain.DueCalculator
import dev.maintracker.domain.MeterReadings
import dev.maintracker.domain.TaskCompletion
import dev.maintracker.domain.TaskSeeder
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/** A service record as typed in the quick-add sheet. */
data class ServiceEntry(
    val vehicleId: String,
    val taskId: String?,
    val area: Area,
    val title: String,
    val description: String?,
    val date: Long,
    val readings: MeterReadings,
    val cost: Double?,
    val currency: String,
    val vendor: String?,
    val partsText: String?,
    val notes: String?,
    val vendorType: VendorType? = null,
)

/** Everything one write changed, so the 10-second Undo can put it back exactly. */
class UndoSnapshot internal constructor(
    internal val vehicle: Vehicle?,
    internal val task: MaintenanceTask?,
    internal val insertedRecordId: String?,
    internal val deletedRecord: ServiceRecord?,
    /** Attachments unlinked from [deletedRecord] (kept in the vehicle's Documents); undo relinks them. */
    internal val detachedAttachments: List<String> = emptyList(),
)

class VehicleRepository(
    private val db: AppDatabase,
    private val photos: PhotoStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** Global due-soon cap from Settings (§6.2). */
    private val leadCap: suspend () -> Int = { DueCalculator.MAX_LEAD_DAYS },
    /** Runs after every committed write that can change what should be reminded (vehicle id, or null = all). */
    private val onChanged: suspend (vehicleId: String?) -> Unit = {},
) {
    private val vehicles = db.vehicles()
    private val tasks = db.tasks()
    private val records = db.records()
    private val parts = db.parts()

    fun observeSummaries(includeArchived: Boolean): Flow<List<VehicleSummary>> =
        vehicles.observeSummaries(includeArchived)

    fun observeVehicleCount(): Flow<Int> = vehicles.observeCount()
    suspend fun activeVehicleCount(): Int = vehicles.activeCount()
    fun observeVehicle(id: String): Flow<Vehicle?> = vehicles.observe(id)
    fun observeTasks(vehicleId: String): Flow<List<MaintenanceTask>> = tasks.observeForVehicle(vehicleId)
    fun observeRecentRecords(vehicleId: String, limit: Int = 3): Flow<List<ServiceRecord>> =
        db.records().observeRecent(vehicleId, limit)
    fun observeDue(): Flow<List<DueItem>> = tasks.observeDue()
    fun observeTask(id: String): Flow<MaintenanceTask?> = tasks.observe(id)
    fun observeRecord(id: String): Flow<ServiceRecord?> = records.observe(id)
    fun observeHistory(vehicleId: String, area: Area?, taskId: String?, query: String): Flow<List<ServiceRecord>> =
        records.observeHistory(vehicleId, area, taskId, query.trim())
    // ---- Attachments (Build Guide §3.6; Pro-gated in the UI, never in the data layer) ----

    fun observeAttachments(vehicleId: String): Flow<List<Attachment>> = db.attachments().observeForVehicle(vehicleId)
    fun observeRecordAttachments(recordId: String): Flow<List<Attachment>> = db.attachments().observeForRecord(recordId)

    /** Copies [source] into private storage and records it. Null if the file can't be read. */
    suspend fun addAttachment(
        vehicleId: String, source: android.net.Uri, kind: AttachmentKind, caption: String?, recordId: String? = null,
    ): Attachment? {
        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { photos.importAttachment(source) } ?: return null
        val a = Attachment(
            vehicleId = vehicleId, serviceRecordId = recordId, kind = kind, fileName = file.fileName,
            mimeType = file.mimeType, byteSize = file.byteSize, caption = caption?.trim()?.ifEmpty { null },
            createdAt = clock(),
        )
        db.attachments().insert(a)
        return a
    }

    suspend fun updateAttachment(a: Attachment) = db.attachments().update(a)

    suspend fun deleteAttachment(a: Attachment) {
        db.attachments().delete(a)
        photos.deleteAttachment(a.fileName)
    }

    fun observeAllRecords(vehicleId: String): Flow<List<ServiceRecord>> = records.observeForVehicle(vehicleId)
    fun observeRecordsForTask(taskId: String): Flow<List<ServiceRecord>> = records.observeForTask(taskId)
    fun observeRecentVendors(limit: Int = 6): Flow<List<String>> = records.observeRecentVendors(limit)
    fun observeParts(vehicleId: String): Flow<List<Part>> = parts.observeForVehicle(vehicleId)
    fun observePartsForTask(taskId: String): Flow<List<Part>> = parts.observeForTask(taskId)

    suspend fun getTask(id: String): MaintenanceTask? = tasks.get(id)
    suspend fun getRecord(id: String): ServiceRecord? = records.get(id)
    suspend fun getPart(id: String): Part? = parts.get(id)
    suspend fun tasksFor(vehicleId: String): List<MaintenanceTask> = tasks.forVehicle(vehicleId)

    suspend fun getVehicle(id: String): Vehicle? = vehicles.get(id)

    /** Idempotent: inserts any shipped template rows not already present, never overwrites edits. */
    suspend fun ensureSeedTemplates() {
        db.templates().insertIgnore(SeedTemplates.all)
    }

    suspend fun templatesFor(type: VehicleType): List<MaintenanceTemplate> {
        // The app-start seed runs async; don't let a fast first tap see an empty schedule.
        if (db.templates().countSeeded() == 0) ensureSeedTemplates()
        return db.templates().forType(type)
    }

    /** Inserts the vehicle and the user-selected seeded tasks in one transaction. */
    suspend fun createVehicle(draft: Vehicle, selectedTemplateIds: Set<String>): Vehicle {
        val now = clock()
        val vehicle = draft.copy(
            createdAt = now,
            updatedAt = now,
            metersSource = "manual".takeIf { hasAnyMeter(draft) },
            metersUpdatedAt = now.takeIf { hasAnyMeter(draft) },
        )
        val chosen = templatesFor(vehicle.vehicleType).filter { it.id in selectedTemplateIds }
        db.withTransaction {
            vehicles.insert(vehicle)
            tasks.insertAll(TaskSeeder.seed(vehicle, chosen, now, zone(), leadCap()))
        }
        onChanged(vehicle.id)
        return vehicle
    }

    suspend fun updateVehicle(edited: Vehicle) {
        val now = clock()
        val old = vehicles.get(edited.id) ?: return
        val metersChanged = old.currentMiles != edited.currentMiles ||
            old.currentEngineHours != edited.currentEngineHours ||
            old.currentGeneratorHours != edited.currentGeneratorHours
        val vehicle = edited.copy(
            updatedAt = now,
            metersSource = if (metersChanged) "manual" else edited.metersSource,
            metersUpdatedAt = if (metersChanged) now else edited.metersUpdatedAt,
        )
        if (old.photoUri != null && old.photoUri != vehicle.photoUri) photos.delete(old.photoUri)
        db.withTransaction {
            vehicles.update(vehicle)
            refreshTasks(vehicle, now)
        }
        onChanged(vehicle.id)
    }

    suspend fun updateMeters(id: String, miles: Double?, engineHours: Double?, generatorHours: Double?) {
        val v = vehicles.get(id) ?: return
        updateVehicle(v.copy(currentMiles = miles, currentEngineHours = engineHours, currentGeneratorHours = generatorHours))
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val v = vehicles.get(id) ?: return
        vehicles.update(v.copy(archived = archived, updatedAt = clock()))
        onChanged(id)
    }

    /** Cascades tasks, records, parts, attachments, reminders (FKs), then removes private files. */
    suspend fun deleteVehicle(id: String) {
        val v = vehicles.get(id) ?: return
        val attachmentFiles = db.attachments().forVehicle(id).map { it.fileName }
        vehicles.delete(v)
        v.photoUri?.let(photos::delete)
        attachmentFiles.forEach(photos::deleteAttachment)
        onChanged(null) // reminder rows cascade-deleted; re-arm the alarm
    }

    // ---- Service log (Build Guide §5.5) ----

    /**
     * One write: the record, the vehicle's meters (largest seen), the linked task's new baseline,
     * then every task's status. Returns what's needed to undo it.
     */
    suspend fun logService(e: ServiceEntry): UndoSnapshot {
        val now = clock()
        return db.withTransaction {
            val oldVehicle = vehicles.get(e.vehicleId) ?: error("No vehicle ${e.vehicleId}")
            val oldTask = e.taskId?.let { tasks.get(it) }
            val record = ServiceRecord(
                vehicleId = e.vehicleId, taskId = oldTask?.id, area = e.area, title = e.title.trim(),
                description = e.description, date = e.date,
                miles = e.readings.miles, engineHours = e.readings.engineHours, generatorHours = e.readings.generatorHours,
                cost = e.cost, currency = e.currency, vendor = e.vendor, vendorType = e.vendorType, partsText = e.partsText, notes = e.notes,
                createdAt = now,
            )
            records.insert(record)
            val vehicle = TaskCompletion.mergeMeters(oldVehicle, e.readings, now)
            if (vehicle != oldVehicle) vehicles.update(vehicle)
            if (oldTask != null) {
                tasks.update(TaskCompletion.complete(oldTask, vehicle, record.id, e.date, e.readings, now, zone()))
            }
            refreshTasks(vehicle, now)
            UndoSnapshot(oldVehicle, oldTask, record.id, null)
        }.also { onChanged(e.vehicleId) }
    }

    /** Quick "meters only" log: no record, just readings + status recompute (§6.5). */
    suspend fun logMeters(vehicleId: String, readings: MeterReadings): UndoSnapshot {
        val now = clock()
        return db.withTransaction {
            val old = vehicles.get(vehicleId) ?: error("No vehicle $vehicleId")
            val v = TaskCompletion.mergeMeters(old, readings, now)
            if (v != old) vehicles.update(v)
            refreshTasks(v, now)
            UndoSnapshot(old, null, null, null)
        }.also { onChanged(vehicleId) }
    }

    /**
     * Edits a record in place. If it is its task's latest completion, the task's baseline follows
     * the edited date/reading. Meters still only ever move forward.
     */
    suspend fun updateRecord(edited: ServiceRecord) {
        val now = clock()
        db.withTransaction {
            records.update(edited)
            val old = vehicles.get(edited.vehicleId) ?: return@withTransaction
            val readings = MeterReadings(edited.miles, edited.engineHours, edited.generatorHours)
            val v = TaskCompletion.mergeMeters(old, readings, now)
            if (v != old) vehicles.update(v)
            val task = edited.taskId?.let { tasks.get(it) }
            if (task != null && task.lastRecordId == edited.id) {
                val meter = task.meterUnit?.let { readings[it] } ?: task.baselineMeter
                tasks.update(task.copy(baselineDate = edited.date, baselineMeter = meter, updatedAt = now))
            }
            refreshTasks(v, now)
        }
        onChanged(edited.vehicleId)
    }

    /**
     * Deletes a record. Meters are NOT rolled back (§3.8). If it was its task's latest completion,
     * the task falls back to the previous one; with none left the baseline stays put.
     */
    suspend fun deleteRecord(id: String): UndoSnapshot? {
        val now = clock()
        return db.withTransaction {
            val rec = records.get(id) ?: return@withTransaction null
            val oldTask = rec.taskId?.let { tasks.get(it) }
            // Receipts outlive the record: they move to the vehicle's Documents instead of cascading away.
            val detached = db.attachments().forRecord(rec.id).map { it.id }
            db.attachments().relink(detached, null)
            records.delete(rec) // FK nulls task.lastRecordId; we set the right value below.
            if (oldTask != null) {
                var t = oldTask.copy(completedCount = (oldTask.completedCount - 1).coerceAtLeast(0), updatedAt = now)
                if (oldTask.lastRecordId == rec.id) {
                    val prev = records.latestForTask(oldTask.id, rec.id)
                    t = if (prev != null) {
                        val meter = oldTask.meterUnit?.let { MeterReadings(prev.miles, prev.engineHours, prev.generatorHours)[it] }
                        t.copy(baselineDate = prev.date, baselineMeter = meter ?: oldTask.baselineMeter, lastRecordId = prev.id)
                    } else {
                        t.copy(lastRecordId = null)
                    }
                }
                tasks.update(t)
            }
            vehicles.get(rec.vehicleId)?.let { refreshTasks(it, now) }
            UndoSnapshot(null, oldTask, null, rec, detached)
        }?.also { onChanged(it.deletedRecord?.vehicleId) }
    }

    suspend fun undo(s: UndoSnapshot) {
        val now = clock()
        db.withTransaction {
            s.insertedRecordId?.let { id ->
                // Anything attached in the undo window goes too; rows cascade, files we remove.
                db.attachments().forRecord(id).forEach { photos.deleteAttachment(it.fileName) }
                records.get(id)?.let { records.delete(it) }
            }
            s.deletedRecord?.let {
                records.insert(it)
                if (s.detachedAttachments.isNotEmpty()) db.attachments().relink(s.detachedAttachments, it.id)
            }
            s.vehicle?.let { vehicles.update(it) }
            s.task?.let { tasks.update(it) }
            vehicleId(s)?.let { vehicles.get(it) }?.let { refreshTasks(it, now) }
        }
        onChanged(vehicleId(s))
    }

    private fun vehicleId(s: UndoSnapshot) = s.vehicle?.id ?: s.task?.vehicleId ?: s.deletedRecord?.vehicleId

    // ---- Tasks ----

    /** Inserts or updates a task; due fields, lead window and status are always recomputed. */
    suspend fun saveTask(task: MaintenanceTask) {
        val now = clock()
        db.withTransaction {
            val vehicle = vehicles.get(task.vehicleId) ?: return@withTransaction
            val existing = tasks.get(task.id)
            val t = DueCalculator.recompute(
                task.copy(
                    leadDays = DueCalculator.leadDays(task.timeIntervalDays, task.dueSoonPct, leadCap()),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
                vehicle, now, zone(),
            )
            if (existing == null) tasks.insert(t) else tasks.update(t)
        }
        onChanged(task.vehicleId)
    }

    suspend fun setTaskActive(id: String, active: Boolean) {
        val t = tasks.get(id) ?: return
        tasks.update(t.copy(isActive = active, updatedAt = clock()))
        onChanged(t.vehicleId)
    }

    /** Records and parts keep existing with taskId = null; reminders cascade (§3.8). */
    suspend fun deleteTask(id: String) {
        val t = tasks.get(id) ?: return
        tasks.delete(t)
        onChanged(t.vehicleId)
    }

    // ---- Parts ----

    suspend fun savePart(part: Part) {
        if (parts.get(part.id) == null) parts.insert(part) else parts.update(part)
    }

    suspend fun deletePart(id: String) {
        parts.get(id)?.let { parts.delete(it) }
    }

    /** Daily rollover / app start: statuses are persisted, so re-evaluate against today. */
    suspend fun recomputeAll() {
        val now = clock()
        db.withTransaction {
            vehicles.all().forEach { refreshTasks(it, now) }
        }
        onChanged(null)
    }

    /** Settings changed the global due-soon cap: every task's lead window follows. */
    suspend fun applyLeadCap() {
        val cap = leadCap()
        val now = clock()
        db.withTransaction {
            for (v in vehicles.all()) {
                val changed = tasks.forVehicle(v.id).map { it.copy(leadDays = DueCalculator.leadDays(it.timeIntervalDays, it.dueSoonPct, cap)) }
                tasks.updateAll(changed)
                refreshTasks(v, now)
            }
        }
        onChanged(null)
    }

    fun observeReminders(taskId: String) = db.reminders().observeForTask(taskId)

    private suspend fun refreshTasks(vehicle: Vehicle, now: Long) {
        val current = tasks.forVehicle(vehicle.id)
        val filled = TaskSeeder.fillMissingBaselines(current, vehicle, now).associateBy { it.id }
        val updated = current.mapNotNull { t ->
            val base = filled[t.id] ?: t
            val r = DueCalculator.recompute(base, vehicle, now, zone())
            if (r != t) r else null
        }
        if (updated.isNotEmpty()) tasks.updateAll(updated)
    }

    private fun hasAnyMeter(v: Vehicle) =
        v.currentMiles != null || v.currentEngineHours != null || v.currentGeneratorHours != null
}
