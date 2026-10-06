package dev.dreamz.weekendfleet.data

import androidx.room.withTransaction
import dev.dreamz.weekendfleet.data.db.AppDatabase
import dev.dreamz.weekendfleet.domain.library.LibraryFile
import dev.dreamz.weekendfleet.domain.library.SetupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/** The two shipped library assets, decoded once on first use. */
class LibraryAssets(private val open: (String) -> InputStream) {
    val library: LibraryFile by lazy { LibraryFile.decode(read(LibraryFile.ASSET)) }
    val setup: SetupFile by lazy { SetupFile.decode(read(SetupFile.ASSET)) }

    private fun read(path: String) = open(path).use { it.readBytes().decodeToString() }
}

/**
 * Brings maintenance_template in line with the shipped library (IMPLEMENTATION.md §5 step 3).
 * Runs when the asset's dataVersion is newer than the last one applied: upserts every template and
 * retires seeded ids the library no longer has. Never touches maintenance_task.
 *
 * Upsert, not REPLACE: REPLACE deletes the row first, which would fire the task FK's ON DELETE
 * SET NULL and silently unlink users' tasks from their templates.
 */
class LibrarySync(
    private val db: AppDatabase,
    val assets: LibraryAssets,
    private val storedVersion: suspend () -> Int,
    private val setStoredVersion: suspend (Int) -> Unit,
) {
    private val mutex = Mutex()
    @Volatile private var synced = false

    /** Cheap after the first call in a process; safe to call before every template read. */
    suspend fun syncIfNeeded() {
        if (synced) return
        mutex.withLock {
            if (synced) return
            withContext(Dispatchers.IO) {
                val file = assets.library
                if (storedVersion() < file.dataVersion) {
                    val dao = db.templates()
                    db.withTransaction {
                        dao.upsertAll(file.templates.map { it.toEntity() })
                        val keep = file.templates.mapTo(HashSet()) { it.id }
                        // Chunked to stay under SQLite's bound-variable limit.
                        dao.seededIds().filterNot { it in keep }.chunked(500).forEach { dao.retire(it) }
                    }
                    setStoredVersion(file.dataVersion)
                }
            }
            synced = true
        }
    }
}
