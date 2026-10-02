package dev.maintracker.domain

import dev.maintracker.data.model.Attachment
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.ServiceRecord
import dev.maintracker.data.model.Vehicle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Export schema v1 (Build Guide §9.1). Rows are the Room entities verbatim, so every field
 * round-trips; timestamps are epoch millis, distances miles, money carries its ISO 4217 code.
 * Reminders aren't exported: they're derived and get re-planned after an import.
 *
 * Files (vehicle photos, attachments) travel only in the .zip form, next to this JSON as
 * `files/<path relative to the app's files dir>`; a bare .json export is rows only.
 */
@Serializable
data class Backup(
    val schemaVersion: Int = SCHEMA_VERSION,
    val app: String = APP_ID,
    val exportedAt: String,
    val appVersion: String? = null,
    /** True when photo/attachment files were exported alongside (zip). */
    val filesIncluded: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val tasks: List<MaintenanceTask> = emptyList(),
    @SerialName("serviceRecords") val records: List<ServiceRecord> = emptyList(),
    val parts: List<Part> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val APP_ID = "maintenance-tracker"
        const val ZIP_JSON_NAME = "backup.json"
        const val ZIP_FILES_DIR = "files/"
    }
}

/** A message fit to show the user; the import changed nothing. */
class BackupException(message: String) : Exception(message)

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true // a newer minor version's extra fields don't block an import
        explicitNulls = false
    }

    fun encode(b: Backup): String = json.encodeToString(Backup.serializer(), b)

    /** Parses and checks a backup before anything on the phone is touched. */
    fun decode(text: String): Backup {
        val b = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("This file isn't a Maintenance Tracker backup, or it's damaged.")
        } catch (e: IllegalArgumentException) {
            throw BackupException("This file isn't a Maintenance Tracker backup, or it's damaged.")
        }
        if (b.app != Backup.APP_ID) throw BackupException("This file came from a different app.")
        if (b.schemaVersion > Backup.SCHEMA_VERSION) {
            throw BackupException("This backup was made by a newer version of the app. Update the app, then import it.")
        }
        validate(b)
        return b
    }

    /** Every reference must point inside the file, and ids must be unique. */
    fun validate(b: Backup) {
        fun dupes(ids: List<String>) = ids.size != ids.toSet().size
        if (dupes(b.vehicles.map { it.id }) || dupes(b.tasks.map { it.id }) || dupes(b.records.map { it.id }) ||
            dupes(b.parts.map { it.id }) || dupes(b.attachments.map { it.id })
        ) throw BackupException("This backup has duplicate entries and can't be imported safely.")
        val vehicles = b.vehicles.map { it.id }.toSet()
        val tasks = b.tasks.map { it.id }.toSet()
        val records = b.records.map { it.id }.toSet()
        val broken = b.tasks.any { it.vehicleId !in vehicles || (it.lastRecordId != null && it.lastRecordId !in records) } ||
            b.records.any { it.vehicleId !in vehicles || (it.taskId != null && it.taskId !in tasks) } ||
            b.parts.any { it.vehicleId !in vehicles || (it.taskId != null && it.taskId !in tasks) } ||
            b.attachments.any {
                it.vehicleId !in vehicles || (it.serviceRecordId != null && it.serviceRecordId !in records) ||
                    (it.taskId != null && it.taskId !in tasks)
            }
        if (broken) throw BackupException("This backup is incomplete (some entries point to things that aren't in it).")
        // File names come from the backup: never let one climb out of the app's own folders.
        val unsafe = b.attachments.any { !safeName(it.fileName) } || b.vehicles.any { v -> v.photoUri?.let { !safePhoto(it) } == true }
        if (unsafe) throw BackupException("This backup has file names the app won't accept.")
    }

    fun safeName(name: String) = name.isNotBlank() && name.none { it == '/' || it == '\\' } && name != "." && name != ".."

    fun safePhoto(path: String) = path.startsWith("photos/") && safeName(path.removePrefix("photos/"))
}
