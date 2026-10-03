package dev.maintracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maintracker.data.db.AppDatabase
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import dev.maintracker.domain.DueCalculator
import dev.maintracker.domain.MeterReadings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** M2 acceptance (Build Guide §12): completion recomputes due for time-only, meter-only and both. */
@RunWith(AndroidJUnit4::class)
class ServiceLogTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private var now = at(2026, 1, 1)
    private lateinit var v: Vehicle

    @Before fun setUp() = runTest {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = VehicleRepository(db, PhotoStore(ctx), testLibrarySync(db), clock = { now }, zone = { zone })
        v = repo.createVehicle(
            Vehicle(name = "Rig", vehicleType = VehicleType.MOTORHOME, currentMiles = 40_000.0,
                currentGeneratorHours = 100.0, createdAt = 0, updatedAt = 0),
            emptySet(),
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun custom(days: Int?, meter: Int?, unit: MeterUnit?): MaintenanceTask {
        val t = MaintenanceTask(
            vehicleId = v.id, area = Area.ENGINE, title = "T-$days-$meter", timeIntervalDays = days,
            meterInterval = meter, meterUnit = unit, baselineDate = now, baselineMeter = unit?.let { v.meter(it) },
            status = TaskStatus.OK, leadDays = 0, createdAt = 0, updatedAt = 0,
        )
        repo.saveTask(t)
        return db.tasks().get(t.id)!!
    }

    private fun entry(task: MaintenanceTask?, date: Long, r: MeterReadings = MeterReadings(), title: String = "Done") =
        ServiceEntry(v.id, task?.id, task?.area ?: Area.OTHER, title, null, date, r, 89.5, "USD", "Self", null, null)

    private fun day(ms: Long?) = DueCalculator.localDate(ms!!, zone)

    @Test fun complete_timeOnly_meterOnly_both() = runTest {
        val time = custom(182, null, null)
        val meter = custom(null, 5_000, MeterUnit.MILES)
        val both = custom(365, 12_000, MeterUnit.MILES)

        now = at(2026, 8, 1)
        repo.logService(entry(time, now))
        repo.logService(entry(meter, now, MeterReadings(miles = 44_000.0)))
        repo.logService(entry(both, now, MeterReadings(miles = 44_100.0)))

        val t = db.tasks().get(time.id)!!
        assertEquals(LocalDate.of(2027, 1, 30), day(t.dueDate))
        assertEquals(1, t.completedCount)
        val m = db.tasks().get(meter.id)!!
        assertEquals(49_000.0, m.dueMeter!!, 0.0)
        assertNull(m.dueDate)
        val b = db.tasks().get(both.id)!!
        assertEquals(LocalDate.of(2027, 8, 1), day(b.dueDate))
        assertEquals(56_100.0, b.dueMeter!!, 0.0)
        // Vehicle took the largest reading.
        assertEquals(44_100.0, repo.getVehicle(v.id)!!.currentMiles!!, 0.0)
        assertEquals("record", repo.getVehicle(v.id)!!.metersSource)
    }

    @Test fun meterDrivePastDue_dueSoonThenOverdue() = runTest {
        val t = custom(null, 3_000, MeterUnit.MILES) // due at 43,000; lead 300
        repo.logMeters(v.id, MeterReadings(miles = 42_750.0))
        assertEquals(TaskStatus.DUE_SOON, db.tasks().get(t.id)!!.status)
        repo.logMeters(v.id, MeterReadings(miles = 43_010.0))
        assertEquals(TaskStatus.OVERDUE, db.tasks().get(t.id)!!.status)
        assertTrue(repo.observeDue().first().any { it.task.id == t.id })
    }

    @Test fun manualTask_neverAutoDue() = runTest {
        val t = custom(null, null, null)
        now = at(2030, 1, 1)
        repo.recomputeAll()
        assertEquals(TaskStatus.MANUAL, db.tasks().get(t.id)!!.status)
    }

    @Test fun undo_restoresTaskVehicleAndRemovesRecord() = runTest {
        val t = custom(182, 5_000, MeterUnit.MILES)
        now = at(2026, 3, 1)
        val undo = repo.logService(entry(t, now, MeterReadings(miles = 41_000.0)))
        assertEquals(1, db.records().countForVehicle(v.id))
        repo.undo(undo)
        assertEquals(0, db.records().countForVehicle(v.id))
        val restored = db.tasks().get(t.id)!!
        assertEquals(t.baselineDate, restored.baselineDate)
        assertEquals(0, restored.completedCount)
        assertNull(restored.lastRecordId)
        assertEquals(40_000.0, repo.getVehicle(v.id)!!.currentMiles!!, 0.0)
    }

    @Test fun deleteRecord_fallsBackToPreviousCompletion_metersKept() = runTest {
        val t = custom(365, null, null)
        now = at(2026, 2, 1)
        repo.logService(entry(t, now, MeterReadings(miles = 41_000.0), "First"))
        now = at(2026, 6, 1)
        repo.logService(entry(t, now, MeterReadings(miles = 45_000.0), "Second"))
        val second = db.tasks().get(t.id)!!.lastRecordId!!

        val undo = repo.deleteRecord(second)!!
        val after = db.tasks().get(t.id)!!
        assertEquals(LocalDate.of(2026, 2, 1), day(after.baselineDate))
        assertEquals(LocalDate.of(2027, 2, 1), day(after.dueDate))
        assertEquals(1, after.completedCount)
        assertEquals(45_000.0, repo.getVehicle(v.id)!!.currentMiles!!, 0.0) // §3.8: no meter rollback

        repo.undo(undo)
        assertEquals(second, db.tasks().get(t.id)!!.lastRecordId)
        assertEquals(LocalDate.of(2027, 6, 1), day(db.tasks().get(t.id)!!.dueDate))
    }

    @Test fun updateRecord_movesLatestCompletionBaseline() = runTest {
        val t = custom(365, null, null)
        now = at(2026, 6, 1)
        repo.logService(entry(t, now))
        val rec = db.records().get(db.tasks().get(t.id)!!.lastRecordId!!)!!
        repo.updateRecord(rec.copy(date = at(2026, 5, 1)))
        assertEquals(LocalDate.of(2027, 5, 1), day(db.tasks().get(t.id)!!.dueDate))
    }

    @Test fun adHocRecord_andHistoryFilters() = runTest {
        val t = custom(365, null, null)
        repo.logService(entry(t, now, title = "Oil change"))
        now += 60_000
        repo.logService(ServiceEntry(v.id, null, Area.PLUMBING_WATER, "Replaced water pump", null, now,
            MeterReadings(), null, "USD", "Camping World", "Shurflo 4008", null))
        assertEquals(2, repo.observeHistory(v.id, null, null, "").first().size)
        assertEquals("Replaced water pump", repo.observeHistory(v.id, Area.PLUMBING_WATER, null, "").first().single().title)
        assertEquals("Oil change", repo.observeHistory(v.id, null, t.id, "").first().single().title)
        assertEquals(1, repo.observeHistory(v.id, null, null, "shurflo").first().size)
        assertEquals(1, repo.observeHistory(v.id, null, null, "camping").first().size)
        assertEquals(listOf("Camping World", "Self"), repo.observeRecentVendors().first())
    }

    @Test fun taskEditDisableDelete_andPartsCrud() = runTest {
        val t = custom(365, null, null)
        repo.saveTask(t.copy(timeIntervalDays = 30))
        val edited = db.tasks().get(t.id)!!
        assertEquals(LocalDate.of(2026, 1, 31), day(edited.dueDate))
        assertEquals(3, edited.leadDays)

        repo.setTaskActive(t.id, false)
        assertTrue(repo.observeDue().first().none { it.task.id == t.id })

        val part = Part(vehicleId = v.id, taskId = t.id, name = "Filter", partNumber = "51515", torqueSpec = "18 ft-lb",
            createdAt = now)
        repo.savePart(part)
        repo.savePart(part.copy(fluidSpec = "5 qt 15W-40"))
        assertEquals("5 qt 15W-40", repo.observePartsForTask(t.id).first().single().fluidSpec)

        repo.logService(entry(t, now))
        repo.deleteTask(t.id)
        assertNull(db.tasks().get(t.id))
        assertNull(repo.getPart(part.id)!!.taskId) // parts kept, unlinked
        assertEquals(1, db.records().countForVehicle(v.id)) // records kept

        repo.deletePart(part.id)
        assertTrue(repo.observeParts(v.id).first().isEmpty())
    }
}
