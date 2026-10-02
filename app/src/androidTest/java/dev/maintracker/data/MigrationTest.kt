package dev.maintracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maintracker.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import dev.maintracker.data.db.MIGRATION_1_2
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
}
