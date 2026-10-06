package dev.dreamz.weekendfleet.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dreamz.weekendfleet.data.db.AppDatabase
import dev.dreamz.weekendfleet.data.model.Area
import dev.dreamz.weekendfleet.data.model.Part
import dev.dreamz.weekendfleet.data.model.ServiceRecord
import dev.dreamz.weekendfleet.data.model.TaskStatus
import dev.dreamz.weekendfleet.data.model.Vehicle
import dev.dreamz.weekendfleet.data.model.VehicleType
import dev.dreamz.weekendfleet.domain.library.isOfferable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** M1 acceptance: one of each vehicle type persists with its seeded tasks; cascade rules (§3.8). */
@RunWith(AndroidJUnit4::class)
class VehicleRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private var now = 1_790_000_000_000L

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = VehicleRepository(db, PhotoStore(ctx), testLibrarySync(db), clock = { now })
    }

    @After fun tearDown() = db.close()

    private fun draft(type: VehicleType) = Vehicle(
        name = "My ${type.label}", vehicleType = type, year = 2018, make = "Make", model = "Model",
        vinOrHin = "1ABCDEFG8421", currentMiles = 61_230.0, currentEngineHours = 812.0,
        currentGeneratorHours = 400.0, notes = "notes", createdAt = 0, updatedAt = 0,
    )

    @Test fun createEachType_fieldsAndSeededTasksPersist() = runTest {
        for (type in VehicleType.entries) {
            // What Setup could offer this unit: no break-in task on a 61k-mile rig.
            val templates = repo.templatesFor(type).filter { it.isOfferable(draft(type)) }
            assertTrue("$type has templates", templates.isNotEmpty())
            val skipped = templates.first().id
            val v = repo.createVehicle(draft(type), templates.map { it.id }.toSet() - skipped)

            val stored = repo.getVehicle(v.id)!!
            assertEquals(draft(type).copy(id = v.id, createdAt = now, updatedAt = now,
                metersSource = "manual", metersUpdatedAt = now), stored)

            val tasks = db.tasks().forVehicle(v.id)
            assertEquals(templates.size - 1, tasks.size)
            assertTrue(tasks.none { it.templateId == skipped })
            assertTrue(tasks.none { it.status == TaskStatus.OVERDUE })
        }
        assertEquals(VehicleType.entries.size, repo.observeSummaries(false).first().size)
    }

    @Test fun meterUpdate_startsIdleMeterClock_andArchiveHidesVehicle() = runTest {
        val type = VehicleType.ATV_UTV
        val unit = draft(type).copy(currentMiles = null, currentEngineHours = null)
        // Unknown meters: Setup offers no break-in (ONE_TIME, measured from 0) tasks.
        val v = repo.createVehicle(unit,
            repo.templatesFor(type).filter { it.isOfferable(unit) }.map { it.id }.toSet())
        assertTrue(db.tasks().forVehicle(v.id).filter { it.meterInterval != null }.all { it.dueMeter == null })

        repo.updateMeters(v.id, null, 50.0, null)
        val hourTasks = db.tasks().forVehicle(v.id).filter { it.meterInterval != null }
        assertTrue(hourTasks.isNotEmpty())
        hourTasks.forEach { assertEquals(50.0 + it.meterInterval!!, it.dueMeter!!, 0.0) }

        repo.setArchived(v.id, true)
        assertTrue(repo.observeSummaries(false).first().isEmpty())
        assertEquals(1, repo.observeSummaries(true).first().size)
    }

    @Test fun deleteVehicle_cascadesEverything() = runTest {
        val type = VehicleType.BOAT_POWER
        val v = repo.createVehicle(draft(type), repo.templatesFor(type).map { it.id }.toSet())
        val task = db.tasks().forVehicle(v.id).first()
        val rec = ServiceRecord(vehicleId = v.id, taskId = task.id, area = Area.ENGINE, title = "Oil",
            date = now, currency = "USD", createdAt = now)
        db.records().insert(rec)
        db.parts().insert(Part(vehicleId = v.id, taskId = task.id, name = "Filter", createdAt = now))

        repo.deleteVehicle(v.id)
        assertNull(repo.getVehicle(v.id))
        assertTrue(db.tasks().forVehicle(v.id).isEmpty())
        assertEquals(0, db.records().countForVehicle(v.id))
        assertEquals(0, db.parts().countForVehicle(v.id))
        // Templates are never deleted with a vehicle.
        assertTrue(repo.templatesFor(type).isNotEmpty())
    }

    @Test fun deleteTask_keepsRecordsAndParts() = runTest {
        val type = VehicleType.MOTORCYCLE
        val v = repo.createVehicle(draft(type), repo.templatesFor(type).map { it.id }.toSet())
        val task = db.tasks().forVehicle(v.id).first()
        val rec = ServiceRecord(vehicleId = v.id, taskId = task.id, area = Area.ENGINE, title = "Oil",
            date = now, currency = "USD", createdAt = now)
        val part = Part(vehicleId = v.id, taskId = task.id, name = "Filter", createdAt = now)
        db.records().insert(rec)
        db.parts().insert(part)

        db.tasks().delete(task)
        assertNull(assertNotNull(db.records().get(rec.id)).let { db.records().get(rec.id)!!.taskId })
        assertNull(db.parts().get(part.id)!!.taskId)
    }
}
