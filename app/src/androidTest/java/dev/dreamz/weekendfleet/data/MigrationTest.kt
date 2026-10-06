package dev.dreamz.weekendfleet.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dreamz.weekendfleet.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import dev.dreamz.weekendfleet.data.db.MIGRATION_1_2
import dev.dreamz.weekendfleet.data.db.MIGRATION_2_3
import dev.dreamz.weekendfleet.data.db.MIGRATION_3_4
import org.junit.runner.RunWith

/**
 * Build Guide §3.8: migration tests from M1 onward. With one schema version this proves the
 * exported 1.json matches the entities; each future bump adds a createDatabase(n) ->
 * runMigrationsAndValidate(n + 1) case here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun exportedSchemaMatchesEntities() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS).close()
    }

    /** v1 → v2 adds service_record.vendorType; existing rows keep everything and get NULL. */
    @Test
    fun migrate1To2KeepsRecords() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO vehicle (id, name, vehicleType, archived, createdAt, updatedAt) VALUES ('v', 'Rig', 'MOTORHOME', 0, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO service_record (id, vehicleId, area, title, date, cost, currency, vendor, createdAt) " +
                    "VALUES ('r', 'v', 'ENGINE', 'Oil', 5, 89.5, 'USD', 'Lazydays', 6)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT title, vendor, cost, vendorType FROM service_record WHERE id = 'r'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Oil", c.getString(0))
                assertEquals("Lazydays", c.getString(1))
                assertEquals(89.5, c.getDouble(2), 0.0)
                assertTrue(c.isNull(3))
            }
        }
    }

    /** v2 → v3 adds the library columns; old seeded templates and their tasks read as plain RECURRING. */
    @Test
    fun migrate2To3KeepsTemplatesAndTasks() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO vehicle (id, name, vehicleType, archived, createdAt, updatedAt) VALUES ('v', 'Rig', 'MOTORHOME', 0, 1, 1)")
            db.execSQL(
                "INSERT INTO maintenance_template (id, vehicleType, area, title, description, timeIntervalDays, defaultDueSoonPct, isSeeded, sortOrder) " +
                    "VALUES ('old:oil', 'MOTORHOME', 'ENGINE', 'Oil', 'd', 365, 0.1, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO maintenance_task (id, vehicleId, templateId, area, title, timeIntervalDays, dueSoonPct, baselineDate, status, " +
                    "leadDays, notifyEnabled, isActive, completedCount, sortOrder, createdAt, updatedAt) " +
                    "VALUES ('t', 'v', 'old:oil', 'ENGINE', 'Oil', 365, 0.1, 1, 'OK', 14, 1, 1, 0, 0, 1, 1)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT appliesTo, kind, phase, safetyCritical, retired, season FROM maintenance_template WHERE id = 'old:oil'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("all", c.getString(0))
                assertEquals("RECURRING", c.getString(1))
                assertEquals(1, c.getInt(2))
                assertEquals(0, c.getInt(3))
                assertEquals(0, c.getInt(4))
                assertTrue(c.isNull(5))
            }
            db.query("SELECT templateId, kind, safetyCritical, timeIntervalDays FROM maintenance_task WHERE id = 't'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("old:oil", c.getString(0))
                assertEquals("RECURRING", c.getString(1))
                assertEquals(0, c.getInt(2))
                assertEquals(365, c.getInt(3))
            }
            db.query("SELECT configTags, setupAnswers FROM vehicle WHERE id = 'v'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("", c.getString(0))
                assertTrue(c.isNull(1))
            }
        }
    }

    @Test
    fun migrate3To4AddsVinCacheAndKeepsVehicles() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                "INSERT INTO vehicle (id, name, vehicleType, vinOrHin, archived, createdAt, updatedAt, configTags) " +
                    "VALUES ('v', 'Wing', 'MOTORCYCLE', '1HGCM82633A004352', 0, 1, 1, '')",
            )
        }
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT name, vinOrHin FROM vehicle WHERE id = 'v'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Wing", c.getString(0))
                assertEquals("1HGCM82633A004352", c.getString(1))
            }
            db.query("SELECT COUNT(*) FROM vin_decode_cache").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }
}
