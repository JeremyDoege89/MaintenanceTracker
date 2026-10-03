package dev.maintracker.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dev.maintracker.data.db.AppDatabase
import dev.maintracker.domain.Backup
import dev.maintracker.domain.BackupCodec
import dev.maintracker.domain.BackupException
import dev.maintracker.domain.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportResult(val vehicles: Int, val records: Int, val attachments: Int, val skippedFiles: Int)

/**
 * Export / import / delete-all (Build Guide §5.7 Data, §9). Everything goes through Storage
 * Access Framework URIs the user picked; nothing is written anywhere else.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val photos: PhotoStore,
    private val repository: VehicleRepository,
    private val clearReminders: suspend () -> Unit,
) {
    private val resolver get() = context.contentResolver

    private suspend fun snapshot(filesIncluded: Boolean): Backup {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return Backup(
            exportedAt = Instant.now().toString(),
            appVersion = version,
            filesIncluded = filesIncluded,
            vehicles = db.vehicles().all(),
            tasks = db.tasks().all(),
            records = db.records().all(),
            parts = db.parts().all(),
            attachments = db.attachments().all(),
        )
    }

    /** Full fidelity. [withFiles] = zip with photos and attachments next to backup.json. */
    suspend fun exportBackup(target: Uri, withFiles: Boolean) = withContext(Dispatchers.IO) {
        val b = snapshot(withFiles)
        val out = resolver.openOutputStream(target, "wt") ?: throw BackupException("Couldn't write to that location.")
        out.use { stream ->
            if (!withFiles) {
                stream.write(BackupCodec.encode(b).toByteArray())
                return@use
            }
            ZipOutputStream(stream.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(Backup.ZIP_JSON_NAME))
                zip.write(BackupCodec.encode(b).toByteArray())
                zip.closeEntry()
                val files = b.vehicles.mapNotNull { it.photoUri } + b.attachments.map { photos.attachmentPath(it.fileName) }
                for (rel in files) {
                    val f = File(context.filesDir, rel)
                    if (!f.isFile) continue
                    zip.putNextEntry(ZipEntry(Backup.ZIP_FILES_DIR + rel))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun exportRecordsCsv(target: Uri) = writeText(target) {
        Csv.records(db.records().all(), db.vehicles().all(), db.tasks().all())
    }

    suspend fun exportTasksCsv(target: Uri) = writeText(target) { Csv.tasks(db.tasks().all(), db.vehicles().all()) }

    private suspend fun writeText(target: Uri, text: suspend () -> String) = withContext(Dispatchers.IO) {
        val body = text()
        (resolver.openOutputStream(target, "wt") ?: throw BackupException("Couldn't write to that location."))
            .use { it.write(body.toByteArray()) }
    }

    /** Parses and checks the file without changing anything — for the confirm dialog. */
    suspend fun preview(source: Uri): Backup = withContext(Dispatchers.IO) { read(source, staging = null) }

    /**
     * Replaces everything on the phone with [source]. The file is fully parsed and checked, and
     * its files unpacked to a staging folder, before a single row is touched; the database swap
     * is one transaction, so a bad file can't leave half the data behind.
     */
    suspend fun import(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        try {
            val b = read(source, staging)
            // Library templates must be in place first, or restored tasks would lose their templateId.
            repository.syncLibrary()
            val templateIds = db.templates().ids().toSet()
            val stagedFile = { rel: String -> File(staging, rel).takeIf { it.isFile } }
            // Files already on this phone count too (re-importing a rows-only export on the same phone).
            val haveFile = { rel: String -> stagedFile(rel) != null || File(context.filesDir, rel).isFile }
            val attachments = b.attachments.filter { haveFile(photos.attachmentPath(it.fileName)) }
            val vehicles = b.vehicles.map { v -> if (v.photoUri != null && !haveFile(v.photoUri)) v.copy(photoUri = null) else v }

            // Old files that the new data won't reference: removed only after the swap commits.
            val keep = (vehicles.mapNotNull { it.photoUri } + attachments.map { photos.attachmentPath(it.fileName) }).toSet()
            val oldFiles = (db.vehicles().all().mapNotNull { it.photoUri } + db.attachments().all().map { photos.attachmentPath(it.fileName) })
                .filter { it !in keep }

            // Staged files go in first; on a failed swap they're unreferenced and cleaned below.
            val moved = keep.mapNotNull { rel -> stagedFile(rel)?.let { src -> rel to src } }
            val added = mutableListOf<File>()
            for ((rel, src) in moved) {
                val dest = File(context.filesDir, rel).apply { parentFile?.mkdirs() }
                if (!dest.exists()) added += dest
                if (!src.renameTo(dest)) src.copyTo(dest, overwrite = true)
            }

            try { db.withTransaction {
                db.vehicles().deleteAll() // cascades every dependent row
                vehicles.forEach { db.vehicles().insert(it) }
                // Tasks and records reference each other: tasks first without lastRecordId, then fix up.
                db.tasks().insertAll(
                    b.tasks.map { it.copy(lastRecordId = null, templateId = it.templateId?.takeIf(templateIds::contains)) },
                )
                b.records.forEach { db.records().insert(it) }
                b.tasks.filter { it.lastRecordId != null }.forEach { t ->
                    db.tasks().get(t.id)?.let { db.tasks().update(it.copy(lastRecordId = t.lastRecordId)) }
                }
                b.parts.forEach { db.parts().insert(it) }
                attachments.forEach { db.attachments().insert(it) }
            } } catch (e: Exception) {
                added.forEach(File::delete) // the old data is untouched; don't leave the new files behind
                throw e
            }
            oldFiles.forEach(photos::delete)
            repository.recomputeAll() // statuses + reminders re-planned from the imported baselines
            ImportResult(vehicles.size, b.records.size, attachments.size, b.attachments.size - attachments.size)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Detects zip vs plain JSON by magic bytes; extracts files to [staging] when given. */
    private fun read(source: Uri, staging: File?): Backup {
        val input = resolver.openInputStream(source) ?: throw BackupException("Couldn't open that file.")
        return BufferedInputStream(input).use { s ->
            s.mark(4)
            val zip = s.read() == 'P'.code && s.read() == 'K'.code
            s.reset()
            if (zip) readZip(s, staging) else BackupCodec.decode(s.readBytes().decodeToString())
        }
    }

    private fun readZip(s: InputStream, staging: File?): Backup {
        var json: String? = null
        ZipInputStream(s).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.isDirectory -> Unit
                    e.name == Backup.ZIP_JSON_NAME -> json = zip.readBytes().decodeToString()
                    staging != null && e.name.startsWith(Backup.ZIP_FILES_DIR) -> {
                        val rel = e.name.removePrefix(Backup.ZIP_FILES_DIR)
                        // Only photos/<name> and attachments/<name>: no zip-slip, no surprises.
                        val parts = rel.split('/')
                        if (parts.size == 2 && parts[0] in setOf("photos", "attachments") && BackupCodec.safeName(parts[1])) {
                            val out = File(staging, rel).apply { parentFile?.mkdirs() }
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        }
        return BackupCodec.decode(json ?: throw BackupException("This zip doesn't contain a Maintenance Tracker backup."))
    }

    /** Settings › Data › Delete all data. Preferences (units, reminders) and Pro are kept. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.withTransaction { db.vehicles().deleteAll() }
        photos.deleteAllFiles()
        clearReminders()
    }

    data class Usage(val databaseBytes: Long, val photoBytes: Long, val attachmentBytes: Long) {
        val total get() = databaseBytes + photoBytes + attachmentBytes
    }

    suspend fun usage(): Usage = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        val dbBytes = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).sumOf { if (it.isFile) it.length() else 0L }
        val (p, a) = photos.usage()
        Usage(dbBytes, p, a)
    }
}
