package dev.maintracker.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maintracker.data.db.AppDatabase
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.AttachmentKind
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import dev.maintracker.domain.BackupException
import dev.maintracker.domain.MeterReadings
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * M5 acceptance (Build Guide §13 "Export fidelity"): export; wipe; import → all fields round-trip,
 * attachments included in the zip form. Runs on a throwaway files dir and an in-memory DB, so
 * the real app's data on the test phone is never touched.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var root: File
    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var photos: PhotoStore
    private lateinit var repo: VehicleRepository
    private lateinit var backups: BackupManager
    private var now = 1_790_000_000_000L

    @Before fun setUp() {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(real.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
        ctx = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getDatabasePath(name: String) = File(root, name)
        }
        db = Room.inMemoryDatabaseBuilder(real, AppDatabase::class.java).build()
        photos = PhotoStore(ctx)
        repo = VehicleRepository(db, photos, clock = { now })
        backups = BackupManager(ctx, db, photos, repo, clearReminders = {})
    }

    @After fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun tmp(name: String, content: ByteArray? = null) = File(root, name).also { f -> content?.let(f::writeBytes) }

    private suspend fun seed(): Triple<String, String, String> {
        repo.ensureSeedTemplates()
        val photo = "photos/hero.jpg"
        File(ctx.filesDir, "photos").mkdirs(); File(ctx.filesDir, photo).writeBytes(byteArrayOf(1, 2, 3))
        val v = repo.createVehicle(
            Vehicle(name = "Big Blue", vehicleType = VehicleType.MOTORHOME, year = 2018, make = "Winnebago", model = "Vista",
                vinOrHin = "1ABC8421", currentMiles = 61230.0, currentGeneratorHours = 812.0, photoUri = photo, notes = "n",
                createdAt = 0, updatedAt = 0),
            repo.templatesFor(VehicleType.MOTORHOME).take(3).map { it.id }.toSet(),
        )
        val task = MaintenanceTask(vehicleId = v.id, area = Area.ENGINE, title = "Oil", timeIntervalDays = 365, meterInterval = 5000,
            meterUnit = MeterUnit.MILES, baselineDate = now, baselineMeter = 60000.0, status = TaskStatus.OK, leadDays = 14,
            createdAt = now, updatedAt = now)
        repo.saveTask(task)
        repo.logService(ServiceEntry(vehicleId = v.id, taskId = task.id, title = "Oil + filter", area = Area.ENGINE,
            description = "5W-40", date = now, readings = MeterReadings(61230.0, null, null), cost = 89.5, currency = "USD",
            vendor = "Self", partsText = "Wix 51515", notes = "a, \"quoted\" note"))
        repo.savePart(Part(vehicleId = v.id, taskId = task.id, name = "Filter", partNumber = "51515", torqueSpec = "18 ft-lb", createdAt = now))
        val src = tmp("receipt.jpg", ByteArray(2048) { it.toByte() })
        val recordId = db.records().all().single().id
        val a = repo.addAttachment(v.id, Uri.fromFile(src), AttachmentKind.RECEIPT, "Receipt", recordId)!!
        return Triple(v.id, recordId, a.fileName)
    }

    private suspend fun dump() = listOf(
        db.vehicles().all().sortedBy { it.id }, db.tasks().all().sortedBy { it.id }, db.records().all().sortedBy { it.id },
        db.parts().all().sortedBy { it.id }, db.attachments().all().sortedBy { it.id },
    )

    @Test fun zipBackupRoundTripsRowsAndFiles() = runTest {
        val (_, _, fileName) = seed()
        val before = dump()
        val out = tmp("backup.zip")
        backups.exportBackup(Uri.fromFile(out), withFiles = true)
        assertTrue(out.length() > 2048)

        backups.deleteAll()
        assertTrue(db.vehicles().all().isEmpty())
        assertFalse(photos.attachmentFile(fileName).exists())

        val r = backups.import(Uri.fromFile(out))
        assertEquals(1, r.vehicles); assertEquals(1, r.attachments); assertEquals(0, r.skippedFiles)
        assertEquals(before, dump())
        assertEquals(2048L, photos.attachmentFile(fileName).length())
        assertTrue(File(ctx.filesDir, "photos/hero.jpg").exists())
    }

    @Test fun jsonBackupWithoutFilesSkipsMissingAttachments() = runTest {
        seed()
        val out = tmp("backup.json")
        backups.exportBackup(Uri.fromFile(out), withFiles = false)
        assertTrue(out.readText().contains("\"schemaVersion\": 1"))
        backups.deleteAll()
        val r = backups.import(Uri.fromFile(out))
        assertEquals(0, r.attachments); assertEquals(1, r.skippedFiles)
        assertEquals(null, db.vehicles().all().single().photoUri) // photo file wasn't in the export
        assertEquals(1, db.records().all().size)
    }

    @Test fun badFileChangesNothing() = runTest {
        seed()
        val before = dump()
        val junk = tmp("junk.json", "{\"app\":\"maintenance-tracker\",\"schemaVersion\":1,\"exportedAt\":\"x\",\"tasks\":[{}]}".toByteArray())
        try { backups.import(Uri.fromFile(junk)); fail("accepted junk") } catch (_: BackupException) {}
        assertEquals(before, dump())
    }

    @Test fun csvExportsHaveHeaderAndRows() = runTest {
        seed()
        val rec = tmp("h.csv"); val sch = tmp("s.csv")
        backups.exportRecordsCsv(Uri.fromFile(rec)); backups.exportTasksCsv(Uri.fromFile(sch))
        val lines = rec.readText().removePrefix("﻿").trim().split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[1], lines[1].contains("\"a, \"\"quoted\"\" note\""))
        assertEquals(1 + 4, sch.readText().trim().split("\r\n").size) // header + 3 seeded + 1 custom
    }

    @Test fun usageCountsFiles() = runTest {
        seed()
        val u = backups.usage()
        assertEquals(3L, u.photoBytes)
        assertEquals(2048L, u.attachmentBytes)
        assertNotNull(u)
    }
}
