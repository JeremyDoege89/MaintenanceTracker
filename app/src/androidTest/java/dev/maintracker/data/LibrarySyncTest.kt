package dev.maintracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maintracker.data.db.AppDatabase
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySyncTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val assets = LibraryAssets(ctx.assets::open)
    private var version = 0
    private fun sync() = LibrarySync(db, assets, storedVersion = { version }, setStoredVersion = { version = it })

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private val oldTemplate = MaintenanceTemplate(
        id = "old:oil", vehicleType = VehicleType.MOTORHOME, area = Area.ENGINE, title = "Oil", description = "d",
        timeIntervalDays = 365, isSeeded = true, sortOrder = 0,
    )

    @Test fun firstSync_loadsEveryTemplate_andRecordsVersion() = runTest {
        sync().syncIfNeeded()
        val lib = assets.library
        assertEquals(lib.dataVersion, version)
        for (type in VehicleType.entries) {
            val expected = lib.templates.count { it.vehicleType == type && it.phase == 1 }
            assertEquals("$type", expected, db.templates().forType(type).size)
        }
    }

    @Test fun retiresGoneSeededIds_withoutUnlinkingTasks() = runTest {
        db.templates().upsertAll(listOf(oldTemplate))
        val v = Vehicle(name = "Rig", vehicleType = VehicleType.MOTORHOME, createdAt = 1, updatedAt = 1)
        db.vehicles().insert(v)
        val task = MaintenanceTask(vehicleId = v.id, templateId = oldTemplate.id, area = Area.ENGINE, title = "Oil",
            timeIntervalDays = 365, baselineDate = 1, status = TaskStatus.OK, leadDays = 14, createdAt = 1, updatedAt = 1)
        db.tasks().insertAll(listOf(task))

        sync().syncIfNeeded()
        assertTrue(db.templates().get(oldTemplate.id)!!.retired)
        assertTrue(db.templates().forType(VehicleType.MOTORHOME).none { it.id == oldTemplate.id })
        assertEquals(oldTemplate.id, db.tasks().get(task.id)!!.templateId)
    }

    @Test fun upToDateVersion_doesNothing() = runTest {
        version = assets.library.dataVersion
        sync().syncIfNeeded()
        assertTrue(db.templates().forType(VehicleType.MOTORHOME).isEmpty())
    }

    @Test fun resyncAfterVersionBump_isIdempotent() = runTest {
        sync().syncIfNeeded()
        val before = db.templates().forType(VehicleType.BOAT_POWER)
        version = 0
        sync().syncIfNeeded()
        assertEquals(before, db.templates().forType(VehicleType.BOAT_POWER))
    }
}
