package dev.maintracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maintracker.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
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
}
